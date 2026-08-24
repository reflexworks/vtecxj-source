package jp.reflexworks.test;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;

import com.maxmind.db.Reader;

public class GeoLite2Example {

	/**
	 * GeoLite2 mmdbファイルの読み込みテスト
	 * @param args
	 *   [0] GeoLite2のmmdbファイルパス
	 *   [1] IPアドレス
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		try {
			if (args == null) {
				throw new IllegalArgumentException("Arguments are required.");
			}
			if (args.length < 2) {
				throw new IllegalArgumentException("Insufficient arguments.");
			}

			String mmdbFilePath = args[0];
			String ipAddress = args[1];

			StringBuilder sb = new StringBuilder();
			sb.append("[GeoLite2Example] start. mmdbFilePath=");
			sb.append(mmdbFilePath);
			sb.append(", ipAddress=");
			sb.append(ipAddress);
			System.out.println(sb.toString());

			File database = new File(mmdbFilePath);
			try (Reader reader = new Reader(database)) {
				InetAddress inetAddress = InetAddress.getByName(ipAddress);
				Map<String, Object> data = reader.get(inetAddress, Map.class);
				System.out.println("data=" + data);
			}

		} catch (Throwable e) {
			System.out.println("Error occured. " + e.getClass().getName() + ": " + e.getMessage());
			e.printStackTrace();
		}
	}

}
