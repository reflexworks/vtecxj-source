package jp.reflexworks.test.useragent;

public final class ClientHintsParserExtension {

	private ClientHintsParserExtension() {
	}

	public static Integer parsePlatformMajor(
			String value) {

		if (value == null || value.isBlank()) {
			return null;
		}

		String normalized = value.trim();

		if (normalized.length() >= 2
				&& normalized.startsWith("\"")
				&& normalized.endsWith("\"")) {

			normalized = normalized.substring(
					1,
					normalized.length() - 1);
		}

		int dot = normalized.indexOf('.');
		String major = dot >= 0
				? normalized.substring(0, dot)
						: normalized;

		try {
			return Integer.valueOf(major);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
