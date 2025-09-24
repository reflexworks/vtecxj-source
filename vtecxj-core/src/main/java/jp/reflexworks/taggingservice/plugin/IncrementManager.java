package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * インクリメント管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 */
public interface IncrementManager extends ReflexPlugin {

	/**
	 * 加算.
	 * @param uri URI
	 * @param num 加算数。0は現在番号の取得。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 加算結果
	 * @throws OutOfRangeException 加算範囲が一度きりの指定で、加算範囲を超えた場合。
	 */
	public long increment(String uri, long num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 値セット.
	 * @param uri URI
	 * @param num 設定値
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	public long setNumber(String uri, long num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 加算範囲セット.
	 * 「{開始値}-{終了値}!」の形式で指定します。
	 * 「-{終了値}」は任意指定です。
	 * 末尾に!を指定すると、番号のインクリメントは一度きりとなります。(任意指定)
	 * インクリメント時に終了値を超える場合はエラーを返します。
	 * 末尾に!が指定されない場合、インクリメント時に終了値を超える場合は開始値に戻り加算を行います。
	 * @param uri URI
	 * @param range 設定値
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	public String setRange(String uri, String range, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 加算範囲取得.
	 * 「{開始値}-{終了値}!」の形式で指定します。
	 * 「-{終了値}」は任意指定です。
	 * 末尾に!を指定すると、番号のインクリメントは一度きりとなります。(任意指定)
	 * インクリメント時に終了値を超える場合はエラーを返します。
	 * 末尾に!が指定されない場合、インクリメント時に終了値を超える場合は開始値に戻り加算を行います。
	 * @param uri URI
	 * @param range 設定値
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	public String getRange(String uri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 全ての加算情報を削除.
	 * サービス削除時に使用
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteAll(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
