package dev.lowball.helper.valuation;

import java.util.ArrayList;
import java.util.List;

import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.util.Fmt;

/** Turns a value into a lowball offer using the preset, volume and supply, tax and profit floor. */
public final class OfferCalculator {
	private OfferCalculator() {
	}

	/** Percent points added for a given daily sales volume. */
	public static int volumeAdjustment(double perDay) {
		if (Double.isNaN(perDay) || perDay < 0) {
			return -5;
		}
		if (perDay >= 50) {
			return 5;
		}
		if (perDay >= 10) {
			return 0;
		}
		if (perDay >= 3) {
			return -5;
		}
		if (perDay >= 1) {
			return -10;
		}
		return -15;
	}

	/** Percent points added for days of supply on the market. */
	public static int supplyAdjustment(double daysToClear) {
		if (Double.isNaN(daysToClear)) {
			return 0;
		}
		if (daysToClear > 14) {
			return -10;
		}
		if (daysToClear > 5) {
			return -5;
		}
		return 0;
	}

	public static double resaleNet(Valuation v, LowballConfig cfg) {
		double gross = v.totalValue();
		if (v.bazaar()) {
			return gross * (1 - cfg.bazaarTaxPercent / 100.0);
		}
		return AhTax.net(gross);
	}

	public static Offer offer(Valuation v, LowballConfig cfg) {
		if (!v.known()) {
			return Offer.NONE;
		}
		List<String> adj = new ArrayList<>();
		double pct = cfg.basePercent();
		if (cfg.volumeAdjust) {
			int a = volumeAdjustment(v.dailyVolume());
			if (a != 0) {
				pct += a;
				adj.add(signed(a) + " volume (" + (Double.isNaN(v.dailyVolume()) ? "unknown" : Fmt.volume(v.dailyVolume()) + "/day") + ")");
			}
		}
		if (cfg.supplyAdjust && !v.bazaar()) {
			int a = supplyAdjustment(v.daysToClear());
			if (a != 0) {
				pct += a;
				adj.add(signed(a) + " supply (" + Fmt.days(v.daysToClear()) + " of listings)");
			}
		}
		double clamped = Math.max(cfg.minPercent, Math.min(cfg.maxPercent, pct));
		if (clamped != pct) {
			adj.add("clamped to " + Math.round(clamped) + "%");
		}
		pct = clamped / 100.0;

		double value = v.totalValue();
		double resale = resaleNet(v, cfg);
		double offer = value * pct;
		if (cfg.minProfit > 0 && offer > resale - cfg.minProfit) {
			offer = Math.max(0, resale - cfg.minProfit);
			adj.add("capped for " + Fmt.coins(cfg.minProfit) + " profit");
		}
		if (cfg.roundMode.sig > 0) {
			offer = Fmt.floorSig(offer, cfg.roundMode.sig);
		}
		return new Offer(value > 0 ? offer / value : 0, offer, resale, resale - offer, List.copyOf(adj));
	}

	private static String signed(int a) {
		return (a > 0 ? "+" : "") + a + "%";
	}
}
