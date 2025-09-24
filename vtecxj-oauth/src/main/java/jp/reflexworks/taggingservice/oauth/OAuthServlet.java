package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexServletBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * OAuth サーブレット.
 */
public class OAuthServlet extends ReflexServletBase {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * GETメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doGet] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doGet] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + "doGet start");
		}
		OAuthBlogic blogic = new OAuthBlogic();
		try {
			String uri = req.getPathInfo();
			// 最上位の階層は"/"のため、0番目は""が設定される。
			String[] uriParts = TaggingEntryUtil.getUriParts(uri);
			if (uriParts == null || uriParts.length < 3) {
				throw new IllegalParameterException("Invalid parameter. " + uri);
			}
			String provider = uriParts[1];
			String action = uriParts[2];

			// providerのチェック
			Class<OAuthProvider> providerCls = OAuthUtil.getOAuthProviderClass(provider);
			if (providerCls == null) {
				throw new IllegalParameterException("Invalid provider. " + provider);
			}
			OAuthProvider oauthProvider = OAuthUtil.getInstance(providerCls);

			// action: oauth
			// action: callback

			if (OAuthConst.ACTION_OAUTH.equals(action)) {
				// 1. OAuthプロバイダ、またはシステム管理サービスへリダイレクト
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "oauth");
				}
				blogic.oauth(req, resp, oauthProvider);

			} else if (OAuthConst.ACTION_CALLBACK.equals(action)) {
				// 2. OAuthプロバイダからのコールバック
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "callback");
				}
				blogic.callback(req, resp, oauthProvider);

			} else {
				throw new IllegalParameterException("Invalid action. " + action);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doPost] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doPost] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + "doPost start");
		}
		OAuthBlogic blogic = new OAuthBlogic();
		try {
			String uri = req.getPathInfo();
			// 最上位の階層は"/"のため、0番目は""が設定される。
			String[] uriParts = TaggingEntryUtil.getUriParts(uri);
			if (uriParts == null || uriParts.length < 3) {
				throw new IllegalParameterException("Invalid parameter. " + uri);
			}
			String provider = uriParts[1];
			String action = uriParts[2];

			Object retObj = null;
			int status = HttpStatus.SC_OK;

			// action: link
			// action: create_state
			// action: check_state

			if (OAuthConst.ACTION_LINK.equals(action)) {
				// 紐付け・ログイン処理
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "link");
				}
				FeedBase feed = req.getFeed();
				blogic.linkLogin(req, resp, provider, feed, param.getOption(
						OAuthConst.PARAM_STATE));

			} else if (OAuthConst.ACTION_CREATE_STATE.equals(action)) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "create_state");
				}
				// stateの生成・キャッシュ登録
				retObj = blogic.createState(req, resp, provider);

			} else if (OAuthConst.ACTION_CHECK_STATE.equals(action)) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "check_state");
				}
				// stateのチェック
				retObj = blogic.checkState(req, resp, provider, param.getOption(
						OAuthConst.PARAM_STATE));

			} else {
				throw new IllegalParameterException("Invalid action. " + action);
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * PUTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		// なし
		throw new MethodNotAllowedException("Method not allowed. " + httpReq.getMethod());
	}
	
	/**
	 * DELETEメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doDelete(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doDelete] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doDelete] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + "doDelete start");
		}
		OAuthBlogic blogic = new OAuthBlogic();
		try {
			String uri = req.getPathInfo();
			// 最上位の階層は"/"のため、0番目は""が設定される。
			String[] uriParts = TaggingEntryUtil.getUriParts(uri);
			if (uriParts == null || uriParts.length < 3) {
				throw new IllegalParameterException("Invalid parameter. " + uri);
			}
			String provider = uriParts[1];
			String action = uriParts[2];

			// action: oauthid

			if (OAuthConst.ACTION_OAUTHID.equals(action)) {
				// 紐付け削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "oauthid");
				}
				blogic.deleteOAuthid(req, resp, provider);

			} else {
				throw new IllegalParameterException("Invalid action. " + action);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

}
