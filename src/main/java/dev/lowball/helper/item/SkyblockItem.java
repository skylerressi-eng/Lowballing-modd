package dev.lowball.helper.item;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.util.Text;

/**
 * A SkyBlock item reduced to what matters for pricing. Built from the SkyBlock attributes compound
 * (the legacy {@code ExtraAttributes} tag in API item bytes, or the {@code custom_data} component in game).
 */
public final class SkyblockItem {
	public static final String[] PET_TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
	private static final Pattern PET_LEVEL = Pattern.compile("\\[Lvl (\\d+)]");
	private static final Pattern STAR_SUFFIX = Pattern.compile("[✪➊➋➌➍➎\\uE000-\\uF8FF]+");

	public final String id;
	/** Key used for auction prices, e.g. {@code HYPERION}, {@code ENDER_DRAGON;4@100}, {@code ENCHANTMENT_ULTIMATE_WISE_5}. */
	public final String key;
	/** Key without the pet level bucket. Equal to {@link #key} for non pets. */
	public final String baseKey;
	public final String name;
	public final int count;
	public final CompoundTag attrs;

	public final boolean pet;
	public final @Nullable String petType;
	public final int petTier;
	public final int petLevel;
	public final @Nullable String petHeldItem;
	public final @Nullable String petSkin;
	public final int petCandy;

	public final int recombs;
	public final int potatoBooks;
	public final int stars;
	public final boolean hasGems;
	public final Map<String, Integer> enchants;

	private SkyblockItem(String id, String name, int count, CompoundTag attrs) {
		this.id = id;
		this.name = name;
		this.count = Math.max(1, count);
		this.attrs = attrs;

		this.recombs = attrs.getIntOr("rarity_upgrades", 0);
		this.potatoBooks = attrs.getIntOr("hot_potato_count", 0);
		this.stars = Math.max(attrs.getIntOr("upgrade_level", 0), attrs.getIntOr("dungeon_item_level", 0));
		CompoundTag gems = attrs.getCompoundOrEmpty("gems");
		boolean anyGem = false;
		for (String k : gems.keySet()) {
			if (!k.equals("unlocked_slots") && !k.endsWith("_gem")) {
				anyGem = true;
				break;
			}
		}
		this.hasGems = anyGem;

		Map<String, Integer> ench = new LinkedHashMap<>();
		CompoundTag enchTag = attrs.getCompoundOrEmpty("enchantments");
		for (String k : enchTag.keySet()) {
			ench.put(k.toUpperCase(Locale.ROOT), enchTag.getIntOr(k, 0));
		}
		this.enchants = Map.copyOf(ench);

		String type = null;
		int tier = -1;
		String held = null;
		String skin = null;
		int candy = 0;
		if (id.equals("PET")) {
			JsonObject info = petInfo(attrs);
			if (info != null) {
				type = str(info, "type");
				tier = tierIndex(str(info, "tier"));
				held = str(info, "heldItem");
				skin = str(info, "skin");
				candy = info.has("candyUsed") && info.get("candyUsed").isJsonPrimitive() ? info.get("candyUsed").getAsInt() : 0;
				// Tier boost raises the shown tier; price the pet at its base tier + the boost item.
				if ("PET_ITEM_TIER_BOOST".equals(held) && tier > 0) {
					tier--;
				}
			}
		}
		this.pet = type != null && tier >= 0;
		this.petType = type;
		this.petTier = tier;
		this.petHeldItem = held;
		this.petSkin = skin;
		this.petCandy = candy;
		this.petLevel = pet ? petLevel(name) : 0;

		if (pet) {
			this.baseKey = type + ";" + tier;
			this.key = baseKey + "@" + levelBucket(petLevel);
		} else {
			this.baseKey = computeKey(id, attrs, enchants);
			this.key = baseKey;
		}
	}

