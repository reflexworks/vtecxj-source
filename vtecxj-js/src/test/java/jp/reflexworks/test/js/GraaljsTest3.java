package jp.reflexworks.test.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class GraaljsTest3 {

	// Engine 生成時に engine.WarnInterpreterOnly を指定
	private static final Engine ENGINE = Engine.newBuilder()
			.option("engine.WarnInterpreterOnly", "false")
			.build();

	public static void main(String[] args) {
		try {
			Source source = Source.newBuilder("js",
					"aaaa",
					"GraaljsTest2")
					.build();

			try (Context ctx = Context.newBuilder("js")
					.engine(ENGINE)          // 共有 Engine を利用
					.allowAllAccess(true)    // 必要ならホストアクセス許可
					.build()) {

				Value result = ctx.eval(source);

				StringBuilder sb = new StringBuilder();
				sb.append("result = ");
				sb.append(result);
				System.out.println(sb.toString());
			}
		} catch (Throwable e) {
			System.out.println("Error occured. " + e.getClass().getName());
			e.printStackTrace();
		}
	}
}
