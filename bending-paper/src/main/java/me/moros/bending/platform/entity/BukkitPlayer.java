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

package me.moros.bending.platform.entity;

import me.moros.bending.platform.entity.player.GameMode;
import me.moros.bending.platform.entity.player.Player;
import me.moros.bending.platform.item.BukkitPlayerInventory;
import me.moros.bending.platform.item.Inventory;
import net.kyori.adventure.util.TriState;
import org.bukkit.inventory.MainHand;

public class BukkitPlayer extends BukkitLivingEntity implements Player {
  public BukkitPlayer(org.bukkit.entity.Player handle) {
    super(handle);
  }

  @Override
  public org.bukkit.entity.Player handle() {
    return (org.bukkit.entity.Player) super.handle();
  }

  @Override
  public boolean hasPermission(String permission) {
    return handle().hasPermission(permission);
  }

  @Override
  public Inventory inventory() {
    return new BukkitPlayerInventory(handle());
  }

  @Override
  public boolean valid() {
    return handle().isOnline();
  }

  @Override
  public boolean sneaking() {
    return handle().isSneaking();
  }

  @Override
  public void sneaking(boolean sneaking) {
    handle().setSneaking(sneaking);
  }

  @Override
  public TriState isRightHanded() {
    return TriState.byBoolean(handle().getMainHand() == MainHand.RIGHT);
  }

  @Override
  public GameMode gamemode() {
    return switch (handle().getGameMode()) {
      case SURVIVAL -> GameMode.SURVIVAL;
      case CREATIVE -> GameMode.CREATIVE;
      case ADVENTURE -> GameMode.ADVENTURE;
      case SPECTATOR -> GameMode.SPECTATOR;
    };
  }

  @Override
  public boolean canSee(Entity other) {
    return handle().canSee(((BukkitEntity) other).handle());
  }
}
