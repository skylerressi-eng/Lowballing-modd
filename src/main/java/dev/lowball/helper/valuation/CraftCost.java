package dev.lowball.helper.valuation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;

import dev.lowball.helper.market.NeuRepo;

/**
 * What it costs to make an item from its recipe at today's prices. Ingredients without a market price
 * are themselves priced from their recipe (up to {@link #MAX_DEPTH} levels deep).
 *
 * @param lines    one per ingredient (per output item)
 * @param coins    coins the recipe costs (forge / NPC shop)
 * @param complete false when an ingredient has no price at all
 * @param loading  true while recipes are still being downloaded
 */
public record CraftCost(String type, List<Line> lines, double coins, double total, boolean complete, boolean loading) {
	static final int MAX_DEPTH = 3;

	/** @param source "BZ", "AH", "craft" or "?" */
	public record Line(String id, String name, double quantity, double unitPrice, String source) {
		public double total() {
			return Double.isNaN(unitPrice) ? 0 : quantity * unitPrice;
		}
	}

	public interface Prices {
		double price(String id);

		@Nullable String source(String id);

		String name(String id);

		/** null while loading, empty when the item has no recipe */
		@Nullable Optional<NeuRepo.Recipe> recipe(String id);
	}

	/** Null when the item has no recipe (or it's still loading and nothing is known yet). */
	public static @Nullable CraftCost of(String id, Prices prices) {
		Optional<NeuRepo.Recipe> r = prices.recipe(id);
		if (r == null) {
			return new CraftCost("?", List.of(), 0, Double.NaN, false, true);
		}
		if (r.isEmpty()) {
			return null;
		}
		Set<String> visiting = new HashSet<>();
		visiting.add(id);
		return build(r.get(), prices, 1, visiting);
	}

	private static CraftCost build(NeuRepo.Recipe recipe, Prices prices, int depth, Set<String> visiting) {
		List<Line> lines = new ArrayList<>();
		boolean complete = true;
		boolean loading = false;
		double total = recipe.coins() / recipe.output();
		for (NeuRepo.Ingredient in : recipe.inputs()) {
			double qty = in.count() / recipe.output();
			double unit = prices.price(in.id());
			String source = prices.source(in.id());
			if (Double.isNaN(unit) && depth < MAX_DEPTH && visiting.add(in.id())) {
				Optional<NeuRepo.Recipe> sub = prices.recipe(in.id());
				if (sub == null) {
					loading = true;
				} else if (sub.isPresent()) {
					CraftCost inner = build(sub.get(), prices, depth + 1, visiting);
					if (inner.complete) {
						unit = inner.total;
						source = "craft";
					}
					loading |= inner.loading;
				}
				visiting.remove(in.id());
			}
			if (Double.isNaN(unit)) {
				complete = false;
				source = "?";
			} else {
				total += qty * unit;
			}
			lines.add(new Line(in.id(), prices.name(in.id()), qty, unit, source == null ? "?" : source));
		}
		lines.sort((a, b) -> Double.compare(b.total(), a.total()));
		return new CraftCost(recipe.type(), List.copyOf(lines), recipe.coins() / recipe.output(), total, complete, loading);
	}

	/** Adapter over plain functions, for tests. */
	public static Prices prices(ToDoubleFunction<String> price, Function<String, @Nullable Optional<NeuRepo.Recipe>> recipe) {
		return new Prices() {
			public double price(String id) {
				return price.applyAsDouble(id);
			}

			public @Nullable String source(String id) {
				return Double.isNaN(price.applyAsDouble(id)) ? null : "BZ";
			}

			public String name(String id) {
				return id;
			}

			public @Nullable Optional<NeuRepo.Recipe> recipe(String id) {
				return recipe.apply(id);
			}
		};
	}
}
