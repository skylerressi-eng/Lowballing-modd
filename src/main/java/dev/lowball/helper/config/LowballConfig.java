package dev.lowball.helper.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.lowball.helper.LowballHelper;

/** All user settings. Saved as config/lowballhelper/config.json. */
public final class LowballConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LowballConfig instance;
	private static int revision;

	// --- Offer ---
	public Preset preset = Preset.STANDARD;
	public double customPercent = 75;
	/** Raise offers on fast sellers, lower them on slow ones. */
	public boolean volumeAdjust = true;
	/** Lower offers when the AH is flooded relative to daily sales. */
	public boolean supplyAdjust = true;
	/** Never offer more than (resale after tax - this). 0 disables. */
	public double minProfit = 0;
	/** Never offer more than this percent of value even after adjustments. */
	public double maxPercent = 95;
	/** Never offer less than this percent of value even after adjustments. */
	public double minPercent = 30;
	public RoundMode roundMode = RoundMode.SIG3;

	// --- Valuation ---
	public ValueMode valueMode = ValueMode.SMART;
	/** Prefer LBIN of items without recomb/potato books/stars/gems as the base. */
	public boolean useCleanLbin = true;
	/** How much of the bazaar cost of applied upgrades counts towards value (percent). */
	public double upgradeCredit = 50;
	public BazaarMode bazaarMode = BazaarMode.SELL_OFFER;
	public double bazaarTaxPercent = 1.25;

	// --- Display ---
	public boolean panelEnabled = true;
	public PanelSide panelSide = PanelSide.AUTO;
	/** Panel size multiplier; 0 = shrink automatically to fit beside the trade menu. */
	public double panelScale = 0;
	public boolean compactRows = false;
	public boolean showYourSide = true;
	public boolean tooltipEnabled = true;
	public boolean tooltipRequireShift = false;
	public boolean signAutofill = true;

	// --- Data ---
	/** Minutes between full auction house scans; 0 disables the scanner (Coflnet only). */
	public int ahScanMinutes = 5;
	public boolean useCoflnet = true;
	public boolean trackSales = true;
	/** Run outside of Hypixel too (for testing on other servers / single player). */
	public boolean enableEverywhere = false;

	public static LowballConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	/** Incremented on every save so cached valuations know to recompute. */
	public static int revision() {
		return revision;
	}

	public double basePercent() {
		return preset == Preset.CUSTOM ? customPercent : preset.percent;
	}

	public void setBasePercent(double pct) {
		customPercent = Math.max(1, Math.min(100, Math.round(pct)));
		preset = Preset.CUSTOM;
	}

	public static Path file() {
		return LowballHelper.dataDir().resolve("config.json");
	}

	private static LowballConfig load() {
		Path file = file();
		if (Files.exists(file)) {
			try {
				LowballConfig cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LowballConfig.class);
				if (cfg != null) {
					cfg.sanitize();
					return cfg;
				}
			} catch (IOException | JsonParseException e) {
				LowballHelper.LOGGER.warn("Could not read config, using defaults", e);
			}
		}
		LowballConfig cfg = new LowballConfig();
		cfg.save();
		return cfg;
	}

	public void sanitize() {
		if (preset == null) preset = Preset.STANDARD;
		if (valueMode == null) valueMode = ValueMode.SMART;
		if (roundMode == null) roundMode = RoundMode.SIG3;
		if (panelSide == null) panelSide = PanelSide.AUTO;
		if (bazaarMode == null) bazaarMode = BazaarMode.SELL_OFFER;
		customPercent = clamp(customPercent, 1, 100);
		upgradeCredit = clamp(upgradeCredit, 0, 100);
		maxPercent = clamp(maxPercent, 1, 100);
		minPercent = clamp(minPercent, 0, maxPercent);
		minProfit = Math.max(0, minProfit);
		bazaarTaxPercent = clamp(bazaarTaxPercent, 0, 10);
		ahScanMinutes = (int) clamp(ahScanMinutes, 0, 120);
		panelScale = panelScale <= 0 ? 0 : clamp(panelScale, 0.5, 1.5);
	}

	public void save() {
		sanitize();
		revision++;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LowballHelper.LOGGER.warn("Could not save config", e);
		}
	}

	public void resetToDefaults() {
		LowballConfig d = new LowballConfig();
		for (var field : LowballConfig.class.getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				field.set(this, field.get(d));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		save();
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
