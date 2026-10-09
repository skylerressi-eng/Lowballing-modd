package dev.lowball.helper.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.util.Fmt;

/** Full settings screen (/lowball). Click cycles forward, shift-click cycles back. */
public final class ConfigScreen extends Screen {
	private final @Nullable Screen parent;
	private final List<Button> optionButtons = new ArrayList<>();
	private int page;
	private int firstOption;
	private @Nullable String pageLabel;
	private int pageLabelY;

	public ConfigScreen() {
		this(null);
	}

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.literal("Lowball Helper"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		optionButtons.clear();
		List<Options.Option> all = Options.screenOptions();
		int colW = 150;
		int gap = 6;
		int rowH = 22;
		int columns = Math.max(1, Math.min(3, (width - 20 + gap) / (colW + gap)));
		int top = 36;
		int footer = 52;
		int rowsFit = Math.max(1, (height - top - footer) / rowH);
		int perPage = rowsFit * columns;
		int pages = (all.size() + perPage - 1) / perPage;
		page = Math.min(page, pages - 1);
		int from = page * perPage;
		int count = Math.min(perPage, all.size() - from);
		int rows = Math.min(rowsFit, (count + columns - 1) / columns);
		int totalW = columns * colW + (columns - 1) * gap;
		int left = (width - totalW) / 2;
		for (int k = 0; k < count; k++) {
			Options.Option o = all.get(from + k);
			int col = k / rows;
			int row = k % rows;
			Button b = Button.builder(label(o), btn -> {
						o.click(minecraft.hasShiftDown());
						refreshLabels();
					})
					.bounds(left + col * (colW + gap), top + row * rowH, colW, 20)
					.tooltip(Tooltip.create(Component.literal(o.description() + "\n§8Shift-click to go back.")))
					.build();
			optionButtons.add(addRenderableWidget(b));
		}
		int by = top + rows * rowH + 6;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
				page = (page + pages - 1) % pages;
				rebuildWidgets();
			}).bounds(width / 2 - 154, by, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
				page = (page + 1) % pages;
				rebuildWidgets();
			}).bounds(width / 2 + 134, by, 20, 20).build());
			pageLabel = "Page " + (page + 1) + "/" + pages;
		} else {
			pageLabel = null;
		}
		addRenderableWidget(Button.builder(Component.literal("Reset defaults"), btn -> {
			LowballConfig.get().resetToDefaults();
			refreshLabels();
		}).bounds(width / 2 - 130, by, 85, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Refresh prices"), btn -> Market.get().forceRefresh())
				.bounds(width / 2 + 45, by, 85, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose()).bounds(width / 2 - 100, by + 24, 200, 20).build());
		pageLabelY = by + 6;
		firstOption = from;
	}

	private static Component label(Options.Option o) {
		return Component.literal(o.label() + ": " + o.value().get());
	}

	private void refreshLabels() {
		for (int i = 0; i < optionButtons.size(); i++) {
			optionButtons.get(i).setMessage(label(Options.screenOptions().get(firstOption + i)));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, "§6§lLowball Helper", width / 2, 12, 0xFFFFFFFF);
		Market m = Market.get();
		String status = (m.auctionsFetchedAt() > 0 ? "AH " + Fmt.full(m.auctionTotal()) + " listings, " + Fmt.ago(m.auctionsFetchedAt()) : "AH not scanned yet")
				+ "  ·  " + (m.bazaarFetchedAt() > 0 ? "Bazaar " + Fmt.ago(m.bazaarFetchedAt()) : "Bazaar not loaded");
		graphics.centeredText(font, "§8" + status, width / 2, 24, 0xFFFFFFFF);
		if (pageLabel != null) {
			graphics.centeredText(font, "§7" + pageLabel, width / 2, pageLabelY, 0xFFFFFFFF);
		}
	}

	@Override
	public void onClose() {
		LowballConfig.get().save();
		dev.lowball.helper.client.Compat.setScreen(parent);
	}
}
