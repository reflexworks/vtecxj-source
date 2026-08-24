package jp.reflexworks.test.useragent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClientHintsParser {

	private ClientHintsParser() {
	}

	/*
	 * Structured Fields形式の簡易解析です。
	 *
	 * 例:
	 * "Chromium";v="150", "Google Chrome";v="150"
	 */
	private static final Pattern BRAND_PATTERN = Pattern.compile(
			"\"([^\"]+)\"\\s*;\\s*v\\s*=\\s*\"([^\"]+)\"");

	public static ClientHintsInfo parse(
			String secChUa,
			String secChUaMobile,
			String secChUaPlatform) {

		Browser browser = parseBrowser(secChUa);
		String osFamily = parsePlatform(secChUaPlatform);
		String deviceClass = parseDeviceClass(secChUaMobile);

		/*
		 * Sec-CH-UA、Sec-CH-UA-Mobile、Sec-CH-UA-Platformだけでは
		 * OSバージョンは取得できません。
		 *
		 * また、これらだけでBotかどうかも通常は確定できません。
		 */
		return new ClientHintsInfo(
				browser.family(),
				browser.major(),
				osFamily,
				null,
				deviceClass,
				detectObviousBotBrand(secChUa));
	}

	private static Browser parseBrowser(String secChUa) {
		List<BrandVersion> brands = parseBrands(secChUa);

		/*
		 * 派生ブラウザをChromiumより優先します。
		 */
		Browser edge = findBrowser(
				brands,
				"Microsoft Edge",
				"Edge");

		if (edge != null) {
			return edge;
		}

		Browser opera = findBrowser(
				brands,
				"Opera",
				"Opera GX");

		if (opera != null) {
			return opera;
		}

		Browser chrome = findBrowser(
				brands,
				"Google Chrome",
				"Chrome");

		if (chrome != null) {
			return new Browser("Chrome", chrome.major());
		}

		Browser chromium = findBrowser(brands, "Chromium");
		if (chromium != null) {
			return chromium;
		}

		Browser firefox = findBrowser(brands, "Firefox");
		if (firefox != null) {
			return firefox;
		}

		/*
		 * GREASEブランド以外で、認識できない最初のブランドを返します。
		 */
		for (BrandVersion brand : brands) {
			if (!isGreaseBrand(brand.brand())) {
				return new Browser(
						brand.brand(),
						brand.major());
			}
		}

		return new Browser(UserAgentInfo.UNKNOWN, null);
	}

	private static List<BrandVersion> parseBrands(String secChUa) {
		List<BrandVersion> result = new ArrayList<>();

		if (secChUa == null || secChUa.isBlank()) {
			return result;
		}

		Matcher matcher = BRAND_PATTERN.matcher(secChUa);

		while (matcher.find()) {
			String brand = matcher.group(1);
			String version = matcher.group(2);

			result.add(new BrandVersion(
					brand,
					parseMajorVersion(version)));
		}

		return result;
	}

	private static Browser findBrowser(
			List<BrandVersion> brands,
			String... names) {

		for (BrandVersion brand : brands) {
			for (String name : names) {
				if (brand.brand().equalsIgnoreCase(name)) {
					return new Browser(
							normalizeBrowserName(brand.brand()),
							brand.major());
				}
			}
		}

		return null;
	}

	private static String normalizeBrowserName(String brand) {
		if (brand.equalsIgnoreCase("Google Chrome")
				|| brand.equalsIgnoreCase("Chrome")) {
			return "Chrome";
		}

		if (brand.equalsIgnoreCase("Microsoft Edge")
				|| brand.equalsIgnoreCase("Edge")) {
			return "Microsoft Edge";
		}

		if (brand.equalsIgnoreCase("Opera GX")) {
			return "Opera";
		}

		return brand;
	}

	private static String parsePlatform(
			String secChUaPlatform) {

		String platform = unquote(secChUaPlatform);

		if (platform == null || platform.isBlank()) {
			return UserAgentInfo.UNKNOWN;
		}

		return switch (platform.toLowerCase(Locale.ROOT)) {
		case "windows" -> "Windows";
		case "macos" -> "macOS";
		case "android" -> "Android";
		case "ios" -> "iOS";
		case "chrome os", "chromeos" -> "ChromeOS";
		case "linux" -> "Linux";
		default -> platform;
		};
	}

	private static String parseDeviceClass(
			String secChUaMobile) {

		if (secChUaMobile == null
				|| secChUaMobile.isBlank()) {
			return UserAgentInfo.UNKNOWN;
		}

		return switch (secChUaMobile.trim()) {
		case "?1" -> UserAgentInfo.MOBILE;
		case "?0" -> UserAgentInfo.DESKTOP;
		default -> UserAgentInfo.UNKNOWN;
		};
	}

	private static Boolean detectObviousBotBrand(
			String secChUa) {

		if (secChUa == null || secChUa.isBlank()) {
			return null;
		}

		String lower = secChUa.toLowerCase(Locale.ROOT);

		/*
		 * 明示的にHeadlessChromeなどが現れた場合だけtrueにします。
		 * 現れないからといって、人間のブラウザだとは証明できません。
		 */
		if (lower.contains("headlesschrome")
				|| lower.contains("googlebot")
				|| lower.contains("bingbot")) {
			return true;
		}

		return null;
	}

	private static boolean isGreaseBrand(String brand) {
		String normalized = brand
				.toLowerCase(Locale.ROOT)
				.replace(" ", "")
				.replace("_", "")
				.replace(".", "")
				.replace("/", "")
				.replace(";", "")
				.replace(":", "");

		/*
		 * "Not A Brand"、"Not_A Brand"、"Not.A/Brand"などを除外。
		 */
		return normalized.contains("notabrand")
				|| normalized.equals("notbrand");
	}

	private static Integer parseMajorVersion(
			String version) {

		if (version == null || version.isBlank()) {
			return null;
		}

		int dot = version.indexOf('.');
		String major = dot >= 0
				? version.substring(0, dot)
						: version;

		try {
			return Integer.valueOf(major);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String unquote(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();

		if (trimmed.length() >= 2
				&& trimmed.startsWith("\"")
				&& trimmed.endsWith("\"")) {

			return trimmed.substring(
					1,
					trimmed.length() - 1);
		}

		return trimmed;
	}

	private record BrandVersion(
			String brand,
			Integer major) {
	}

	private record Browser(
			String family,
			Integer major) {
	}
}