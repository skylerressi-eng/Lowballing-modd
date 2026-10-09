package dev.lowball.helper.client.feature;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import dev.lowball.helper.client.Items;
import dev.lowball.helper.client.LowballHelperClient;
import dev.lowball.helper.client.ui.ConfigScreen;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.config.Preset;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.OfferCalculator;
import dev.lowball.helper.valuation.Upgrade;
import dev.lowball.helper.valuation.Valuation;
import dev.lowball.helper.valuation.Valuator;

public final class LowballCommand {
	private static final String PREFIX = "§6[Lowball] §r";

	private LowballCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(literal("lowball")
				.executes(ctx -> openConfig())
				.then(literal("config").executes(ctx -> openConfig()))
				.then(literal("help").executes(LowballCommand::help))
				.then(literal("check")
						.executes(ctx -> checkHeld(ctx.getSource()))
						.then(argument("item", StringArgumentType.greedyString())
								.suggests((ctx, b) -> {
									String typed = b.getRemaining().toLowerCase(Locale.ROOT);
									int n = 0;
									for (ItemRegistry.Info i : Market.get().items().all()) {
										if (i.name().toLowerCase(Locale.ROOT).startsWith(typed) && n++ < 40) {
											b.suggest(i.name());
										}
									}
									return b.buildFuture();
								})
								.executes(ctx -> checkNamed(ctx.getSource(), StringArgumentType.getString(ctx, "item")))))
				.then(literal("preset")
						.then(argument("name", StringArgumentType.word())
								.suggests((ctx, b) -> {
									for (Preset p : Preset.values()) {
										b.suggest(p.name().toLowerCase(Locale.ROOT));
									}
									return b.buildFuture();
								})
								.executes(ctx -> preset(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(literal("percent")
						.then(argument("value", IntegerArgumentType.integer(1, 100))
								.executes(ctx -> {
									LowballConfig cfg = LowballConfig.get();
									cfg.setBasePercent(IntegerArgumentType.getInteger(ctx, "value"));
									cfg.save();
									ctx.getSource().sendFeedback(Component.literal(PREFIX + "§7Offering §e" + Math.round(cfg.basePercent()) + "% §7(Custom)"));
									return 1;
								})))
				.then(literal("refresh").executes(ctx -> {
					Market.get().forceRefresh();
					ctx.getSource().sendFeedback(Component.literal(PREFIX + "§7Refreshing bazaar and auction house…"));
					return 1;
				}))
				.then(literal("status").executes(LowballCommand::status))
				.then(literal("demo").executes(ctx -> {
					LowballHelperClient.openNextTick(DemoTrade::open);
					return 1;
				})));
		dispatcher.register(literal("lbh").executes(ctx -> openConfig()).redirect(root));
	}

	private static int openConfig() {
		LowballHelperClient.openNextTick(() -> Minecraft.getInstance().setScreen(new ConfigScreen()));
		return 1;
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource s = ctx.getSource();
		s.sendFeedback(Component.literal(PREFIX + "§fCommands"));
		s.sendFeedback(Component.literal("§e/lowball §7- settings"));
		s.sendFeedback(Component.literal("§e/lowball check §7- price check the item in your hand"));
		s.sendFeedback(Component.literal("§e/lowball check <name> §7- price check any item"));
		s.sendFeedback(Component.literal("§e/lowball preset <snipe|aggressive|standard|fair|generous> §7- switch preset"));
		s.sendFeedback(Component.literal("§e/lowball percent <1-100> §7- custom offer percent"));
		s.sendFeedback(Component.literal("§e/lowball refresh §7- refresh prices now"));
		s.sendFeedback(Component.literal("§e/lowball status §7- data sources"));
		s.sendFeedback(Component.literal("§e/lowball demo §7- preview the trade panel with sample items"));
		return 1;
	}

	private static int preset(FabricClientCommandSource s, String name) {
		for (Preset p : Preset.values()) {
			if (p.name().equalsIgnoreCase(name) || p.label.equalsIgnoreCase(name)) {
				LowballConfig cfg = LowballConfig.get();
				cfg.preset = p;
				cfg.save();
				s.sendFeedback(Component.literal(PREFIX + "§7Preset §e" + p.label + " §7(" + Math.round(cfg.basePercent()) + "%): " + p.description));
				return 1;
			}
		}
		s.sendError(Component.literal("Unknown preset " + name));
		return 0;
	}

	private static int checkHeld(FabricClientCommandSource s) {
		ItemStack stack = s.getPlayer().getMainHandItem();
		SkyblockItem item = Items.of(stack);
		if (item == null) {
			s.sendError(Component.literal("Hold a SkyBlock item, or use /lowball check <item name>"));
			return 0;
		}
		report(s, item);
		return 1;
	}

	private static int checkNamed(FabricClientCommandSource s, String query) {
		ItemRegistry reg = Market.get().items();
		String id = reg.idForName(query);
		if (id == null) {
			String upper = query.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
			if (reg.get(upper) != null || Market.get().bazaar(upper) != null || Market.get().auction(upper) != null) {
				id = upper;
			}
		}
		if (id == null) {
			String q = query.toLowerCase(Locale.ROOT);
			ItemRegistry.Info best = null;
			for (ItemRegistry.Info i : reg.all()) {
				if (i.name().toLowerCase(Locale.ROOT).contains(q) && (best == null || i.name().length() < best.name().length())) {
					best = i;
				}
			}
			id = best == null ? null : best.id();
		}
		if (id == null) {
			s.sendError(Component.literal("No item matches \"" + query + "\""));
			return 0;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		ItemRegistry.Info info = reg.get(id);
		SkyblockItem item = SkyblockItem.of(tag, info != null ? info.name() : id, 1);
		if (item == null) {
			return 0;
		}
		report(s, item);
		return 1;
	}

	private static void report(FabricClientCommandSource s, SkyblockItem item) {
		Valuation v = Valuator.value(item, true);
		LowballConfig cfg = LowballConfig.get();
		s.sendFeedback(Component.literal(PREFIX + "§f" + item.name + (item.count > 1 ? " §8×" + item.count : "") + " §8(" + item.key + ")"));
		if (!v.known()) {
			s.sendFeedback(Component.literal(" §cNo price data yet." + (Market.get().hasAnyData() ? "" : " Data is still loading.")));
			return;
		}
		Offer o = OfferCalculator.offer(v, cfg);
		s.sendFeedback(Component.literal(" §7Value §f" + Fmt.coins(v.totalValue()) + " §8(" + v.baseSource()
				+ (v.upgradesRaw() > 0 ? ", +" + Fmt.coins(v.upgradesCredited()) + " upgrades" : "") + ")"));
		s.sendFeedback(Component.literal(" §7Offer §a" + Fmt.coins(o.totalOffer()) + " §7(" + Fmt.percent(o.percent()) + ") §8→ §7profit after tax "
				+ (o.profit() >= 0 ? "§a+" : "§c") + Fmt.coins(o.profit())));
		AuctionStats ah = v.auction();
		if (ah != null) {
			s.sendFeedback(Component.literal(" §7LBIN §f" + Fmt.coins(ah.lowest()) + (ah.cheapest().length > 1 ? " §8· §72nd §f" + Fmt.coins(ah.second()) : "")
					+ (!Double.isNaN(ah.cleanLowest()) ? " §8· §7clean §f" + Fmt.coins(ah.cleanLowest()) : "") + " §8· §e" + ah.binCount() + " §7listed"));
		} else if (v.bazaarProduct() != null) {
			s.sendFeedback(Component.literal(" §7Bazaar insta-sell §f" + Fmt.coins(v.bazaarProduct().instaSell()) + " §8· §7insta-buy §f" + Fmt.coins(v.bazaarProduct().instaBuy())));
		}
		s.sendFeedback(Component.literal(" §7Volume §f" + (Double.isNaN(v.dailyVolume()) ? "?" : Fmt.volume(v.dailyVolume()) + "/day") + " §8(" + v.volumeSource() + ")"
				+ (Double.isNaN(v.median()) ? "" : " §8· §7median §f" + Fmt.coins(v.median()))));
		if (!v.upgrades().isEmpty()) {
			StringBuilder sb = new StringBuilder(" §7Upgrades: ");
			int n = 0;
			for (Upgrade u : v.upgrades()) {
				if (n++ > 0) {
					sb.append("§8, ");
				}
				sb.append("§7").append(u.label()).append(" §f").append(Fmt.coins(u.total()));
			}
			s.sendFeedback(Component.literal(sb.toString()));
		}
		for (String w : v.warnings()) {
			s.sendFeedback(Component.literal(" §c⚠ " + w));
		}
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		Market m = Market.get();
		FabricClientCommandSource s = ctx.getSource();
		s.sendFeedback(Component.literal(PREFIX + "§fData status"));
		s.sendFeedback(Component.literal(" §7Auction house: " + (m.auctionsFetchedAt() > 0
				? "§f" + Fmt.full(m.auctionTotal()) + " §7listings, updated §f" + Fmt.ago(m.auctionsFetchedAt())
				: "§enot scanned yet") + (m.scanning() ? " §e(scanning)" : "")));
		s.sendFeedback(Component.literal(" §7Bazaar: " + (m.bazaarFetchedAt() > 0 ? "§fupdated " + Fmt.ago(m.bazaarFetchedAt()) : "§enot loaded")));
		s.sendFeedback(Component.literal(" §7Item data: §f" + m.items().size() + " §7items"));
		if (m.lastError() != null) {
			s.sendFeedback(Component.literal(" §cLast error: " + m.lastError()));
		}
		return 1;
	}
}
