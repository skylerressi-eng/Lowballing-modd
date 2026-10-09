package dev.lowball.helper.client.ui;

import java.util.List;
import java.util.function.Supplier;

import dev.lowball.helper.config.BazaarMode;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.config.PanelSide;
import dev.lowball.helper.config.Preset;
import dev.lowball.helper.config.RoundMode;
import dev.lowball.helper.config.ValueMode;
import dev.lowball.helper.util.Fmt;

/** Every user setting as a clickable option, shared by the config screen and the in-panel quick settings. */
public final class Options {
	/** @param back true for right click / shift click (go backwards or decrease) */
	@FunctionalInterface
	public interface Change {
		void apply(LowballConfig cfg, boolean back);
	}

	public record Option(String label, Supplier<String> value, Change change, String description, boolean quick) {
		public void click(boolean back) {
			LowballConfig cfg = LowballConfig.get();
			change.apply(cfg, back);
			cfg.save();
		}
	}

	private static final double[] CREDITS = {0, 25, 50, 75, 100};
	private static final double[] MIN_PROFITS = {0, 100_000, 250_000, 500_000, 1_000_000, 2_500_000, 5_000_000, 10_000_000};
	private static final double[] MAX_PCTS = {80, 85, 90, 95, 100};
	private static final int[] SCANS = {0, 2, 5, 10, 15, 30};
	private static final double[] SCALES = {0, 1.0, 0.9, 0.8, 0.7, 0.6};

	private Options() {
	}

	private static LowballConfig c() {
		return LowballConfig.get();
	}

	private static String onOff(boolean b) {
		return b ? "§aON" : "§cOFF";
	}

