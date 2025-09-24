package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;
import java.util.concurrent.Future;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.blogic.DatetimeBlogic;
import jp.reflexworks.taggingservice.blogic.LoginLogoutBlogic;
import jp.reflexworks.taggingservice.blogic.RedirectBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.LogManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * TaggingService Filter
 */
public class TaggingFilter implements Filter, ReflexServletConst {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * @param filterConfig FilterConfig
	 */
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// Do nothing.
	}

	/**
	 * シャットダウン.
	 * (2022.10.4)ServletContextListener.contextDestroyed が実行されないのでTaggingServletに移動。
	 * (2022.11.4)汎用API機能に対応し、ServletでなくFilterでシャットダウン処理を行うよう修正。
	 */
	@Override
	public void destroy() {
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] start.");
		}
		TaggingEnvUtil.destroy();
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] end.");
		}
	}

	/**
	 * フィルタ処理.
	 * @param request リクエスト
	 * @param response レスポンス
	 * @param chain チェイン
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpReq = (HttpServletRequest)request;
		HttpServletResponse httpResp = (HttpServletResponse)response;

		if (logger.isTraceEnabled()) {
			logger.debug("[doFilter] start.");
		}

		if (isEnableAccessLog()) {
			logger.debug("[doFilter] 0.1");
			logger.debug("[doFilter] [request]" + httpReq.getRequestURL().toString());
		}

		RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
		AuthenticationManager authenticationManager =
				TaggingEnvUtil.getAuthenticationManager();
		LogManager logManager = TaggingEnvUtil.getLogManager();

		// リクエスト・レスポンスをラップ
		ReflexRequest req = reqRespManager.createReflexRequest(httpReq);
		ReflexResponse resp = reqRespManager.createReflexResponse(req, httpResp);

		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		Future<Boolean> futureWriteAccessLogStart = null;

		try {
			// このシステムが稼働中かどうかチェック
			if (!TaggingEnvUtil.isRunning()) {
				throw new IllegalStateException("Tagging service is not running.");
			}

			// サービス稼働チェック
			ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
			boolean isEnabledService = serviceManager.isEnabled(req, serviceName,
					requestInfo, connectionInfo);

			if (isEnableAccessLog()) {
				logger.debug("[doFilter] 0.2 isEnabledService=" + isEnabledService);
			}

			if (isEnabledService) {
				// このノードでサービス情報を保持していない場合、設定処理を行う。
				serviceManager.settingServiceIfAbsent(serviceName, requestInfo,
						connectionInfo);
			}

			if (isEnableAccessLog()) {
				logger.debug("[doFilter] 0.3");
			}

			// サービス設定後にRequestParamを取得する。(内部処理でテンプレートを使用するため。)
			RequestType param = req.getRequestType();

			// アクセスログ
			futureWriteAccessLogStart = logManager.writeAccessStart(req);

			if (isEnableAccessLog()) {
				logger.debug("[doFilter] 1");
			}

			if (!isEnabledService) {
				// サービス稼働エラー
				throw new NotInServiceException(serviceName);
			}
			// 接続チェック
			if (chain == null) {
				throw new IOException("FilterChain is null.");
			}

			// サービスのリクエストプロトコルチェック
			boolean isProtocolOk = serviceManager.checkServiceStatus(req, resp);
			if (!isProtocolOk) {
				// リダイレクト設定済み
				return;
			}

			// セキュリティチェック
			SecurityBlogic securityBlogic = new SecurityBlogic();
			securityBlogic.checkRequestHost(req, resp);

			securityBlogic.checkRequestedWith(req);

			// アクセスカウンタ
			serviceManager.incrementAccessCounter(serviceName, requestInfo, connectionInfo);

			if (!StringUtils.isBlank(param.getOption(RequestParam.PARAM_REDIRECT_APP))) {
				// アプリリダイレクト
				RedirectBlogic redirectBlogic = new RedirectBlogic();
				redirectBlogic.redirectApp(
						param.getOption(RequestParam.PARAM_REDIRECT_APP), req, resp);
				return;
			}
			
			// CORSチェック
			securityBlogic.checkCORS(req, resp);

			// OPTIONメソッドはここで終了する
			if (ReflexServletConst.OPTIONS.equals(req.getMethod())) {
				doResponse(req, resp, HttpStatus.SC_OK, null);
				return;
			}
			
			if (param.getOption(RequestParam.PARAM_NOW) != null ||
				param.getOption(RequestParam.PARAM_GETDATETIME) != null) {
				// 現在時間取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_getdatetime or _now");
				}
				DatetimeBlogic datetimeBlogic = new DatetimeBlogic();
				FeedBase retFeed = datetimeBlogic.getDatetime(req.getServiceName());
				doResponse(req, resp, HttpStatus.SC_OK, retFeed);
				return;
			}

			// 認証
			ReflexAuthentication auth = authenticationManager.autheticate(req);
			if (!reqRespManager.afterAuthenticate(req, resp, auth)) {
				return;
			}

			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[doFilter] [autheticate] uid=");
				if (auth != null) {
					sb.append(auth.getUid());
				} else {
					sb.append("null");
				}
				logger.info(sb.toString());
			}

			LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
			if (param.getOption(RequestParam.PARAM_LOGIN) != null) {
				// ログイン
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_login");
				}
				FeedBase retFeed = loginLogoutBlogic.login(req, resp);
				if (retFeed != null) {
					doResponse(req, resp, HttpStatus.SC_OK, retFeed);
				}
				return;

			} else if (param.getOption(RequestParam.PARAM_LOGOUT) != null) {
				// ログアウト
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_logout");
				}
				FeedBase retFeed = loginLogoutBlogic.logout(req, resp);
				doResponse(req, resp, HttpStatus.SC_OK, retFeed);
				return;
			}

			// セキュリティチェック2 (認証結果によるサーバ許可チェックなど)

			// レスポンスにセッションIDをセット
			authenticationManager.setSessionIdToResponse(req, resp);

			// ログイン履歴出力
			loginLogoutBlogic.writeLoginHistory(req);

			// サーブレットに処理移譲
			chain.doFilter(req, resp);

		} catch (Throwable e) {
			// エラーの場合
			doException(req, resp, e);

		} finally {
			// リクエストのクリア (Deflate圧縮辞書メモリ解放)
			try {
				req.close();
			} catch (Exception e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "Request close error.", e);
				}
			}

			// アクセスログ
			// アクセス開始ログの出力終了を待つ。
			if (futureWriteAccessLogStart != null) {
				try {
					futureWriteAccessLogStart.get();
				} catch (Exception e) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "writeAccessLog(start) error.", e);
					}
				}
			}

			// アクセス終了ログ
			logManager.writeAccessEnd(req, resp);
		}
	}

	/**
	 * レスポンス返却
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param status ステータス
	 * @param retObj レスポンスデータ
	 */
	private void doResponse(ReflexRequest req, ReflexResponse resp,
			int status, Object respObj)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();

		if (!resp.isWritten()) {
			RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
			int format = req.getResponseFormat();
			boolean isGZip = reqRespManager.isGZip();
			boolean isStrict = false;
			boolean isNoCache = reqRespManager.isNoCache(req);
			boolean isSameOrigin = reqRespManager.isSameOrigin(req);
			ReflexServletUtil.doResponse(req, resp, respObj, format,
					TaggingEnvUtil.getResourceMapper(serviceName),
					req.getConnectionInfo().getDeflateUtil(), status, isGZip,
					isStrict, isNoCache, isSameOrigin);
		}
	}

	/**
	 * エラー処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param e 例外
	 */
	private void doException(ReflexRequest req, ReflexResponse resp, Throwable te)
	throws IOException {
		// ログアウト処理の場合、Cookieのセッションをクリアする
		if (req.getRequestType().getOption(RequestParam.PARAM_LOGOUT) != null) {
			LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
			try {
				loginLogoutBlogic.logout(req, resp);
			} catch (Throwable e) {
				logger.warn(LogUtil.getRequestInfoStr(req.getRequestInfo()) +
						"[doException] logout failed.", e);
			}
		}
		ExceptionUtil.doException(req, resp, te);
	}

	/**
	 * フィルタののアクセスログ（処理経過ログ）を出力するかどうか.
	 * テスト用
	 * @return フィルタののアクセスログ（処理経過ログ）を出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				TaggingEnvConst.FILTER_ENABLE_ACCESSLOG, false);
	}

}
