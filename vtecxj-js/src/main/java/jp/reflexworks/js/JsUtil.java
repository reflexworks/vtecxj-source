package jp.reflexworks.js;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import jakarta.servlet.http.Cookie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバサイドJS実行ユーティリティ
 */
public class JsUtil {
	
	/** Script Engine名 */
	private static final String SCRIPT_ENGINE_NAME = "graal.js";

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(JsUtil.class);

	/**
	 * GraalJSスクリプトエンジンを取得する.
	 * @param manager ScriptEngineManager
	 * @param requestInfo リクエスト情報 (エラー時のログ出力用)
	 * @return GraalJSのスクリプトエンジン
	 */
	public static ScriptEngine getScriptEngine(ScriptEngineManager manager,
			RequestInfo requestInfo) 
		throws IOException, ScriptException {
		ScriptEngine engine = null;
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			if (factory.getNames().contains(SCRIPT_ENGINE_NAME)) {
				engine = manager.getEngineByName(SCRIPT_ENGINE_NAME);
				
				engine.eval("load('nashorn:mozilla_compat.js')");
				break;
			}
		}
		if (engine == null) {
			throw new IOException("ScriptEngine is not available. " + SCRIPT_ENGINE_NAME);
		}
		return engine;
	}

	/**
	 * ReflexContextのメソッドを実行した後の戻り値をJSON.parse()する.
	 */
	public static String replaceReflexContext(String text,String method) {
		String result = "";

		int p=0;

		while(true) {
			int s = text.indexOf("ReflexContext."+method+"(",p);
			if (s<0) break;
			int e = text.indexOf(")",s);
			int k = text.indexOf("(",s);
			while(true) {
				k = text.indexOf("(",k+1);
				if (k!=-1&&k<e) {
					e = text.indexOf(")",e+1);
				}else {
					break;
				}
			}

			result += text.substring(p, s);
			result += "JSON.parse(";
			result += text.substring(s, e+1);
			result += ")";
			p=e+1;
		}
		result += text.substring(p);

		return result;
	}

	/**
	 * 実行可能なJavaScriptに変換.
	 * @param text JavaScript
	 * @return reflexjs上で実行可能なJavaScript
	 */
	public static String replace(String text) throws IOException, PermissionException {
		text = replaceReflexContext(text,"getFeed");
		text = replaceReflexContext(text,"getEntry");
		text = replaceReflexContext(text,"getRequest");
		text = replaceReflexContext(text,"getCookies");
		text = replaceReflexContext(text,"getHeaders");
		text = replaceReflexContext(text,"getMail");
		text = replaceReflexContext(text,"getCsv");
		text = replaceReflexContext(text,"getPage");
		text = replaceReflexContext(text,"getBQ");
		text = replaceReflexContext(text,"post");
		text = replaceReflexContext(text,"put");
		text = replaceReflexContext(text,"postBDBQ");
		text = replaceReflexContext(text,"putBDBQ");
		text = replaceReflexContext(text,"adduserByAdmin");
		text = replaceReflexContext(text,"deleteUser");
		text = replaceReflexContext(text,"getSessionFeed");
		text = replaceReflexContext(text,"getSessionEntry");
		text = replaceReflexContext(text,"urlfetch");
		text = replaceReflexContext(text,"getMessageQueue");
		text = replaceReflexContext(text,"joinGroup");
		text = replaceReflexContext(text,"leaveGroup");
		text = replaceReflexContext(text,"getPayloadJson");
		text = replaceReflexContext(text,"addGroup");
		text = replaceReflexContext(text,"addGroupByAdmin");
		text = replaceReflexContext(text,"createGroupadmin");
		text = replaceAtMethod(text,"setHeaders");
		text = replaceAtMethod(text,"put");
		text = replaceAtMethod(text,"bulkput");
		text = replaceAtMethod(text,"post");
		text = replaceAtMethod(text,"postBQ");
		text = replaceAtMethod(text,"postBDBQ");
		text = replaceAtMethod(text,"putBDBQ");
		text = replaceAtMethod(text,"bulkputBDBQ");
		text = replaceAtMethod(text,"doResponse");
		text = replaceAtMethod(text,"sendMail");
		text = replaceAtMethod(text,"adduserByAdmin");
		text = replaceAtMethod(text,"deleteUser");
		text = replaceAtMethod(text,"setSessionFeed");
		text = replaceAtMethod(text,"setSessionEntry");
		text = replaceAtMethod(text,"putSignatures");
		text = replaceAtMethod(text,"pushNotification");
		text = replaceAtMethod(text,"sendWebSocket");
		text = replaceAtMethod(text,"setMessageQueue");
		text = replaceAtMethod(text,"createGroupadmin");
		return text;
	}

	/**
	 * 実行する際のパラメータを、JSON文字列に変換する.
	 */
	public static String replaceAtMethod(String text,String method) {
		String result = "";

		int p=0;
		try {
			while(true) {
				int s = text.indexOf("ReflexContext."+method+"(",p);
				if (s<0) break;
				int e = text.indexOf(")",s);
				int e2 = text.indexOf(",",s);
				int k = text.indexOf("(",s);
				int k0=k+1;
				while(true) {
					k = text.indexOf("(",k+1);
					if (k!=-1&&k<e) {
						e = text.indexOf(")",e+1);
					}else {
						break;
					}
				}
				if (e2>0&&e2<e) e=e2;

				result += text.substring(p, k0);
				result += "JSON.stringify(";
				result += text.substring(k0, e);
				result += ")";
				p=e;
			}
			result += text.substring(p);
		}catch(StringIndexOutOfBoundsException e) {
			return text;
		}
		return result;
	}

	/**
	 * BigQueryの実行結果をJSONに変換する.
	 * @param resultbq BigQuery実行結果
	 * @param parent 親項目
	 * @return JSON
	 */
	public static String convertResultBQ(List<Map<String, Object>> resultbq, String parent) {
		String result = "[";
		if (resultbq!=null) {
			for (int j=0;j<resultbq.size();j++) {
				Map<String, Object> line = resultbq.get(j);
				result += "{";
				if (parent!=null) {
					result += "\""+parent + "\":{";
				}
				List<String> keys = new ArrayList<String>(line.keySet());

				for(int i=0;i<keys.size();i++) {
					Object value = line.get(keys.get(i));
					if (value!=null) {
						if (i!=0) result += ",";
						result += "\""+keys.get(i)+"\":"+quote(value);
					}
				}
				if (parent!=null) {
					result += "}";
				}
				result += "}";
				if (j<resultbq.size()-1) result += ",";
			}
		}
		result += "]";
		return result;
	}

	/**
	 * BigQueryの実行結果をCSVに変換する.
	 * @param resultbq BigQuery実行結果
	 * @param header CSV先頭行
	 * @return CSV文字列配列
	 */
	public static String[] convertResultBQcsv(List<Map<String, Object>> resultbq, String header) {
		List<String> results = new ArrayList<String>();
		if (resultbq!=null) {
			for (int j=0;j<resultbq.size();j++) {
				Map<String, Object> line = resultbq.get(j);
				List<String> keys = new ArrayList<String>(line.keySet());
				// header
				if (j==0){
					if (header==null){
						String row = "";
						for(int i=0;i<keys.size();i++) {
							row += keys.get(i);
							if (i<keys.size()-1) row += ",";
						}
						results.add(row);
					}else {
						results.add(header);
					}
				}
				String row = "";
				for(int i=0;i<keys.size();i++) {
					Object value = line.get(keys.get(i));
					if (value!=null) {
						row += "\""+value+"\"";
					}else {
						row += "\"\"";
					}
					if (i<keys.size()-1) row += ",";
				}
				results.add(row);
			}
		}
		return results.toArray(new String[results.size()]);
	}
	
	/**
	 * リクエストのCookie情報をJSON形式に変換する.
	 * @param cookies リクエストCookie
	 * @return Cookie情報をJSON変換した文字列
	 */
	public static String convertHttpCookies(Cookie[] cookies) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		
		if (cookies != null && cookies.length > 0) {
			boolean isFirst = true;
			for (Cookie reqcookie : cookies) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append("{");
				
				boolean isFirstItem = true;
				String jsonPart = getJsonPart("comment", reqcookie.getComment(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("domain", reqcookie.getDomain(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("httponly", reqcookie.isHttpOnly(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("maxage", reqcookie.getMaxAge(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("name", reqcookie.getName(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("path", reqcookie.getPath(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("secure", reqcookie.getSecure(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("value", reqcookie.getValue(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				jsonPart = getJsonPart("version", reqcookie.getVersion(), isFirstItem);
				sb.append(jsonPart);
				isFirstItem = isFirstItem(isFirstItem, jsonPart);
	
				sb.append("}");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	/**
	 * リクエストのヘッダ情報をJSON形式に変換する.
	 * @param req リクエスト
	 * @return リクエストヘッダ情報をJSON変換した文字列
	 */
	public static String convertHttpHeaders(ReflexRequest req) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		
		boolean isFirst = true;
		for (Enumeration<String> e = req.getHeaderNames(); e.hasMoreElements();) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(",");
			}
			sb.append("{");
			
			boolean isFirstItem = true;
			String name = e.nextElement();
			String jsonPart = getJsonPart("name", name, isFirstItem);
			sb.append(jsonPart);
			isFirstItem = isFirstItem(isFirstItem, jsonPart);

			jsonPart = getJsonPart("value", req.getHeader(name), isFirstItem);
			sb.append(jsonPart);
			isFirstItem = isFirstItem(isFirstItem, jsonPart);
			
			sb.append("}");
		}
		
		sb.append("]");
		return sb.toString();
	}

	/**
	 * JSONの部分文字列を生成
	 * 「"name" : val」(valはオブジェクトによってクォートで囲む。)
	 * @param name 名前
	 * @param val 値
	 * @param isFirstItem 先頭の項目であればtrue、2番目以降であればfalse。falseの場合最初にカンマを付ける。
	 * @return JSONの部分文字列
	 */
	private static String getJsonPart(String name, Object val, boolean isFirstItem) {
		if (val != null) {
			StringBuilder sb = new StringBuilder();
			if (!isFirstItem) {
				sb.append(",");
			}
			sb.append(quote(name));
			sb.append(" : ");
			sb.append(quote(val));
			return sb.toString();
		} else {
			return "";
		}
	}
	
	/**
	 * 最初の項目かどうか.
	 * @param isFirstItem 前回のフラグ
	 * @param jsonPart JSON文字列
	 * @return 最初の項目の場合true
	 */
	private static boolean isFirstItem(boolean isFirstItem, String jsonPart) {
		if (!isFirstItem) {
			return isFirstItem;
		}
		return StringUtils.isBlank(jsonPart);
	}
	/**
	 * クォート編集.
	 * 数値・boolean以外はクォートで囲む編集を行う。
	 * 配列の場合、大括弧で囲み、再帰的処理を行う。
	 * @param obj オブジェクト
	 * @return 編集した値
	 */
	private static String quote(Object obj) {
		if (obj instanceof Number | obj instanceof Boolean) {
			return "" + obj;
		} else if (obj != null && obj.getClass().isArray()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			int length = Array.getLength(obj);
			for (int i = 0; i < length; i++) {
				if (i > 0) {
					sb.append(",");
				}
			    Object tmp = Array.get(obj, i);
			    sb.append(quote(tmp));
			}
			sb.append("]");
			return sb.toString();
		}
		return escape("" + obj);
	}

	/**
	 * エスケープ編集.
	 * @param string 編集対象
	 * @return エスケープ編集を行った文字列
	 */
	private static String escape(String string) {
		if (string == null || string.length() == 0) {
			return "\"\"";
		}

		char c = 0;
		int i;
		int len = string.length();
		StringBuilder sb = new StringBuilder(len + 4);
		String t;

		sb.append('"');
		for (i = 0; i < len; i += 1) {
			c = string.charAt(i);
			switch (c) {
			case '\\':
			case '"':
				sb.append('\\');
				sb.append(c);
				break;
			case '/':
				sb.append('\\');
				sb.append(c);
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\r':
				sb.append("\\r");
				break;
			default:
				if (c < ' ') {
					t = "000" + Integer.toHexString(c);
					sb.append("\\u" + t.substring(t.length() - 4));
				} else {
					sb.append(c);
				}
			}
		}
		sb.append('"');
		return sb.toString();
	}

	/**
	 * JSONをFeed形式に編集する.
	 * @param feedstr JSON
	 * @return Feed形式のJSON
	 */
	public static String addfeedstr(String feedstr) {
		if (feedstr!=null&&feedstr.trim().charAt(0)=='[') {
			return "{ \"feed\" : { \"entry\" :" +feedstr + "}}";
		}else {
			return feedstr;
		}
	}

}
