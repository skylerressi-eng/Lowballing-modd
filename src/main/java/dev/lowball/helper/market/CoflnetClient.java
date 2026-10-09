package dev.lowball.helper.market;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.util.Http;

/**
 * On-demand recent sale statistics from the public Coflnet API (median sold price and sales per day).
 * Requests are queued and spaced out to stay well under Coflnet's rate limit.
 */
public final class CoflnetClient {
	private static final String BASE = "https://sky.coflnet.com/api/item/price/";
	private static final long TTL = 15 * 60_000L;
	private static final long FAIL_TTL = 5 * 60_000L;
	private static final long SPACING_MS = 400;

	/** @param median median sale price in the last day, @param perDay sales in the last day */
	public record Stats(double median, double perDay, double min, double max, long fetchedAt, boolean ok) {
	}

	private final Map<String, Stats> cache = new ConcurrentHashMap<>();
	private final Set<String> queued = ConcurrentHashMap.newKeySet();
	private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
	private final Runnable onUpdate;
	private volatile boolean enabled = true;

	public CoflnetClient(Runnable onUpdate) {
		this.onUpdate = onUpdate;
		Thread worker = new Thread(this::run, "LowballHelper-Coflnet");
		worker.setDaemon(true);
		worker.start();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public @Nullable Stats get(String key) {
		Stats s = cache.get(key);
		long now = System.currentTimeMillis();
		if (s == null || now - s.fetchedAt() > (s.ok() ? TTL : FAIL_TTL)) {
			request(key, false);
		}
		return s != null && s.ok() ? s : null;
	}

	/** @param urgent put in front of the queue (item currently on screen) */
	public void request(String key, boolean urgent) {
		if (!enabled || tag(key) == null) {
			return;
		}
		if (queued.add(key)) {
			if (urgent) {
				queue.addFirst(key);
			} else {
				queue.addLast(key);
			}
		}
	}

	private void run() {
		while (true) {
			try {
				String key = queue.takeFirst();
				try {
					if (enabled) {
						cache.put(key, fetch(key));
						onUpdate.run();
					}
				} catch (Http.StatusException e) {
					cache.put(key, new Stats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, System.currentTimeMillis(), false));
					if (e.status == 429) {
						Thread.sleep(15_000);
					}
				} catch (IOException | RuntimeException e) {
					LowballHelper.LOGGER.debug("Coflnet lookup failed for {}", key, e);
					cache.put(key, new Stats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, System.currentTimeMillis(), false));
				} finally {
					queued.remove(key);
				}
				Thread.sleep(SPACING_MS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static Stats fetch(String key) throws IOException {
		String url = BASE + tag(key);
		JsonElement el = Http.json(url);
		if (!el.isJsonObject()) {
			throw new IOException("Unexpected Coflnet response");
		}
		JsonObject o = el.getAsJsonObject();
		return new Stats(num(o, "median"), num(o, "volume"), num(o, "min"), num(o, "max"), System.currentTimeMillis(), true);
	}

	/** Coflnet path (+query) for one of our price keys, or null when Coflnet has no matching item. */
	static @Nullable String tag(String key) {
		if (key.startsWith("ENCHANTMENT_") || key.contains("_RUNE;") || key.startsWith("POTION_")) {
			return null;
		}
		int semi = key.indexOf(';');
		if (semi < 0) {
			return URLEncoder.encode(key, StandardCharsets.UTF_8);
		}
		int at = key.indexOf('@');
		String type = key.substring(0, semi);
		String tierPart = key.substring(semi + 1, at < 0 ? key.length() : at);
		int tier;
		try {
			tier = Integer.parseInt(tierPart);
		} catch (NumberFormatException e) {
			return null;
		}
		String query = "?Rarity=" + SkyblockItem.tierName(tier);
		if (at >= 0) {
			String bucket = key.substring(at + 1);
			query += switch (bucket) {
				case "200" -> "&PetLevel=200";
				case "100" -> "&PetLevel=100-199";
				default -> "&PetLevel=1-99";
			};
		}
		return "PET_" + URLEncoder.encode(type, StandardCharsets.UTF_8) + query;
	}

	private static double num(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : Double.NaN;
	}
}
