package jp.reflexworks.js;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバサイドJS実行クラス
 */
public class JsExec {

	/**
	 * 設定 : サーバサイドJS実行タイムアウト時間(秒).
	 *       サービスごとの設定が無い場合、こちらの値を使用する。
	 */
	private static final String JAVASCRIPT_EXECTIMEOUT = JsServiceSettingConst.JAVASCRIPT_EXECTIMEOUT;
	/** 設定 : バッチジョブのサーバサイドJS実行タイムアウト時間(秒) */
	public static final String JAVASCRIPT_BATCHJOBTIMEOUT = JsServiceSettingConst.JAVASCRIPT_BATCHJOBTIMEOUT;
	/** 設定 : サーバサイドJSキャッシュサイズ(個数) */
	private static final String JAVASCRIPT_CACHESIZE = "_javascript.cachesize";
	/** 設定 : ExecutorServiceのデフォルトのプールサイズ */
	private static final String JSEXEC_POOLSIZE = "_jsexec.poolsize";
	/** 設定 : シャットダウン時の強制終了待ち時間(秒) */
	private static final String JSEXEC_AWAITTERMINATION_SEC = "_jsexec.awaittermination.sec";
	/** 設定 : サーバサイドJSアクセスログを出力するかどうか */
	private static final String JAVASCRIPT_ENABLE_ACCESSLOG = "_javascript.enable.accesslog";

	/** サーバサイドJS実行タイムアウト時間(秒) デフォルト値 */
	private static final int JAVASCRIPT_EXECTIMEOUT_DEFAULT = 300;
	/** バッチジョブのサーバサイドJS実行タイムアウト時間(秒) デフォルト値 */
	private static final int JAVASCRIPT_BATCHJOBTIMEOUT_DEFAULT = 300;
	/** ExecutorServiceのデフォルトのプールサイズ デフォルト値 */
	private static final int JSEXEC_POOLSIZE_DEFAULT = 10;
	/** シャットダウン時の強制終了待ち時間(秒) デフォルト値 */
	private static final int JSEXEC_AWAITTERMINATION_SEC_DEFAULT = 60;
	/** サーバタイプ : バッチジョブ */
	private static final String SERVERTYPE_BATCHJOB = Constants.SERVERTYPE_BATCHJOB;

	/** サーバサイドJS非同期実行クラス */
	private static ScheduledExecutorService exec = Executors.newScheduledThreadPool(
			TaggingEnvUtil.getSystemPropInt(JSEXEC_POOLSIZE, JSEXEC_POOLSIZE_DEFAULT));
	/** サーバサイドJSキャッシュ */
	private static Cache<String, CompiledScript> CACHE = null;

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(JsExec.class);
	
	/**
	 * 初期設定.
	 */
	public static void init() {
		// load('nashorn:mozilla_compat.js') するために必要、権限も全て付与される
		System.setProperty("polyglot.js.nashorn-compat", "true");
		// GraalVMからの、polyglotを使用すべきでないというWARNINGログを出力しない
		System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
	}

	/**
	 * サーバサイドJS実行
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param func キー
	 * @param method メソッド
	 * @return JsContext
	 */
	public static JsContext doExec(ReflexRequest req, ReflexResponse resp, String func, String method)
			throws IOException {
		ReflexAuthentication auth = null;
		String uid = null;
		if (req != null) {
			auth = req.getAuth();
			if (auth != null) {
				uid = auth.getUid();
			}
		}
		return doExec(req, resp, func, auth, uid,method);
	}

