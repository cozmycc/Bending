/*
 *   Copyright 2020-2021 Moros <https://github.com/PrimordialMoros>
 *
 *    This file is part of Bending.
 *
 *   Bending is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Bending is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with Bending.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.moros.bending.model.user;

import java.util.Optional;

import me.moros.atlas.caffeine.cache.Cache;
import me.moros.atlas.caffeine.cache.Caffeine;
import me.moros.atlas.caffeine.cache.Scheduler;
import me.moros.atlas.cf.checker.nullness.qual.NonNull;
import me.moros.atlas.cf.checker.nullness.qual.Nullable;
import me.moros.atlas.cf.common.value.qual.IntRange;
import me.moros.bending.Bending;
import me.moros.bending.events.BindChangeEvent;
import me.moros.bending.model.ability.description.AbilityDescription;
import me.moros.bending.model.ability.description.CooldownExpiry;
import me.moros.bending.model.predicate.general.BendingConditions;
import me.moros.bending.model.predicate.general.CompositeBendingConditional;
import me.moros.bending.model.preset.Preset;
import me.moros.bending.model.slots.AbilitySlotContainer;
import me.moros.bending.util.Tasker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class BendingUser implements User {
  private final ElementHolder elementHolder = new ElementHolder();
  private final AbilitySlotContainer slotContainer;
  private final Cache<AbilityDescription, Long> cooldowns;
  private final CompositeBendingConditional bendingConditional;
  private final LivingEntity entity;

  protected BendingUser(@NonNull LivingEntity entity) {
    this.entity = entity;
    cooldowns = Caffeine.newBuilder().expireAfter(new CooldownExpiry())
      .removalListener((key, value, cause) ->
        Tasker.simpleTask(() -> Bending.eventBus().postCooldownRemoveEvent(this, key), 0)
      )
      .scheduler(Scheduler.systemScheduler())
      .build();
    slotContainer = new AbilitySlotContainer();
    bendingConditional = BendingConditions.builder().build();
  }

  @Override
  public @NonNull LivingEntity entity() {
    return entity;
  }

  @Override
  public @NonNull ElementHolder elementHolder() {
    return elementHolder;
  }

  public @NonNull Preset createPresetFromSlots(String name) {
    return slotContainer.toPreset(name);
  }

  public int bindPreset(@NonNull Preset preset) {
    slotContainer.fromPreset(preset);
    validateSlots();
    if (this instanceof BendingPlayer) {
      Bending.game().boardManager().updateBoard((Player) entity());
    }
    Bending.eventBus().postBindChangeEvent(this, BindChangeEvent.Result.MULTIPLE);
    return preset.compare(createPresetFromSlots(""));
  }

  @Override
  public Optional<AbilityDescription> slotAbility(@IntRange(from = 1, to = 9) int slot) {
    return Optional.ofNullable(slotContainer.slot(slot));
  }

  @Override
  public void slotAbilityInternal(@IntRange(from = 1, to = 9) int slot, @Nullable AbilityDescription desc) {
    slotContainer.slot(slot, desc);
  }

  @Override
  public void slotAbility(@IntRange(from = 1, to = 9) int slot, @Nullable AbilityDescription desc) {
    slotAbilityInternal(slot, desc);
    if (this instanceof BendingPlayer) {
      Bending.game().boardManager().updateBoardSlot((Player) entity(), desc);
    }
    Bending.eventBus().postBindChangeEvent(this, BindChangeEvent.Result.SINGLE);
  }

  @Override
  public Optional<AbilityDescription> selectedAbility() {
    return Optional.empty(); // Non-player bending users don't have anything selected.
  }

  @Override
  public boolean isOnCooldown(@NonNull AbilityDescription desc) {
    return cooldowns.getIfPresent(desc) != null;
  }

  @Override
  public void addCooldown(@NonNull AbilityDescription desc, long duration) {
    if (duration <= 0) {
      return;
    }
    cooldowns.put(desc, duration);
    Bending.eventBus().postCooldownAddEvent(this, desc, duration);
  }

  @Override
  public @NonNull CompositeBendingConditional bendingConditional() {
    return bendingConditional;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof BendingUser) {
      return entity().equals(((BendingUser) obj).entity());
    }
    return entity().equals(obj);
  }

  @Override
  public int hashCode() {
    return entity.hashCode();
  }

  public static Optional<User> createUser(@NonNull LivingEntity entity) {
    if (entity instanceof Player) {
      return Optional.empty();
    }
    if (Bending.game().benderRegistry().isBender(entity)) {
      return Bending.game().benderRegistry().user(entity);
    }
    return Optional.of(new BendingUser(entity));
  }
}
