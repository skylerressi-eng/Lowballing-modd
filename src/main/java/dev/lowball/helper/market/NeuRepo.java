package dev.lowball.helper.market;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.util.Http;

/**
 * Recipes and reforge stones from the NotEnoughUpdates item repository, fetched per item on demand and cached on disk.
 */
public final class NeuRepo {
	private static final String BASE = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/";
	private static final long DISK_TTL = 3L * 24 * 3600_000L;

	public record Ingredient(String id, double count) {
	}

	/** @param type crafting, forge or npc_shop; @param coins coins in the recipe; @param output items produced */
	public record Recipe(String type, List<Ingredient> inputs, double coins, int output) {
	}

	/** @param costs coins to apply per rarity */
	public record ReforgeStone(String id, String name, Map<String, Double> costs) {
	}

	private final Map<String, Optional<Recipe>> recipes = new ConcurrentHashMap<>();
	private final Set<String> queued = ConcurrentHashMap.newKeySet();
	private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
	private volatile Map<String, ReforgeStone> stones = Map.of();
	private volatile boolean stonesRequested;
	private final Runnable onUpdate;

	public NeuRepo(Runnable onUpdate) {
		this.onUpdate = onUpdate;
		Thread t = new Thread(this::run, "LowballHelper-NEU");
		t.setDaemon(true);
		t.start();
	}

	/** Recipe if known; empty if the item has none; null while it is still loading. */
	@SuppressWarnings("OptionalAssignedToNull")
	public @Nullable Optional<Recipe> recipe(String id) {
		Optional<Recipe> r = recipes.get(id);
		if (r == null && queued.add(id)) {
			queue.addFirst(id);
		}
		return r;
	}

	/** Reforge stone for a {@code modifier} value, or null for blacksmith reforges / not loaded yet. */
	public @Nullable ReforgeStone stone(String modifier) {
		if (!stonesRequested) {
			stonesRequested = true;
			queue.addLast("#stones");
		}
		return stones.get(normalize(modifier));
	}

	private static String normalize(String s) {
		return s.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("'", "");
	}

	private void run() {
		while (true) {
			String id;
			try {
				id = queue.takeFirst();
			} catch (InterruptedException e) {
				return;
			}
			try {
				if (id.equals("#stones")) {
					stones = parseStones(load("constants/reforgestones.json", "reforgestones.json"));
				} else {
					String file = "items/" + id + ".json";
					recipes.put(id, Optional.ofNullable(parseRecipe(load(file, "items/" + id.replace(';', '-') + ".json"))));
				}
				onUpdate.run();
			} catch (Http.StatusException e) {
				if (!id.startsWith("#")) {
					recipes.put(id, Optional.empty()); // not in the repo
				}
			} catch (IOException | RuntimeException e) {
				LowballHelper.LOGGER.debug("NEU repo lookup failed for {}", id, e);
				if (id.equals("#stones")) {
					stonesRequested = false;
				}
			} finally {
				queued.remove(id);
			}
		}
	}

	private static String load(String repoPath, String cacheName) throws IOException {
		Path cache = LowballHelper.dataDir().resolve("neu").resolve(cacheName);
		try {
			if (Files.exists(cache) && System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() < DISK_TTL) {
				return Files.readString(cache, StandardCharsets.UTF_8);
			}
		} catch (IOException ignored) {
			// re-download
		}
		String url = BASE + repoPath.replace(";", "%3B");
		StringBuilder sb = new StringBuilder();
		try (var r = Http.reader(url)) {
			char[] buf = new char[8192];
			int n;
			while ((n = r.read(buf)) > 0) {
				sb.append(buf, 0, n);
			}
		}
		String body = sb.toString();
		Files.createDirectories(cache.getParent());
		Files.writeString(cache, body, StandardCharsets.UTF_8);
		return body;
	}

