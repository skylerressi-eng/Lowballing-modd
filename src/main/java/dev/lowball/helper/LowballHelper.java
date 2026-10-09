package dev.lowball.helper;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LowballHelper {
	public static final String MOD_ID = "lowballhelper";
	public static final String NAME = "Lowball Helper";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	private static Path dataDir;

	private LowballHelper() {
	}

	public static Path dataDir() {
		if (dataDir == null) {
			dataDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		}
		return dataDir;
	}

	/** Used by tests so nothing is written into a real config folder. */
	public static void setDataDir(Path dir) {
		dataDir = dir;
	}
}
