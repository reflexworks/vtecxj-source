package jp.reflexworks.test;

import jp.reflexworks.test.useragent.UserAgentInfo;
import jp.reflexworks.test.useragent.UserAgentParser;

public class UserAgentExample {

	/**
	 * User-Agentの解析テスト
	 * @param args
	 *   [0] User-Agentの値
	 */
	public static void main(String[] args) {
		try {
			if (args == null) {
				throw new IllegalArgumentException("Arguments are required.");
			}
			if (args.length < 1) {
				throw new IllegalArgumentException("Insufficient arguments.");
			}

			String userAgent = args[0];

			UserAgentInfo result = UserAgentParser.parse(userAgent);

			System.out.println("browserFamily = "
					+ result.browserFamily());

			System.out.println("browserMajor = "
					+ result.browserMajor());

			System.out.println("osFamily = "
					+ result.osFamily());

			System.out.println("osMajor = "
					+ result.osMajor());

			System.out.println("deviceClass = "
					+ result.deviceClass());

			System.out.println("isBot = "
					+ result.bot());

		} catch (Throwable e) {
			System.out.println("Error occured. " + e.getClass().getName() + ": " + e.getMessage());
			e.printStackTrace();
		}
	}

}
