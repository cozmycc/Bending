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

package me.moros.bending.fabric.mixin;

import me.moros.bending.fabric.event.ServerMobEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {
  @Shadow
  private LivingEntity target;

  @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
  private void onSetAttackTarget(@Nullable LivingEntity livingEntity, CallbackInfo ci) {
    Mob bending$this = (Mob) (Object) this;
    if (bending$this.level.isClientSide || livingEntity == null || target == livingEntity) {
      return;
    }
    if (!ServerMobEvents.TARGET.invoker().onEntityTarget(bending$this, livingEntity)) {
      ci.cancel();
    }
  }
}
