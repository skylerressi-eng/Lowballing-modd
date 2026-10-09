package dev.lowball.helper.market;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.item.ItemBytes;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.util.Http;

/**
 * Builds our own sales volume by polling the "auctions ended in the last minute" endpoint and bucketing sales per hour.
 * The number of minutes actually observed is tracked too, so partial coverage extrapolates honestly.
 */
public final class SalesTracker {
	static final String URL = "https://api.hypixel.net/v2/skyblock/auctions_ended";
	private static final long HOUR = 3_600_000L;
	private static final int KEEP_HOURS = 48;
	private static final Gson GSON = new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();

	/** hour → observed minutes. */
	private final TreeMap<Long, Integer> coverage = new TreeMap<>();
	/** key → hour → {count, sum}. */
	private final Map<String, TreeMap<Long, double[]>> sales = new HashMap<>();
	private long lastUpdated;

	public record Volume(double perDay, double avgPrice, int sales, double observedHours) {
	}

	public synchronized void record(String key, double unitPrice, long timestamp) {
		long hour = timestamp / HOUR;
		double[] b = sales.computeIfAbsent(key, k -> new TreeMap<>()).computeIfAbsent(hour, h -> new double[2]);
		b[0]++;
		b[1] += unitPrice;
	}

	public synchronized void observeMinute(long timestamp) {
		coverage.merge(timestamp / HOUR, 1, Integer::sum);
	}

	/** Returns null when we haven't observed enough to say anything. */
	public synchronized Volume volume(String key, long now) {
		long from = now / HOUR - 23;
		int minutes = 0;
		for (int m : coverage.tailMap(from, true).values()) {
			minutes += m;
		}
		if (minutes < 20) {
			return null;
		}
		double count = 0, sum = 0;
		TreeMap<Long, double[]> hours = sales.get(key);
		if (hours != null) {
			for (double[] b : hours.tailMap(from, true).values()) {
				count += b[0];
				sum += b[1];
			}
		}
		double fraction = Math.min(1.0, minutes / 1440.0);
		return new Volume(count / fraction, count > 0 ? sum / count : Double.NaN, (int) count, minutes / 60.0);
	}

	public void poll() throws IOException {
		try (Reader r = Http.reader(URL); JsonReader json = new JsonReader(r)) {
			long updated = 0;
			json.beginObject();
			List<Object[]> rows = new ArrayList<>();
			while (json.hasNext()) {
				switch (json.nextName()) {
					case "lastUpdated" -> updated = json.nextLong();
					case "auctions" -> {
						json.beginArray();
						while (json.hasNext()) {
							Object[] row = readEnded(json);
							if (row != null) {
								rows.add(row);
							}
						}
						json.endArray();
					}
					default -> json.skipValue();
				}
			}
			json.endObject();
			synchronized (this) {
				if (updated != 0 && updated == lastUpdated) {
					return;
				}
				lastUpdated = updated;
				long now = updated != 0 ? updated : System.currentTimeMillis();
				observeMinute(now);
				for (Object[] row : rows) {
					record((String) row[0], (Double) row[1], (Long) row[2]);
				}
				prune(now);
			}
		}
	}

	private static Object[] readEnded(JsonReader json) throws IOException {
		String bytes = null;
		double price = 0;
		long ts = 0;
		json.beginObject();
		while (json.hasNext()) {
			String f = json.nextName();
			if (json.peek() == JsonToken.NULL) {
				json.nextNull();
				continue;
			}
			switch (f) {
				case "item_bytes" -> bytes = json.nextString();
				case "price" -> price = json.nextDouble();
				case "timestamp" -> ts = json.nextLong();
				default -> json.skipValue();
			}
		}
		json.endObject();
		if (bytes == null || price <= 0) {
			return null;
		}
		try {
			SkyblockItem item = ItemBytes.decodeFirst(bytes, "");
			if (item == null) {
				return null;
			}
			return new Object[]{item.key, price / item.count, ts};
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private void prune(long now) {
		long cutoff = now / HOUR - KEEP_HOURS;
		coverage.headMap(cutoff).clear();
		sales.values().removeIf(m -> {
			m.headMap(cutoff).clear();
			return m.isEmpty();
		});
	}

	// --- persistence ---

	private static final class Saved {
		Map<Long, Integer> coverage;
		Map<String, Map<Long, double[]>> sales;
		long lastUpdated;
	}

	public synchronized void save(Path file) {
		Saved s = new Saved();
		s.coverage = coverage;
		s.sales = new HashMap<>(sales);
		s.lastUpdated = lastUpdated;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(s), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LowballHelper.LOGGER.warn("Could not save sales history", e);
		}
	}

	public synchronized void load(Path file) {
		if (!Files.exists(file)) {
			return;
		}
		try {
			Saved s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<Saved>() {
			}.getType());
			if (s == null) {
				return;
			}
			if (s.coverage != null) {
				coverage.putAll(s.coverage);
			}
			if (s.sales != null) {
				s.sales.forEach((k, v) -> sales.put(k, new TreeMap<>(v)));
			}
			lastUpdated = s.lastUpdated;
			prune(System.currentTimeMillis());
		} catch (IOException | JsonParseException e) {
			LowballHelper.LOGGER.warn("Could not read sales history", e);
		}
	}
}
