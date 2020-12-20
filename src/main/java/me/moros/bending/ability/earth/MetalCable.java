/*
 *   Copyright 2020 Moros <https://github.com/PrimordialMoros>
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

package me.moros.bending.ability.earth;

import me.moros.atlas.cf.checker.nullness.qual.NonNull;
import me.moros.atlas.configurate.commented.CommentedConfigurationNode;
import me.moros.bending.Bending;
import me.moros.bending.config.Configurable;
import me.moros.bending.game.temporal.BendingFallingBlock;
import me.moros.bending.game.temporal.TempBlock;
import me.moros.bending.model.ability.Ability;
import me.moros.bending.model.ability.AbilityInstance;
import me.moros.bending.model.ability.description.AbilityDescription;
import me.moros.bending.model.ability.util.ActivationMethod;
import me.moros.bending.model.ability.util.UpdateResult;
import me.moros.bending.model.attribute.Attribute;
import me.moros.bending.model.collision.Collider;
import me.moros.bending.model.collision.Collision;
import me.moros.bending.model.collision.geometry.AABB;
import me.moros.bending.model.collision.geometry.Ray;
import me.moros.bending.model.collision.geometry.Sphere;
import me.moros.bending.model.math.Vector3;
import me.moros.bending.model.predicate.removal.OutOfRangeRemovalPolicy;
import me.moros.bending.model.predicate.removal.Policies;
import me.moros.bending.model.predicate.removal.RemovalPolicy;
import me.moros.bending.model.predicate.removal.SwappedSlotsRemovalPolicy;
import me.moros.bending.model.user.User;
import me.moros.bending.util.BendingProperties;
import me.moros.bending.util.DamageUtil;
import me.moros.bending.util.Metadata;
import me.moros.bending.util.ParticleUtil;
import me.moros.bending.util.SoundUtil;
import me.moros.bending.util.collision.CollisionUtil;
import me.moros.bending.util.material.MaterialUtil;
import me.moros.bending.util.methods.UserMethods;
import me.moros.bending.util.methods.WorldMethods;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MetalCable extends AbilityInstance implements Ability {
	private static final Set<Material> armorItems = EnumSet.of(
		Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
		Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
		Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS
	);
	private static final AABB BOX = AABB.BLOCK_BOUNDS.grow(new Vector3(0.25, 0.25, 0.25));

	private static final Config config = new Config();

	private User user;
	private Config userConfig;
	private RemovalPolicy removalPolicy;

	private final Collection<Vector3> pointLocations = new ArrayList<>();
	private Vector3 location;
	private Arrow cable;
	private CableTarget target;
	private BendingFallingBlock projectile;

	private boolean hasHit = false;
	private boolean launched = false;
	private int ticks;

	public MetalCable(@NonNull AbilityDescription desc) {
		super(desc);
	}

	@Override
	public boolean activate(@NonNull User user, @NonNull ActivationMethod method) {
		if (!user.hasPermission("bending.metal")) return false;

		if (Bending.getGame().getAbilityManager(user.getWorld()).hasAbility(user, MetalCable.class)) {
			return false;
		}

		this.user = user;
		recalculateConfig();

		return launchCable();
	}

	@Override
	public void recalculateConfig() {
		userConfig = Bending.getGame().getAttributeSystem().calculate(this, config);
	}

	@Override
	public @NonNull UpdateResult update() {
		if (removalPolicy.test(user, getDescription())) {
			return UpdateResult.REMOVE;
		}
		ticks++;
		if (launched) return updateProjectile();
		if (cable == null || !cable.isValid()) {
			return UpdateResult.REMOVE;
		}
		location = new Vector3(cable.getLocation());
		double distance = user.getLocation().distance(location);
		if (hasHit) {
			if (!handleMovement(distance)) return UpdateResult.REMOVE;
		}
		return visualizeLine(distance) ? UpdateResult.CONTINUE : UpdateResult.REMOVE;
	}

	private UpdateResult updateProjectile() {
		if (projectile == null || !projectile.getFallingBlock().isValid()) {
			return UpdateResult.REMOVE;
		}
		location = new Vector3(projectile.getFallingBlock().getLocation());
		if (ticks % 4 == 0) {
			if (CollisionUtil.handleEntityCollisions(user, BOX.at(location), this::onProjectileHit, true)) {
				BlockData bd = projectile.getFallingBlock().getBlockData();
				ParticleUtil.create(Particle.BLOCK_CRACK, location.toLocation(user.getWorld())).count(4)
					.offset(0.25, 0.15, 0.25).data(bd).spawn();
				ParticleUtil.create(Particle.BLOCK_DUST, location.toLocation(user.getWorld())).count(6)
					.offset(0.25, 0.15, 0.25).data(bd).spawn();
				return UpdateResult.REMOVE;
			}
		}
		return UpdateResult.CONTINUE;
	}

	private boolean onProjectileHit(Entity entity) {
		DamageUtil.damageEntity(entity, user, userConfig.damage, getDescription());
		return true;
	}

	private boolean handleMovement(double distance) {
		if (target == null || !target.isValid(user)) return false;
		Entity entityToMove = user.getEntity();
		Vector3 targetLocation = location;
		if (target.getType() == MetalCable.CableTarget.Type.ENTITY) {
			cable.teleport(target.getEntity().getLocation());
			if (user.isSneaking() || projectile != null) {
				entityToMove = target.getEntity();
				Ray ray = user.getRay(distance / 2);
				targetLocation = ray.origin.add(ray.direction);
			}
		}
		Vector3 direction = targetLocation.subtract(new Vector3(entityToMove.getLocation())).normalize();
		if (distance > 3) {
			entityToMove.setVelocity(direction.scalarMultiply(0.8).clampVelocity());
		} else {
			if (target.getType() == MetalCable.CableTarget.Type.ENTITY) {
				entityToMove.setVelocity(new Vector());
				if (target.getEntity() instanceof FallingBlock) {
					FallingBlock fb = (FallingBlock) target.getEntity();
					Location tempLocation = fb.getLocation().add(0, 0.5, 0);
					ParticleUtil.create(Particle.BLOCK_CRACK, tempLocation).count(4)
						.offset(0.25, 0.15, 0.25).data(fb.getBlockData()).spawn();
					ParticleUtil.create(Particle.BLOCK_DUST, tempLocation).count(6)
						.offset(0.25, 0.15, 0.25).data(fb.getBlockData()).spawn();
					target.getEntity().remove();
				}
				return false;
			} else {
				if (distance <= 3 && distance > 1.5) {
					entityToMove.setVelocity(direction.scalarMultiply(0.35).clampVelocity());
				} else {
					user.getEntity().setVelocity(new Vector(0, 0.5, 0));
					return false;
				}
			}
		}

		return true;
	}

	private boolean launchCable() {
		if (!hasRequiredInv()) return false;

		Location targetLocation = WorldMethods.getTargetEntity(user, userConfig.range)
			.map(LivingEntity::getEyeLocation)
			.orElseGet(() -> WorldMethods.getTarget(user.getWorld(), user.getRay(userConfig.range)));

		if (targetLocation.getBlock().isLiquid()) {
			return false;
		}

		Vector3 origin = UserMethods.getMainHandSide(user);
		Vector dir = targetLocation.toVector().subtract(origin.toVector()).normalize();
		Arrow arrow = user.getWorld().spawnArrow(origin.toLocation(user.getWorld()), dir, 1.8f, 0);
		arrow.setShooter(user.getEntity());
		arrow.setGravity(false);
		arrow.setInvulnerable(true);
		arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
		arrow.setMetadata(Metadata.METAL_CABLE, Metadata.customMetadata(this));
		cable = arrow;
		location = new Vector3(cable.getLocation());
		SoundUtil.METAL_SOUND.play(arrow.getLocation());

		removalPolicy = Policies.builder()
			.add(new SwappedSlotsRemovalPolicy(getDescription()))
			.add(new OutOfRangeRemovalPolicy(userConfig.range, origin, () -> location))
			.build();
		user.setCooldown(getDescription(), userConfig.cooldown);
		return true;
	}

	private boolean visualizeLine(double distance) {
		if (ticks % 2 == 0) return true;
		pointLocations.clear();
		pointLocations.addAll(getLinePoints(UserMethods.getMainHandSide(user), location, NumberConversions.ceil(distance * 2)));
		int counter = 0;
		for (Vector3 temp : pointLocations) {
			Block block = temp.toBlock(user.getWorld());
			if (block.isLiquid() || !MaterialUtil.isTransparent(block)) {
				if (++counter > 2) {
					return false;
				}
			}
			ParticleUtil.createRGB(temp.toLocation(user.getWorld()), "444444").spawn();
		}
		return true;
	}

	private Collection<Vector3> getLinePoints(Vector3 startLoc, Vector3 endLoc, int points) {
		Vector3 diff = endLoc.subtract(startLoc).scalarMultiply(1.0 / points);
		return IntStream.rangeClosed(1, points).mapToObj(i -> startLoc.add(diff.scalarMultiply(i)))
			.collect(Collectors.toList());
	}

	public void setHitBlock(@NonNull Block block) {
		if (target != null) return;
		if (!Bending.getGame().getProtectionSystem().canBuild(user, block)) {
			remove();
			return;
		}
		if (user.isSneaking() && !MaterialUtil.isUnbreakable(block)) {
			BlockData data = block.getState().getBlockData();
			TempBlock.create(block, Material.AIR, BendingProperties.EARTHBENDING_REVERT_TIME, true);
			Vector3 velocity = user.getEyeLocation().subtract(location).normalize().scalarMultiply(0.2);
			projectile = new BendingFallingBlock(block, data, velocity, true, 30000);
			target = new CableTarget(projectile.getFallingBlock());
		} else {
			target = new CableTarget(block);
		}
		hasHit = true;
	}

	public void setHitEntity(@NonNull Entity entity) {
		if (target != null) return;
		if (!Bending.getGame().getProtectionSystem().canBuild(user, entity.getLocation().getBlock())) {
			remove();
			return;
		}
		target = new CableTarget(entity);
		hasHit = true;
	}

	private boolean hasRequiredInv() {
		if (user.getEntity().getEquipment() != null) {
			for (ItemStack item : user.getEntity().getEquipment().getArmorContents()) {
				if (armorItems.contains(item.getType())) return true;
			}
		}
		return user.getInventory().map(itemStacks -> itemStacks.contains(Material.IRON_INGOT)).orElse(false);
	}

	public static void attemptDestroy(User user) {
		Location center = user.getEntity().getEyeLocation();
		Predicate<Entity> predicate = e -> e.hasMetadata(Metadata.METAL_CABLE);
		for (Entity entity : center.getNearbyEntitiesByType(Arrow.class, 3, predicate)) {
			MetalCable ability = (MetalCable) entity.getMetadata(Metadata.METAL_CABLE).get(0).value();
			if (ability != null && !entity.equals(ability.getUser().getEntity())) {
				ability.remove();
				return;
			}
		}
	}

	public static void launch(User user) {
		if (user.getSelectedAbility().map(AbilityDescription::getName).orElse("").equals("MetalCable")) {
			Bending.getGame().getAbilityManager(user.getWorld()).getFirstInstance(user, MetalCable.class).ifPresent(MetalCable::attemptLaunchTarget);
		}
	}

	private void remove() {
		removalPolicy = (u, d) -> true; // Remove in next tick
	}

	public void attemptLaunchTarget() {
		if (launched || target == null || target.getType() == MetalCable.CableTarget.Type.BLOCK) return;

		launched = true;
		Vector3 targetLocation = new Vector3(WorldMethods.getTargetEntity(user, userConfig.projectileRange)
			.map(LivingEntity::getEyeLocation)
			.orElseGet(() -> WorldMethods.getTarget(user.getWorld(), user.getRay(userConfig.projectileRange))));

		Vector3 velocity = targetLocation.subtract(location).normalize().scalarMultiply(userConfig.blockSpeed);
		target.getEntity().setVelocity(velocity.clampVelocity());
		if (target.getEntity() instanceof FallingBlock) {
			removalPolicy = Policies.builder()
				.add(new OutOfRangeRemovalPolicy(userConfig.projectileRange, location, () -> location))
				.build();
			onDestroy();
		} else {
			remove();
		}
	}

	@Override
	public void onDestroy() {
		if (cable != null) cable.remove();
	}

	@Override
	public @NonNull User getUser() {
		return user;
	}

	@Override
	public @NonNull Collection<@NonNull Collider> getColliders() {
		return Collections.singletonList(new Sphere(location, 0.8));
	}

	@Override
	public void onCollision(@NonNull Collision collision) {
		if (collision.shouldRemoveFirst()) {
			Bending.getGame().getAbilityManager(user.getWorld()).destroyInstance(user, this);
		}
	}

	public static class CableTarget {
		private enum Type {ENTITY, BLOCK}

		private final Type type;
		private final Entity entity;
		private final Block block;
		private final Material material;

		public CableTarget(Entity entity) {
			block = null;
			material = null;
			this.entity = entity;
			type = Type.ENTITY;
		}

		public CableTarget(Block block) {
			entity = null;
			this.block = block;
			material = block.getType();
			type = Type.BLOCK;
		}

		public Type getType() {
			return type;
		}

		public Entity getEntity() {
			return entity;
		}

		public Block getBlock() {
			return block;
		}

		public boolean isValid(User u) {
			if (type == Type.ENTITY) {
				return entity != null && entity.isValid() && entity.getWorld().equals(u.getWorld());
			} else {
				return block.getType() == material;
			}
		}
	}

	public static class Config extends Configurable {
		@Attribute(Attribute.COOLDOWN)
		public long cooldown;
		@Attribute(Attribute.RANGE)
		public double range;
		@Attribute(Attribute.RANGE)
		public double projectileRange;
		@Attribute(Attribute.DAMAGE)
		private double damage;
		@Attribute(Attribute.SPEED)
		private double blockSpeed;

		@Override
		public void onConfigReload() {
			CommentedConfigurationNode abilityNode = config.getNode("abilities", "earth", "metalcable");

			cooldown = abilityNode.getNode("cooldown").getLong(5000);
			range = abilityNode.getNode("range").getDouble(28.0);
			projectileRange = abilityNode.getNode("projectile-range").getDouble(48.0);
			damage = abilityNode.getNode("damage").getDouble(4.0);
			blockSpeed = abilityNode.getNode("speed").getDouble(1.4);
		}
	}
}