	/**
	 * サーバサイドJS実行
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param func キー
	 * @param auth 認証情報
	 * @param uid UID
	 * @param method メソッド
	 * @return JsContext
	 */
	private static JsContext doExec(ReflexRequest req, ReflexResponse resp, String func,
		ReflexAuthentication auth, String uid, String method) throws IOException {

		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// external

		// Hex dump
		/*
		String message1 = "";
		byte[] bytes = req.getPayload();
		for (int i = 0; i < bytes.length; i++) {
	        message1 += " "+Integer.toHexString(bytes[i] & 0xff);
	    }
		reflexContext.log("payload","", message1);

		try {
			Object feed = reflexContext.getResourceMapper().fromMessagePack(bytes);
			reflexContext.log("json","", reflexContext.getResourceMapper().toJSON(feed));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		 */
		// Hex dump

		try {
			JsContext jscontext = new JsContext(reflexContext, req, resp, method);

			StringBuilder logsb = new StringBuilder();
			logsb.append(LogUtil.getRequestInfoStr(req.getRequestInfo()));
			logsb.append("[doExec] js=");
			logsb.append(func);
			logsb.append(" ");
			String logPrefix = logsb.toString();
			long startTime = new Date().getTime();
			
			Future<Object> future = submit(jscontext, req, resp, func, method, 0, null, 
					reflexContext);
			
			//if (isEnableAccessLog()) {
			if (logger.isInfoEnabled()) {
				logger.info(logPrefix + "submit" + LogUtil.getElapsedTimeLog(startTime));
				startTime = new Date().getTime();
			}

			// サーバサイドJS実行タイムアウト時間
			// サービスごとの設定がある場合はサービスごとの設定を使用する。
			// サービスごとの設定が無い場合、サービス指定なしのサーバサイドJS実行タイムアウト時間を使用する。
			int exectimeout = getTimeout(reflexContext.getServiceName(),
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
			// jscontext.resultに実行結果、jscontext.getStatus()にステータスコードが返る
			jscontext.result = future.get(exectimeout, TimeUnit.SECONDS);
			
			//if (isEnableAccessLog()) {
			if (logger.isInfoEnabled()) {
				logger.info(logPrefix + "future.get" + LogUtil.getElapsedTimeLog(startTime));
				startTime = new Date().getTime();
			}

			return jscontext;

		} catch (TaggingException e) {
			throw new IOException(e);
		// TODO Java17では jdk.nashorn.internal.runtime.ECMAException がない。
		//} catch (ParseException | TimeoutException |
			//		jdk.nashorn.internal.runtime.ECMAException e) {	// TypeError など
		} catch (ParseException | TimeoutException e) {	// TypeError など
			throw new IOException(new InvalidServiceSettingException(e));
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			// causeがRuntimeExceptionそのものの場合、そのcauseが実際のエラー
			if (cause.getClass().equals(RuntimeException.class)) {
				Throwable tmpCause = cause.getCause();
				if (tmpCause != null) {
					cause = tmpCause;
				}
			}
			if (cause instanceof IOException) {
				throw (IOException)cause;
			} else if (cause instanceof ScriptException) {
				// JS実行エラーはScriptExceptionでラップされているので取り出す。
				Throwable seCause = cause.getCause();
				if (seCause != null) {
					if (seCause instanceof IOException) {
						throw (IOException)seCause;
					} else if (seCause instanceof TaggingException) {
						throw new IOException(seCause);
					// TODO Java17では jdk.nashorn.internal.runtime.ECMAException がない。
						/*
					} else if (seCause instanceof jdk.nashorn.internal.runtime.ECMAException) {
						Throwable ecmaCause = seCause.getCause();
						if (ecmaCause != null) {
							if (ecmaCause instanceof IOException) {
								throw (IOException)ecmaCause;
							} else if (ecmaCause instanceof TaggingException) {
								throw new IOException(ecmaCause);
							} else {
								throw new IOException(ecmaCause);
							}
						} else {
							// ECMAExceptionは TypeError など
							throw new IOException(new InvalidServiceSettingException(seCause));
						}
						*/
					} else {
						throw new IOException(seCause);
					}
				}
				throw new IOException(cause);
			} else {
				throw new IOException(cause);
			}
		}
	}

	private static CompiledScript getCompiledScript(String func, String entrykey,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		try {
			return CACHE.get(entrykey, key -> {
				try {
					return createCompiledScript(func,reflexContext);
				} catch (IOException | TaggingException | ScriptException e) {
					throw new CompiledScriptException(e);
				}
			});
		} catch (CompiledScriptException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw (IOException)cause;
			} else if (cause instanceof TaggingException) {
				throw (TaggingException)cause;
			} else {	// ScriptException
				throw new InvalidServiceSettingException(cause);
			}
		}
	}

	private static CompiledScript createCompiledScript(String func, ReflexContext reflexContext)
	throws IOException, TaggingException, ScriptException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		StringBuilder logsb = new StringBuilder();
		logsb.append(LogUtil.getRequestInfoStr(requestInfo));
		logsb.append("[createCompiledScript] js=");
		logsb.append(func);
		logsb.append(" ");
		String logPrefix = logsb.toString();
		long startTime = new Date().getTime();
		if (isEnableAccessLog()) {
			logger.debug(logPrefix + "start.");
		}

		ScriptEngine scriptEngine = JsUtil.getScriptEngine(new ScriptEngineManager(),
				reflexContext.getRequestInfo());

		if (isEnableAccessLog()) {
			logger.debug(logPrefix + "getScriptEngine" + LogUtil.getElapsedTimeLog(startTime));
			startTime = new Date().getTime();
		}

