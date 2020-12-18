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

package me.moros.bending.util.material;

import com.destroystokyo.paper.MaterialSetTag;
import com.destroystokyo.paper.MaterialTags;
import me.moros.atlas.cf.checker.nullness.qual.NonNull;
import me.moros.bending.Bending;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class MaterialUtil {
	public static final Map<Material, Material> COOKABLE = new HashMap<>();
	public static final MaterialSetTag AIR;
	public static final MaterialSetTag TRANSPARENT;
	public static final MaterialSetTag CONTAINERS;
	public static final MaterialSetTag UNBREAKABLES;

	static {
		COOKABLE.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
		COOKABLE.put(Material.BEEF, Material.COOKED_BEEF);
		COOKABLE.put(Material.CHICKEN, Material.COOKED_CHICKEN);
		COOKABLE.put(Material.COD, Material.COOKED_COD);
		COOKABLE.put(Material.SALMON, Material.COOKED_SALMON);
		COOKABLE.put(Material.POTATO, Material.BAKED_POTATO);
		COOKABLE.put(Material.MUTTON, Material.COOKED_MUTTON);
		COOKABLE.put(Material.RABBIT, Material.COOKED_RABBIT);
		COOKABLE.put(Material.WET_SPONGE, Material.SPONGE);
		COOKABLE.put(Material.KELP, Material.DRIED_KELP);
		COOKABLE.put(Material.STICK, Material.TORCH);

		AIR = new MaterialSetTag(Bending.getLayer().getCoreKey(), Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

		TRANSPARENT = new MaterialSetTag(Bending.getLayer().getCoreKey(), AIR.getValues());
		TRANSPARENT.add(MaterialSetTag.SIGNS.getValues());
		TRANSPARENT.add(MaterialSetTag.FIRE.getValues());
		TRANSPARENT.add(MaterialSetTag.SAPLINGS.getValues());
		TRANSPARENT.add(MaterialSetTag.FLOWERS.getValues());
		TRANSPARENT.add(MaterialSetTag.SMALL_FLOWERS.getValues());
		TRANSPARENT.add(MaterialSetTag.TALL_FLOWERS.getValues());
		TRANSPARENT.add(MaterialSetTag.CARPETS.getValues());
		TRANSPARENT.add(MaterialSetTag.BUTTONS.getValues());
		TRANSPARENT.add(MaterialSetTag.CROPS.getValues());
		TRANSPARENT.add(MaterialTags.MUSHROOMS.getValues());
		TRANSPARENT.add(Material.COBWEB, Material.GRASS, Material.TALL_GRASS, Material.SNOW, Material.LARGE_FERN,
			Material.VINE, Material.FERN, Material.SUGAR_CANE, Material.DEAD_BUSH);
		TRANSPARENT.endsWith("TORCH");

		CONTAINERS = new MaterialSetTag(Bending.getLayer().getCoreKey(), Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
			Material.BARREL, Material.SHULKER_BOX, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
			Material.DISPENSER, Material.DROPPER, Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.BEACON,
			Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.GRINDSTONE,
			Material.CARTOGRAPHY_TABLE, Material.LOOM, Material.SMITHING_TABLE
		);

		UNBREAKABLES = new MaterialSetTag(Bending.getLayer().getCoreKey(), Material.BARRIER, Material.BEDROCK,
			Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.NETHER_PORTAL,
			Material.END_PORTAL, Material.END_PORTAL_FRAME, Material.END_GATEWAY
		);
	}

	public static boolean isAir(@NonNull Block block) {
		return AIR.isTagged(block);
	}

	public static boolean isTransparent(@NonNull Block block) {
		return TRANSPARENT.isTagged(block);
	}

	public static boolean isContainer(@NonNull Block block) {
		return CONTAINERS.isTagged(block.getType()) || (block.getState() instanceof InventoryHolder);
	}

	public static boolean isUnbreakable(@NonNull Block block) {
		return UNBREAKABLES.isTagged(block.getType()) || isContainer(block) || (block.getState() instanceof CreatureSpawner);
	}

	public static boolean isIgnitable(@NonNull Block block) {
		if ((block.getType().isFlammable() || block.getType().isBurnable()) && isTransparent(block)) {
			return true;
		}
		return isAir(block) && block.getRelative(BlockFace.DOWN).getType().isSolid();
	}

	public static boolean isFire(@NonNull Block block) {
		return MaterialSetTag.FIRE.isTagged(block.getType());
	}

	public static boolean isLava(@NonNull Block block) {
		return block.getType() == Material.LAVA;
	}

	public static boolean isWater(@NonNull Block block) {
		return block.getType() == Material.WATER || isWaterLogged(block.getBlockData());
	}

	public static boolean isWaterLogged(@NonNull BlockData data) {
		return data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged();
	}

	// Finds a suitable solid block type to replace a falling-type block with.
	public static @NonNull BlockData getSolidType(@NonNull BlockData data) {
		return getSolidType(data, data);
	}

	public static @NonNull BlockData getSolidType(@NonNull BlockData data, BlockData def) {
		switch (data.getMaterial()) { // TODO implement concrete powder maybe (if it ever becomes bendable)?
			case SAND:
				return Material.SANDSTONE.createBlockData();
			case RED_SAND:
				return Material.RED_SANDSTONE.createBlockData();
			case GRAVEL:
				return Material.STONE.createBlockData();
			default:
				return def;
		}
	}

	public static @NonNull BlockData getFocusedType(@NonNull BlockData data) {
		return data.getMaterial() == Material.STONE ? Material.COBBLESTONE.createBlockData() : getSolidType(data, Material.STONE.createBlockData());
	}

	// Finds a suitable soft block type to replace a solid block
	public static @NonNull BlockData getSoftType(@NonNull BlockData data) {
		if (MaterialTags.SANDSTONES.isTagged(data.getMaterial())) {
			return Material.SAND.createBlockData();
		} else if (MaterialTags.RED_SANDSTONES.isTagged(data.getMaterial())) {
			return Material.RED_SAND.createBlockData();
		}
		switch (data.getMaterial()) {
			case STONE:
			case GRANITE:
			case POLISHED_GRANITE:
			case DIORITE:
			case POLISHED_DIORITE:
			case ANDESITE:
			case POLISHED_ANDESITE:
				return Material.GRAVEL.createBlockData();
			case DIRT:
			case MYCELIUM:
			case GRASS_BLOCK:
			case GRASS_PATH:
				return Material.COARSE_DIRT.createBlockData();
			case SAND:
				return Material.SAND.createBlockData();
			case RED_SAND:
				return Material.RED_SAND.createBlockData();
		}
		return Material.SAND.createBlockData();
	}

	public static boolean isSourceBlock(@NonNull Block block) {
		BlockData blockData = block.getBlockData();
		return blockData instanceof Levelled && ((Levelled) blockData).getLevel() == 0;
	}

	public static @NonNull BlockData getLavaData(int level) {
		return Material.LAVA.createBlockData(d -> ((Levelled) d).setLevel((level < 0 || level > ((Levelled) d).getMaximumLevel()) ? 0 : level));
	}

	public static @NonNull BlockData getWaterData(int level) {
		return Material.WATER.createBlockData(d -> ((Levelled) d).setLevel((level < 0 || level > ((Levelled) d).getMaximumLevel()) ? 0 : level));
	}
}