package dev.lowball.helper.market;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.util.Http;

/** Static SkyBlock item data: names, NPC prices, star costs and gemstone slot costs. */
public final class ItemRegistry {
	static final String URL = "https://api.hypixel.net/v2/resources/skyblock/items";

	/** One cost entry: an item id (essence is {@code ESSENCE_<TYPE>}) or coins (id == null). */
	public record Cost(@Nullable String itemId, double amount) {
	}

	public record GemSlot(String type, List<Cost> unlockCost) {
	}

	public record Info(String id, String name, String tier, double npcSell, List<List<Cost>> upgradeCosts, List<GemSlot> gemSlots, boolean dungeon) {
	}

	private final Map<String, Info> byId;
	private final Map<String, String> idByLowerName;

	public ItemRegistry(Map<String, Info> byId) {
		this.byId = byId;
		this.idByLowerName = new HashMap<>();
		for (Info i : byId.values()) {
			idByLowerName.putIfAbsent(i.name().toLowerCase(Locale.ROOT), i.id());
		}
	}

	public static ItemRegistry empty() {
		return new ItemRegistry(Map.of());
	}

	public int size() {
		return byId.size();
	}

	public @Nullable Info get(String id) {
		return byId.get(id);
	}

	public @Nullable String idForName(String name) {
		return idByLowerName.get(name.toLowerCase(Locale.ROOT));
	}

	public Iterable<Info> all() {
		return byId.values();
	}

	public static ItemRegistry download(Path cacheFile) throws IOException {
		String body;
		try (Reader r = Http.reader(URL)) {
			StringBuilder sb = new StringBuilder(1 << 22);
			char[] buf = new char[1 << 16];
			int n;
			while ((n = r.read(buf)) > 0) {
				sb.append(buf, 0, n);
			}
			body = sb.toString();
		}
		ItemRegistry reg = parse(body);
		if (reg.size() > 0) {
			Files.createDirectories(cacheFile.getParent());
			Files.writeString(cacheFile, body, StandardCharsets.UTF_8);
		}
		return reg;
	}

	public static @Nullable ItemRegistry loadCached(Path cacheFile) {
		try {
			return Files.exists(cacheFile) ? parse(Files.readString(cacheFile, StandardCharsets.UTF_8)) : null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	static ItemRegistry parse(String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		Map<String, Info> map = new HashMap<>();
		for (JsonElement el : root.getAsJsonArray("items")) {
			JsonObject o = el.getAsJsonObject();
			String id = str(o, "id");
			if (id == null) {
				continue;
			}
			List<List<Cost>> upgrades = new ArrayList<>();
			if (o.has("upgrade_costs")) {
				for (JsonElement level : o.getAsJsonArray("upgrade_costs")) {
					upgrades.add(costs(level.getAsJsonArray()));
				}
			}
			List<GemSlot> gems = new ArrayList<>();
			if (o.has("gemstone_slots")) {
				for (JsonElement s : o.getAsJsonArray("gemstone_slots")) {
					JsonObject so = s.getAsJsonObject();
					gems.add(new GemSlot(String.valueOf(str(so, "slot_type")), so.has("costs") ? costs(so.getAsJsonArray("costs")) : List.of()));
				}
			}
			map.put(id, new Info(id, String.valueOf(str(o, "name")), String.valueOf(str(o, "tier")),
					o.has("npc_sell_price") ? o.get("npc_sell_price").getAsDouble() : 0,
					List.copyOf(upgrades), List.copyOf(gems), o.has("dungeon_item") && o.get("dungeon_item").getAsBoolean()));
		}
		return new ItemRegistry(map);
	}

	private static List<Cost> costs(JsonArray arr) {
		List<Cost> out = new ArrayList<>();
		for (JsonElement c : arr) {
			JsonObject co = c.getAsJsonObject();
			String type = String.valueOf(str(co, "type"));
			switch (type) {
				case "ESSENCE" -> out.add(new Cost("ESSENCE_" + str(co, "essence_type"), num(co, "amount")));
				case "ITEM" -> out.add(new Cost(str(co, "item_id"), num(co, "amount")));
				case "COINS" -> out.add(new Cost(null, num(co, "coins")));
				default -> {
				}
			}
		}
		return List.copyOf(out);
	}

	private static @Nullable String str(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}

	private static double num(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : (k.equals("amount") ? 1 : 0);
	}
}
