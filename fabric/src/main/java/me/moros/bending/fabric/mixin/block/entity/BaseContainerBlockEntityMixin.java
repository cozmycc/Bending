/*
 * Copyright 2020-2024 Moros
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

package me.moros.bending.fabric.mixin.block.entity;

import java.util.Optional;

import me.moros.bending.api.platform.block.Lockable;
import me.moros.bending.fabric.event.ServerItemEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin implements Lockable {
  @Shadow
  private LockCode lockKey;

  @Override
  public Optional<String> lock() {
    return Optional.of(lockKey.key()).filter(s -> !s.isBlank());
  }

  @Override
  public void lock(String lock) {
    if (lock.isBlank()) {
      lockKey = LockCode.NO_LOCK;
    } else {
      lockKey = new LockCode(lock);
    }
  }

  @Inject(method = "canUnlock", at = @At(value = "HEAD"), cancellable = true)
  private static void bending$canUnlock(Player player, LockCode lock, net.minecraft.network.chat.Component name, CallbackInfoReturnable<Boolean> cir) {
    if (!player.isSpectator()) {
      var result = ServerItemEvents.ACCESS_LOCK.invoker().onAccess(player, lock.key(), player.getMainHandItem());
      if (result == TriState.TRUE) {
        cir.setReturnValue(true);
      } else if (result == TriState.FALSE) {
        cir.setReturnValue(false);
      }
    }
  }
}
