/*
 * Copyright 2020-2023 Moros
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

package me.moros.bending.ability.water.passive;

import me.moros.bending.model.ability.AbilityDescription;
import me.moros.bending.model.ability.AbilityInstance;
import me.moros.bending.model.ability.Activation;
import me.moros.bending.model.collision.geometry.AABB;
import me.moros.bending.model.user.User;
import me.moros.bending.util.material.WaterMaterials;
import me.moros.math.Vector3d;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public class HydroSink extends AbilityInstance {
  private User user;

  public HydroSink(AbilityDescription desc) {
    super(desc);
  }

  @Override
  public boolean activate(User user, Activation method) {
    this.user = user;
    loadConfig();
    return true;
  }

  @Override
  public void loadConfig() {
  }

  @Override
  public UpdateResult update() {
    return UpdateResult.CONTINUE;
  }

  private boolean canHydroSink() {
    if (!user.canBend(description())) {
      return false;
    }
    AABB entityBounds = user.bounds().grow(Vector3d.of(0, 0.2, 0));
    AABB floorBounds = new AABB(Vector3d.of(-1, -0.5, -1), Vector3d.of(1, 0, 1)).at(user.location());
    return user.world().nearbyBlocks(floorBounds, b -> entityBounds.intersects(b.bounds()))
      .stream().anyMatch(WaterMaterials::isWaterBendable);
  }

  public static boolean canHydroSink(User user) {
    return user.game().abilityManager(user.worldKey()).firstInstance(user, HydroSink.class)
      .map(HydroSink::canHydroSink).orElse(false);
  }

  @Override
  public @MonotonicNonNull User user() {
    return user;
  }
}
