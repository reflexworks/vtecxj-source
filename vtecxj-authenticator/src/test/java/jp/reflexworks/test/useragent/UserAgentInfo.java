package jp.reflexworks.test.useragent;

public record UserAgentInfo(
		String browserFamily,
		Integer browserMajor,
		String osFamily,
		Integer osMajor,
		String deviceClass,
		boolean bot) {

	public static final String UNKNOWN = "Unknown";

	public static final String DESKTOP = "desktop";
	public static final String MOBILE = "mobile";
	public static final String TABLET = "tablet";
	public static final String BOT = "bot";
	public static final String API_CLIENT = "api-client";
}
