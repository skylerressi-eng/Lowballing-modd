package dev.lowball.helper.market;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * On-demand sale statistics from the public Coflnet API: 24h median/volume (optionally only clean copies)
 * and 30 day history for rare things like exotic colors. Requests are queued and spaced out.
 */
public final class CoflnetClient {
	private static final String BASE = "https://sky.coflnet.com/api/item/price/";
	private static final long TTL = 15 * 60_000L;
	private static final long HISTORY_TTL = 60 * 60_000L;
	private static final long FAIL_TTL = 5 * 60_000L;
	private static final long SPACING_MS = 350;

	/** 24h summary. @param median median sale price, @param perDay sales in the last day */
	public record Stats(double median, double perDay, double min, double max, long fetchedAt, boolean ok) {
	}

	/** 30 day history reduced to one line. */
	public record History(int sales, double median, double min, double max, long lastSale, long fetchedAt, boolean ok) {
		public boolean any() {
			return ok && sales > 0;
		}
	}

	private record Request(String url, boolean history) {
	}

	private final Map<String, Object> cache = new ConcurrentHashMap<>();
	private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
	private final Set<String> queued = ConcurrentHashMap.newKeySet();
	private final LinkedBlockingDeque<Request> queue = new LinkedBlockingDeque<>();
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

	/** 24h stats for a price key; {@code clean} limits to copies without upgrades. */
	public @Nullable Stats get(String key, boolean clean) {
		String tag = tag(key);
		if (tag == null) {
			return null;
		}
		String url = BASE + tag + (clean ? (tag.contains("?") ? "&" : "?") + "Clean=yes" : "");
		return lookup(url, false, Stats.class);
	}

	public @Nullable Stats get(String key) {
		return get(key, false);
	}

	/** 30 day history for an item id with extra filters, e.g. {@code Color=31:0:48}. */
	public @Nullable History history(String id, String filters) {
		return history(id, filters, "month");
	}

	/** History over {@code period} ("week" or "month") for an item id with extra filters. */
	public @Nullable History history(String id, String filters, String period) {
		String url = BASE + URLEncoder.encode(id, StandardCharsets.UTF_8) + "/history/" + period + (filters.isEmpty() ? "" : "?" + filters);
		return lookup(url, true, History.class);
	}

	private <T> @Nullable T lookup(String url, boolean history, Class<T> type) {
		Object o = cache.get(url);
		long now = System.currentTimeMillis();
		long fetched = o instanceof Stats st ? st.fetchedAt() : o instanceof History h ? h.fetchedAt() : 0;
		boolean ok = o instanceof Stats st ? st.ok() : o instanceof History h && h.ok();
		long ttl = !ok ? FAIL_TTL : history ? HISTORY_TTL : TTL;
		if (o == null || now - fetched > ttl) {
			Long failed = failedAt.get(url);
			if (failed == null || now - failed > FAIL_TTL) {
				enqueue(new Request(url, history));
			}
		}
		return ok && type.isInstance(o) ? type.cast(o) : null;
	}

	/** True while a lookup for this key is still pending. */
	public boolean pending() {
		return !queue.isEmpty();
	}

	private void enqueue(Request r) {
		if (enabled && queued.add(r.url)) {
			queue.addFirst(r);
		}
	}

	private void run() {
		while (true) {
			try {
				Request r = queue.takeFirst();
				try {
					if (enabled) {
						cache.put(r.url, r.history ? fetchHistory(r.url) : fetchStats(r.url));
						failedAt.remove(r.url);
						onUpdate.run();
					}
				} catch (Http.StatusException e) {
					failedAt.put(r.url, System.currentTimeMillis());
					if (e.status == 429) {
						queued.remove(r.url);
						queue.addLast(r);
						Thread.sleep(10_000);
						continue;
					}
					cache.put(r.url, r.history ? new History(0, Double.NaN, Double.NaN, Double.NaN, 0, System.currentTimeMillis(), false)
							: new Stats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, System.currentTimeMillis(), false));
				} catch (IOException | RuntimeException e) {
					LowballHelper.LOGGER.debug("Coflnet lookup failed for {}", r.url, e);
					failedAt.put(r.url, System.currentTimeMillis());
				} finally {
					queued.remove(r.url);
				}
				Thread.sleep(SPACING_MS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static Stats fetchStats(String url) throws IOException {
		JsonElement el = Http.json(url);
		if (!el.isJsonObject()) {
			throw new IOException("Unexpected Coflnet response");
		}
		JsonObject o = el.getAsJsonObject();
		double median = num(o, "median");
		// Coflnet answers 0 for "no sales"
		return new Stats(median > 0 ? median : Double.NaN, num(o, "volume"), num(o, "min"), num(o, "max"), System.currentTimeMillis(), true);
	}

	private static History fetchHistory(String url) throws IOException {
		JsonElement el = Http.json(url);
		if (!el.isJsonArray()) {
			throw new IOException("Unexpected Coflnet response");
		}
		List<double[]> buckets = new ArrayList<>();
		int sales = 0;
		double min = Double.NaN, max = Double.NaN;
		long last = 0;
		for (JsonElement e : el.getAsJsonArray()) {
			JsonObject b = e.getAsJsonObject();
			int vol = (int) num(b, "volume");
			double avg = num(b, "avg");
			if (vol <= 0 || !(avg > 0)) {
				continue;
			}
			sales += vol;
			buckets.add(new double[]{avg, vol});
			min = Double.isNaN(min) ? num(b, "min") : Math.min(min, num(b, "min"));
			max = Double.isNaN(max) ? num(b, "max") : Math.max(max, num(b, "max"));
			try {
				last = Math.max(last, java.time.LocalDateTime.parse(b.get("time").getAsString()).toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
			} catch (RuntimeException ignored) {
				// time is informational only
			}
		}
		return new History(sales, weightedMedian(buckets), min, max, last, System.currentTimeMillis(), true);
	}

	/** Median of daily averages weighted by how many sales each day had. */
	static double weightedMedian(List<double[]> buckets) {
		if (buckets.isEmpty()) {
			return Double.NaN;
		}
		List<double[]> sorted = new ArrayList<>(buckets);
		sorted.sort((a, b) -> Double.compare(a[0], b[0]));
		double total = 0;
		for (double[] b : sorted) {
			total += b[1];
		}
		double acc = 0;
		for (double[] b : sorted) {
			acc += b[1];
			if (acc >= total / 2) {
				return b[0];
			}
		}
		return sorted.get(sorted.size() - 1)[0];
	}

	/** Coflnet path (+query) for one of our price keys, or null when Coflnet has no matching item. */
	static @Nullable String tag(String key) {
		if (key.startsWith("ENCHANTMENT_") || key.contains("_RUNE;") || key.startsWith("POTION_") || key.contains("#")) {
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
				case "90" -> "&PetLevel=90-99";
				case "50" -> "&PetLevel=50-89";
				default -> "&PetLevel=1-49";
			};
		}
		return "PET_" + URLEncoder.encode(type, StandardCharsets.UTF_8) + query;
	}

	private static double num(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : Double.NaN;
	}
}
