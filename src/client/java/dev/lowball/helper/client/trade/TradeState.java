package dev.lowball.helper.client.trade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.lowball.helper.client.Items;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.OfferCalculator;
import dev.lowball.helper.valuation.Valuation;
import dev.lowball.helper.valuation.Valuator;

/** Both sides of a trade, valued, plus totals. Rebuilt only when the slots, data or settings change. */
public final class TradeState {
	public final List<TradeEntry> theirs = new ArrayList<>();
	public final List<TradeEntry> yours = new ArrayList<>();

	public double theirItemsValue;
	public double theirCoins;
	public double yourItemsValue;
	public double yourCoins;
	public double suggestedOffer;
	public double resaleNet;
	public int unknownItems;

	public static TradeState build(AbstractContainerMenu menu) {
		TradeState s = new TradeState();
		LowballConfig cfg = LowballConfig.get();
		for (Slot slot : menu.slots) {
			int idx = slot.index;
			boolean mine = TradeMenu.isYours(idx);
			if (!mine && !TradeMenu.isTheirs(idx)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			double coins = Items.coins(stack);
			TradeEntry entry;
			if (!Double.isNaN(coins)) {
				entry = new TradeEntry(slot, stack, coins, null, Offer.NONE);
				if (mine) {
					s.yourCoins += coins;
				} else {
					s.theirCoins += coins;
				}
			} else {
				SkyblockItem item = Items.of(stack);
				Valuation v = item == null ? null : Valuator.value(item, true);
				Offer offer = v == null ? Offer.NONE : OfferCalculator.offer(v, cfg);
				entry = new TradeEntry(slot, stack, Double.NaN, v, offer);
				if (v == null || !v.known()) {
					if (!mine) {
						s.unknownItems++;
					}
				} else if (mine) {
					s.yourItemsValue += v.totalValue();
				} else {
					s.theirItemsValue += v.totalValue();
					s.suggestedOffer += offer.totalOffer();
					s.resaleNet += offer.resaleNet();
				}
			}
			(mine ? s.yours : s.theirs).add(entry);
		}
		return s;
	}

	/** Average percent of value the suggested offer represents. */
	public double suggestedPercent() {
		return theirItemsValue > 0 ? suggestedOffer / theirItemsValue : 0;
	}

	/** Percent of value you're currently paying (coins + items you put in). */
	public double currentPercent() {
		return theirItemsValue > 0 ? (yourCoins + yourItemsValue) / theirItemsValue : 0;
	}

	/** Profit if you resell their items at value with the price currently in the trade. */
	public double currentProfit() {
		return resaleNet + theirCoins - yourCoins - yourItemsValue;
	}

	public int signature() {
		return theirs.size() * 31 + yours.size();
	}
}
