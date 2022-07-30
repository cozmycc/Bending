/*
 * Copyright 2020-2022 Moros
 *
 * This file is part of Bending.
 *
 * Bending is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bending is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bending. If not, see <https://www.gnu.org/licenses/>.
 */

package me.moros.bending.event;

import java.util.Objects;

import me.moros.bending.model.ability.AbilityDescription;
import me.moros.bending.model.math.Vector3d;
import me.moros.bending.model.user.User;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;

/**
 * Called when an ability attempts to alter the velocity of a LivingEntity.
 */
public class VelocityEvent extends BendingEvent implements AbilityEvent, Cancellable {
  private final User user;
  private final AbilityDescription desc;
  private final LivingEntity target;
  private Vector3d velocity;
  private boolean cancelled = false;

  VelocityEvent(User user, LivingEntity target, AbilityDescription desc, Vector3d velocity) {
    this.user = user;
    this.desc = desc;
    this.target = target;
    this.velocity = velocity;
  }

  @Override
  public User user() {
    return user;
  }

  @Override
  public AbilityDescription ability() {
    return desc;
  }

  /**
   * Provides the target whose velocity is being changed.
   * @return the LivingEntity target
   */
  public LivingEntity target() {
    return target;
  }

  /**
   * Provides the new velocity that will be applied to the target.
   * @return the new velocity
   */
  public Vector3d velocity() {
    return velocity;
  }

  /**
   * Sets the new velocity that will be applied to the target.
   * @param velocity the velocity to apply
   */
  public void velocity(Vector3d velocity) {
    this.velocity = Objects.requireNonNull(velocity);
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    this.cancelled = cancel;
  }
}