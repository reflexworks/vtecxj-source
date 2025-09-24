package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;

/**
 * ログ管理インターフェース.
 */
public interface LogManager extends ReflexPlugin {

	/**
	 * ログファイル出力、およびログエントリー出力 (リクエストから実行)
	 * @param statusCode レスポンスステータスコード
	 * @param message ログメッセージ
	 * @param logLevel ログレベル
	 * @param e 例外
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void writeLogger(Integer statusCode, String message, LogLevel logLevel,
			Throwable e, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo);

	/**
	 * ログエントリーを出力.
	 * @param title タイトル
	 * @param subtitle サブタイトル
	 * @param message メッセージ
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void writeLogEntry(String title, String subtitle, String message,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo);

	/**
	 * アクセス開始ログを出力.
	 * リクエストデータが大きい場合時間がかかるので非同期処理とする。
	 * @param req リクエスト
	 * @return Future
	 */
	public Future<Boolean> writeAccessStart(ReflexRequest req)
	throws IOException, TaggingException;

	/**
	 * アクセス終了ログを出力.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void writeAccessEnd(ReflexRequest req, ReflexResponse resp);

	/**
	 * ログ用リクエスト情報表記を取得.
	 * @param requestInfo リクエスト情報
	 * @return ログ用リクエスト情報文字列
	 */
	public String getRequestInfoStr(RequestInfo requestInfo);

	/**
	 * エラー時のログエントリーに出力するメッセージを取得する.
	 * causeがある場合、エラークラスとメッセージを取得する。
	 * @param e 例外
	 * @return メッセージ
	 */
	public String getErrorMessage(Throwable e);

}
