package dev.lowball.helper.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.lowball.helper.client.Items;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.OfferCalculator;
import dev.lowball.helper.valuation.Valuation;
import dev.lowball.helper.valuation.Valuator;

/** Full-screen breakdown of one item (/lowball value). */
public final class ValueScreen extends Screen {
	private final ItemStack stack;
	private Breakdown breakdown;
	private Valuation valuation;
	private Offer offer;
	private int version = -1;
	private int revision = -1;
	private int scroll;
	private int boxX, boxY, boxW, boxH;

	public ValueScreen(ItemStack stack) {
		super(Component.literal("Item value"));
		this.stack = stack.copy();
	}

	@Override
	protected void init() {
		boxW = Math.min(300, width - 20);
		boxX = (width - boxW) / 2;
		boxY = 24;
		boxH = height - boxY - 32;
		addRenderableWidget(Button.builder(Component.literal("Copy offer"), b -> {
			if (offer != null) {
				minecraft.keyboardHandler.setClipboard(Fmt.signAmount(Math.floor(offer.totalOffer())));
			}
		}).bounds(width / 2 - 154, height - 26, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Copy worth"), b -> {
			if (valuation != null) {
				minecraft.keyboardHandler.setClipboard(Fmt.signAmount(Math.floor(valuation.totalValue())));
			}
		}).bounds(width / 2 - 50, height - 26, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 54, height - 26, 100, 20).build());
	}

	private void refresh() {
		int mv = Market.get().version();
		int cr = LowballConfig.revision();
		if (breakdown == null || mv != version || cr != revision) {
			SkyblockItem item = Items.of(stack);
			if (item == null) {
				return;
			}
			valuation = Valuator.value(item, true);
			offer = OfferCalculator.offer(valuation, LowballConfig.get());
			breakdown = Breakdown.of(stack, valuation, offer, true);
			version = mv;
			revision = cr;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		refresh();
		g.centeredText(font, "§6§lLowball Helper §r§7· value breakdown", width / 2, 8, Draw.WHITE);
		Draw.panel(g, boxX - 6, boxY - 4, boxW + 12, boxH + 8);
		if (breakdown == null) {
			g.centeredText(font, "§cNot a SkyBlock item", width / 2, boxY + 20, Draw.WHITE);
			return;
		}
		int content = breakdown.height(font, boxW - 6);
		int max = Math.max(0, content - boxH);
		scroll = Math.max(0, Math.min(scroll, max));
		g.enableScissor(boxX - 4, boxY, boxX + boxW + 4, boxY + boxH);
		breakdown.render(g, font, boxX, boxY - scroll, boxW - 6, boxY, boxY + boxH);
		g.disableScissor();
		if (max > 0) {
			int thumb = Math.max(12, boxH * boxH / (boxH + max));
			int ty = boxY + (boxH - thumb) * scroll / max;
			g.fill(boxX + boxW + 1, boxY, boxX + boxW + 3, boxY + boxH, 0x20FFFFFF);
			g.fill(boxX + boxW + 1, ty, boxX + boxW + 3, ty + thumb, 0xA0FFFFFF);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll -= (int) (scrollY * 20);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isDown()) {
			scroll += 20;
			return true;
		}
		if (event.isUp()) {
			scroll -= 20;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
