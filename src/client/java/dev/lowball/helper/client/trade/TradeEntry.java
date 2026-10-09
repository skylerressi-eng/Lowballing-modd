package dev.lowball.helper.client.trade;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.Valuation;

/** One non-empty slot of the trade, valued. Exactly one of {@code coins}/{@code valuation} is meaningful. */
public record TradeEntry(Slot slot, ItemStack stack, double coins, @Nullable Valuation valuation, Offer offer) {
	public boolean isCoins() {
		return !Double.isNaN(coins);
	}

	public double value() {
		if (isCoins()) {
			return coins;
		}
		return valuation != null ? valuation.totalValue() : 0;
	}
}
