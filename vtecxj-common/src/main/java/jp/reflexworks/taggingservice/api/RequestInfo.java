package jp.reflexworks.taggingservice.api;

/**
 * リクエスト情報.
 */
public interface RequestInfo {

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName();

	/**
	 * IPアドレスを取得.
	 * @return IPアドレス
	 */
	public String getIp();

	/**
	 * メソッドを取得.
	 * @return メソッド
	 */
	public String getMethod();

	/**
	 * URLを取得.
	 * @return URL
	 */
	public String getUrl();

	/**
	 * UIDを取得.
	 * @return UID
	 */
	public String getUid();

	/**
	 * アカウントを取得.
	 * @return アカウント
	 */
	public String getAccount();

}
