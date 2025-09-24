package jp.reflexworks.taggingservice.model;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.servlet.TaggingRequest;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログ出力のためのリクエスト情報クラス
 */
public class RequestInfoImpl implements RequestInfo {

	/** サービス名 */
	private String serviceName;
	/** IPアドレス */
	private String ip;
	/** UID */
	private String uid;
	/** アカウント */
	private String account;
	/** メソッド */
	private String method;
	/** URL */
	private String url;
	/** リクエスト */
	private TaggingRequest req;
	/** ReflexContextメッセージ (例外時のみ設定) */
	private String reflexContextMessage;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public RequestInfoImpl(TaggingRequest req) {
		if (req == null) {
			return;
		}
		this.serviceName = req.getServiceName();
		this.ip = req.getRemoteAddr();
		this.method = req.getMethod();
		this.url = req.getRequestURLWithQueryString();
		// 認証情報はリクエストから取得する。
		// このコンストラクタが呼ばれた時点で認証処理はまだ行われていないため。
		this.req = req;
	}

	/**
	 * フィルタを通る前に使用するコンストラクタ.
	 * @param req リクエスト
	 * @param serviceName サービス名
	 */
	public RequestInfoImpl(HttpServletRequest req, String serviceName) {
		if (req == null) {
			return;
		}
		this.serviceName = serviceName;
		this.ip = req.getRemoteAddr();
		this.method = req.getMethod();
		this.url = UrlUtil.getRequestURLWithQueryString(req);
	}

	/**
	 * バッチ用または内部処理用コンストラクタ.
	 * @param serviceName サービス名
	 * @param ip IPアドレス
	 * @param uid UID
	 * @param account アカウント
	 * @param method メソッド
	 * @param url URL
	 */
	public RequestInfoImpl(String serviceName, String ip, String uid, String account,
			String method, String url) {
		this.serviceName = serviceName;
		this.ip = ip;
		this.uid = uid;
		this.account = account;
		this.method = method;
		this.url = url;
	}

	/**
	 * サービス名を設定.
	 * @param serviceName サービス名
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * 認証情報を設定.
	 * @param auth 認証情報
	 */
	public void setAuth(ReflexAuthentication auth) {
		if (auth != null) {
			this.uid = auth.getUid();
			this.account = auth.getAccount();
		}
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return serviceName;
	}

	/**
	 * IPアドレスを取得.
	 * @return IPアドレス
	 */
	public String getIp() {
		return ip;
	}

	/**
	 * UIDを取得.
	 * @return UID
	 */
	public String getUid() {
		if (req != null && req.getAuth() != null) {
			return req.getAuth().getUid();
		} else {
			return uid;
		}
	}

	/**
	 * アカウントを取得.
	 * @return アカウント
	 */
	public String getAccount() {
		if (req != null) {
			if (req.getAuth() != null) {
				return req.getAuth().getAccount();
			} else if (req.getWsseAuth() != null) {
				String[] getUsernameAndService = AuthTokenUtil.getUsernameAndService(
						req.getWsseAuth());
				if (getUsernameAndService != null && getUsernameAndService.length > 0) {
					return getUsernameAndService[0];
				}
			}
		}
		return account;
	}

	/**
	 * メソッドを取得.
	 * @return メソッド
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * URLを取得.
	 * @return URL
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * ReflexContextメッセージを取得.
	 * 例外時のみ設定される
	 * @return ReflexContextメッセージ
	 */
	public String getReflexContextMessage() {
		return reflexContextMessage;
	}

	/**
	 * ReflexContextメッセージを設定.
	 * 例外時のみ設定
	 * @param msg ReflexContextメッセージ
	 */
	public void setReflexContextMessage(String msg) {
		if (StringUtils.isBlank(reflexContextMessage)) {
			reflexContextMessage = msg;
		}
	}

	/**
	 * toString.
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		return "RequestInfo [serviceName=" + serviceName + ", ip=" + ip
				+ ", uid=" + uid + ", account=" + account + ", method="
				+ method + ", url=" + url + "]";
	}

}
