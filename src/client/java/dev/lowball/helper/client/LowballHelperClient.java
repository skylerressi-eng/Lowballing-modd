package dev.lowball.helper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.jspecify.annotations.Nullable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.client.feature.LowballCommand;
import dev.lowball.helper.client.feature.PriceTooltip;
import dev.lowball.helper.client.feature.SignAutofill;
import dev.lowball.helper.client.trade.TradeMenu;
import dev.lowball.helper.client.trade.LowballPanel;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.market.Market;

public final class LowballHelperClient implements ClientModInitializer {
	private static @Nullable Runnable pendingScreen;

	@Override
	public void onInitializeClient() {
		LowballConfig.get();
		Market.get().start(HypixelState::active);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			HypixelState.tick(mc);
			if (pendingScreen != null) {
				Runnable open = pendingScreen;
				pendingScreen = null;
				open.run();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Market.get().stop());

		KeyMapping inspect = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lowballhelper.inspect", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_V, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(LowballHelper.MOD_ID, "main"))));
		LowballPanel.setInspectKey(inspect);

		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			SignAutofill.onScreenInit(screen);
			if (screen instanceof AbstractContainerScreen<?> cs && HypixelState.active()) {
				LowballPanel.attach(cs, TradeMenu.partner(cs));
			}
		});

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> PriceTooltip.append(stack, lines));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> LowballCommand.register(dispatcher));
		LowballHelper.LOGGER.info("Lowball Helper ready");
	}

	/** Chat closes after a command runs, so screens opened from commands wait one tick. */
	public static void openNextTick(Runnable open) {
		pendingScreen = open;
	}
}
