package dev.lowball.helper.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Decodes real auction house item bytes captured from the Hypixel API. */
class ItemBytesTest {
	@Test
	void decodesRealAuctions() throws Exception {
		JsonObject root;
		try (var r = new InputStreamReader(getClass().getResourceAsStream("/auctions-sample.json"), StandardCharsets.UTF_8)) {
			root = JsonParser.parseReader(r).getAsJsonObject();
		}
		int pets = 0, starred = 0, dirty = 0;
		for (JsonElement el : root.getAsJsonArray("auctions")) {
			JsonObject a = el.getAsJsonObject();
			String name = a.get("item_name").getAsString();
			SkyblockItem item = ItemBytes.decodeFirst(a.get("item_bytes").getAsString(), name);
			assertNotNull(item, name);
			assertFalse(item.id.isEmpty(), name);
			if (name.startsWith("[Lvl")) {
				assertTrue(item.pet, name + " should be a pet");
				assertTrue(item.key.matches("[A-Z_]+;[0-5]@(LOW|100|200)"), name + " -> " + item.key);
				pets++;
			}
			if (name.contains("✪")) {
				assertTrue(item.stars > 0, name + " should have stars");
				starred++;
			}
			if (!item.isClean()) {
				dirty++;
			}
		}
		assertTrue(pets >= 3, "pets decoded");
		assertTrue(starred >= 3, "starred decoded");
		assertTrue(dirty >= 3, "upgraded items detected");
	}
}
