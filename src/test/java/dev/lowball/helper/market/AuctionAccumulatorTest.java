package dev.lowball.helper.market;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuctionAccumulatorTest {
	@Test
	void keepsFiveCheapestAndCounts() {
		AuctionScanner.Acc a = new AuctionScanner.Acc("X", "X");
		for (double p : new double[]{90, 50, 70, 10, 80, 60, 30}) {
			a.addBin(p, p >= 60);
		}
		AuctionStats s = a.build();
		assertArrayEquals(new double[]{10, 30, 50, 60, 70}, s.cheapest());
		assertEquals(10, s.lowest());
		assertEquals(60, s.cleanLowest());
		assertEquals(7, s.binCount());
	}

	@Test
	void mergeKeepsTotals() {
		AuctionScanner.Acc a = new AuctionScanner.Acc("X", "X");
		AuctionScanner.Acc b = new AuctionScanner.Acc("X", "X");
		for (int i = 0; i < 8; i++) {
			a.addBin(100 + i, false);
			b.addBin(50 + i, i == 3);
		}
		b.bids = 4;
		a.merge(b);
		AuctionStats s = a.build();
		assertEquals(16, s.binCount());
		assertEquals(4, s.auctionCount());
		assertArrayEquals(new double[]{50, 51, 52, 53, 54}, s.cheapest());
		assertEquals(53, s.cleanLowest());
	}

	@Test
	void petBaseKeysCombineLevels() {
		Map<String, AuctionStats> m = new HashMap<>();
		m.put("BAL;4@100", new AuctionStats("BAL;4@100", "Bal", 30, 30, new double[]{30, 31}, 2, 0));
		m.put("BAL;4@LOW", new AuctionStats("BAL;4@LOW", "Bal", 5, 5, new double[]{5}, 1, 1));
		AuctionScanner.addPetBaseKeys(m);
		AuctionStats base = m.get("BAL;4");
		assertEquals(5, base.lowest());
		assertEquals(3, base.binCount());
		assertEquals(1, base.auctionCount());
	}
}