	public static final List<Option> ALL = List.of(
			new Option("Preset", () -> "§e" + c().preset.label + " §7(" + Math.round(c().basePercent()) + "%)",
					(cfg, back) -> cfg.preset = back ? cfg.preset.previous() : cfg.preset.next(),
					"Base offer as a percent of value. Snipe 55, Aggressive 65, Standard 75, Fair 85, Generous 92.", true),
			new Option("Custom percent", () -> Math.round(c().customPercent) + "%",
					(cfg, back) -> {
						cfg.customPercent = Math.max(1, Math.min(100, cfg.customPercent + (back ? -5 : 5)));
						cfg.preset = Preset.CUSTOM;
					},
					"Your own base percent (selects the Custom preset). Shift/right click lowers.", false),
			new Option("Value from", () -> c().valueMode.label,
					(cfg, back) -> cfg.valueMode = cycle(ValueMode.values(), cfg.valueMode, back),
					"Smart = clean LBIN, switching to the sold median if LBIN is inflated. LBIN, sold median, or the lower of both.", true),
			new Option("Volume adjust", () -> onOff(c().volumeAdjust),
					(cfg, back) -> cfg.volumeAdjust = !cfg.volumeAdjust,
					"+5% for 50+ sales/day, -5% under 10, -10% under 3, -15% under 1 per day.", true),
			new Option("Supply adjust", () -> onOff(c().supplyAdjust),
					(cfg, back) -> cfg.supplyAdjust = !cfg.supplyAdjust,
					"-5% when listings take 5+ days to sell through, -10% for 14+ days.", true),
			new Option("Clean LBIN base", () -> onOff(c().useCleanLbin),
					(cfg, back) -> cfg.useCleanLbin = !cfg.useCleanLbin,
					"Price upgraded items from the cheapest listing without recomb/books/stars/gems, then add upgrades.", true),
			new Option("Upgrade credit", () -> Math.round(c().upgradeCredit) + "%",
					(cfg, back) -> cfg.upgradeCredit = cycle(CREDITS, cfg.upgradeCredit, back),
					"How much of the cost of recombs, books, enchants, stars, gems etc. counts as value.", true),
			new Option("Min profit", () -> c().minProfit <= 0 ? "§7off" : Fmt.coins(c().minProfit),
					(cfg, back) -> cfg.minProfit = cycle(MIN_PROFITS, cfg.minProfit, back),
					"Never suggest an offer that leaves less than this profit after AH tax.", true),
			new Option("Max percent", () -> Math.round(c().maxPercent) + "%",
					(cfg, back) -> cfg.maxPercent = cycle(MAX_PCTS, cfg.maxPercent, back),
					"Hard cap on the offer percent after volume adjustments.", false),
			new Option("Rounding", () -> c().roundMode.label,
					(cfg, back) -> cfg.roundMode = cycle(RoundMode.values(), cfg.roundMode, back),
					"Round offers down to clean numbers.", true),
			new Option("Bazaar price", () -> c().bazaarMode.label,
					(cfg, back) -> cfg.bazaarMode = cycle(BazaarMode.values(), cfg.bazaarMode, back),
					"Sell offer = patient resale price, Insta-sell = instant resale price.", true),
			new Option("Compact rows", () -> onOff(c().compactRows),
					(cfg, back) -> cfg.compactRows = !cfg.compactRows,
					"Two lines per item instead of three.", true),
			new Option("Show your side", () -> onOff(c().showYourSide),
					(cfg, back) -> cfg.showYourSide = !cfg.showYourSide,
					"Also list and value what you put in.", true),
			new Option("Panel side", () -> c().panelSide.label,
					(cfg, back) -> cfg.panelSide = cycle(PanelSide.values(), cfg.panelSide, back),
					"Where the panel goes next to the trade menu.", true),
			new Option("Panel size", () -> c().panelScale <= 0 ? "Auto" : Math.round(c().panelScale * 100) + "%",
					(cfg, back) -> cfg.panelScale = cycle(SCALES, cfg.panelScale, back),
					"Auto shrinks the panel to fit next to the trade menu at any GUI scale.", true),
			new Option("Item tooltips", () -> !c().tooltipEnabled ? "§cOFF" : c().tooltipRequireShift ? "§eShift" : "§aON",
					(cfg, back) -> {
						if (!cfg.tooltipEnabled) {
							cfg.tooltipEnabled = true;
							cfg.tooltipRequireShift = false;
						} else if (!cfg.tooltipRequireShift) {
							cfg.tooltipRequireShift = true;
						} else {
							cfg.tooltipEnabled = false;
						}
					},
					"Price, volume, listings and offer on every SkyBlock item tooltip.", true),
			new Option("Autofill coin sign", () -> onOff(c().signAutofill),
					(cfg, back) -> cfg.signAutofill = !cfg.signAutofill,
					"After pressing Autofill, the next coin sign gets the offer typed in. You still press Done yourself.", true),
			new Option("AH scan", () -> c().ahScanMinutes <= 0 ? "§cOFF" : "every " + c().ahScanMinutes + "m",
					(cfg, back) -> cfg.ahScanMinutes = cycle(SCANS, cfg.ahScanMinutes, back),
					"Full auction house scan for LBIN, clean LBIN and listing counts (~50 MB per scan).", false),
			new Option("Coflnet stats", () -> onOff(c().useCoflnet),
					(cfg, back) -> cfg.useCoflnet = !cfg.useCoflnet,
					"Fetch 24h sales volume and sold median from sky.coflnet.com for items you look at.", false),
			new Option("Track sales", () -> onOff(c().trackSales),
					(cfg, back) -> cfg.trackSales = !cfg.trackSales,
					"Count sold auctions every minute for our own volume numbers.", false),
			new Option("Trade panel", () -> onOff(c().panelEnabled),
					(cfg, back) -> cfg.panelEnabled = !cfg.panelEnabled,
					"Show the panel next to the trade menu.", false),
			new Option("Outside Hypixel", () -> onOff(c().enableEverywhere),
					(cfg, back) -> cfg.enableEverywhere = !cfg.enableEverywhere,
					"Run data fetching and tooltips on any server (testing).", false));

	private static <T> T cycle(T[] values, T current, boolean back) {
		int i = 0;
		for (int k = 0; k < values.length; k++) {
			if (values[k] == current) {
				i = k;
			}
		}
		return values[(i + (back ? -1 : 1) + values.length) % values.length];
	}

	private static double cycle(double[] values, double current, boolean back) {
		int i = nearest(values.length, k -> Math.abs(values[k] - current));
		return values[(i + (back ? -1 : 1) + values.length) % values.length];
	}

	private static int cycle(int[] values, int current, boolean back) {
		int i = nearest(values.length, k -> Math.abs(values[k] - current));
		return values[(i + (back ? -1 : 1) + values.length) % values.length];
	}

	private static int nearest(int n, java.util.function.IntToDoubleFunction dist) {
		int best = 0;
		for (int k = 1; k < n; k++) {
			if (dist.applyAsDouble(k) < dist.applyAsDouble(best)) {
				best = k;
			}
		}
		return best;
	}
}
