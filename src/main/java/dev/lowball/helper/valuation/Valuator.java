package dev.lowball.helper.valuation;

import java.util.ArrayList;
import java.util.List;

import dev.lowball.helper.config.BazaarMode;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.market.SalesTracker;
import dev.lowball.helper.util.Fmt;

/** Turns a {@link SkyblockItem} into a {@link Valuation} using live market data and the user's settings. */
public final class Valuator {
	/** LBIN this far above the sold median is treated as inflated in SMART mode. */
	static final double INFLATED = 1.6;

	private Valuator() {
	}

	public static Valuation value(SkyblockItem item, boolean urgent) {
		Market m = Market.get();
		LowballConfig cfg = LowballConfig.get();
		BazaarProduct bz = m.bazaar(item.key);
		AuctionStats ah = m.auction(item.key);
		CoflnetClient.Stats cofl = null;
		if (bz == null && cfg.useCoflnet) {
			cofl = m.coflnet().get(item.key);
			if (cofl == null && urgent) {
				m.coflnet().request(item.key, true);
			}
		}
		SalesTracker.Volume local = bz == null ? m.sales().volume(item.key, System.currentTimeMillis()) : null;
		return compute(item, cfg, bz, ah, cofl, local, m.items(), m::componentPrice, bz != null ? m.bazaarPrice(item.key) : Double.NaN);
	}

	/** Pure valuation logic, separated from {@link Market} for testing. */
	static Valuation compute(SkyblockItem item, LowballConfig cfg, BazaarProduct bz, AuctionStats ah, CoflnetClient.Stats cofl,
			SalesTracker.Volume local, ItemRegistry registry, java.util.function.ToDoubleFunction<String> price, double bazaarPrice) {
		List<String> warnings = new ArrayList<>();
		double median = cofl != null ? cofl.median() : local != null ? local.avgPrice() : Double.NaN;

		// ---- volume & supply
		double volume;
		String volumeSource;
		int listed;
		if (bz != null) {
			volume = bz.dailyVolume();
			volumeSource = "Bazaar 7d avg";
			listed = bz.sellOffers();
		} else {
			if (cofl != null && cofl.perDay() >= 0) {
				volume = cofl.perDay();
				volumeSource = "Coflnet 24h";
			} else if (local != null) {
				volume = local.perDay();
				volumeSource = "Tracked " + Math.round(local.observedHours()) + "h";
			} else {
				volume = Double.NaN;
				volumeSource = "unknown";
			}
			listed = ah != null ? ah.binCount() : (Market.get().auctionsFetchedAt() > 0 ? 0 : -1);
		}

		// ---- upgrades
		List<Upgrade> upgrades = UpgradeValuer.value(item, price, registry);
		double upgradesRaw = 0;
		for (Upgrade u : upgrades) {
			upgradesRaw += u.total();
		}
		double credit = cfg.upgradeCredit / 100.0;

		// ---- base price
		double base;
		String source;
		boolean isBazaar = bz != null;
		if (isBazaar) {
			base = bazaarPrice;
			source = cfg.bazaarMode == BazaarMode.INSTA_SELL ? "Bazaar insta-sell" : "Bazaar sell offer";
			// ignore troll buy orders (a few coins) that make every spread look huge
			if (bz.instaSell() > bz.instaBuy() * 0.4 && bz.instaBuy() > bz.instaSell() * 1.5) {
				warnings.add("Wide bazaar spread (" + Fmt.coins(bz.instaSell()) + " / " + Fmt.coins(bz.instaBuy()) + ")");
			}
			if (item.isEnchantedBook() && item.enchants.size() == 1) {
				// a book's value is its enchant; nothing else to credit
				upgrades = List.of();
				upgradesRaw = 0;
			}
		} else if (item.isEnchantedBook() && item.enchants.size() > 1) {
			// multi-enchant book: the enchants are the item
			base = upgradesRaw;
			source = "Sum of enchants";
			upgrades = List.of();
			upgradesRaw = 0;
		} else {
			double lbin = Double.NaN;
			String lbinLabel = "LBIN";
			if (ah != null) {
				lbin = ah.lowest();
				boolean upgraded = !item.isClean();
				if (cfg.useCleanLbin && upgraded && !Double.isNaN(ah.cleanLowest())) {
					lbin = ah.cleanLowest();
					lbinLabel = "Clean LBIN";
				} else if (cfg.useCleanLbin && upgraded && upgradesRaw > 0) {
					warnings.add("No clean listing: LBIN may already include upgrades");
				}
			}
			switch (cfg.valueMode) {
				case LBIN -> {
					base = lbin;
					source = lbinLabel;
				}
				case MEDIAN -> {
					base = !Double.isNaN(median) ? median : lbin;
					source = !Double.isNaN(median) ? "Sold median" : lbinLabel;
				}
				case LOWEST -> {
					if (!Double.isNaN(median) && (Double.isNaN(lbin) || median < lbin)) {
						base = median;
						source = "Sold median";
					} else {
						base = lbin;
						source = lbinLabel;
					}
				}
				default -> {
					if (Double.isNaN(lbin)) {
						base = median;
						source = "Sold median (none listed)";
					} else if (!Double.isNaN(median) && lbin > median * INFLATED && volume >= 1) {
						base = median;
						source = "Sold median (LBIN inflated)";
					} else {
						base = lbin;
						source = lbinLabel;
					}
				}
			}
			if (!Double.isNaN(median) && !Double.isNaN(lbin)) {
				// (a median far above LBIN is normal: Coflnet's median mixes in upgraded copies)
				if (lbin > median * INFLATED) {
					warnings.add("LBIN " + Fmt.percent(lbin / median - 1) + " above sold median");
				}
			}
			if (ah != null && ah.cheapest().length > 1 && ah.second() > ah.lowest() * 1.25 && ah.binCount() > 1) {
				warnings.add("Gap to 2nd LBIN: " + Fmt.coins(ah.second()));
			}
		}

		if (Double.isNaN(base) || base <= 0) {
			ItemRegistry.Info info = registry.get(item.id);
			if (info != null && info.npcSell() > 0) {
				base = info.npcSell();
				source = "NPC sell price";
				warnings.add("No market data: using NPC price");
			} else {
				base = Double.NaN;
				source = "No data";
			}
		}
		if (item.pet && item.petCandy > 0) {
			warnings.add("Candied pet (" + item.petCandy + " candy): sells for less");
		}
		if (!(volume >= 0)) {
			warnings.add("Sales volume unknown");
		} else if (volume < 1) {
			warnings.add("Very low volume: may take days to sell");
		}

		double unitValue = Double.isNaN(base) ? Double.NaN : base + upgradesRaw * credit;
		return new Valuation(item, isBazaar, base, source, ah, bz, cofl, median, volume, volumeSource, listed,
				List.copyOf(upgrades), upgradesRaw, credit, unitValue, List.copyOf(warnings));
	}
}
