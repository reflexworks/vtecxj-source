package jp.reflexworks.taggingservice.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnv;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;
import jp.reflexworks.taggingservice.plugin.def.ServiceManagerDefault;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * バッチ用起動プログラム.
 * プロパティファイルを指定し起動します。
 */
public class ReflexApplication<O> {

	/** ReflexContext継承クラス名 */
	public static final String PROP_REFLEXCONTEXT_CLASSNAME = "_reflexcontext.classname";

	/** 非同期処理の終了待ちループ回数 */
	public static final int TASKWAIT_RETRY_COUNT = 100;
	/** 非同期処理の終了待ち時間(ミリ秒) */
	public static final int TASKWAIT_RETRY_WAITMILLIS = 1000;

	/** ロガー */
	private static Logger logger = LoggerFactory.getLogger(ReflexApplication.class);

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 */
	public static void main(String[] args) {
		ReflexApplication<?> me = new ReflexApplication<>();
		me.exec(args);
	}

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 * @return 処理結果
	 */
	public O exec(String[] args) {
		// 引数チェック
		checkArgs(args);

		// 環境初期設定
		String propFile = args[0];
		contextInitialized(propFile, false);

		// 認証情報
		ServiceManager serviceManager = new ServiceManagerDefault();
		String serviceName = args[1];
		ReflexAuthentication auth = serviceManager.createServiceAdminAuth(
				serviceName);
		return exec(args, auth);
	}

	/**
	 * バッチ用起動メソッド.
	 * @param isInitialized 初期データ登録処理の場合true
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 * @param auth 認証情報
	 * @return 処理結果
	 */
	protected O exec(String[] args, ReflexAuthentication auth) {
		int start = 3;

		//String propFile = args[0];
		String serviceName = args[1];
		String clsNameBlogic = args[2];
		String[] params = null;
		int len = args.length;
		if (len > start) {
			int paramLen = len - start;
			params = new String[paramLen];
			System.arraycopy(args, start, params, 0, paramLen);
		}

		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;

		String editArgs = serviceName + " " + clsNameBlogic;
		try {
			// ビジネスロジッククラス
			Class<ReflexBlogic<ReflexContext, O>> clsBlogic =
					(Class<ReflexBlogic<ReflexContext, O>>)Class.forName(clsNameBlogic);

			// 各種設定
			String ip = "local";
			String method = clsBlogic.getSimpleName();
			String url = clsNameBlogic;

			// オブジェクト生成
			// リクエスト情報
			RequestInfo requestInfo = new RequestInfoImpl(serviceName, ip, auth.getUid(),
					auth.getAccount(), method, url);

			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);

			// サービスの設定
			initService(TaggingEnvUtil.getSystemService(), requestInfo, connectionInfo);
			initService(serviceName, requestInfo, connectionInfo);

			// 一旦コネクション情報をクリアする。
			((ConnectionInfoImpl)connectionInfo).reset();

			// ReflexContextの生成
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(
					auth, requestInfo, connectionInfo, true);	// External

			// ReflexBlogicの生成
			ReflexBlogic<ReflexContext, O> reflexBlogic = clsBlogic.getDeclaredConstructor().newInstance();

			if (logger.isInfoEnabled()) {
				logger.info("[exec] start : " + editArgs);
			}

			// 実行
			O obj = reflexBlogic.exec(reflexContext, params);

			if (logger.isInfoEnabled()) {
				logger.info("[exec] end : " + editArgs);
			}

			return obj;

		} catch (Throwable e) {
			logger.error("[exec] Error occurred.", e);
			if (e instanceof Error) {
				throw (Error)e;
			}
			if (e instanceof RuntimeException) {
				throw (RuntimeException)e;
			}
			if (e instanceof RuntimeException) {
				throw (RuntimeException)e;
			}
			throw new RuntimeException(e);

		} finally {
			if (logger.isTraceEnabled()) {
				logger.debug("[exec] finally start : " + editArgs);
			}
			// 非同期実行が終了していない場合、終わるまで待つ。
			TaskQueueManager taskQueueManager = TaggingEnvUtil.getTaskQueueManager();
			int numRetries = TASKWAIT_RETRY_COUNT;
			int waitMillis = TASKWAIT_RETRY_WAITMILLIS;
			for (int r = 0; r <= numRetries; r++) {
				if (taskQueueManager == null || !taskQueueManager.hasTask()) {
					break;
				}
				if (r == numRetries) {
					logger.warn("[exec finally] task abort.");
				} else {
					if (logger.isInfoEnabled()) {
						logger.info("[exec finally] wait task ...");
					}
					RetryUtil.sleep(waitMillis);
				}
			}

			if (connectionInfo != null) {
				connectionInfo.close();
			}
			ReflexStatic.close();
			
			if (logger.isDebugEnabled()) {
				logger.debug("[exec] finally end : " + editArgs);
			}
		}
	}

	/**
	 * 引数をログ出力用に編集
	 * @param args 引数
	 * @return ログ出力用文字列
	 */
	protected String editArgs(String[] args) {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String arg : args) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			sb.append(arg);
		}
		return sb.toString();
	}

	/**
	 * 処理開始時に呼ばれるメソッド
	 * @param propFile プロパティファイル
	 * @param isInitialized 初期データ登録の場合のみtrue。
	 */
	protected void contextInitialized(String propFile, boolean isInitialized) {
		// 環境情報設定のための必須処理
		// 1. TaggingEnvを生成 (ReflexEnvを実装したクラス)
		TaggingEnv env = new TaggingEnv(propFile, isInitialized);
		try {
			// 2. TaggingEnvをstatic変数に保存
			ReflexStatic.setEnv(env);
			// 3. TaggingEnvの初期処理
			env.init();

		} catch (StaticDuplicatedException e) {
			// エラーを出力し処理継続
			if (logger.isInfoEnabled()) {
				logger.info("[contextInitialized] Setting environment is duplicated: " +
						e.getMessage());
			}
		}
	}

	/**
	 * サービス情報の設定.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	protected void initService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		// サービス稼働チェック
		boolean isEnabled = serviceManager.isEnabled(null, serviceName, requestInfo,
				connectionInfo);
		if (!isEnabled) {
			throw new NotInServiceException(serviceName);
		}
		// サービス情報の設定
		serviceManager.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 引数チェック
	 * @param args 引数
	 */
	protected void checkArgs(String[] args) {
		int start = 3;
		if (args == null || args.length < start) {
			throw new IllegalArgumentException("引数を指定してください。(1)プロパティファイル、(2)サービス名、(3)ビジネスロジックのクラス名、(4〜)ビジネスロジックへの引き渡し文字列");
		}
	}

}
