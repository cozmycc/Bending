/*
 *   Copyright 2020-2021 Moros <https://github.com/PrimordialMoros>
 *
 *    This file is part of Bending.
 *
 *   Bending is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Bending is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with Bending.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.moros.bending.ability.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import me.moros.bending.game.temporal.TempBlock;
import me.moros.bending.model.ability.Updatable;
import me.moros.bending.model.collision.geometry.AABB;
import me.moros.bending.model.math.Vector3d;
import me.moros.bending.model.user.User;
import me.moros.bending.util.BendingProperties;
import me.moros.bending.util.SoundUtil;
import me.moros.bending.util.collision.CollisionUtil;
import me.moros.bending.util.material.MaterialUtil;
import me.moros.bending.util.methods.BlockMethods;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.index.qual.Positive;
import org.checkerframework.checker.nullness.qual.NonNull;

public class Pillar implements Updatable {
  private final User user;
  private final Block origin;
  private final BlockFace direction;
  private final BlockFace opposite;
  private final Collection<Block> pillarBlocks;
  private final Predicate<Block> predicate;

  private final int length;
  private final int distance;
  private final long interval;
  private final long duration;

  private int currentDistance;
  private long nextUpdateTime;

  protected Pillar(@NonNull PillarBuilder builder) {
    this.user = builder.user;
    this.origin = builder.origin;
    this.direction = builder.direction;
    this.opposite = direction.getOppositeFace();
    this.length = builder.length;
    this.distance = builder.distance;
    this.interval = builder.interval;
    this.duration = builder.duration;
    this.predicate = builder.predicate;
    this.pillarBlocks = new ArrayList<>(length);
    currentDistance = 0;
    nextUpdateTime = 0;
  }

  @Override
  public @NonNull UpdateResult update() {
    if (currentDistance >= distance) {
      return UpdateResult.REMOVE;
    }

    Block start = origin.getRelative(direction, currentDistance + 1);
    Block finish = start.getRelative(opposite, length - 1);
    BoundingBox box = BoundingBox.of(start, finish).expand(direction, 0.65);
    AABB collider = new AABB(new Vector3d(box.getMin()), new Vector3d(box.getMax()));
    CollisionUtil.handleEntityCollisions(user, collider, this::onEntityHit, false, true); // Push entities

    long time = System.currentTimeMillis();
    if (time < nextUpdateTime) {
      return UpdateResult.CONTINUE;
    }

    nextUpdateTime = time + interval;
    Block currentIndex = origin.getRelative(direction, ++currentDistance);
    return move(currentIndex) ? UpdateResult.CONTINUE : UpdateResult.REMOVE;
  }

  private boolean move(Block newBlock) {
    if (MaterialUtil.isLava(newBlock)) {
      return false;
    }
    if (!MaterialUtil.isTransparentOrWater(newBlock)) {
      return false;
    }
    BlockMethods.tryBreakPlant(newBlock);

    SelectedSource.tryRevertSource(newBlock.getRelative(opposite));

    for (int i = 0; i < length; i++) {
      Block forwardBlock = newBlock.getRelative(opposite, i);
      Block backwardBlock = forwardBlock.getRelative(opposite);
      if (!predicate.test(backwardBlock)) {
        TempBlock.createAir(forwardBlock, duration);
        playSound(forwardBlock);
        return false;
      }
      BlockData data = TempBlock.getLastValidData(backwardBlock);
      TempBlock.create(forwardBlock, MaterialUtil.solidType(data), duration, true);
    }
    pillarBlocks.add(newBlock);
    TempBlock.createAir(newBlock.getRelative(opposite, length), duration);
    playSound(newBlock);
    return true;
  }

  protected @NonNull Vector3d normalizeVelocity(Vector3d velocity, double factor) {
    return switch (direction) {
      case NORTH, SOUTH -> velocity.setX(direction.getModX() * factor);
      case EAST, WEST -> velocity.setZ(direction.getModZ() * factor);
      default -> velocity.setY(direction.getModY() * factor);
    };
  }

  public @NonNull Collection<@NonNull Block> pillarBlocks() {
    return List.copyOf(pillarBlocks);
  }

  public @NonNull Block origin() {
    return origin;
  }

  public void playSound(@NonNull Block block) {
    SoundUtil.EARTH.play(block.getLocation());
  }

  public boolean onEntityHit(@NonNull Entity entity) {
    double factor = 0.75 * (length - 0.4 * currentDistance) / length;
    entity.setVelocity(normalizeVelocity(new Vector3d(entity.getVelocity()), factor).clampVelocity());
    return true;
  }

  public static @NonNull PillarBuilder builder(@NonNull User user, @NonNull Block origin) {
    return builder(user, origin, Pillar::new);
  }

  public static <T extends Pillar> @NonNull PillarBuilder builder(@NonNull User user, @NonNull Block origin, @NonNull Function<PillarBuilder, T> constructor) {
    return new PillarBuilder(user, origin, constructor);
  }

  public static class PillarBuilder {
    private final User user;
    private final Block origin;
    private final Function<PillarBuilder, ? extends Pillar> constructor;
    private BlockFace direction = BlockFace.UP;
    private int length;
    private int distance;
    private long interval = 125;
    private long duration = BendingProperties.EARTHBENDING_REVERT_TIME;
    private Predicate<Block> predicate = b -> true;

    public <T extends Pillar> PillarBuilder(@NonNull User user, @NonNull Block origin, @NonNull Function<PillarBuilder, T> constructor) {
      this.user = user;
      this.origin = origin;
      this.constructor = constructor;
    }

    public @NonNull PillarBuilder direction(@NonNull BlockFace direction) {
      if (!BlockMethods.FACES.contains(direction)) {
        throw new IllegalStateException("Pillar direction must be one of the 6 main BlockFaces!");
      }
      this.direction = direction;
      return this;
    }

    public @NonNull PillarBuilder interval(@NonNegative long interval) {
      this.interval = interval;
      return this;
    }

    public @NonNull PillarBuilder duration(@NonNegative long duration) {
      this.duration = duration;
      return this;
    }

    public @NonNull PillarBuilder predicate(@NonNull Predicate<Block> predicate) {
      this.predicate = predicate;
      return this;
    }

    public Optional<Pillar> build(@Positive int length) {
      return build(length, length);
    }

    public Optional<Pillar> build(@Positive int length, @Positive int distance) {
      int maxLength = validateLength(length);
      if (maxLength < 1) {
        return Optional.empty();
      }
      int maxDistance = validateDistance(distance);
      if (maxDistance < 1) {
        return Optional.empty();
      }
      this.length = maxLength;
      this.distance = Math.min(maxLength, maxDistance);
      return Optional.of(constructor.apply(this));
    }

    /**
     * Check region protections and return maximum valid length in blocks
     */
    private int validateLength(int max) {
      for (int i = 0; i < max; i++) {
        Block backwardBlock = origin.getRelative(direction.getOppositeFace(), i);
        if (!TempBlock.isBendable(backwardBlock) || !user.canBuild(backwardBlock) || !predicate.test(backwardBlock)) {
          return i;
        }
      }
      return max;
    }

    private int validateDistance(int max) {
      for (int i = 0; i < max; i++) {
        Block forwardBlock = origin.getRelative(direction, i + 1);
        if (!user.canBuild(forwardBlock)) {
          return i;
        }
      }
      return max;
    }
  }
}
