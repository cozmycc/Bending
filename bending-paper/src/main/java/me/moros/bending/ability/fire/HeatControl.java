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

package me.moros.bending.ability.fire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import me.moros.bending.ability.common.basic.PhaseTransformer;
import me.moros.bending.config.ConfigManager;
import me.moros.bending.config.Configurable;
import me.moros.bending.game.temporal.TempBlock;
import me.moros.bending.game.temporal.TempLight;
import me.moros.bending.model.ability.AbilityInstance;
import me.moros.bending.model.ability.Activation;
import me.moros.bending.model.ability.description.AbilityDescription;
import me.moros.bending.model.attribute.Attribute;
import me.moros.bending.model.attribute.Modifiable;
import me.moros.bending.model.key.RegistryKey;
import me.moros.bending.model.math.Vector3d;
import me.moros.bending.model.predicate.removal.Policies;
import me.moros.bending.model.predicate.removal.RemovalPolicy;
import me.moros.bending.model.user.BendingPlayer;
import me.moros.bending.model.user.User;
import me.moros.bending.util.ColorPalette;
import me.moros.bending.util.ParticleUtil;
import me.moros.bending.util.WorldUtil;
import me.moros.bending.util.material.MaterialUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public class HeatControl extends AbilityInstance {
  private enum Light {ON, OFF}

  private static final Config config = ConfigManager.load(Config::new);

  private User user;
  private Config userConfig;
  private RemovalPolicy removalPolicy;

  private final Solidify solidify = new Solidify();
  private final Melt melt = new Melt();

  private boolean canLight = true;
  private TempLight light;
  private int ticks = 3;

  private long startTime;

  public HeatControl(AbilityDescription desc) {
    super(desc);
  }

  @Override
  public boolean activate(User user, Activation method) {
    this.user = user;
    loadConfig();
    removalPolicy = Policies.builder().build();
    startTime = System.currentTimeMillis();
    return true;
  }

  @Override
  public void loadConfig() {
    userConfig = user.game().configProcessor().calculate(this, config);
  }

  @Override
  public UpdateResult update() {
    if (removalPolicy.test(user, description()) || !user.canBend(description())) {
      solidify.clear();
      melt.clear();
      resetLight();
      return UpdateResult.CONTINUE;
    }
    melt.processQueue(1);
    long time = System.currentTimeMillis();
    if (description().equals(user.selectedAbility())) {
      Vector3d origin = user.mainHandSide();
      boolean cooking = user.sneaking() && isHoldingFood() && !MaterialUtil.isWater(origin.toBlock(user.world()));
      if (cooking) {
        if (startTime <= 0) {
          startTime = time;
        } else if (time > startTime + userConfig.cookInterval && cook()) {
          startTime = System.currentTimeMillis();
        }
        int freezeTicks = user.entity().getFreezeTicks();
        if (freezeTicks > 1) {
          user.entity().setFreezeTicks(freezeTicks - 2);
        }
        solidify.processQueue(1);
      } else {
        solidify.clear();
        startTime = 0;
      }
      Block head = user.headBlock();
      if (cooking || (canLight && head.getLightLevel() < 7)) {
        ParticleUtil.fire(user, origin).spawn(user.world());
        createLight(head);
      } else {
        resetLight();
      }
    } else {
      startTime = 0;
      resetLight();
    }
    return UpdateResult.CONTINUE;
  }

  private void createLight(Block block) {
    if (light != null && !block.equals(light.block())) {
      light.unlockAndRevert();
    }
    light = TempLight.builder(++ticks).rate(2).duration(0).build(block).map(TempLight::lock).orElse(null);
  }

  private void resetLight() {
    ticks = 3;
    if (light != null) {
      light.unlockAndRevert();
      light = null;
    }
  }

  private boolean isHoldingFood() {
    if (user instanceof BendingPlayer bendingPlayer) {
      return MaterialUtil.COOKABLE.containsKey(bendingPlayer.inventory().getItemInMainHand().getType());
    }
    return false;
  }

  private boolean cook() {
    if (user instanceof BendingPlayer bendingPlayer) {
      PlayerInventory inventory = bendingPlayer.inventory();
      Material cooked = MaterialUtil.COOKABLE.get(inventory.getItemInMainHand().getType());
      if (cooked != null) {
        inventory.addItem(new ItemStack(cooked)).values().forEach(item -> user.world().dropItem(user.headBlock().getLocation(), item));
        int amount = inventory.getItemInMainHand().getAmount();
        if (amount == 1) {
          inventory.clear(inventory.getHeldItemSlot());
        } else {
          inventory.getItemInMainHand().setAmount(amount - 1);
        }
        return true;
      }
    }
    return false;
  }

  private void act() {
    if (!user.canBend(description()) || user.onCooldown(description())) {
      return;
    }
    boolean acted = false;
    Vector3d center = user.rayTrace(userConfig.range).blocks(user.world()).position();
    Predicate<Block> predicate = b -> MaterialUtil.isFire(b) || MaterialUtil.isCampfire(b) || MaterialUtil.isMeltable(b);
    Predicate<Block> safe = b -> TempBlock.isBendable(b) && user.canBuild(b);
    List<Block> toMelt = new ArrayList<>();
    for (Block block : WorldUtil.nearbyBlocks(user.world(), center, userConfig.radius, predicate.and(safe))) {
      acted = true;
      if (MaterialUtil.isFire(block) || MaterialUtil.isCampfire(block)) {
        WorldUtil.tryExtinguishFire(user, block);
      } else if (MaterialUtil.isMeltable(block)) {
        toMelt.add(block);
      }
    }
    if (!toMelt.isEmpty()) {
      Collections.shuffle(toMelt);
      melt.fillQueue(toMelt);
    }
    if (acted) {
      user.addCooldown(description(), userConfig.cooldown);
    }
  }

  private void onSneak() {
    if (!user.canBend(description()) || user.onCooldown(description())) {
      return;
    }
    Vector3d center = user.rayTrace(userConfig.solidifyRange).ignoreLiquids(false).blocks(user.world()).position();
    if (solidify.fillQueue(getShuffledBlocks(user.world(), center, userConfig.solidifyRadius, MaterialUtil::isLava))) {
      user.addCooldown(description(), userConfig.cooldown);
    }
  }

  public static void act(User user) {
    if (user.selectedAbilityName().equals("HeatControl")) {
      user.game().abilityManager(user.world()).firstInstance(user, HeatControl.class).ifPresent(HeatControl::act);
    }
  }

  public static void onSneak(User user) {
    if (user.selectedAbilityName().equals("HeatControl")) {
      user.game().abilityManager(user.world()).firstInstance(user, HeatControl.class).ifPresent(HeatControl::onSneak);
    }
  }

  public static void toggleLight(User user) {
    if (user.selectedAbilityName().equals("HeatControl")) {
      var key = RegistryKey.create("heatcontrol-light", Light.class);
      if (user.store().canEdit(key)) {
        Light light = user.store().toggle(key, Light.ON);
        user.sendActionBar(Component.text("Light: " + light.name(), ColorPalette.TEXT_COLOR));
        user.game().abilityManager(user.world()).firstInstance(user, HeatControl.class).ifPresent(h -> h.canLight = light == Light.ON);
      }
    }
  }

  private Collection<Block> getShuffledBlocks(World world, Vector3d center, double radius, Predicate<Block> predicate) {
    List<Block> newBlocks = WorldUtil.nearbyBlocks(world, center, radius, predicate);
    newBlocks.removeIf(b -> !user.canBuild(b));
    Collections.shuffle(newBlocks);
    return newBlocks;
  }

  public static boolean canBurn(User user) {
    AbilityDescription selected = user.selectedAbility();
    if (selected == null) {
      return true;
    }
    return !selected.name().equals("HeatControl") || !user.canBend(selected);
  }

  @Override
  public void onDestroy() {
    resetLight();
  }

  @Override
  public @MonotonicNonNull User user() {
    return user;
  }

  private class Solidify extends PhaseTransformer {
    @Override
    protected boolean processBlock(Block block) {
      if (MaterialUtil.isLava(block) && TempBlock.isBendable(block)) {
        return WorldUtil.tryCoolLava(user, block);
      }
      return false;
    }
  }

  private class Melt extends PhaseTransformer {
    @Override
    protected boolean processBlock(Block block) {
      if (!TempBlock.isBendable(block)) {
        return false;
      }
      return WorldUtil.tryMelt(user, block);
    }
  }

  @ConfigSerializable
  private static class Config extends Configurable {
    @Modifiable(Attribute.COOLDOWN)
    private long cooldown = 2000;
    @Modifiable(Attribute.RANGE)
    private double range = 10;
    @Modifiable(Attribute.RADIUS)
    private double radius = 5;
    @Modifiable(Attribute.RANGE)
    private double solidifyRange = 5;
    @Modifiable(Attribute.RADIUS)
    private double solidifyRadius = 6;
    @Modifiable(Attribute.CHARGE_TIME)
    private long cookInterval = 2000;

    @Override
    public Iterable<String> path() {
      return List.of("abilities", "fire", "heatcontrol");
    }
  }
}

