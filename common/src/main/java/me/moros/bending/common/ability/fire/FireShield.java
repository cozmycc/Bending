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

package me.moros.bending.common.ability.fire;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.ability.AbilityInstance;
import me.moros.bending.api.ability.Activation;
import me.moros.bending.api.collision.Collision;
import me.moros.bending.api.collision.CollisionUtil;
import me.moros.bending.api.collision.geometry.AABB;
import me.moros.bending.api.collision.geometry.Collider;
import me.moros.bending.api.collision.geometry.Disk;
import me.moros.bending.api.collision.geometry.OBB;
import me.moros.bending.api.collision.geometry.Sphere;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.platform.block.Block;
import me.moros.bending.api.platform.entity.Entity;
import me.moros.bending.api.platform.particle.ParticleBuilder;
import me.moros.bending.api.platform.sound.SoundEffect;
import me.moros.bending.api.temporal.TempLight;
import me.moros.bending.api.user.User;
import me.moros.bending.api.util.BendingEffect;
import me.moros.bending.api.util.ExpiringSet;
import me.moros.bending.api.util.functional.ExpireRemovalPolicy;
import me.moros.bending.api.util.functional.Policies;
import me.moros.bending.api.util.functional.RemovalPolicy;
import me.moros.bending.api.util.functional.SwappedSlotsRemovalPolicy;
import me.moros.bending.common.config.ConfigManager;
import me.moros.math.Rotation;
import me.moros.math.Vector3d;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public class FireShield extends AbilityInstance {
  private static final Config config = ConfigManager.load(Config::new);

  private User user;
  private Config userConfig;
  private RemovalPolicy removalPolicy;

  private final ExpiringSet<Entity> affectedEntities = new ExpiringSet<>(500);
  private Shield shield;
  private ThreadLocalRandom rand;

  private boolean sphere = false;

  public FireShield(AbilityDescription desc) {
    super(desc);
  }

  @Override
  public boolean activate(User user, Activation method) {
    if (user.game().abilityManager(user.worldKey()).hasAbility(user, FireShield.class)) {
      return false;
    }

    if (Policies.PARTIALLY_UNDER_WATER.test(user, description()) || Policies.PARTIALLY_UNDER_LAVA.test(user, description())) {
      return false;
    }

    this.user = user;
    loadConfig();

    rand = ThreadLocalRandom.current();
    if (method == Activation.SNEAK) {
      sphere = true;
      shield = new SphereShield();
      removalPolicy = Policies.builder()
        .add(SwappedSlotsRemovalPolicy.of(description()))
        .add(ExpireRemovalPolicy.of(userConfig.shieldDuration))
        .add(Policies.NOT_SNEAKING)
        .add(Policies.PARTIALLY_UNDER_WATER)
        .add(Policies.PARTIALLY_UNDER_LAVA)
        .build();
    } else {
      shield = new DiskShield();
      removalPolicy = Policies.builder()
        .add(SwappedSlotsRemovalPolicy.of(description()))
        .add(ExpireRemovalPolicy.of(userConfig.diskDuration))
        .add(Policies.PARTIALLY_UNDER_WATER)
        .add(Policies.PARTIALLY_UNDER_LAVA)
        .build();
    }

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
    shield.render();
    CollisionUtil.handle(user, shield.collider(), this::onEntityHit, false);
    shield.update();
    return UpdateResult.CONTINUE;
  }

  private boolean onEntityHit(Entity entity) {
    if (sphere && entity.isProjectile()) {
      entity.remove();
      return true;
    }
    BendingEffect.FIRE_TICK.apply(user, entity);
    if (!affectedEntities.contains(entity)) {
      entity.damage(userConfig.damage, user, description());
      affectedEntities.add(entity);
    }
    return false;
  }

  public boolean isSphere() {
    return sphere;
  }

  @Override
  public void onDestroy() {
    user.addCooldown(description(), sphere ? userConfig.shieldCooldown : userConfig.diskCooldown);
    if (sphere) {
      TempLight light = ((SphereShield) shield).light;
      if (light != null) {
        light.unlockAndRevert();
      }
    }
  }

  @Override
  public @MonotonicNonNull User user() {
    return user;
  }

  @Override
  public Collection<Collider> colliders() {
    return List.of(shield.collider());
  }

  @Override
  public void onCollision(Collision collision) {
    if (!sphere) {
      List<String> ignore = List.of("EarthBlast", "WaterManipulation");
      String collidedName = collision.collidedAbility().description().name();
      if (collision.removeOther() && ignore.contains(collidedName)) {
        collision.removeOther(false);
      }
    }
  }

  private interface Shield {
    void update();

    void render();

    Collider collider();
  }

  private final class DiskShield implements Shield {
    private Disk disk;
    private Vector3d location;
    private long nextRenderTime = 0;
    private int ticks = 6;

    private DiskShield() {
      update();
    }

    @Override
    public void update() {
      location = user.eyeLocation().add(user.direction().multiply(userConfig.diskRange));
      double r = userConfig.diskRadius;
      AABB aabb = new AABB(Vector3d.of(-r, -r, -1), Vector3d.of(r, r, 1));
      Vector3d right = user.rightSide();
      Rotation rotation = Rotation.from(Vector3d.PLUS_J, Math.toRadians(user.yaw()));
      rotation = rotation.applyTo(Rotation.from(right, Math.toRadians(user.pitch())));
      disk = new Disk(new OBB(aabb, rotation), new Sphere(userConfig.diskRadius)).at(location);
    }

    @Override
    public void render() {
      TempLight.builder(++ticks).rate(1).duration(200).build(user.world().blockAt(location));
      long time = System.currentTimeMillis();
      if (time < nextRenderTime) {
        return;
      }
      nextRenderTime = time + 200;
      Rotation rotation = Rotation.from(user.direction(), Math.toRadians(20));
      double[] array = Vector3d.PLUS_J.cross(user.direction()).normalize().toArray();
      for (int i = 0; i < 18; i++) {
        for (double j = 0.2; j <= 1; j += 0.2) {
          Vector3d spawnLoc = location.add(Vector3d.from(array).multiply(j * userConfig.diskRadius));
          ParticleBuilder.fire(user, spawnLoc).offset(0.15).extra(0.01).spawn(user.world());
          if (rand.nextInt(12) == 0) {
            SoundEffect.FIRE.play(user.world(), spawnLoc);
          }
        }
        rotation.applyTo(array, array);
      }
    }

    @Override
    public Collider collider() {
      return disk;
    }
  }

  private final class SphereShield implements Shield {
    private Sphere sphere;
    private int currentPoint = 0;
    private TempLight light;

    private SphereShield() {
      update();
    }

    @Override
    public Collider collider() {
      return sphere;
    }

    @Override
    public void update() {
      sphere = new Sphere(center(), userConfig.shieldRadius);
    }

    @Override
    public void render() {
      Vector3d center = center();
      double radius = userConfig.shieldRadius;
      currentPoint++;
      double spacing = radius / 16;
      for (int i = 1; i < 32; i++) {
        double y = (i * spacing) - radius;
        double factor = 1 - (y * y) / (radius * radius);
        if (factor <= 0.2) {
          continue;
        }
        double x = radius * factor * Math.cos(i * currentPoint);
        double z = radius * factor * Math.sin(i * currentPoint);
        Vector3d spawnLoc = center.add(x, y, z);
        ParticleBuilder.fire(user, spawnLoc).offset(0.1).extra(0.005).spawn(user.world());
        if (rand.nextInt(12) == 0) {
          SoundEffect.FIRE.play(user.world(), spawnLoc);
        }
      }
      Block block = user.world().blockAt(center);
      if (light == null || currentPoint <= 10) {
        createLight(block);
      } else if (!block.equals(light.block())) {
        light.unlockAndRevert();
        createLight(block);
      }
    }

    private void createLight(Block block) {
      light = TempLight.builder(5 + currentPoint).rate(1)
        .duration(userConfig.shieldDuration).build(block).map(TempLight::lock).orElse(null);
    }

    private Vector3d center() {
      return user.center();
    }
  }

  public static double shieldFromExplosion(User user, Vector3d source, double damage) {
    FireShield shield = user.game().abilityManager(user.worldKey()).userInstances(user, FireShield.class)
      .filter(FireShield::isSphere).findAny().orElse(null);
    if (shield == null) {
      return damage;
    }
    double distSq = source.distanceSq(user.center());
    double r = shield.userConfig.shieldRadius;
    if (distSq >= r * r) {
      return 0;
    } else {
      return 0.25 * damage;
    }
  }

  @ConfigSerializable
  private static class Config extends Configurable {
    @Modifiable(Attribute.DAMAGE)
    private double damage = 0.5;
    @Modifiable(Attribute.COOLDOWN)
    private long diskCooldown = 1000;
    @Modifiable(Attribute.DURATION)
    private long diskDuration = 1000;
    @Modifiable(Attribute.RADIUS)
    private double diskRadius = 2;
    @Modifiable(Attribute.RANGE)
    private double diskRange = 1.5;

    @Modifiable(Attribute.COOLDOWN)
    private long shieldCooldown = 2000;
    @Modifiable(Attribute.DURATION)
    private long shieldDuration = 10000;
    @Modifiable(Attribute.RADIUS)
    private double shieldRadius = 3;

    @Override
    public List<String> path() {
      return List.of("abilities", "fire", "fireshield");
    }
  }
}