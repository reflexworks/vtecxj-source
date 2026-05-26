package jp.reflexworks.test.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class GraaljsCommonJsShimTest {

	private static final Engine ENGINE = Engine.newBuilder()
			.option("engine.WarnInterpreterOnly", "false")
			.build();

	public static void main(String[] args) {
		try {
			String script = ""
					+ "if (typeof module === 'undefined') { var module = { exports: {} }; }"
					+ "if (typeof exports === 'undefined') { var exports = module.exports; }"
					+ "(()=>{\"use strict\";var e={1(e){e.exports=123}},t={},o=function o(n){var l=t[n];if(void 0!==l)return l.exports;var s=t[n]={exports:{}};return e[n].call(s.exports,s,s.exports,o),s.exports};module.exports=o(1)})();"
					+ "module.exports;";

			Source source = Source.newBuilder("js", script, "GraaljsCommonJsShimTest").build();

			try (Context ctx = Context.newBuilder("js")
					.engine(ENGINE)
					.allowAllAccess(true)
					.build()) {
				Value result = ctx.eval(source);
				System.out.println("result = " + result.asInt());
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
