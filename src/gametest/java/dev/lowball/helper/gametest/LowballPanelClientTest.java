package dev.lowball.helper.gametest;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.lowball.helper.client.feature.DemoTrade;
import dev.lowball.helper.client.trade.TradeMenu;
import dev.lowball.helper.client.trade.LowballPanel;
import dev.lowball.helper.market.Market;

/**
 * Opens the demo trade in a real client, waits for live prices and screenshots the panel.
 * Run: xvfb-run ./gradlew runClientGameTest (screenshots land in build/run/clientGameTest/screenshots).
 */
public class LowballPanelClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(40);
			context.getInput().resizeWindow(1920, 1080);
			context.runOnClient(mc -> DemoTrade.open());
			context.waitForScreen(ContainerScreen.class);
			if (!context.computeOnClient(mc -> TradeMenu.is(dev.lowball.helper.client.Compat.screen()))) {
				throw new AssertionError("Demo trade not detected as a trade menu");
			}
			// wait for bazaar + auction scan + item registry (live network), then Coflnet lookups
			context.waitFor(mc -> Market.get().auctionsFetchedAt() > 0 && Market.get().bazaarFetchedAt() > 0 && Market.get().items().size() > 0, 20 * 120);
			context.waitTicks(80);
			context.getInput().setCursorPos(5, 5);
			context.waitTicks(2);
			context.takeScreenshot("panel-1080p");

			click(context, "row0");
			context.getInput().setCursorPos(5, 5);
			context.waitTicks(40);
			context.takeScreenshot("breakdown-hyperion-1");
			context.runOnClient(mc -> LowballPanel.scrollBy((ContainerScreen) dev.lowball.helper.client.Compat.screen(), 160));
			context.waitTicks(2);
			context.takeScreenshot("breakdown-hyperion-2");
			context.runOnClient(mc -> LowballPanel.scrollBy((ContainerScreen) dev.lowball.helper.client.Compat.screen(), 200));
			context.waitTicks(2);
			context.takeScreenshot("breakdown-hyperion-3");
			click(context, "back");

			// the exotic chestplate (slot 23) via the inspect path
			context.runOnClient(mc -> LowballPanel.inspect((ContainerScreen) dev.lowball.helper.client.Compat.screen(), ((ContainerScreen) dev.lowball.helper.client.Compat.screen()).getMenu().slots.get(23).getItem()));
			context.waitTicks(60);
			context.getInput().setCursorPos(5, 5);
			context.waitTicks(2);
			context.takeScreenshot("breakdown-exotic");
			click(context, "back");

			// terminator: crafting cost section
			context.runOnClient(mc -> LowballPanel.inspect((ContainerScreen) dev.lowball.helper.client.Compat.screen(), ((ContainerScreen) dev.lowball.helper.client.Compat.screen()).getMenu().slots.get(7).getItem()));
			context.waitTicks(60);
			context.runOnClient(mc -> LowballPanel.scrollBy((ContainerScreen) dev.lowball.helper.client.Compat.screen(), 150));
			context.waitTicks(2);
			context.takeScreenshot("breakdown-terminator-craft");
			click(context, "back");
			context.getInput().setCursorPos(5, 5);

			click(context, "settings");
			context.getInput().setCursorPos(5, 5);
			context.waitTicks(3);
			context.takeScreenshot("panel-1080p-settings");
			click(context, "settings");

			context.getInput().resizeWindow(854, 480);
			context.waitTicks(5);
			context.getInput().setCursorPos(5, 5);
			context.waitTicks(2);
			context.takeScreenshot("panel-small-window");
			context.takeScreenshot("panel-small-window-2");

			click(context, "collapse");
			context.waitTicks(2);
			context.takeScreenshot("panel-collapsed");
			click(context, "expand");
			context.waitTicks(2);

			// vanilla item tooltip gets the price lines too (hover the Hyperion slot)
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(5);
			hover(context, s -> {
				var slot = s.getMenu().slots.get(5);
				return new int[]{dev.lowball.helper.client.ScreenAccess.left(s) + slot.x + 8, dev.lowball.helper.client.ScreenAccess.top(s) + slot.y + 8};
			});
			context.takeScreenshot("item-tooltip");

			// autofill: arm the offer, then open a Hypixel-style coin sign and check the amount was typed in
			long offer = context.computeOnClient(mc -> {
				var st = dev.lowball.helper.client.trade.TradeState.build(((ContainerScreen) dev.lowball.helper.client.Compat.screen()).getMenu());
				return (long) Math.floor(st.suggestedOffer);
			});
			click(context, "autofill");
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(null));
			BlockPos pos = context.computeOnClient(mc -> mc.player.blockPosition().above(2));
			world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
					+ " minecraft:oak_sign{front_text:{messages:['','^^^^^^^^^^^^^^^','Enter amount','of coins']}}");
			context.waitTicks(10);
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(signScreen((SignBlockEntity) mc.level.getBlockEntity(pos))));
			context.waitForScreen(SignEditScreen.class);
			String typed = context.computeOnClient(mc -> dev.lowball.helper.client.ScreenAccess.signLines((net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen) dev.lowball.helper.client.Compat.screen())[0]);
			context.takeScreenshot("sign-autofill");
			if (!typed.equals(Long.toString(offer))) {
				throw new AssertionError("Sign autofill typed '" + typed + "', expected " + offer);
			}
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(null));

			// /lowball value screen
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(new dev.lowball.helper.client.ui.ValueScreen(DemoTrade.hyperion())));
			context.waitTicks(40);
			context.takeScreenshot("value-screen");
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(null));

			// settings screen
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(new dev.lowball.helper.client.ui.ConfigScreen()));
			context.waitTicks(3);
			context.takeScreenshot("config-screen");
			context.runOnClient(mc -> dev.lowball.helper.client.Compat.setScreen(null));
		}
	}

	/** SignEditScreen's constructor changed in 26.3 (boolean front → SignTextSlot). */
	private static SignEditScreen signScreen(SignBlockEntity sign) {
		for (var c : SignEditScreen.class.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length == 3 && p[0] == SignBlockEntity.class && p[2] == boolean.class) {
				Object side = p[1] == boolean.class ? Boolean.TRUE : p[1].isEnum() ? p[1].getEnumConstants()[0] : null;
				try {
					return (SignEditScreen) c.newInstance(sign, side, false);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			}
		}
		throw new AssertionError("no SignEditScreen constructor");
	}

	private interface Locator {
		int[] find(ContainerScreen screen);
	}

	private static void hover(ClientGameTestContext context, Locator locator) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		int[] p = context.computeOnClient(mc -> locator.find((ContainerScreen) dev.lowball.helper.client.Compat.screen()));
		if (p == null) {
			throw new AssertionError("control not found");
		}
		context.getInput().setCursorPos(p[0] * scale + scale / 2, p[1] * scale + scale / 2);
		context.waitTicks(3);
	}

	private static void click(ClientGameTestContext context, String id) {
		hover(context, s -> LowballPanel.controlCenter(s, id));
		context.getInput().pressMouse(0);
		context.waitTicks(2);
	}
}
