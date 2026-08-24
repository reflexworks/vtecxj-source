package jp.reflexworks.test.useragent;

public final class UserAgentInfoResolver {

	private UserAgentInfoResolver() {
	}

	public static UserAgentInfo resolve(
			String userAgent,
			String secChUa,
			String secChUaMobile,
			String secChUaPlatform,
			String secChUaPlatformVersion) {

		UserAgentInfo ua =
				UserAgentParser.parse(userAgent);

		ClientHintsInfo ch =
				ClientHintsParser.parse(
						secChUa,
						secChUaMobile,
						secChUaPlatform);

		Integer clientHintOsMajor =
				ClientHintsParserExtension.parsePlatformMajor(
						secChUaPlatformVersion);

		String browserFamily = known(ch.browserFamily())
				? ch.browserFamily()
						: ua.browserFamily();

		Integer browserMajor = ch.browserMajor() != null
				? ch.browserMajor()
						: ua.browserMajor();

		String osFamily = known(ch.osFamily())
				? ch.osFamily()
						: ua.osFamily();

		Integer osMajor = clientHintOsMajor != null
				? clientHintOsMajor
						: ua.osMajor();

		String deviceClass = known(ch.deviceClass())
				? ch.deviceClass()
						: ua.deviceClass();

		boolean bot = ua.bot()
				|| Boolean.TRUE.equals(ch.bot());

		return new UserAgentInfo(
				browserFamily,
				browserMajor,
				osFamily,
				osMajor,
				deviceClass,
				bot);
	}

	private static boolean known(String value) {
		return value != null
				&& !value.isBlank()
				&& !UserAgentInfo.UNKNOWN.equals(value);
	}
}
