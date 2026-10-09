package dev.lowball.helper.client.feature;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.lowball.helper.client.HypixelState;
import dev.lowball.helper.client.Items;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.OfferCalculator;
import dev.lowball.helper.valuation.Valuation;
import dev.lowball.helper.valuation.Valuator;

/** Adds price, volume, listings and the lowball offer to every SkyBlock item tooltip. */
public final class PriceTooltip {
	private record Cached(int marketVersion, int configRevision, List<Component> lines) {
	}

	private static final Map<ItemStack, Cached> CACHE = new WeakHashMap<>();

	private PriceTooltip() {
	}

	public static void append(ItemStack stack, List<Component> lines) {
		LowballConfig cfg = LowballConfig.get();
		if (!cfg.tooltipEnabled || !HypixelState.active()) {
			return;
		}
		if (cfg.tooltipRequireShift && !Minecraft.getInstance().hasShiftDown()) {
			return;
		}
		int mv = Market.get().version();
		int cr = LowballConfig.revision();
		Cached c = CACHE.get(stack);
		if (c == null || c.marketVersion != mv || c.configRevision != cr) {
			c = new Cached(mv, cr, build(stack, cfg));
			CACHE.put(stack, c);
		}
		lines.addAll(c.lines);
	}

	private static List<Component> build(ItemStack stack, LowballConfig cfg) {
		if (!Double.isNaN(Items.coins(stack))) {
			return List.of();
		}
		SkyblockItem item = Items.of(stack);
		if (item == null) {
			return List.of();
		}
		Valuation v = Valuator.value(item, true);
		if (!v.known()) {
			return List.of();
		}
		Offer o = OfferCalculator.offer(v, cfg);
		String vol = Double.isNaN(v.dailyVolume()) ? "§8?/day" : "§a" + Fmt.volume(v.dailyVolume()) + "/day";
		String first;
		if (v.bazaar()) {
			first = "§6Lowball §8» §7Bazaar §f" + Fmt.coins(v.unitBase()) + " §8· " + vol;
		} else {
			AuctionStats ah = v.auction();
			String price = ah != null && !Double.isNaN(ah.lowest()) ? "§7LBIN §f" + Fmt.coins(ah.lowest()) : "§7Med §f" + Fmt.coins(v.unitBase());
			String listed = v.listed() >= 0 ? " §8· §e" + v.listed() + " §7on AH" : "";
			first = "§6Lowball §8» " + price + " §8· " + vol + listed;
		}
		String second = "§7Worth §f" + Fmt.coins(v.totalValue()) + (v.upgradesRaw() > 0 ? " §8(+" + Fmt.coins(v.upgradesRaw()) + " upgrades)" : "")
				+ " §8→ §aOffer " + Fmt.coins(o.totalOffer()) + " §7(" + Fmt.percent(o.percent()) + ")";
		return List.of(Component.literal(first), Component.literal(second));
	}
}
