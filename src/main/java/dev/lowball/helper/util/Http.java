package dev.lowball.helper.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public final class Http {
	private static final String USER_AGENT = "LowballHelper/1.0 (Hypixel SkyBlock mod)";
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private Http() {
	}

	public static final class StatusException extends IOException {
		public final int status;

		public StatusException(String url, int status) {
			super("HTTP " + status + " from " + url);
			this.status = status;
		}
	}

	/** Opens a (possibly gzip-encoded) response body. Caller closes the stream. */
	public static InputStream open(String url) throws IOException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", USER_AGENT)
				.header("Accept-Encoding", "gzip")
				.GET()
				.build();
		HttpResponse<InputStream> res;
		try {
			res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted", e);
		}
		InputStream body = res.body();
		if (res.statusCode() != 200) {
			body.close();
			throw new StatusException(url, res.statusCode());
		}
		boolean gzip = res.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
		return gzip ? new GZIPInputStream(body, 1 << 16) : body;
	}

	public static Reader reader(String url) throws IOException {
		return new InputStreamReader(open(url), StandardCharsets.UTF_8);
	}

	public static JsonElement json(String url) throws IOException {
		try (Reader r = reader(url)) {
			return JsonParser.parseReader(r);
		} catch (RuntimeException e) {
			throw new IOException("Bad JSON from " + url, e);
		}
	}
}
