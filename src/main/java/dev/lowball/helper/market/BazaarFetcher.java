package dev.lowball.helper.market;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.stream.JsonReader;

import dev.lowball.helper.util.Http;

public final class BazaarFetcher {
	static final String URL = "https://api.hypixel.net/v2/skyblock/bazaar";

	private BazaarFetcher() {
	}

	public static Map<String, BazaarProduct> fetch() throws IOException {
		try (Reader r = Http.reader(URL)) {
			return parse(r);
		}
	}

	static Map<String, BazaarProduct> parse(Reader r) throws IOException {
		Map<String, BazaarProduct> out = new HashMap<>(4096);
		try (JsonReader json = new JsonReader(r)) {
			json.beginObject();
			while (json.hasNext()) {
				String name = json.nextName();
				if (!name.equals("products")) {
					json.skipValue();
					continue;
				}
				json.beginObject();
				while (json.hasNext()) {
					String id = json.nextName();
					BazaarProduct p = readProduct(id, json);
					if (p != null) {
						out.put(id, p);
					}
				}
				json.endObject();
			}
			json.endObject();
		}
		return out;
	}

	private static BazaarProduct readProduct(String id, JsonReader json) throws IOException {
		BazaarProduct result = null;
		json.beginObject();
		while (json.hasNext()) {
			if (!json.nextName().equals("quick_status")) {
				json.skipValue();
				continue;
			}
			double sell = 0, buy = 0;
			long sellWeek = 0, buyWeek = 0;
			int sellOrders = 0, buyOrders = 0;
			json.beginObject();
			while (json.hasNext()) {
				switch (json.nextName()) {
					// Hypixel naming: "sell" = price you get selling instantly into buy orders
					case "sellPrice" -> sell = json.nextDouble();
					case "buyPrice" -> buy = json.nextDouble();
					case "sellMovingWeek" -> sellWeek = json.nextLong();
					case "buyMovingWeek" -> buyWeek = json.nextLong();
					// ...and "sellOrders" counts those buy orders, "buyOrders" counts sell offers
					case "sellOrders" -> buyOrders = json.nextInt();
					case "buyOrders" -> sellOrders = json.nextInt();
					default -> json.skipValue();
				}
			}
			json.endObject();
			result = new BazaarProduct(id, sell, buy, sellWeek, buyWeek, sellOrders, buyOrders);
		}
		json.endObject();
		return result;
	}
}
