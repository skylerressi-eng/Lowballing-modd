package dev.lowball.helper.market;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.item.ItemBytes;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.util.Http;

/**
 * Downloads every page of the public auctions endpoint and reduces it to per-key stats:
 * lowest BIN, clean lowest BIN, the 5 cheapest listings and listing counts.
 * The endpoint needs no API key; pages are fetched in parallel and decoded off-thread.
 */
public final class AuctionScanner {
	static final String URL = "https://api.hypixel.net/v2/skyblock/auctions?page=";

	public record Result(Map<String, AuctionStats> stats, long lastUpdated, int auctions) {
	}

	/** Mutable per-key accumulator. */
	static final class Acc {
		final String key;
		String name;
		final double[] low = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		double clean = Double.NaN;
		int bins;
		int bids;

		Acc(String key, String name) {
			this.key = key;
			this.name = name;
		}

		void addBin(double price, boolean isClean) {
			bins++;
			if (isClean && (Double.isNaN(clean) || price < clean)) {
				clean = price;
			}
			for (int i = 0; i < low.length; i++) {
				if (Double.isNaN(low[i]) || price < low[i]) {
					System.arraycopy(low, i, low, i + 1, low.length - i - 1);
					low[i] = price;
					return;
				}
			}
		}

		void merge(Acc o) {
			bins += o.bins - countFilled(o.low);
			bids += o.bids;
			for (double p : o.low) {
				if (!Double.isNaN(p)) {
					addBin(p, false);
				}
			}
			if (!Double.isNaN(o.clean) && (Double.isNaN(clean) || o.clean < clean)) {
				clean = o.clean;
			}
		}

		AuctionStats build() {
			int n = countFilled(low);
			return new AuctionStats(key, name, n > 0 ? low[0] : Double.NaN, clean, Arrays.copyOf(low, n), bins, bids);
		}

		private static int countFilled(double[] a) {
			int n = 0;
			for (double d : a) {
				if (!Double.isNaN(d)) {
					n++;
				}
			}
			return n;
		}
	}

	private final ExecutorService pool;

	public AuctionScanner(ExecutorService pool) {
		this.pool = pool;
	}

	/** Returns null if the data has not changed since {@code previousUpdate}. */
	public Result scan(long previousUpdate) throws IOException {
		Map<String, Acc> first = new HashMap<>();
		long[] meta = new long[3]; // totalPages, lastUpdated, auctions
		readPage(0, first, meta);
		if (meta[1] != 0 && meta[1] == previousUpdate) {
			return null;
		}
		int pages = (int) meta[0];
		List<Future<Map<String, Acc>>> futures = new ArrayList<>();
		for (int p = 1; p < pages; p++) {
			final int page = p;
			futures.add(pool.submit(() -> {
				Map<String, Acc> local = new HashMap<>();
				long[] m = new long[3];
				for (int attempt = 0; ; attempt++) {
					try {
						readPage(page, local, m);
						return local;
					} catch (Http.StatusException e) {
						// a page may vanish while the API rotates; that's fine
						if (e.status == 404) {
							return local;
						}
						if (attempt >= 2) {
							throw e;
						}
					} catch (IOException e) {
						if (attempt >= 2) {
							throw e;
						}
						local.clear();
					}
					Thread.sleep(500L * (attempt + 1));
				}
			}));
		}
		Map<String, Acc> all = first;
		int auctions = (int) meta[2];
		for (Future<Map<String, Acc>> f : futures) {
			try {
				for (Acc acc : f.get().values()) {
					Acc existing = all.get(acc.key);
					if (existing == null) {
						all.put(acc.key, acc);
					} else {
						existing.merge(acc);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted", e);
			} catch (java.util.concurrent.ExecutionException e) {
				throw new IOException("Auction page failed", e.getCause());
			}
		}
		Map<String, AuctionStats> stats = new HashMap<>(all.size() * 2);
		for (Acc acc : all.values()) {
			stats.put(acc.key, acc.build());
		}
		addPetBaseKeys(stats);
		return new Result(stats, meta[1], auctions);
	}

	/** Also index pets by tier regardless of level, so a pet without a matching level bucket still prices. */
	static void addPetBaseKeys(Map<String, AuctionStats> stats) {
		Map<String, Acc> base = new HashMap<>();
		for (AuctionStats s : stats.values()) {
			int at = s.key().indexOf('@');
			if (at < 0) {
				continue;
			}
			String baseKey = s.key().substring(0, at);
			Acc acc = base.computeIfAbsent(baseKey, k -> new Acc(k, s.name()));
			Acc tmp = new Acc(baseKey, s.name());
			for (double p : s.cheapest()) {
				tmp.addBin(p, false);
			}
			tmp.bins = s.binCount();
			tmp.bids = s.auctionCount();
			tmp.clean = s.cleanLowest();
			acc.merge(tmp);
		}
		for (Acc acc : base.values()) {
			stats.putIfAbsent(acc.key, acc.build());
		}
	}

	private static void readPage(int page, Map<String, Acc> out, long[] meta) throws IOException {
		try (Reader r = Http.reader(URL + page); JsonReader json = new JsonReader(r)) {
			json.beginObject();
			while (json.hasNext()) {
				switch (json.nextName()) {
					case "totalPages" -> meta[0] = json.nextLong();
					case "lastUpdated" -> meta[1] = json.nextLong();
					case "totalAuctions" -> meta[2] = json.nextLong();
					case "success" -> {
						if (!json.nextBoolean()) {
							throw new IOException("API returned success=false");
						}
					}
					case "auctions" -> {
						json.beginArray();
						while (json.hasNext()) {
							readAuction(json, out);
						}
						json.endArray();
					}
					default -> json.skipValue();
				}
			}
			json.endObject();
		}
	}

	private static void readAuction(JsonReader json, Map<String, Acc> out) throws IOException {
		boolean bin = false;
		boolean claimed = false;
		double startingBid = 0;
		String bytes = null;
		String name = "";
		json.beginObject();
		while (json.hasNext()) {
			String field = json.nextName();
			if (json.peek() == JsonToken.NULL) {
				json.nextNull();
				continue;
			}
			switch (field) {
				case "bin" -> bin = json.nextBoolean();
				case "claimed" -> claimed = json.nextBoolean();
				case "starting_bid" -> startingBid = json.nextDouble();
				case "item_bytes" -> bytes = json.nextString();
				case "item_name" -> name = json.nextString();
				default -> json.skipValue();
			}
		}
		json.endObject();
		if (bytes == null || claimed) {
			return;
		}
		SkyblockItem item;
		try {
			item = ItemBytes.decodeFirst(bytes, name);
		} catch (IOException | RuntimeException e) {
			LowballHelper.LOGGER.debug("Skipping undecodable auction item {}", name, e);
			return;
		}
		if (item == null) {
			return;
		}
		Acc acc = out.computeIfAbsent(item.key, k -> new Acc(k, item.name));
		if (bin) {
			acc.addBin(startingBid / item.count, item.isClean());
		} else {
			acc.bids++;
		}
	}
}
