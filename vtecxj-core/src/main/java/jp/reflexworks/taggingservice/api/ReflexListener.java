package jp.reflexworks.taggingservice.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.env.TaggingEnv;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * APサーバ起動、シャットダウン時のリスナー.
 */
public class ReflexListener implements ServletContextListener {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ起動時に呼び出されるメソッド.
	 * web.xmlのlistenerに本クラスを定義しておく必要がある。
	 * @param sce ServletContextEvent
	 */
	public void contextInitialized(ServletContextEvent sce) {
		if (logger.isInfoEnabled()) {
			logger.info("[contextInitialized] start.");
		}
		// 環境情報設定のための必須処理
		// 1. TaggingEnvを生成 (ReflexEnvを実装したクラス)
		ServletContextUtil contextUtil = new ServletContextUtil();
		contextUtil.contextInitialized(sce);
		TaggingEnv env = new TaggingEnv(contextUtil);
		try {
			// 2. TaggingEnvをstatic変数に保存
			ReflexStatic.setEnv(env);
			// 3. TaggingEnvの初期処理
			env.init();

			// システム管理サービスの初期設定
			initSystemService();

			if (logger.isInfoEnabled()) {
				logger.info("[contextInitialized] end.");
			}

		} catch (StaticDuplicatedException e) {
			// エラーを出力し処理継続
			if (logger.isInfoEnabled()) {
				logger.info("[contextInitialized] Setting environment is duplicated: " +
						e.getMessage());
			}
		} catch (IOException e) {
			logger.error("[contextInitialized] Error occurred.", e);
			throw new IllegalStateException(e);
		} catch (TaggingException e) {
			logger.error("[contextInitialized] Error occurred.", e);
			throw new IllegalStateException(e);
		}
	}

	/**
	 * サーバシャットダウン時に呼び出されるメソッド.
	 * web.xmlのlistenerに本クラスを定義しておく必要がある。
	 * @param sce ServletContextEvent
	 */
	public void contextDestroyed(ServletContextEvent sce) {
		/* (2022.10.4)この処理が実行されないので、HttpServlet.destoryに移動。
		if (logger.isInfoEnabled()) {
			logger.info("[contextDestroyed] start.");
		}
		ReflexStatic.close();
		if (logger.isInfoEnabled()) {
			logger.info("[contextDestroyed] end.");
		}
		*/
	}

	/**
	 * システム管理サービスの初期設定.
	 */
	private void initSystemService()
	throws IOException, TaggingException {
		// 各種設定
		String serviceName = TaggingEnvUtil.getSystemService();
		String ip = "init";
		String method = "init";
		String url = "";
		String uid = SystemAuthentication.UID_SYSTEM;
		String account = SystemAuthentication.ACCOUNT_SYSTEM;

		// オブジェクト生成
		// リクエスト情報
		RequestInfo requestInfo = new RequestInfoImpl(serviceName, ip, uid,
				account, method, url);
		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);

			// ノードのオートスケール時にクラスタへのアクセスができなくなる期間があるため、
			// リトライ処理を設け、リトライ時間を長く、リトライ回数を多く設定する。
			int numRetries = getServiceSettingsInitRetryCount();
			int waitMillis = getServiceSettingsInitRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				try {
					// サービス情報の設定
					ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
					serviceManager.settingServiceIfAbsent(serviceName,
							requestInfo, connectionInfo);
					break;

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					try {
						convertError(e, Constants.GET, requestInfo);
					} catch (IOException ie) {
						throw new IllegalStateException(ie);
					}
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw new IllegalStateException(e);
					}
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[init] " + RetryUtil.getRetryLog(e, r));
					}
					RetryUtil.sleep(waitMillis + r * 10);
				} catch (TaggingException e) {
					throw new IllegalStateException(e);
				}
			}

		} finally {
			if (connectionInfo != null) {
				connectionInfo.close();
			}
		}
	}

	/**
	 * 例外を変換する.
	 * リトライ対象の場合例外をスローしない。
	 * @param e データストア例外
	 * @param method GET, POST, PUT or DELETE
	 * @param requestInfo リクエスト情報
	 */
	private void convertError(IOException e, String method, RequestInfo requestInfo)
	throws IOException {
		if (RetryUtil.isRetryError(e, method)) {
			return;
		}
		throw e;
	}

	/**
	 * 初期起動でのアクセス失敗時リトライ総数を取得.
	 * @return 初期起動でのアクセス失敗時リトライ総数
	 */
	private int getServiceSettingsInitRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(TaggingEnvConst.SERVICESETTINGS_INIT_RETRY_COUNT,
				TaggingEnvConst.SERVICESETTINGS_INIT_RETRY_COUNT_DEFAULT);
	}

	/**
	 * 初期起動でのアクセス失敗時リトライ時のスリープ時間を取得.
	 * @return 初期起動でのアクセス失敗時のスリープ時間(ミリ秒)
	 */
	private int getServiceSettingsInitRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(TaggingEnvConst.SERVICESETTINGS_INIT_RETRY_WAITMILLIS,
				TaggingEnvConst.SERVICESETTINGS_INIT_RETRY_WAITMILLIS_DEFAULT);
	}

}
