package jp.reflexworks.taggingservice.model;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.servlet.ReflexBDBRequest;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログ出力のためのリクエスト情報クラス
 */
public class ReflexBDBRequestInfo implements RequestInfo {

	/** サービス名 */
	private String serviceName;
	/** 名前空間 */
	private String namespace;
	/** IPアドレス */
	private String ip;
	/** メソッド */
	private String method;
	/** URL */
	private String url;
	/** リクエスト */
	private ReflexBDBRequest req;

	/** ReflexContextメッセージ (例外時のみ設定) */
	private String reflexContextMessage;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public ReflexBDBRequestInfo(ReflexBDBRequest req) {
		if (req == null) {
			return;
		}
		this.serviceName = req.getServiceName();
		this.namespace = req.getNamespace();
		this.ip = req.getRemoteAddr();
		this.method = req.getMethod();
		this.url = req.getRequestURLWithQueryString();
		// 認証情報はリクエストから取得する。
		// このコンストラクタが呼ばれた時点で認証処理はまだ行われていないため。
		this.req = req;
	}

	/**
	 * サービス名を設定.
	 * @param serviceName サービス名
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return serviceName;
	}

	/**
	 * 名前空間を取得
	 * @return 名前空間
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * 名前空間を設定
	 * @param namespace 名前空間
	 */
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	/**
	 * IPアドレスを取得.
	 * @return IPアドレス
	 */
	public String getIp() {
		return ip;
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
	 * リクエストを取得.
	 * @return リクエスト
	 */
	public ReflexBDBRequest getReq() {
		return req;
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
		return "RequestInfo [serviceName=" + serviceName + ", namespace=" + namespace + ", ip=" + ip
				+ ", method=" + method + ", url=" + url + ", req=" + req + ", reflexContextMessage="
				+ reflexContextMessage + "]";
	}

	@Override
	public String getUid() {
		// 
		return null;
	}

	@Override
	public String getAccount() {
		// 
		return null;
	}

}
