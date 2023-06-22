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

package me.moros.bending.paper.adapter.v1_19_R3;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.papermc.paper.adventure.PaperAdventure;
import me.moros.bending.api.event.BendingDamageEvent;
import me.moros.bending.api.locale.Message;
import me.moros.bending.api.platform.item.Item;
import me.moros.bending.api.platform.world.World;
import me.moros.bending.common.adapter.AbstractNativeAdapter;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.block.data.CraftBlockData;

public final class NativeAdapterImpl extends AbstractNativeAdapter {
  private final Function<me.moros.bending.api.platform.block.BlockState, BlockData> mapper;

  public NativeAdapterImpl(Function<me.moros.bending.api.platform.block.BlockState, BlockData> mapper) {
    super(MinecraftServer.getServer().getPlayerList());
    this.mapper = mapper;
  }

  @Override
  protected ServerLevel adapt(World world) {
    var bukkitWorld = Objects.requireNonNull(Bukkit.getWorld(world.name()));
    return ((CraftWorld) bukkitWorld).getHandle();
  }

  @Override
  protected BlockState adapt(me.moros.bending.api.platform.block.BlockState state) {
    return ((CraftBlockData) mapper.apply(state)).getState();
  }

  @Override
  protected ItemStack adapt(Item item) {
    return BuiltInRegistries.ITEM.get(new ResourceLocation(item.key().namespace(), item.key().value())).getDefaultInstance();
  }

  @Override
  protected Component adapt(net.kyori.adventure.text.Component component) {
    return PaperAdventure.asVanilla(component);
  }

  @Override
  protected int nextEntityId() {
    return net.minecraft.world.entity.Entity.nextEntityId();
  }

  @Override
  public boolean damage(BendingDamageEvent event, Function<String, Optional<TranslatableComponent>> translator) {
    // TODO remove once paper damage api is introduced
    var deathMsg = translator.apply(event.ability().deathKey()).orElseGet(Message.ABILITY_GENERIC_DEATH);
    var damageSource = new AbilityDamageSource(adapt(event.user().entity()), event.user(), event.ability(), deathMsg);
    return adapt(event.target()).hurt(damageSource, (float) event.damage());
  }
}
