package jp.reflexworks.test.js;

import org.graalvm.polyglot.Context;

public class GraaljsTest {

	public static void main(String[] args) {
		try (Context context = Context.create()) {
			context.eval("js", "console.log('Hello from GraalJS!')");
		}
	}
}
