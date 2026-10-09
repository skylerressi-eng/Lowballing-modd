package dev.lowball.helper.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import dev.lowball.helper.config.LowballConfig;

/** Tracks whether we're connected to Hypixel; read from background threads. */
public final class HypixelState {
	private static volatile boolean onHypixel;
	private static volatile boolean demo;

	private HypixelState() {
	}

	public static void tick(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		onHypixel = mc.player != null && server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel");
	}

	public static boolean active() {
		return onHypixel || demo || LowballConfig.get().enableEverywhere;
	}

	/** The demo trade works anywhere, so it switches data fetching on for the session. */
	public static void enableDemo() {
		demo = true;
	}
}
