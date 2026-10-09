package dev.lowball.helper.valuation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.lowball.helper.config.BazaarMode;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.Exotic;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionScanner;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.market.NeuRepo;
import dev.lowball.helper.market.SalesTracker;
import dev.lowball.helper.util.Fmt;

/** Turns a {@link SkyblockItem} into a {@link Valuation} using live market data and the user's settings. */
public final class Valuator {
	/** LBIN this far above the sold median is treated as inflated in SMART mode. */
	static final double INFLATED = 1.6;
	/** Item categories where "clean" (no upgrades) copies trade differently from upgraded ones. */
	private static final Set<String> GEAR = Set.of("SWORD", "LONGSWORD", "BOW", "WAND", "FISHING_WEAPON", "HELMET", "CHESTPLATE",
			"LEGGINGS", "BOOTS", "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET", "DRILL", "PICKAXE", "AXE", "HOE", "SHOVEL",
			"FISHING_ROD", "DEPLOYABLE", "GAUNTLET");

	private Valuator() {
	}

	public static Valuation value(SkyblockItem item, boolean urgent) {
		return compute(item, LowballConfig.get(), Market.get().view());
	}

	public static Valuation compute(SkyblockItem item, LowballConfig cfg, MarketView m) {
		List<String> warnings = new ArrayList<>();
		ItemRegistry registry = m.registry();
		ItemRegistry.Info info = registry.get(item.id);
		BazaarProduct bz = m.bazaar(item.key);
		AuctionStats ah = m.auction(item.key);
		boolean gear = info != null && GEAR.contains(info.category()) || !item.isClean() || !item.enchants.isEmpty();

		CoflnetClient.Stats cofl = null, coflClean = null;
		if (bz == null && cfg.useCoflnet) {
			cofl = m.coflnet(item.key, false);
			if (gear && !item.pet) {
				coflClean = m.coflnet(item.key, true);
			}
		}
		SalesTracker.Volume local = bz == null ? m.localVolume(item.key) : null;
		double median = cofl != null ? cofl.median() : local != null ? local.avgPrice() : Double.NaN;
		double cleanMedian = coflClean != null ? coflClean.median() : Double.NaN;

		// ---- exotics
		Valuation.ExoticInfo exotic = null;
		if (cfg.exoticPricing && item.color >= 0 && bz == null) {
			int def = registry.defaultColor(item.id);
			Exotic.Type type = Exotic.classify(item, def);
			if (type != Exotic.Type.NONE) {
				exotic = exotic(item, type, def, m, cfg);
			}
		}

		// ---- volume & supply
		double volume;
		String volumeSource;
		int listed;
		if (bz != null) {
			volume = bz.dailyVolume();
			volumeSource = "Bazaar 7d avg";
			listed = bz.sellOffers();
		} else if (exotic != null && (exotic.exactSales() != null && exotic.exactSales().any() || exotic.typeSales() != null && exotic.typeSales().any())) {
			boolean exact = exotic.exactSales() != null && exotic.exactSales().any();
			CoflnetClient.History h = exact ? exotic.exactSales() : exotic.typeSales();
			volume = h.sales() / 30.0;
			volumeSource = "Coflnet 30d (" + (exact ? "this color" : exotic.type().label) + ")";
			AuctionStats l = exotic.exactListings() != null ? exotic.exactListings() : exotic.typeListings();
			listed = l != null ? l.binCount() : 0;
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
			listed = ah != null ? ah.binCount() : (m.auctionsLoaded() ? 0 : -1);
		}

		// ---- upgrades
		List<Upgrade> raw = UpgradeValuer.value(item, m::componentPrice, registry, m::stone);
		List<Valuation.Credited> upgrades = new ArrayList<>();
		for (Upgrade u : raw) {
			upgrades.add(new Valuation.Credited(u, cfg.credit(u.category())));
		}
		double upgradesRaw = 0;
		for (Upgrade u : raw) {
			upgradesRaw += u.total();
		}

		// ---- crafting cost
		CraftCost craft = null;
		if (bz == null && !item.pet && !item.isEnchantedBook()) {
			craft = CraftCost.of(item.id, new CraftCost.Prices() {
				public double price(String id) {
					return m.componentPrice(id);
				}

				public @Nullable String source(String id) {
					return m.componentSource(id);
				}

				public String name(String id) {
					return registry.name(id);
				}

				public @Nullable Optional<NeuRepo.Recipe> recipe(String id) {
					return m.recipe(id);
				}
			});
		}
		boolean craftOk = craft != null && craft.complete() && craft.total() > 0;

		// ---- base price
		List<Valuation.Candidate> cands = new ArrayList<>();
		double base;
		String source;
		boolean isBazaar = bz != null;
		if (isBazaar) {
			base = m.bazaarPrice(item.key);
			source = cfg.bazaarMode == BazaarMode.INSTA_SELL ? "Bazaar insta-sell" : "Bazaar sell offer";
			cands.add(new Valuation.Candidate("Bazaar sell offer", bz.sellOfferPrice(), "patient resale", cfg.bazaarMode == BazaarMode.SELL_OFFER));
			cands.add(new Valuation.Candidate("Bazaar insta-sell", bz.safeInstaSell(), "instant resale", cfg.bazaarMode == BazaarMode.INSTA_SELL));
			if (bz.instaSell() > bz.instaBuy() * 0.4 && bz.instaBuy() > bz.instaSell() * 1.5) {
				warnings.add("Wide bazaar spread (" + Fmt.coins(bz.instaSell()) + " / " + Fmt.coins(bz.instaBuy()) + ")");
			}
			if (item.isEnchantedBook()) {
				upgrades.clear();
				upgradesRaw = 0;
			}
		} else if (item.isEnchantedBook() && item.enchants.size() > 1) {
			// a multi-enchant book is worth its single books
			double sum = 0;
			int priced = 0;
			for (var e : item.enchants.entrySet()) {
				double p = m.componentPrice("ENCHANTMENT_" + e.getKey() + "_" + e.getValue());
				if (p > 0) {
					sum += p;
					priced++;
				}
			}
			base = sum > 0 ? sum : Double.NaN;
			source = "Sum of enchants";
			cands.add(new Valuation.Candidate("Sum of enchant books", sum, priced + "/" + item.enchants.size() + " enchants priced", true));
			if (priced < item.enchants.size()) {
				warnings.add((item.enchants.size() - priced) + " enchant(s) on this book have no price");
			}
			upgrades.clear();
			upgradesRaw = 0;
		} else {
			boolean upgraded = !item.isClean();
			double lbin = ah != null ? ah.lowest() : Double.NaN;
			double clean = ah != null ? ah.cleanLowest() : Double.NaN;
			double comp = lbin;
			String compLabel = "LBIN";
			if (cfg.useCleanLbin && upgraded) {
				if (!Double.isNaN(clean)) {
					comp = clean;
					compLabel = "Clean LBIN";
				} else if (upgradesRaw > 0 && !Double.isNaN(lbin)) {
					warnings.add("No clean copy listed: LBIN may already include upgrades");
				}
			}
			double ref = !Double.isNaN(cleanMedian) ? cleanMedian : median;
			String refLabel = !Double.isNaN(cleanMedian) ? "Clean sold median" : "Sold median";

			switch (cfg.valueMode) {
				case LBIN -> {
					base = comp;
					source = compLabel;
				}
				case MEDIAN -> {
					base = !Double.isNaN(ref) ? ref : comp;
					source = !Double.isNaN(ref) ? refLabel : compLabel;
				}
				case LOWEST -> {
					base = comp;
					source = compLabel;
					if (!Double.isNaN(ref) && (Double.isNaN(base) || ref < base)) {
						base = ref;
						source = refLabel;
					}
				}
				default -> {
					if (Double.isNaN(comp)) {
						base = ref;
						source = refLabel + " (none listed)";
					} else if (!Double.isNaN(ref) && comp > ref * INFLATED && volume >= 1) {
						base = ref;
						source = refLabel + " (LBIN inflated)";
					} else {
						base = comp;
						source = compLabel;
					}
				}
			}
			if (craftOk && (Double.isNaN(base) || (cfg.craftCap && cfg.valueMode != dev.lowball.helper.config.ValueMode.LBIN && craft.total() < base * 0.97))) {
				base = craft.total();
				source = Double.isNaN(base) ? "Craft cost" : "Craft cost (cheaper than buying)";
			}
			if (exotic != null && !Double.isNaN(exotic.estimate())) {
				base = exotic.estimate();
				source = "Exotic: " + exotic.estimateSource();
			}

			// what was considered, for the breakdown
			if (exotic != null) {
				cands.add(new Valuation.Candidate("Exotic estimate", exotic.estimate(), exotic.estimateSource(), source.startsWith("Exotic")));
			}
			cands.add(new Valuation.Candidate("Clean LBIN", clean, "cheapest without recomb/books/stars/gems", source.equals("Clean LBIN")));
			cands.add(new Valuation.Candidate("LBIN", lbin, ah != null ? ah.binCount() + " listed" : "none listed", source.equals("LBIN")));
			cands.add(new Valuation.Candidate("Clean sold median", cleanMedian,
					coflClean != null ? Fmt.volume(coflClean.perDay()) + " clean sales/24h" : "24h, Coflnet", source.startsWith("Clean sold median")));
			cands.add(new Valuation.Candidate("Sold median", median,
					cofl != null ? Fmt.volume(cofl.perDay()) + " sales/24h incl. upgraded" : "24h", source.startsWith("Sold median")));
			if (craft != null) {
				cands.add(new Valuation.Candidate("Craft cost", craft.total(),
						craft.loading() ? "loading recipe…" : craft.complete() ? craft.type() + ", " + craft.lines().size() + " ingredients" : "some ingredients unpriced",
						source.startsWith("Craft cost")));
			}
			if (!Double.isNaN(lbin) && !Double.isNaN(ref) && lbin > ref * INFLATED) {
				warnings.add("LBIN " + Fmt.percent(lbin / ref - 1) + " above " + refLabel.toLowerCase(java.util.Locale.ROOT));
			}
			if (ah != null && ah.cheapest().length > 1 && ah.second() > ah.lowest() * 1.25 && ah.binCount() > 1) {
				warnings.add("Gap to 2nd LBIN: " + Fmt.coins(ah.second()));
			}
		}

		if (Double.isNaN(base) || base <= 0) {
			if (info != null && info.npcSell() > 0) {
				base = info.npcSell();
				source = "NPC sell price";
				warnings.add("No market data: using NPC price");
			} else {
				base = Double.NaN;
				source = "No data";
			}
		}
		if (info != null && info.npcSell() > 0 && !isBazaar) {
			cands.add(new Valuation.Candidate("NPC sell", info.npcSell(), "floor", source.startsWith("NPC")));
		}
		if (exotic != null) {
			warnings.add(exotic.type().label + " #" + exotic.hex() + ": collector item, prices vary a lot. Double check!");
		}
		if (item.pet && item.petCandy > 0) {
			warnings.add("Candied pet (" + item.petCandy + " candy): sells for less");
		}
		if (!(volume >= 0)) {
			warnings.add("Sales volume unknown");
		} else if (volume < 1) {
			warnings.add("Very low volume: may take days to sell");
		}

		double credited = 0;
		for (Valuation.Credited c : upgrades) {
			credited += c.credited();
		}
		double unitValue = Double.isNaN(base) ? Double.NaN : base + credited;
		return new Valuation(item, isBazaar, base, source, List.copyOf(cands), ah, bz, cofl, coflClean, median, cleanMedian,
				volume, volumeSource, listed, List.copyOf(upgrades), craft, exotic, unitValue, List.copyOf(warnings));
	}

