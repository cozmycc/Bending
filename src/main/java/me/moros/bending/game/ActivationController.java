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

package me.moros.bending.game;

import me.moros.atlas.cf.checker.nullness.qual.NonNull;
import me.moros.atlas.expiringmap.ExpirationPolicy;
import me.moros.atlas.expiringmap.ExpiringMap;
import me.moros.bending.ability.air.*;
import me.moros.bending.ability.air.passives.*;
import me.moros.bending.ability.air.sequences.*;
import me.moros.bending.ability.earth.*;
import me.moros.bending.ability.earth.passives.*;
import me.moros.bending.ability.fire.*;
import me.moros.bending.ability.fire.sequences.*;
import me.moros.bending.ability.water.*;
import me.moros.bending.ability.water.passives.*;
import me.moros.bending.game.manager.AbilityManager;
import me.moros.bending.game.temporal.TempArmor;
import me.moros.bending.model.Element;
import me.moros.bending.model.ability.Ability;
import me.moros.bending.model.ability.description.AbilityDescription;
import me.moros.bending.model.ability.util.ActivationMethod;
import me.moros.bending.model.user.BendingPlayer;
import me.moros.bending.model.user.User;
import me.moros.bending.util.Flight;
import me.moros.bending.util.methods.WorldMethods;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles ability activation.
 */
public final class ActivationController {
	private final Map<User, Boolean> ignoreSwing = ExpiringMap.builder()
		.expiration(100, TimeUnit.MILLISECONDS)
		.expirationPolicy(ExpirationPolicy.CREATED)
		.build();
	private final Game game;

	public ActivationController(@NonNull Game game) {
		this.game = game;
	}

	public boolean activateAbility(@NonNull User user, @NonNull ActivationMethod method) {
		AbilityDescription desc = user.getSelectedAbility().orElse(null);
		if (desc == null || !desc.isActivatedBy(method) || !user.canBend(desc)) return false;
		Ability ability = desc.createAbility();
		if (ability.activate(user, method)) {
			game.getAbilityManager(user.getWorld()).addAbility(user, ability);
			return true;
		}
		return false;
	}

	public void onPlayerLogout(@NonNull BendingPlayer player) {
		TempArmor.manager.get(player.getEntity()).ifPresent(TempArmor::revert);
		game.getAttributeSystem().clearModifiers(player);
		game.getStorage().savePlayerAsync(player);
		Flight.remove(player);

		UUID uuid = player.getProfile().getUniqueId();
		game.getBoardManager().invalidate(uuid);
		game.getPlayerManager().invalidatePlayer(uuid);
		game.getProtectionSystem().invalidate(player);
		game.getAbilityManager(player.getWorld()).clearPassives(player);
	}

	public void onUserSwing(@NonNull User user) {
		if (ignoreSwing.containsKey(user)) return;
		AbilityManager manager = game.getAbilityManager(user.getWorld());
		AbilityDescription desc = user.getSelectedAbility().orElse(null);
		boolean removed = false;
		if (desc != null) {
			if (desc.getName().equals("FireJet")) {
				removed |= manager.destroyInstanceType(user, FireJet.class);
				removed |= manager.destroyInstanceType(user, JetBlast.class);
			}
		}
		removed |= manager.destroyInstanceType(user, AirScooter.class);
		removed |= manager.destroyInstanceType(user, AirWheel.class);
		if (removed) return;

		AirBurst.activateCone(user);
		WaterManipulation.launch(user);
		PhaseChange.freeze(user);
		WaterWave.freeze(user);
		IceCrawl.launch(user);
		WaterRing.launchShard(user);
		EarthBlast.launch(user);
		EarthLine.launch(user);
		Shockwave.activateCone(user);
		HeatControl.act(user);
		Combustion.explode(user);
		FireBurst.activateCone(user);

		if (WorldMethods.getTargetEntity(user, 4).isPresent()) {
			game.getSequenceManager().registerAction(user, ActivationMethod.PUNCH_ENTITY);
		} else {
			game.getSequenceManager().registerAction(user, ActivationMethod.PUNCH);
		}

		activateAbility(user, ActivationMethod.PUNCH);
	}

	public void onUserSneak(@NonNull User user, boolean sneaking) {
		if (sneaking) PhaseChange.melt(user);
		ActivationMethod action = sneaking ? ActivationMethod.SNEAK : ActivationMethod.SNEAK_RELEASE;
		game.getSequenceManager().registerAction(user, action);
		activateAbility(user, action);
		game.getAbilityManager(user.getWorld()).destroyInstanceType(user, AirScooter.class);
	}

	public void onUserMove(@NonNull User user, @NonNull Vector velocity) {
		game.getAbilityManager(user.getWorld()).getFirstInstance(user, AirSpout.class).ifPresent(spout ->
			spout.handleMovement(velocity.setY(0))
		);
		game.getAbilityManager(user.getWorld()).getFirstInstance(user, WaterSpout.class).ifPresent(spout ->
			spout.handleMovement(velocity.setY(0))
		);
	}

	public boolean onFallDamage(@NonNull User user) {
		activateAbility(user, ActivationMethod.FALL);
		if (user.hasElement(Element.AIR) && GracefulDescent.isGraceful(user)) {
			return false;
		}
		if (user.hasElement(Element.WATER) && HydroSink.canHydroSink(user)) {
			return false;
		}
		if (user.hasElement(Element.EARTH) && DensityShift.isSoftened(user)) {
			return false;
		}
		return !Flight.hasFlight(user);
	}

	public void onUserInteract(@NonNull User user, @NonNull ActivationMethod method) {
		if (!method.isInteract()) return;
		ignoreNextSwing(user);

		EarthLine.setPrisonMode(user);

		game.getSequenceManager().registerAction(user, method);
		activateAbility(user, method);
	}

	public void ignoreNextSwing(@NonNull User user) {
		ignoreSwing.put(user, true);
	}

	public boolean onFireTickDamage(@NonNull User user) {
		return HeatControl.canBurn(user);
	}
}