	/**
	 * @param attrs SkyBlock attributes (must contain {@code id}); if a full legacy tag is passed the
	 *              {@code ExtraAttributes} child is used.
	 * @param name  display name, with or without formatting codes
	 */
	public static @Nullable SkyblockItem of(@Nullable CompoundTag attrs, String name, int count) {
		if (attrs == null) {
			return null;
		}
		if (!attrs.contains("id") && attrs.contains("ExtraAttributes")) {
			attrs = attrs.getCompoundOrEmpty("ExtraAttributes");
		}
		String id = attrs.getStringOr("id", "");
		if (id.isEmpty()) {
			return null;
		}
		return new SkyblockItem(id, cleanName(name), count, attrs);
	}

	/** True if nothing is applied that typically changes the price (recomb, potato books, stars, gems). */
	public boolean isClean() {
		return recombs == 0 && potatoBooks == 0 && stars == 0 && !hasGems;
	}

	public boolean isEnchantedBook() {
		return id.equals("ENCHANTED_BOOK");
	}

	public String petTierName() {
		return petTier >= 0 && petTier < PET_TIERS.length ? PET_TIERS[petTier] : "?";
	}

	public static String levelBucket(int level) {
		if (level >= 200) {
			return "200";
		}
		if (level >= 100) {
			return "100";
		}
		return "LOW";
	}

	public static String tierName(int tier) {
		return tier >= 0 && tier < PET_TIERS.length ? PET_TIERS[tier] : "?";
	}

	public static int tierIndex(@Nullable String tier) {
		if (tier == null) {
			return -1;
		}
		for (int i = 0; i < PET_TIERS.length; i++) {
			if (PET_TIERS[i].equalsIgnoreCase(tier)) {
				return i;
			}
		}
		return -1;
	}

	private static String computeKey(String id, CompoundTag attrs, Map<String, Integer> enchants) {
		switch (id) {
			case "ENCHANTED_BOOK" -> {
				if (enchants.size() == 1) {
					var e = enchants.entrySet().iterator().next();
					return "ENCHANTMENT_" + e.getKey() + "_" + e.getValue();
				}
				return id;
			}
			case "RUNE", "UNIQUE_RUNE" -> {
				CompoundTag runes = attrs.getCompoundOrEmpty("runes");
				for (String k : runes.keySet()) {
					return k.toUpperCase(Locale.ROOT) + "_RUNE;" + runes.getIntOr(k, 1);
				}
				return id;
			}
			case "NEW_YEAR_CAKE" -> {
				int year = attrs.getIntOr("new_years_cake", -1);
				return year >= 0 ? id + ";" + year : id;
			}
			case "POTION" -> {
				String potion = attrs.getStringOr("potion", "");
				int level = attrs.getIntOr("potion_level", 0);
				return potion.isEmpty() ? id : "POTION_" + potion.toUpperCase(Locale.ROOT) + ";" + level;
			}
			default -> {
				return id;
			}
		}
	}

	static @Nullable JsonObject petInfo(CompoundTag attrs) {
		Tag t = attrs.get("petInfo");
		if (t == null) {
			return null;
		}
		try {
			if (t instanceof CompoundTag c) {
				JsonObject o = new JsonObject();
				for (String k : c.keySet()) {
					Tag v = c.get(k);
					if (v != null) {
						v.asString().ifPresentOrElse(s -> o.addProperty(k, s),
								() -> v.asNumber().ifPresent(n -> o.addProperty(k, n)));
					}
				}
				return o;
			}
			String json = t.asString().orElse(null);
			if (json == null) {
				return null;
			}
			var el = JsonParser.parseString(json);
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static @Nullable String str(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}

	static int petLevel(String name) {
		Matcher m = PET_LEVEL.matcher(name);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	static String cleanName(String raw) {
		String s = Text.strip(raw);
		s = STAR_SUFFIX.matcher(s).replaceAll("");
		return s.trim();
	}

	@Override
	public String toString() {
		return key + " x" + count + " (" + name + ")";
	}
}
