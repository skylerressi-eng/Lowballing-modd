package dev.lowball.helper.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BazaarFetcherTest {
	@Test
	void parsesQuickStatus() throws Exception {
		String json = """
				{"success":true,"lastUpdated":1,"products":{"RECOMBOBULATOR_3000":{"product_id":"RECOMBOBULATOR_3000",
				"sell_summary":[],"buy_summary":[],"quick_status":{"productId":"RECOMBOBULATOR_3000","sellPrice":6000000.5,
				"sellVolume":10,"sellMovingWeek":700,"sellOrders":12,"buyPrice":6200000,"buyVolume":40,"buyMovingWeek":1400,"buyOrders":30}}}}""";
		Map<String, BazaarProduct> m = BazaarFetcher.parse(new StringReader(json));
		BazaarProduct p = m.get("RECOMBOBULATOR_3000");
		assertEquals(6000000.5, p.instaSell());
		assertEquals(6200000, p.instaBuy());
		assertEquals(300, p.dailyVolume());
		assertEquals(30, p.sellOffers());
		assertEquals(12, p.buyOrders());
	}

	@Test
	void ignoresTrollBuyOrders() {
		BazaarProduct p = new BazaarProduct("ENCHANTMENT_ULTIMATE_WISE_5", 0.9, 786_892, 1786, 8120, 323, 11);
		assertEquals(786_892 * 0.4, p.safeInstaSell(), 1e-6);
	}
}