		StringBuilder sb = new StringBuilder();
		sb.append("var console = {};console.log = function(s) { var f = ReflexContext.settingValue('console.log'); if (f&&f==='true') ReflexContext.log(s)};console.error=function(s) { var f=ReflexContext.settingValue('console.error'); if (f&&f==='true') ReflexContext.log(s)};console.warn=function(s) { var f = ReflexContext.settingValue('console.warn');if (f&&f==='true') ReflexContext.log(s)};");
		String main = contentjs(func,reflexContext);
		if (isEnableAccessLog()) {
			StringBuilder dbg = new StringBuilder();
			dbg.append(logPrefix);
			dbg.append("contentjs length=");
			if (main == null) {
				dbg.append("null");
			} else {
				dbg.append(main.length());
			}
			dbg.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(dbg.toString());
			startTime = new Date().getTime();
		}
		if (main==null) throw new ScriptException(func+".js is not found.");
		sb.append(main);

		CompiledScript ret = ((Compilable)scriptEngine).compile(sb.toString());
		if (isEnableAccessLog()) {
			logger.debug(logPrefix + "compile" + LogUtil.getElapsedTimeLog(startTime));
		}
		return ret;
	}

	private static String contentjs(String uri,ReflexContext reflexContext) throws IOException, TaggingException {
		byte[] content;
		if (uri.startsWith("@")) {
			content = reflexContext.getHtmlContent("/@/server/"+uri.substring(1)+".js");
		}else {
			if (!uri.startsWith("/")) {
				uri = "/"+uri;
			}
			content = reflexContext.getHtmlContent("/server"+uri+".js");
		}
		if (content!=null) {
			return JsUtil.replace(new String(content)).replaceAll("\n", "").replaceAll("\r", "");
		}
		else return null;
	}

	/**
	 * サーバサイドJS非同期実行開始.
	 * バッチジョブからも使用できるよう実行開始部分を切り出し。
	 * @param jscontext JsContext
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param func キー
	 * @param method メソッド
	 * @param delay 遅延実行の場合(時間単位はtimeUnitに指定)
	 * @param timeUnit 遅延実行の場合の時間単位
	 * @param reflexContext ReflexContext
	 * @return Futureオブジェクト
	 */
	public static Future<Object> submit(JsContext jscontext, ReflexRequest req,
			ReflexResponse resp, String func, String method, long delay, TimeUnit timeUnit,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// キャッシュの生成
		if (CACHE == null) {
			long startTime = new Date().getTime();
			
			String cachesizeStr = TaggingEnvUtil.getSystemProp(JAVASCRIPT_CACHESIZE, null);
			Integer cachesize = null;
			if (!StringUtils.isBlank(cachesizeStr) && StringUtils.isInteger(cachesizeStr)) {
				int tmpCachesize = Integer.parseInt(cachesizeStr);
				if (tmpCachesize > 0) {
					cachesize = tmpCachesize;
				}
			}

			if (cachesize != null) {
				CACHE = Caffeine
						.newBuilder()
						.expireAfterAccess(3, TimeUnit.DAYS)
						.softValues()
						.maximumSize(cachesize)
						.build();
			} else {
				CACHE = Caffeine
						.newBuilder()
						.expireAfterAccess(3, TimeUnit.DAYS)
						.softValues()
						.build();
			}
			
			if (isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
						"[submit] create js CACHE. " + LogUtil.getElapsedTimeLog(startTime));
			}
		}

		EntryBase entry = null;
		if (func.startsWith("@")) {
			entry = (EntryBase) reflexContext.getEntry("/@/server/" + func.substring(1) + ".js");
		} else {
			entry = (EntryBase) reflexContext.getEntry("/_html/server/" + func + ".js");
		}
		String key = null;
		if (entry != null) {
			key = entry.updated + "/@" + reflexContext.getServiceName() + entry.id;

			JsCallable callable = new JsCallable(
					getCompiledScript(func, key, reflexContext), jscontext);
			/*
			Future<Object> future = null;
			if (delay > 0 && timeUnit != null) {
				future = exec.schedule(callable, delay, timeUnit);
			} else {
				future = exec.submit(callable);
			}
			return future;
			*/
			long countdownMillis = getCountdownMillis(delay, timeUnit);
			return (Future<Object>)TaskQueueUtil.addTask(callable, countdownMillis, 
					auth, requestInfo, connectionInfo);

		} else {
			StringBuilder sb = new StringBuilder();
			sb.append("[serverside js] ");
			sb.append(func);
			sb.append(" is not found.");
			NoExistingEntryException ne = new NoExistingEntryException(sb.toString());
			throw new InvalidServiceSettingException(ne);
		}
	}
	
	/**
	 * 遅延実行時間をミリ秒で取得.
	 * @param delay 遅延実行の場合(時間単位はtimeUnitに指定)
	 * @param timeUnit 遅延実行の場合の時間単位
	 * @return 遅延実行時間(ミリ秒)
	 */
	private static long getCountdownMillis(long delay, TimeUnit timeUnit) 
	throws InvalidServiceSettingException {
		if (delay == 0 || timeUnit == null) {
			return delay;
		} else if (timeUnit == TimeUnit.MILLISECONDS) {
			return delay;
		} else if (timeUnit == TimeUnit.SECONDS) {
			return delay * 1000;
		} else if (timeUnit == TimeUnit.MINUTES) {
			return delay * 60 * 1000;
		} else if (timeUnit == TimeUnit.HOURS) {
			return delay * 60 * 60 * 1000;
		} else {
			// 遅延時間指定エラー
			throw new InvalidServiceSettingException("Invalid delay. timeUnit=" + timeUnit);
		}
	}

	/**
	 * サーバサイドJS実行タイムアウト時間(秒)を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバサイドJS実行タイムアウト時間(秒)
	 */
	public static int getTimeout(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		// システム管理サービス定義の値をデフォルト値とする
		int defaultTimeout = 0;
		String propName = null;
		String serverType = TaggingEnvUtil.getSystemProp(TaggingEnvConst.REFLEX_SERVERTYPE, null);
		if (SERVERTYPE_BATCHJOB.equals(serverType)) {
			defaultTimeout = TaggingEnvUtil.getSystemPropInt(JAVASCRIPT_BATCHJOBTIMEOUT,
					JAVASCRIPT_BATCHJOBTIMEOUT_DEFAULT);
			propName = JAVASCRIPT_BATCHJOBTIMEOUT;
		} else {
			defaultTimeout = TaggingEnvUtil.getSystemPropInt(JAVASCRIPT_EXECTIMEOUT,
					JAVASCRIPT_EXECTIMEOUT_DEFAULT);
			propName = JAVASCRIPT_EXECTIMEOUT;
		}

		try {
			// productionサービスの場合サービス固有設定を使用する。
			ServiceBlogic serviceBlogic = new ServiceBlogic();
			String serviceStatus = serviceBlogic.getServiceStatus(serviceName,
					requestInfo, connectionInfo);
			if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
				// サービスごとの設定値を取得
				return TaggingEnvUtil.getPropInt(serviceName, propName, defaultTimeout);
			}
		} catch (InvalidServiceSettingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getTimeout] InvalidServiceSettingException: ");
			sb.append(e.getMessage());
			sb.append(" serviceName=");
			sb.append(serviceName);
			sb.append(", propertyName=");
			sb.append(propName);
			logger.warn(sb.toString());
		} catch (IOException | TaggingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getTimeout] ");
			sb.append(e.getClass().getSimpleName());
			sb.append(": ");
			sb.append(e.getMessage());
			sb.append(" serviceName=");
			sb.append(serviceName);
			sb.append(", propertyName=");
			sb.append(propName);
			logger.warn(sb.toString(), e);
		}

		// サービスステータスがその他の場合、設定値取得でエラーの場合はデフォルト値を返却。
		return defaultTimeout;
	}

	/**
	 * 終了処理.
	 */
	public static void close() {
		long startTime = 0;
		if (logger.isDebugEnabled()) {
			logger.debug("[close] shutdown start.");
			startTime = new Date().getTime();
		}
		exec.shutdown();
		int timeoutSec = TaggingEnvUtil.getSystemPropInt(
				JSEXEC_AWAITTERMINATION_SEC,
				JSEXEC_AWAITTERMINATION_SEC_DEFAULT);
		boolean isTermination = false;
		try {
			isTermination = exec.awaitTermination(timeoutSec, TimeUnit.SECONDS);
			if (!isTermination) {
				logger.warn("[close] awaitTermination failed.");
			}
		} catch (InterruptedException e) {
			logger.warn("[close] InterruptedException: " + e.getMessage(), e);
		} finally {
			if (logger.isDebugEnabled()) {
				long finishTime = new Date().getTime();
				long time = finishTime - startTime;
				StringBuilder sb = new StringBuilder();
				sb.append("[close] shutdown end. isTermination = ");
				sb.append(isTermination);
				sb.append(" - ");
				sb.append(time);
				sb.append("ms");
				logger.debug(sb.toString());
			}
		}
	}

	/**
	 * サーバサイドJS実行のアクセスログを出力するかどうかを取得.
	 * @return サーバサイドJS実行のアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				JAVASCRIPT_ENABLE_ACCESSLOG, false) &&
				logger.isDebugEnabled();
	}

}
