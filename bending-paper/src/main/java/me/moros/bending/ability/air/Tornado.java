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

package me.moros.bending.ability.air;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import me.moros.bending.config.ConfigManager;
import me.moros.bending.config.Configurable;
import me.moros.bending.model.ability.AbilityInstance;
import me.moros.bending.model.ability.Activation;
import me.moros.bending.model.ability.description.AbilityDescription;
import me.moros.bending.model.attribute.Attribute;
import me.moros.bending.model.attribute.Modifiable;
import me.moros.bending.model.collision.geometry.AABB;
import me.moros.bending.model.key.RegistryKey;
import me.moros.bending.model.math.Vector3d;
import me.moros.bending.model.predicate.removal.ExpireRemovalPolicy;
import me.moros.bending.model.predicate.removal.Policies;
import me.moros.bending.model.predicate.removal.RemovalPolicy;
import me.moros.bending.model.predicate.removal.SwappedSlotsRemovalPolicy;
import me.moros.bending.model.user.User;
import me.moros.bending.util.ColorPalette;
import me.moros.bending.util.EntityUtil;
import me.moros.bending.util.ParticleUtil;
import me.moros.bending.util.SoundUtil;
import me.moros.bending.util.collision.CollisionUtil;
import me.moros.bending.util.material.MaterialUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public class Tornado extends AbilityInstance {
  public enum Mode {PUSH, PULL}

  private static final Config config = ConfigManager.load(Config::new);

  private User user;
  private Config userConfig;
  private RemovalPolicy removalPolicy;

  private Mode mode;

  private double yOffset = 0;
  private double currentAngle = 0;
  private long startTime;

  public Tornado(AbilityDescription desc) {
    super(desc);
  }

  @Override
  public boolean activate(User user, Activation method) {
    this.user = user;
    loadConfig();
    removalPolicy = Policies.builder()
      .add(SwappedSlotsRemovalPolicy.of(description()))
      .add(ExpireRemovalPolicy.of(userConfig.duration))
      .add(Policies.NOT_SNEAKING)
      .add(Policies.UNDER_WATER)
      .add(Policies.UNDER_LAVA)
      .build();
    mode = user.store().getOrDefault(RegistryKey.create("tornado-mode", Mode.class), Mode.PUSH);
    startTime = System.currentTimeMillis();
    return true;
  }

  @Override
  public void loadConfig() {
    userConfig = user.game().configProcessor().calculate(this, config);
  }

  @Override
  public UpdateResult update() {
    if (removalPolicy.test(user, description())) {
      return UpdateResult.REMOVE;
    }
    Vector3d base = user.rayTrace(userConfig.range).ignoreLiquids(false).blocks(user.world()).position();
    Block baseBlock = base.toBlock(user.world());
    if (MaterialUtil.isTransparent(baseBlock.getRelative(BlockFace.DOWN))) {
      return UpdateResult.CONTINUE;
    }
    if (!user.canBuild(baseBlock)) {
      return UpdateResult.REMOVE;
    }

    long time = System.currentTimeMillis();
    double factor = Math.min(1, (time - startTime) / (double) userConfig.growthTime);
    double height = 2 + factor * (userConfig.height - 2);
    double radius = 2 + factor * (userConfig.radius - 2);
    double rBox = 0.6 * radius;
    AABB box = new AABB(new Vector3d(-rBox, 0, -rBox), new Vector3d(rBox, height, rBox)).at(base);
    CollisionUtil.handle(user, box, entity -> {
      double dy = entity.getLocation().getY() - base.y();
      double r = 0.5 + (radius - 0.5) * dy;
      Vector3d delta = EntityUtil.entityCenter(entity).subtract(base);
      double distSq = delta.x() * delta.x() + delta.z() * delta.z();
      if (distSq > r * r) {
        return false;
      }
      Vector3d velocity;
      if (entity.equals(user.entity())) {
        double velY;
        if (dy >= height * .95) {
          velY = 0;
        } else if (dy >= height * .85) {
          velY = 6.0 * (.95 - dy / height);
        } else {
          velY = 0.6;
        }
        velocity = user.direction().withY(velY).multiply(factor);
      } else {
        if (mode == Mode.PUSH) {
          Vector3d normal = delta.withY(0).normalize();
          Vector3d ortho = normal.cross(Vector3d.PLUS_J).normalize();
          velocity = ortho.add(normal).normalize().add(new Vector3d(0, 0.5, 0)).multiply(factor);
        } else {
          velocity = delta.add(new Vector3d(0, 0.75 * height, 0)).normalize().multiply(factor);
        }
      }
      EntityUtil.applyVelocity(this, entity, velocity);
      return false;
    }, true, true);

    render(base, factor, height, radius);
    return UpdateResult.CONTINUE;
  }

  private void render(Vector3d base, double factor, double height, double radius) {
    double amount = Math.min(30, Math.max(4, factor * 30));
    yOffset += 0.1;
    if (yOffset >= 1) {
      yOffset = 0;
    }
    currentAngle += 4.5;
    if (currentAngle >= 360) {
      currentAngle = 0;
    }
    for (int i = 0; i < 3; i++) {
      double offset = currentAngle + i * 2 * Math.PI / 3.0;
      for (double y = yOffset; y < height; y += (height / amount)) {
        double r = 0.5 + (radius - 0.5) * y / height;
        double x = r * Math.cos(y + offset);
        double z = r * Math.sin(y + offset);
        Vector3d loc = base.add(new Vector3d(x, y, z));
        ParticleUtil.air(loc).spawn(user.world());
        if (ThreadLocalRandom.current().nextInt(20) == 0) {
          SoundUtil.AIR.play(user.world(), loc);
        }
      }
    }
  }

  @Override
  public void onDestroy() {
    user.addCooldown(description(), userConfig.cooldown);
  }

  @Override
  public @MonotonicNonNull User user() {
    return user;
  }

  public static void switchMode(User user) {
    if (user.selectedAbilityName().equals("Tornado")) {
      var key = RegistryKey.create("tornado-mode", Mode.class);
      if (user.store().canEdit(key)) {
        Mode mode = user.store().toggle(key, Mode.PUSH);
        user.sendActionBar(Component.text("Mode: " + mode.name(), ColorPalette.TEXT_COLOR));
        user.game().abilityManager(user.world()).firstInstance(user, Tornado.class).ifPresent(t -> t.mode = mode);
      }
    }
  }

  @ConfigSerializable
  private static class Config extends Configurable {
    @Modifiable(Attribute.COOLDOWN)
    private long cooldown = 4000;
    @Modifiable(Attribute.DURATION)
    private long duration = 8000;
    @Modifiable(Attribute.RADIUS)
    private double radius = 8;
    @Modifiable(Attribute.HEIGHT)
    private double height = 12;
    @Modifiable(Attribute.RANGE)
    private double range = 16;
    private long growthTime = 3000;

    @Override
    public Iterable<String> path() {
      return List.of("abilities", "air", "tornado");
    }
  }
}
