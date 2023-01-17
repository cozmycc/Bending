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

package me.moros.bending.platform.item;

import java.util.Collection;

import me.moros.bending.platform.PlatformAdapter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class FabricPlayerInventory extends FabricInventory {
  private final Inventory handle;

  public FabricPlayerInventory(ServerPlayer player) {
    super(player);
    this.handle = player.getInventory();
  }

  @Override
  public int selectedSlot() {
    return handle.selected;
  }

  @Override
  public boolean has(Item type, int amount) {
    var mat = PlatformAdapter.ITEM_MATERIAL_INDEX.key(type);
    return mat != null && handle.contains(new ItemStack(mat, amount));
  }

  @Override
  public int add(ItemSnapshot item) {
    var fabricItem = PlatformAdapter.toFabricItem(item);
    return handle.add(fabricItem) ? 0 : item.amount(); // TODO handle distribution and remaining amount
  }

  @Override
  public boolean remove(Item type, int amount) {
    var mat = PlatformAdapter.ITEM_MATERIAL_INDEX.key(type);
    if (mat != null) {
      int remaining = amount;
      if (remaining > 0) {
        remaining = removeFrom(handle.items, mat, remaining);
      }
      if (remaining > 0) {
        remaining = removeFrom(handle.armor, mat, remaining);
      }
      if (remaining > 0) {
        remaining = removeFrom(handle.offhand, mat, remaining);
      }
      return remaining <= 0;
    }
    return false;
  }

  private int removeFrom(Collection<ItemStack> collection, net.minecraft.world.item.Item type, int amount) {
    for (ItemStack item : collection) {
      if (!item.isEmpty() && item.is(type)) {
        if (item.getCount() >= amount) {
          item.setCount(item.getCount() - amount);
          return 0;
        } else {
          amount -= item.getCount();
        }
      }
    }
    return amount;
  }
}