	private static Valuation.ExoticInfo exotic(SkyblockItem item, Exotic.Type type, int def, MarketView m, LowballConfig cfg) {
		CoflnetClient.History exact = null, sameType = null;
		if (cfg.useCoflnet) {
			exact = m.history(item.id, "Color=" + Exotic.coflnetColor(item.color));
			sameType = m.history(item.id, "ExoticColor=" + URLEncoder.encode(type.coflnet, StandardCharsets.UTF_8).replace("+", "%20"));
		}
		AuctionStats exactL = m.auction(AuctionScanner.exoticHexKey(item.id, item.color));
		AuctionStats typeL = m.auction(AuctionScanner.exoticTypeKey(item.id, type));
		double est;
		String src;
		if (exact != null && exact.any()) {
			est = exact.median();
			src = "this color sold " + exact.sales() + "× in 30d";
		} else if (sameType != null && sameType.any()) {
			est = sameType.median();
			src = type.label + " " + Fmt.coins(sameType.median()) + " median of " + sameType.sales() + " sales/30d";
		} else if (exactL != null) {
			est = exactL.lowest();
			src = "cheapest same-color listing";
		} else if (typeL != null) {
			est = typeL.lowest();
			src = "cheapest " + type.label + " listing";
		} else {
			est = Double.NaN;
			src = "no comparable sales";
		}
		return new Valuation.ExoticInfo(type, item.color, def, exact, sameType, exactL, typeL, est, src);
	}
}
