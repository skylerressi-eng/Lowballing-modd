package dev.lowball.helper.market;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.config.BazaarMode;
import dev.lowball.helper.config.LowballConfig;

/** Owns all market data and the background jobs that refresh it. Every getter is safe to call from the render thread. */
public final class Market {
	private static final Market INSTANCE = new Market();
	private static final Gson GSON = new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();

	private volatile Map<String, AuctionStats> auctions = Map.of();
	private volatile long auctionsFetchedAt;
	private volatile int auctionTotal;
	private volatile long auctionsApiUpdated;
	private volatile Map<String, BazaarProduct> bazaar = Map.of();
	private volatile long bazaarFetchedAt;
	private volatile ItemRegistry items = ItemRegistry.empty();
	private volatile @Nullable String lastError;
	private volatile boolean scanning;
	private volatile long lastScanAttempt;
	private volatile long lastItemsAttempt;

	private final SalesTracker sales = new SalesTracker();
	private final AtomicInteger version = new AtomicInteger();
	private final CoflnetClient cofl = new CoflnetClient(version::incrementAndGet);
	private final AtomicBoolean started = new AtomicBoolean();
	private ScheduledExecutorService scheduler;
	private ExecutorService pool;
	private BooleanSupplier active = () -> true;

	private Market() {
	}

	public static Market get() {
		return INSTANCE;
	}

	// ------------------------------------------------------------------ lifecycle

