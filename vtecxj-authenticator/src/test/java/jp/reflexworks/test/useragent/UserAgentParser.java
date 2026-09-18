package jp.reflexworks.test.useragent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserAgentParser {

	private UserAgentParser() {
	}

	/*
	 * ブラウザ判定では順序が重要です。
	 *
	 * EdgeやOperaのUser-Agentにも "Chrome/..." が含まれるため、
	 * Chromeより先にEdge、Operaなどを判定します。
	 */
	private static final List<BrowserRule> BROWSER_RULES = List.of(
			/*
			 * ブラウザ以外のHTTPクライアント
			 */
			new BrowserRule(
					"Postman",
					Pattern.compile(
							"PostmanRuntime/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"curl",
					Pattern.compile(
							"curl/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"wget",
					Pattern.compile(
							"Wget/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"HTTPie",
					Pattern.compile(
							"HTTPie/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Python Requests",
					Pattern.compile(
							"python-requests/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"OkHttp",
					Pattern.compile(
							"okhttp/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Apache HttpClient",
					Pattern.compile(
							"Apache-HttpClient/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Java HTTP Client",
					Pattern.compile(
							"Java-http-client/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			/*
			 * 一般的なブラウザ
			 */
			new BrowserRule(
					"Microsoft Edge",
					Pattern.compile(
							"(?:Edg|EdgA|EdgiOS)/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Opera",
					Pattern.compile(
							"(?:OPR|Opera)/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Samsung Internet",
					Pattern.compile(
							"SamsungBrowser/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Chrome",
					Pattern.compile(
							"(?:Chrome|CriOS)/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Firefox",
					Pattern.compile(
							"(?:Firefox|FxiOS)/(\\d+)",
							Pattern.CASE_INSENSITIVE)),

			/*
			 * Chrome系ブラウザのUAにもSafari/...が含まれるため、
			 * SafariはChromeなどより後で判定します。
			 */
			new BrowserRule(
					"Safari",
					Pattern.compile(
							"Version/(\\d+).*Safari/",
							Pattern.CASE_INSENSITIVE)),

			new BrowserRule(
					"Internet Explorer",
					Pattern.compile(
							"(?:MSIE\\s+|rv:)(\\d+)",
							Pattern.CASE_INSENSITIVE))
			);

	private static final Pattern WINDOWS_PATTERN =
			Pattern.compile("Windows NT (\\d+)(?:\\.(\\d+))?",
					Pattern.CASE_INSENSITIVE);

	private static final Pattern ANDROID_PATTERN =
			Pattern.compile("Android\\s+(\\d+)",
					Pattern.CASE_INSENSITIVE);

	private static final Pattern IOS_PATTERN =
			Pattern.compile("(?:CPU(?: iPhone)? OS|iPhone OS)\\s+(\\d+)",
					Pattern.CASE_INSENSITIVE);

	private static final Pattern MAC_OS_PATTERN =
			Pattern.compile("Mac OS X\\s+(\\d+)",
					Pattern.CASE_INSENSITIVE);

	private static final Pattern CHROME_OS_PATTERN =
			Pattern.compile("CrOS\\s+[^\\s]+\\s+(\\d+)",
					Pattern.CASE_INSENSITIVE);

	private static final Pattern API_CLIENT_PATTERN = Pattern.compile(
			"(?i)("
					+ "postmanruntime|"
					+ "^node$|"
					+ "^undici$|"
					+ "curl|"
					+ "wget|"
					+ "httpie|"
					+ "python-requests|"
					+ "python-urllib|"
					+ "okhttp|"
					+ "apache-httpclient|"
					+ "java-http-client|"
					+ "got/|"
					+ "axios/|"
					+ "insomnia"
					+ ")");

	/*
	 * 一般的なBot、クローラー、CLI、監視ツールなどの名前です。
	 * 完全なリストではありません。
	 */
	private static final Pattern BOT_PATTERN = Pattern.compile(
			"(?i)("
					+ "bot|crawler|spider|slurp|bingpreview|"
					+ "googlebot|bingbot|duckduckbot|baiduspider|yandexbot|"
					+ "facebookexternalhit|twitterbot|linkedinbot|"
					+ "headlesschrome|phantomjs|selenium|playwright|puppeteer|"
					+ "uptimerobot|pingdom|datadog|newrelic"
					+ ")");

	public static UserAgentInfo parse(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return new UserAgentInfo(
					UserAgentInfo.UNKNOWN,
					null,
					UserAgentInfo.UNKNOWN,
					null,
					UserAgentInfo.UNKNOWN,
					false);
		}

		boolean bot = BOT_PATTERN.matcher(userAgent).find();

		Browser browser = parseBrowser(userAgent);
		OperatingSystem os = parseOperatingSystem(userAgent);
		String deviceClass = parseDeviceClass(userAgent, bot);

		return new UserAgentInfo(
				browser.family(),
				browser.major(),
				os.family(),
				os.major(),
				deviceClass,
				bot);
	}

	private static Browser parseBrowser(String userAgent) {
		String normalized = userAgent.trim();

		/*
		 * バージョンを含まない特殊なUser-Agent
		 */
		if (normalized.equalsIgnoreCase("node")) {
			return new Browser("Node.js", null);
		}

		if (normalized.equalsIgnoreCase("undici")) {
			return new Browser("Node.js undici", null);
		}

		for (BrowserRule rule : BROWSER_RULES) {
			Matcher matcher = rule.pattern().matcher(userAgent);

			if (matcher.find()) {
				return new Browser(
						rule.family(),
						parseInteger(matcher.group(1)));
			}
		}

		return new Browser(UserAgentInfo.UNKNOWN, null);
	}

	private static OperatingSystem parseOperatingSystem(String userAgent) {
		/*
		 * iPhone/iPadのUAには "like Mac OS X" が含まれるため、
		 * macOSより先にiOS/iPadOSを判定します。
		 */
		Matcher iosMatcher = IOS_PATTERN.matcher(userAgent);
		if (iosMatcher.find()) {
			String family = containsIgnoreCase(userAgent, "iPad")
					? "iPadOS"
							: "iOS";

			return new OperatingSystem(
					family,
					parseInteger(iosMatcher.group(1)));
		}

		Matcher androidMatcher = ANDROID_PATTERN.matcher(userAgent);
		if (androidMatcher.find()) {
			return new OperatingSystem(
					"Android",
					parseInteger(androidMatcher.group(1)));
		}

		Matcher windowsMatcher = WINDOWS_PATTERN.matcher(userAgent);
		if (windowsMatcher.find()) {
			/*
			 * User-AgentのWindows NTバージョンはOSの商品名と
			 * 単純には一致しません。
			 *
			 * Windows 10とWindows 11のUAは、いずれも通常
			 * Windows NT 10.0になります。
			 */
			return new OperatingSystem(
					"Windows",
					parseInteger(windowsMatcher.group(1)));
		}

		Matcher chromeOsMatcher = CHROME_OS_PATTERN.matcher(userAgent);
		if (chromeOsMatcher.find()) {
			return new OperatingSystem(
					"ChromeOS",
					parseInteger(chromeOsMatcher.group(1)));
		}

		Matcher macMatcher = MAC_OS_PATTERN.matcher(userAgent);
		if (macMatcher.find()) {
			return new OperatingSystem(
					"macOS",
					parseInteger(macMatcher.group(1)));
		}

		if (containsIgnoreCase(userAgent, "Linux")) {
			return new OperatingSystem("Linux", null);
		}

		return new OperatingSystem(UserAgentInfo.UNKNOWN, null);
	}

	private static String parseDeviceClass(
			String userAgent,
			boolean bot) {

		if (bot) {
			return UserAgentInfo.BOT;
		}

		if (API_CLIENT_PATTERN.matcher(userAgent).find()) {
			return UserAgentInfo.API_CLIENT;
		}

		if (containsIgnoreCase(userAgent, "iPad")
				|| containsIgnoreCase(userAgent, "Tablet")
				|| containsIgnoreCase(userAgent, "Kindle")
				|| containsIgnoreCase(userAgent, "Silk/")) {

			return UserAgentInfo.TABLET;
		}

		if (containsIgnoreCase(userAgent, "Android")
				&& !containsIgnoreCase(userAgent, "Mobile")) {

			return UserAgentInfo.TABLET;
		}

		if (containsIgnoreCase(userAgent, "Mobile")
				|| containsIgnoreCase(userAgent, "iPhone")
				|| containsIgnoreCase(userAgent, "iPod")
				|| containsIgnoreCase(userAgent, "Windows Phone")) {

			return UserAgentInfo.MOBILE;
		}

		/*
		 * desktopと判定できるOS・ブラウザ情報がある場合だけdesktopにする。
		 */
		if (containsIgnoreCase(userAgent, "Windows")
				|| containsIgnoreCase(userAgent, "Macintosh")
				|| containsIgnoreCase(userAgent, "CrOS")
				|| containsIgnoreCase(userAgent, "X11")
				|| containsIgnoreCase(userAgent, "Linux")) {

			return UserAgentInfo.DESKTOP;
		}

		return UserAgentInfo.UNKNOWN;
	}

	private static boolean containsIgnoreCase(
			String value,
			String search) {

		return value.toLowerCase(Locale.ROOT)
				.contains(search.toLowerCase(Locale.ROOT));
	}

	private static Integer parseInteger(String value) {
		try {
			return value == null ? null : Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private record BrowserRule(
			String family,
			Pattern pattern) {
	}

	private record Browser(
			String family,
			Integer major) {
	}

	private record OperatingSystem(
			String family,
			Integer major) {
	}
}