package jp.reflexworks.test;

import jp.reflexworks.test.useragent.ClientHintsInfo;
import jp.reflexworks.test.useragent.ClientHintsParser;

public class ClientHintsExample {

	/**
	 * Client Hintsの解析テスト
	 * @param args
	 *   [0] sec-ch-uaの値
	 *   [1] sec-ch-ua-mobileの値
	 *   [2] sec-ch-ua-platformの値
	 */
	public static void main(String[] args) {
		try {
			if (args == null) {
				throw new IllegalArgumentException("Arguments are required.");
			}
			if (args.length < 3) {
				throw new IllegalArgumentException("Insufficient arguments.");
			}

			String secChUa = args[0];
			String secChUaMobile = args[1];
			String secChUaPlatform = args[2];

			ClientHintsInfo result = ClientHintsParser.parse(
					secChUa,
					secChUaMobile,
					secChUaPlatform);

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
