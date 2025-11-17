package jp.reflexworks.js;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;

public class JsCallable extends ReflexCallable<Object> {

	private Source source;
	private JsContext jscontext;

	public JsCallable(Source source, JsContext jscontext) {
		this.source = source;
		this.jscontext = jscontext;
	}

	public Object call() throws IOException, TaggingException {
		try (Context ctx = Context.newBuilder("js")
				.engine(JsExec.getEngine())	// 共有 Engine を利用
				.allowAllAccess(true)		// 必要ならホストアクセス許可
				.build()) {

			// JavaScript から参照できるようにバインドに登録
			ctx.getBindings("js").putMember("ReflexContext", jscontext);

			Value result = ctx.eval(source);

			// 返却は必要に応じてJava型へ変換
			if (result == null || result.isNull()) return null;
			if (result.isHostObject()) return result.asHostObject();
			if (result.isBoolean()) return result.asBoolean();
			if (result.isNumber()) {
				try {
					return result.asInt();
				} catch (Exception ignore) {
					try { return result.asLong(); } catch (Exception ignore2) {
						return result.asDouble();
					}
				}
			}
			if (result.isString()) return result.asString();
			// その他
			return result.toString();

		} catch (PolyglotException e) {
			// 旧来の ScriptException 相当のマッピング
			if (e.isHostException()) {
				Throwable host = e.asHostException();
				if (host instanceof TaggingException) throw (TaggingException) host;
				if (host instanceof IOException) throw (IOException) host;
			}
			// JS文法・型エラーの扱い（従来の "TypeError" を InvalidServiceSetting に）
			String msg = e.getMessage();
			if (e.isSyntaxError() || (msg != null && msg.startsWith("TypeError"))) {
				throw new InvalidServiceSettingException(e);
			}
			throw new InvalidServiceSettingException(e);
		}
	}

}

