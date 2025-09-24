package jp.reflexworks.js;

import java.io.IOException;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;

public class JsCallable extends ReflexCallable<Object> {

	private CompiledScript compiledexec;
	private JsContext jscontext;
	
	public JsCallable(CompiledScript compiledexec, JsContext jscontext) {
		this.compiledexec = compiledexec;
		this.jscontext = jscontext;
	}
	
	public Object call() throws IOException, TaggingException {
		try {
			Bindings bindings = compiledexec.getEngine().createBindings();
			bindings.put("ReflexContext", jscontext);	        
			SimpleScriptContext context = new SimpleScriptContext();
			context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
			return (Object) compiledexec.eval(context);
			
		} catch (ScriptException e) {
			// 「org.graalvm.polyglot.PolyglotException: TypeError: 」はサーバサイドJSの文法エラー
			Throwable cause = e.getCause();
			if (cause != null) {
				if (cause instanceof TaggingException) {
					throw (TaggingException)cause;
				} else if (cause instanceof IOException) {
					throw (IOException)cause;
				}
				String errmsg = cause.getMessage();
				if (errmsg != null && errmsg.startsWith("TypeError")) {	// 
					throw new InvalidServiceSettingException(e);
				}
			}
			//throw e;
			throw new InvalidServiceSettingException(e);
		}
	}
	
}

