package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.LoginLogoutManager;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログイン・ログアウト処理.
 */
public class LoginLogoutBlogic {
	
	/**
	 * ログイン.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return ログインメッセージ
	 */
	public FeedBase login(ReflexRequest req, ReflexResponse resp) 
	throws IOException, TaggingException {
		// ログインの場合認証情報がなければエラー
		if (req.getAuth() == null || StringUtils.isBlank(req.getAuth().getAccount()) ||
				StringUtils.isBlank(req.getAuth().getUid())) {
			String subMsg = "Authentication is required when logging in.";
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(subMsg);
			throw ae;
		}
		
		// レスポンスにセッションIDをセット
		AuthenticationManager authenticationManager = 
				TaggingEnvUtil.getAuthenticationManager();
		authenticationManager.setSessionIdToResponse(req, resp);
		
		LoginLogoutManager loginLogoutManager = 
				TaggingEnvUtil.getLoginLogoutManager();
		
		String loginService = req.getRequestType().getOption(RequestParam.PARAM_LOGIN);
		if (StringUtils.isBlank(loginService)) {
			// ログイン履歴出力
			writeLoginHistory(req);
			
			// 自サービスにログイン
			return loginLogoutManager.login(req, resp);
			
		} else {
			// 他サービスにログイン
			// サービス名は小文字のみ
			loginService = TaggingEnvUtil.getServiceManager().editServiceName(loginService);
			String serviceName = req.getServiceName();
			if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
				throw new IllegalParameterException("Login dispatching is not available with this service.");
			}
			return loginLogoutManager.loginService(req, resp, loginService);
		}
	}
	
	/**
	 * ログアウト.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return ログアウトメッセージ
	 */
	public FeedBase logout(ReflexRequest req, ReflexResponse resp) 
	throws IOException, TaggingException {
		// セッションを削除する。
		if (req.getAuth() != null && !StringUtils.isBlank(req.getAuth().getSessionId())) {
			SessionBlogic sessionBlogic = new SessionBlogic();
			sessionBlogic.deleteSession(req.getAuth(), req.getRequestInfo(), 
					req.getConnectionInfo());
		}
		// Cookieのセッションも合わせて削除する。
		AuthenticationManager authenticationManager = 
				TaggingEnvUtil.getAuthenticationManager();
		authenticationManager.deleteSessionIdFromResponse(req, resp);
		
		LoginLogoutManager loginLogoutManager = 
				TaggingEnvUtil.getLoginLogoutManager();
		return loginLogoutManager.logout(req, resp);
	}
	
	/**
	 * ログイン履歴を出力
	 * @param req リクエスト
	 */
	public void writeLoginHistory(ReflexRequest req) {
		LoginLogoutManager loginLogoutManager = 
				TaggingEnvUtil.getLoginLogoutManager();
		loginLogoutManager.writeLoginHistory(req);
	}
	
	/**
	 * ログイン失敗履歴を出力
	 * @param req リクエスト
	 * @param ae AuthenticationException
	 */
	public void writeAuthError(ReflexRequest req, AuthenticationException ae) {
		LoginLogoutManager loginLogoutManager = 
				TaggingEnvUtil.getLoginLogoutManager();
		loginLogoutManager.writeAuthError(req, ae);
	}

	/**
	 * ユーザごとのログイン履歴フォルダエントリーのキーを取得.
	 *  /_user/{UID}/login_history
	 * @param uid UID
	 * @return ユーザごとのログイン履歴フォルダエントリーのキー
	 */
	public String getLoginHistoryUserFolderUri(String uid) {
		LoginLogoutManager loginLogoutManager = 
				TaggingEnvUtil.getLoginLogoutManager();
		return loginLogoutManager.getLoginHistoryUserFolderUri(uid);
	}

}