	static @Nullable Recipe parseRecipe(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		if (o.has("recipe") && o.get("recipe").isJsonObject()) {
			JsonObject g = o.getAsJsonObject("recipe");
			int count = g.has("count") && g.get("count").isJsonPrimitive() ? g.get("count").getAsInt() : 1;
			Recipe r = grid(g, count);
			if (r != null) {
				return r;
			}
		}
		if (o.has("recipes") && o.get("recipes").isJsonArray()) {
			Recipe forge = null, shop = null;
			for (JsonElement el : o.getAsJsonArray("recipes")) {
				JsonObject r = el.getAsJsonObject();
				String type = r.has("type") ? r.get("type").getAsString() : "";
				switch (type) {
					case "crafting" -> {
						Recipe g = grid(r, r.has("count") ? r.get("count").getAsInt() : 1);
						if (g != null) {
							return g;
						}
					}
					case "forge" -> {
						if (forge == null) {
							forge = list("forge", r, "inputs", r.has("count") ? r.get("count").getAsInt() : 1);
						}
					}
					case "npc_shop" -> {
						if (shop == null) {
							int out = 1;
							if (r.has("result")) {
								out = (int) Math.max(1, ingredient(r.get("result").getAsString()).count());
							}
							shop = list("npc_shop", r, "cost", out);
						}
					}
					default -> {
					}
				}
			}
			return forge != null ? forge : shop;
		}
		return null;
	}

	private static @Nullable Recipe grid(JsonObject r, int output) {
		Map<String, Double> totals = new java.util.LinkedHashMap<>();
		double coins = 0;
		for (String slot : new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"}) {
			if (!r.has(slot) || !r.get(slot).isJsonPrimitive()) {
				continue;
			}
			String v = r.get(slot).getAsString();
			if (v.isEmpty()) {
				continue;
			}
			Ingredient in = ingredient(v);
			if (in.id().equals("SKYBLOCK_COIN")) {
				coins += in.count();
			} else {
				totals.merge(in.id(), in.count(), Double::sum);
			}
		}
		if (totals.isEmpty()) {
			return null;
		}
		List<Ingredient> list = new ArrayList<>();
		totals.forEach((id, c) -> list.add(new Ingredient(id, c)));
		return new Recipe("crafting", List.copyOf(list), coins, Math.max(1, output));
	}

	private static @Nullable Recipe list(String type, JsonObject r, String field, int output) {
		if (!r.has(field) || !r.get(field).isJsonArray()) {
			return null;
		}
		Map<String, Double> totals = new java.util.LinkedHashMap<>();
		double coins = 0;
		for (JsonElement el : r.getAsJsonArray(field)) {
			Ingredient in = ingredient(el.getAsString());
			if (in.id().equals("SKYBLOCK_COIN")) {
				coins += in.count();
			} else {
				totals.merge(in.id(), in.count(), Double::sum);
			}
		}
		List<Ingredient> list = new ArrayList<>();
		totals.forEach((id, c) -> list.add(new Ingredient(id, c)));
		return list.isEmpty() && coins == 0 ? null : new Recipe(type, List.copyOf(list), coins, Math.max(1, output));
	}

	static Ingredient ingredient(String s) {
		int colon = s.lastIndexOf(':');
		if (colon < 0) {
			return new Ingredient(s, 1);
		}
		try {
			return new Ingredient(s.substring(0, colon), Double.parseDouble(s.substring(colon + 1)));
		} catch (NumberFormatException e) {
			return new Ingredient(s, 1);
		}
	}

	static Map<String, ReforgeStone> parseStones(String json) {
		Map<String, ReforgeStone> out = new HashMap<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		for (var e : root.entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject o = e.getValue().getAsJsonObject();
			String name = o.has("reforgeName") ? o.get("reforgeName").getAsString() : null;
			if (name == null) {
				continue;
			}
			Map<String, Double> costs = new HashMap<>();
			if (o.has("reforgeCosts") && o.get("reforgeCosts").isJsonObject()) {
				for (var c : o.getAsJsonObject("reforgeCosts").entrySet()) {
					costs.put(c.getKey(), c.getValue().getAsDouble());
				}
			}
			out.put(normalize(name), new ReforgeStone(e.getKey(), name, Map.copyOf(costs)));
		}
		return out;
	}
}
