package jp.reflexworks.test.useragent;

public record ClientHintsInfo(
		String browserFamily,
		Integer browserMajor,
		String osFamily,
		Integer osMajor,
		String deviceClass,
		Boolean bot) {
}