	public void start(BooleanSupplier active) {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		this.active = active;
		scheduler = Executors.newSingleThreadScheduledExecutor(daemon("LowballHelper-Scheduler"));
		pool = Executors.newFixedThreadPool(4, daemon("LowballHelper-Fetch"));

		scheduler.execute(this::loadCaches);
		scheduler.scheduleWithFixedDelay(guard("bazaar", this::refreshBazaar), 2, 60, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(guard("items", this::refreshItems), 3, 60, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(guard("sales", this::pollSales), 5, 60, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(guard("auctions", this::maybeScanAuctions), 8, 20, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(this::saveCaches, 10, 10, TimeUnit.MINUTES);
	}

	public void stop() {
		if (started.get()) {
			saveCaches();
		}
	}

	/** Drops timers so everything refreshes as soon as possible. */
	public void forceRefresh() {
		lastScanAttempt = 0;
		auctionsApiUpdated = 0;
		if (scheduler != null) {
			scheduler.execute(guard("bazaar", this::refreshBazaar));
			scheduler.execute(guard("auctions", this::maybeScanAuctions));
		}
	}

	// ------------------------------------------------------------------ lookups

	public int version() {
		return version.get();
	}

	public @Nullable AuctionStats auction(String key) {
		return auctions.get(key);
	}

	public @Nullable BazaarProduct bazaar(String id) {
		return bazaar.get(id);
	}

	public ItemRegistry items() {
		return items;
	}

	public SalesTracker sales() {
		return sales;
	}

	public CoflnetClient coflnet() {
		return cofl;
	}

	public long auctionsFetchedAt() {
		return auctionsFetchedAt;
	}

	public long bazaarFetchedAt() {
		return bazaarFetchedAt;
	}

	public int auctionTotal() {
		return auctionTotal;
	}

	public boolean scanning() {
		return scanning;
	}

	public @Nullable String lastError() {
		return lastError;
	}

	public boolean hasAnyData() {
		return !auctions.isEmpty() || !bazaar.isEmpty();
	}

	/** Bazaar price per the configured mode. NaN if not a bazaar product. */
	public double bazaarPrice(String id) {
		BazaarProduct p = bazaar.get(id);
		if (p == null) {
			return Double.NaN;
		}
		double v = LowballConfig.get().bazaarMode == BazaarMode.INSTA_SELL ? p.safeInstaSell() : p.sellOfferPrice();
		return v > 0 ? v : Double.NaN;
	}

	/** Best single-number price for an upgrade component (bazaar first, then cheapest BIN). NaN if unknown. */
	public double componentPrice(String id) {
		double bz = bazaarPrice(id);
		if (!Double.isNaN(bz)) {
			return bz;
		}
		AuctionStats a = auctions.get(id);
		if (a != null) {
			return !Double.isNaN(a.cleanLowest()) ? a.cleanLowest() : a.lowest();
		}
		return Double.NaN;
	}

	// ------------------------------------------------------------------ jobs

	private void refreshBazaar() throws IOException {
		if (!active.getAsBoolean()) {
			return;
		}
		Map<String, BazaarProduct> fresh = BazaarFetcher.fetch();
		if (!fresh.isEmpty()) {
			bazaar = Map.copyOf(fresh);
			bazaarFetchedAt = System.currentTimeMillis();
			version.incrementAndGet();
		}
	}

	private void refreshItems() throws IOException {
		if (!active.getAsBoolean()) {
			return;
		}
		Path cache = LowballHelper.dataDir().resolve("items-cache.json");
		long age = Long.MAX_VALUE;
		try {
			if (Files.exists(cache)) {
				age = System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis();
			}
		} catch (IOException ignored) {
			// treat as missing
		}
		if (items.size() > 0 && age < 12 * 3600_000L) {
			return;
		}
		if (System.currentTimeMillis() - lastItemsAttempt < 10 * 60_000L) {
			return;
		}
		lastItemsAttempt = System.currentTimeMillis();
		ItemRegistry reg = ItemRegistry.download(cache);
		if (reg.size() > 0) {
			items = reg;
			version.incrementAndGet();
		}
	}

	private void pollSales() throws IOException {
		if (!active.getAsBoolean() || !LowballConfig.get().trackSales) {
			return;
		}
		sales.poll();
	}

	private void maybeScanAuctions() throws IOException {
		LowballConfig cfg = LowballConfig.get();
		cofl.setEnabled(cfg.useCoflnet);
		long now = System.currentTimeMillis();
		if (!active.getAsBoolean() || cfg.ahScanMinutes <= 0 || scanning) {
			return;
		}
		if (now - lastScanAttempt < cfg.ahScanMinutes * 60_000L) {
			return;
		}
		lastScanAttempt = now;
		scanning = true;
		try {
			long t0 = System.nanoTime();
			AuctionScanner.Result result = new AuctionScanner(pool).scan(auctionsApiUpdated);
			if (result == null) {
				return;
			}
			auctions = Map.copyOf(result.stats());
			auctionsApiUpdated = result.lastUpdated();
			auctionsFetchedAt = System.currentTimeMillis();
			auctionTotal = result.auctions();
			version.incrementAndGet();
			LowballHelper.LOGGER.info("Scanned {} auctions into {} price keys in {} ms",
					result.auctions(), result.stats().size(), (System.nanoTime() - t0) / 1_000_000);
			saveAuctions();
		} finally {
			scanning = false;
		}
	}

	private Runnable guard(String name, IoTask task) {
		return () -> {
			try {
				task.run();
				if (lastError != null && lastError.startsWith(name)) {
					lastError = null;
				}
			} catch (IOException | RuntimeException e) {
				lastError = name + ": " + e.getMessage();
				LowballHelper.LOGGER.warn("Lowball Helper {} refresh failed: {}", name, e.toString());
			} catch (Throwable t) {
				LowballHelper.LOGGER.error("Lowball Helper {} job crashed", name, t);
			}
		};
	}

	@FunctionalInterface
	private interface IoTask {
		void run() throws IOException;
	}

	// ------------------------------------------------------------------ disk cache

	private static final class SavedAuctions {
		long fetchedAt;
		long apiUpdated;
		int total;
		Map<String, AuctionStats> stats;
	}

	private void loadCaches() {
		Path dir = LowballHelper.dataDir();
		ItemRegistry cached = ItemRegistry.loadCached(dir.resolve("items-cache.json"));
		if (cached != null && cached.size() > 0) {
			items = cached;
		}
		sales.load(dir.resolve("sales-history.json"));
		Path auctionsFile = dir.resolve("auctions-cache.json");
		if (Files.exists(auctionsFile) && auctions.isEmpty()) {
			try {
				SavedAuctions s = GSON.fromJson(Files.readString(auctionsFile, StandardCharsets.UTF_8), SavedAuctions.class);
				// only trust a cache from the last 6 hours
				if (s != null && s.stats != null && System.currentTimeMillis() - s.fetchedAt < 6 * 3600_000L) {
					auctions = Map.copyOf(s.stats);
					auctionsFetchedAt = s.fetchedAt;
					auctionTotal = s.total;
				}
			} catch (IOException | JsonParseException e) {
				LowballHelper.LOGGER.warn("Could not read auction cache", e);
			}
		}
		version.incrementAndGet();
	}

	private void saveAuctions() {
		SavedAuctions s = new SavedAuctions();
		s.fetchedAt = auctionsFetchedAt;
		s.apiUpdated = auctionsApiUpdated;
		s.total = auctionTotal;
		s.stats = new HashMap<>(auctions);
		try {
			Path file = LowballHelper.dataDir().resolve("auctions-cache.json");
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(s), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LowballHelper.LOGGER.warn("Could not save auction cache", e);
		}
	}

	private void saveCaches() {
		sales.save(LowballHelper.dataDir().resolve("sales-history.json"));
	}

	private static ThreadFactory daemon(String name) {
		AtomicInteger n = new AtomicInteger();
		return r -> {
			Thread t = new Thread(r, name + "-" + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		};
	}
}
