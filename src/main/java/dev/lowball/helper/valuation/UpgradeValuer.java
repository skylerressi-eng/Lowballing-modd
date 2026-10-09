package dev.lowball.helper.valuation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.NeuRepo;
import dev.lowball.helper.util.Text;

/** Prices everything applied on top of the base item, using current bazaar/AH prices for each component. */
public final class UpgradeValuer {
	private static final String[] MASTER_STARS = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};
	private static final List<String> UNIVERSAL_GEM_SLOTS = List.of("COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING", "UNIVERSAL", "CHISEL");

	/** NBT counters that each represent N copies of an applied item. */
	private static final Map<String, String> COUNTED = Map.ofEntries(
			Map.entry("art_of_war_count", "THE_ART_OF_WAR"),
			Map.entry("art_of_peace_applied", "THE_ART_OF_PEACE"),
			Map.entry("wood_singularity_count", "WOOD_SINGULARITY"),
			Map.entry("farming_for_dummies_count", "FARMING_FOR_DUMMIES"),
			Map.entry("jalapeno_count", "JALAPENO_BOOK"),
			Map.entry("mana_disintegrator_count", "MANA_DISINTEGRATOR"),
			Map.entry("tuned_transmission", "TRANSMISSION_TUNER"),
			Map.entry("polarvoid", "POLARVOID_BOOK"),
			Map.entry("ethermerge", "ETHERWARP_CONDUIT"),
			Map.entry("divan_powder_coating", "DIVAN_POWDER_COATING"),
			Map.entry("bookworm_books", "BOOKWORM_BOOK"));

	/** NBT strings that name a single applied item. */
	private static final List<String> NAMED = List.of("dye_item", "power_ability_scroll", "drill_part_engine", "drill_part_fuel_tank", "drill_part_upgrade_module");

	private UpgradeValuer() {
	}

	/**
	 * @param price component id → price per unit, NaN when unknown
	 */
	public static List<Upgrade> value(SkyblockItem item, ToDoubleFunction<String> price, ItemRegistry registry) {
		return value(item, price, registry, m -> null);
	}

	/** @param stones reforge name → reforge stone (null for blacksmith reforges) */
	public static List<Upgrade> value(SkyblockItem item, ToDoubleFunction<String> price, ItemRegistry registry,
			Function<String, NeuRepo.ReforgeStone> stones) {
		List<Upgrade> out = new ArrayList<>();
		CompoundTag a = item.attrs;

		if (item.recombs > 0) {
			add(out, price, UpgradeCategory.RECOMB, "Recombobulator 3000", "RECOMBOBULATOR_3000", 1);
		}
		if (item.potatoBooks > 0) {
			add(out, price, UpgradeCategory.POTATO_BOOKS, "Hot Potato Book", "HOT_POTATO_BOOK", Math.min(10, item.potatoBooks));
			if (item.potatoBooks > 10) {
				add(out, price, UpgradeCategory.POTATO_BOOKS, "Fuming Potato Book", "FUMING_POTATO_BOOK", item.potatoBooks - 10);
			}
		}
		if (!item.isEnchantedBook()) {
			for (var e : item.enchants.entrySet()) {
				String id = "ENCHANTMENT_" + e.getKey() + "_" + e.getValue();
				add(out, price, UpgradeCategory.ENCHANTS, enchantLabel(e.getKey(), e.getValue()), id, 1);
			}
		}
		if (item.reforge != null) {
			NeuRepo.ReforgeStone stone = stones.apply(item.reforge);
			if (stone != null) {
				double stonePrice = price.applyAsDouble(stone.id());
				double apply = stone.costs().getOrDefault(rarity(item, registry), 0.0);
				if (stonePrice > 0 || apply > 0) {
					out.add(new Upgrade(UpgradeCategory.REFORGE, stone.name() + " (" + registry.name(stone.id()) + ")", stone.id(), 1,
							(Double.isNaN(stonePrice) ? 0 : stonePrice) + apply));
				}
			}
		}
		stars(item, price, registry, out);
		gems(item, price, registry, out);

		for (var e : COUNTED.entrySet()) {
			int n = a.getIntOr(e.getKey(), 0);
			if (n > 0) {
				add(out, price, UpgradeCategory.OTHER, Text.prettyId(e.getValue()), e.getValue(), n);
			}
		}
		for (String key : NAMED) {
			String id = a.getStringOr(key, "");
			if (!id.isEmpty()) {
				UpgradeCategory cat = key.equals("dye_item") ? UpgradeCategory.DYE : key.equals("power_ability_scroll") ? UpgradeCategory.SCROLLS : UpgradeCategory.OTHER;
				add(out, price, cat, registry.name(id.toUpperCase(Locale.ROOT)), id.toUpperCase(Locale.ROOT), 1);
			}
		}
		String enrichment = a.getStringOr("talisman_enrichment", "");
		if (!enrichment.isEmpty()) {
			String id = "TALISMAN_ENRICHMENT_" + enrichment.toUpperCase(Locale.ROOT);
			add(out, price, UpgradeCategory.OTHER, "Enrichment (" + Text.prettyId(enrichment) + ")", id, 1);
		}
		ListTag scrolls = a.getListOrEmpty("ability_scroll");
		for (int i = 0; i < scrolls.size(); i++) {
			String id = scrolls.getStringOr(i, "");
			if (!id.isEmpty()) {
				add(out, price, UpgradeCategory.SCROLLS, registry.name(id), id, 1);
			}
		}
		if (!item.id.equals("RUNE") && !item.id.equals("UNIQUE_RUNE")) {
			CompoundTag runes = a.getCompoundOrEmpty("runes");
			for (String r : runes.keySet()) {
				int lvl = runes.getIntOr(r, 1);
				add(out, price, UpgradeCategory.RUNE, Text.prettyId(r) + " Rune " + Text.roman(lvl), r.toUpperCase(Locale.ROOT) + "_RUNE;" + lvl, 1);
			}
		}
		if (item.pet) {
			if (item.petHeldItem != null) {
				add(out, price, UpgradeCategory.PET_ITEM, registry.name(item.petHeldItem), item.petHeldItem, 1);
			}
			if (item.petSkin != null) {
				add(out, price, UpgradeCategory.SKIN, registry.name("PET_SKIN_" + item.petSkin), "PET_SKIN_" + item.petSkin, 1);
			}
		}
		return out;
	}

	private static void stars(SkyblockItem item, ToDoubleFunction<String> price, ItemRegistry registry, List<Upgrade> out) {
		if (item.stars <= 0) {
			return;
		}
		ItemRegistry.Info info = registry.get(item.id);
		List<List<ItemRegistry.Cost>> costs = info == null ? List.of() : info.upgradeCosts();
		int normal = Math.min(item.stars, costs.size());
		double coins = 0;
		java.util.Map<String, Double> totals = new java.util.LinkedHashMap<>();
		for (int i = 0; i < normal; i++) {
			for (ItemRegistry.Cost c : costs.get(i)) {
				if (c.itemId() == null) {
					coins += c.amount();
				} else {
					totals.merge(c.itemId(), c.amount(), Double::sum);
				}
			}
		}
		for (var e : totals.entrySet()) {
			add(out, price, UpgradeCategory.STARS, normal + "★ " + registry.name(e.getKey()), e.getKey(), e.getValue());
		}
		if (coins > 0) {
			out.add(new Upgrade(UpgradeCategory.STARS, normal + "★ coins", "COINS", 1, coins));
		}
		boolean dungeon = info == null || info.dungeon();
		if (dungeon && costs.size() <= 5) {
			for (int s = 6; s <= Math.min(10, item.stars); s++) {
				String id = MASTER_STARS[s - 6];
				add(out, price, UpgradeCategory.MASTER_STARS, registry.name(id), id, 1);
			}
		}
	}

	private static void gems(SkyblockItem item, ToDoubleFunction<String> price, ItemRegistry registry, List<Upgrade> out) {
		CompoundTag gems = item.attrs.getCompoundOrEmpty("gems");
		if (gems.isEmpty()) {
			return;
		}
		for (String slot : gems.keySet()) {
			if (slot.equals("unlocked_slots") || slot.endsWith("_gem")) {
				continue;
			}
			Tag t = gems.get(slot);
			String quality = t instanceof CompoundTag c ? c.getStringOr("quality", "") : t == null ? "" : t.asString().orElse("");
			if (quality.isEmpty()) {
				continue;
			}
			String slotType = slotType(slot);
			String gemType = UNIVERSAL_GEM_SLOTS.contains(slotType) ? gems.getStringOr(slot + "_gem", "") : slotType;
			if (gemType.isEmpty()) {
				continue;
			}
			String id = quality.toUpperCase(Locale.ROOT) + "_" + gemType.toUpperCase(Locale.ROOT) + "_GEM";
			add(out, price, UpgradeCategory.GEMSTONES, Text.prettyId(quality + " " + gemType) + " §8(" + Text.prettyId(slot) + ")", id, 1);
		}

		ListTag unlocked = gems.getListOrEmpty("unlocked_slots");
		ItemRegistry.Info info = registry.get(item.id);
		if (info == null || unlocked.isEmpty()) {
			return;
		}
		double coins = 0;
		java.util.Map<String, Double> totals = new java.util.LinkedHashMap<>();
		for (int i = 0; i < unlocked.size(); i++) {
			String slot = unlocked.getStringOr(i, "");
			String type = slotType(slot);
			int nth = slotIndex(slot);
			int seen = 0;
			for (ItemRegistry.GemSlot gs : info.gemSlots()) {
				if (!gs.type().equals(type)) {
					continue;
				}
				if (seen++ == nth) {
					for (ItemRegistry.Cost c : gs.unlockCost()) {
						if (c.itemId() == null) {
							coins += c.amount();
						} else {
							totals.merge(c.itemId(), c.amount(), Double::sum);
						}
					}
					break;
				}
			}
		}
		for (var e : totals.entrySet()) {
			add(out, price, UpgradeCategory.GEM_SLOTS, registry.name(e.getKey()), e.getKey(), e.getValue());
		}
		if (coins > 0) {
			out.add(new Upgrade(UpgradeCategory.GEM_SLOTS, "Unlock coins", "COINS", 1, coins));
		}
	}

	static String slotType(String slot) {
		int us = slot.lastIndexOf('_');
		return us > 0 ? slot.substring(0, us) : slot;
	}

	static int slotIndex(String slot) {
		int us = slot.lastIndexOf('_');
		try {
			return us > 0 ? Integer.parseInt(slot.substring(us + 1)) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String enchantLabel(String name, int level) {
		String n = name.startsWith("ULTIMATE_") ? name.substring(9) : name;
		return Text.prettyId(n) + " " + Text.roman(level);
	}

	private static void add(List<Upgrade> out, ToDoubleFunction<String> price, UpgradeCategory cat, String label, String id, double qty) {
		double p = price.applyAsDouble(id);
		if (p > 0 && !Double.isNaN(p) && qty > 0) {
			out.add(new Upgrade(cat, label, id, qty, p));
		}
	}

	private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL");

	/** Current rarity: base tier from item data, bumped once by a recombobulator. */
	static String rarity(SkyblockItem item, ItemRegistry registry) {
		ItemRegistry.Info info = registry.get(item.id);
		String base = info == null ? "LEGENDARY" : info.tier();
		int i = RARITIES.indexOf(base);
		if (i < 0) {
			return base;
		}
		return RARITIES.get(Math.min(RARITIES.size() - 1, i + (item.recombs > 0 ? 1 : 0)));
	}
}
