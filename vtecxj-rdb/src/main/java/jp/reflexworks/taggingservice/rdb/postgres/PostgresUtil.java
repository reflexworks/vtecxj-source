package jp.reflexworks.taggingservice.rdb.postgres;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.rdb.ReflexRDBConst;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * postgresql 固有のユーティリティ
 */
public final class PostgresUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(PostgresUtil.class);

	/**
	 * コンストラクタ.
	 */
	private PostgresUtil() { }

	/**
	 * SQLExceptionの設定エラー判定.
	 * @param e IOException
	 * @param requestInfo リクエスト情報
	 * @return 設定エラーの場合true
	 */
	public static boolean isSettingError(SQLException e, RequestInfo requestInfo) {
		// TODO 要検討
		return false;
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e IOException
	 * @param requestInfo リクエスト情報
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(SQLException e, RequestInfo requestInfo) {
		if (e != null && e.getMessage() != null) {
			String sqlState = e.getSQLState();
			String msg = e.getMessage();
			// タイムアウトの場合リトライ対象とする。(仮)
			// メッセージに"try again"とあればリトライ対象とする。
			if (msg.indexOf("timeout") > -1 || msg.indexOf("Timeout") > -1 ||
					msg.indexOf("try again") > -1) {
				return true;
			}
			if ("53300".equals(sqlState) || "08001".equals(sqlState)) {
			//if ("53300".equals(sqlState)) {
				// 53300 : 接続過多
				// 08001 : SQLクライアントはSQL接続を確立できなかった
				return true;
			}
		}
		return false;
	}
	
	/**
	 * SQLExceptionの入力エラー判定.
	 * 入力エラーの場合、適切な例外に変換してスローする。
	 * @param e
	 * @param requestInfo
	 */
	public static void checkInputError(SQLException e, RequestInfo requestInfo) 
	throws TaggingException {
		String sqlState = e.getSQLState();
		String msg = e.getMessage();
		StringBuilder sb = new StringBuilder();
		sb.append("SQLState=");
		sb.append(sqlState);
		sb.append(" ");
		sb.append(msg);
		String errMsg = sb.toString();
		if ("23505".equals(sqlState) || msg.indexOf("duplicate key") > -1) {
			// 23505 : 一意性違反
			throw new EntryDuplicatedException(errMsg);
		//} else if ("42P01".equals(sqlState)) {
		//	// 42P01 : 未定義テーブル → drop tableの場合は404でも問題ないが、insert等は入力エラーと判定すべき。
		//	throw new NoExistingEntryException(errMsg);
		} else if ("23502".equals(sqlState) || sqlState.startsWith("42") ||
				msg.indexOf("invalid input syntax") > -1 ||
				msg.indexOf("syntex error") > -1) {
			// 23502 : 非NULL違反
			// 42601 : 構文エラー
			throw new IllegalParameterException(errMsg);
		}
		
	}
	
	/**
	 * JDBC接続文字列を返却
	 * @param serviceName サービス名
	 * @return JDBC接続文字列
	 */
	public static final String getJdbcUrl(String serviceName) {
		String host = TaggingEnvUtil.getProp(serviceName, ReflexRDBConst.RDB_HOST, null);
		String port = TaggingEnvUtil.getProp(serviceName, ReflexRDBConst.RDB_PORT, null);
		String database = TaggingEnvUtil.getProp(serviceName, ReflexRDBConst.RDB_DATABASE, null);
		if (StringUtils.isBlank(host) && StringUtils.isBlank(database)) {
			return null;
		}
		CheckUtil.checkNotNull(host, "RDB host setting");
		CheckUtil.checkNotNull(database, "RDB Database setting");
		
		// 接続文字列
		// jdbc:postgresql://<接続先DBサーバのIP or ホスト名>:<DBのポート番号>/<DB名>
		StringBuilder sb = new StringBuilder();
		sb.append("jdbc:postgresql://");
		sb.append(host);
		if (!StringUtils.isBlank(port)) {
			sb.append(":");
			sb.append(port);
		}
		sb.append("/");
		sb.append(database);
		
		return sb.toString();
	}

}
