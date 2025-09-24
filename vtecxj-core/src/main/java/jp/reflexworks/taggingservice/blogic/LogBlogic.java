package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.LogManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;

/**
 * ログ出力ビジネスロジック.
 */
public class LogBlogic {

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
			ConnectionInfo connectionInfo) {
		LogManager logManager = TaggingEnvUtil.getLogManager();
		logManager.writeLogger(statusCode, message, logLevel, e,
				serviceName, requestInfo, connectionInfo);
	}

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
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		LogManager logManager = TaggingEnvUtil.getLogManager();
		logManager.writeLogEntry(title, subtitle, message, serviceName,
				requestInfo, connectionInfo);
	}

	/**
	 * ログエントリーを出力.
	 * <ul>
	 *   <li>title → title</li>
	 *   <li>subtitle → subtitle</li>
	 *   <li>summary → message</li>
	 * <ul>
	 * @param feed feed
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void writeLogEntry(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		CheckUtil.checkNotNull(feed, "Log entry");
		CheckUtil.checkNotNull(feed.entry, "Log entry");
		for (EntryBase entry : feed.entry) {
			CheckUtil.checkNotNull(entry.title, "Log title");
			CheckUtil.checkNotNull(entry.summary, "Log message");
		}

		LogManager logManager = TaggingEnvUtil.getLogManager();
		for (EntryBase entry : feed.entry) {
			logManager.writeLogEntry(entry.title, entry.subtitle, entry.summary,
					serviceName, requestInfo, connectionInfo);
		}
	}

	/**
	 * ログエントリーを出力.
	 * サービス管理者のみ実行可。/d からの実行を想定。
	 * <ul>
	 *   <li>title → title</li>
	 *   <li>subtitle → subtitle</li>
	 *   <li>summary → message</li>
	 * <ul>
	 * @param feed feed
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void writeLogEntryByAdmin(FeedBase feed, ReflexRequest req)
	throws IOException, TaggingException {
		ReflexAuthentication auth = req.getAuth();
		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_ADMIN);

		writeLogEntry(feed, auth, req.getRequestInfo(), req.getConnectionInfo());
	}

	/**
	 * エラー時のログエントリーに出力するメッセージを取得する.
	 * causeがある場合、エラークラスとメッセージを取得する。
	 * @param e 例外
	 * @return メッセージ
	 */
	public String getErrorMessage(Throwable e) {
		LogManager logManager = TaggingEnvUtil.getLogManager();
		return logManager.getErrorMessage(e);
	}

}
