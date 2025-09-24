package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockMode;

import jp.reflexworks.atom.api.EntryUtil.UriPair;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvConst;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBユーティリティクラス.
 */
public class BDBUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BDBUtil.class);

	/**
	 * ManifestのBDBキーを取得
	 * @param uri URI
	 * @return ManifestのBDBキー
	 */
	public static DatabaseEntry getManifestDbKey(String uri) {
		String manifestUri = getManifestUri(uri);
		return getDbKey(manifestUri);
	}

	/**
	 * 文字列をBDBのキーオブジェクトに変換.
	 * @param keyStr キー文字列
	 * @return BDBのキーオブジェクトに
	 */
	public static DatabaseEntry getDbKey(String keyStr) {
		if (keyStr != null) {
			try {
				return new DatabaseEntry(keyStr.getBytes(BDBConst.ENCODING));
			} catch (UnsupportedEncodingException e) {}	// Do nothing.
		}
		return null;
	}

	/**
	 * バイト配列をBDBのキーオブジェクトにに変換
	 * @param keyBytes キー
	 * @return BDBのキーオブジェクト
	 */
	public static DatabaseEntry getDbKey(byte[] keyBytes) {
		return new DatabaseEntry(keyBytes);
	}

	/**
	 * DatabaseEntryを文字列に変換
	 * @param data DatabaseEntry
	 * @return 文字列
	 */
	public static String getDbString(DatabaseEntry data) {
		if (data != null && data.getData() != null) {
			try {
				return new String(data.getData(), BDBConst.ENCODING);
			} catch (UnsupportedEncodingException e) {}	// Do nothing.
		}
		return null;
	}

	/**
	 * DatabaseEntryをバイト配列に変換.
	 * @param data DatabaseEntry
	 * @return DatabaseEntry
	 */
	public static byte[] getDbBytes(DatabaseEntry data) {
		return data.getData();
	}

	/**
	 * Manifestのuriに指定する値を取得.
	 * Feed検索の範囲指定のため親階層とselfidの間の`/`のみ`\ufffe`に変換。
	 * @param uri URI
	 * @return Manifestのuriに指定する値
	 */
	public static String getManifestUri(String uri) {
		UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
		StringBuilder sb = new StringBuilder();
		sb.append(TaggingEntryUtil.removeLastSlash(uriPair.parent));
		sb.append(BDBConst.END_PARENT_URI_STRING);
		sb.append(uriPair.selfid);
		return sb.toString();
	}

	/**
	 * Manifestのuriに指定する値を取得.
	 * 親階層の末尾の`/`を`\ufffe`に変換。
	 * @param parentUri 親階層
	 * @return Manifestのuriに指定する値
	 */
	public static String getManifestParentUri(String parentUri) {
		StringBuilder sb = new StringBuilder();
		sb.append(TaggingEntryUtil.removeLastSlash(parentUri));
		sb.append(BDBConst.END_PARENT_URI_STRING);
		return sb.toString();
	}

	/**
	 * Entryバインディングを取得.
	 * @param mapper FeedTemplateMapper
	 * @param deflateUtil DeflateUtil
	 * @return Entryバインディング
	 */
	public static final EntryBaseBinding getEntryBinding(FeedTemplateMapper mapper,
			DeflateUtil deflateUtil) {
		return new EntryBaseBinding(mapper, deflateUtil);
	}

	/**
	 * Incrementバインディングを取得.
	 * @return Incrementバインディング
	 */
	public static final IncrementBinding getIncrementBinding() {
		return new IncrementBinding();
	}

	/**
	 * 文字列値バインディングを取得
	 * @return 文字列値バインディング
	 */
	public static final StringBinding getStringBinding() {
		return new StringBinding();
	}

	/**
	 * 数値バインディングを取得
	 * @return 数値バインディング
	 */
	public static final IntegerBinding getIntegerBinding() {
		return new IntegerBinding();
	}

	/**
	 * 数値バインディングを取得
	 * @return 数値バインディング
	 */
	public static final LongBinding getLongBinding() {
		return new LongBinding();
	}

	/**
	 * リストバインディングを取得
	 * @return リストバインディング
	 */
	public static final ListBinding getListBinding() {
		return new ListBinding();
	}

	/**
	 * バイト配列型バインディングを取得
	 * @return バイト配列型バインディング
	 */
	public static final ByteArrayBinding getByteArrayBinding() {
		return new ByteArrayBinding();
	}

	/**
	 * デフォルトのロックモードを取得
	 * @return デフォルトのロックモード
	 */
	public static final LockMode getLockMode() {
		return LockMode.DEFAULT;
	}

	/**
	 * 書き込み(排他)ロックモードを取得
	 * @return 書き込み(排他)ロックモード
	 */
	public static final LockMode getLockModeRMW() {
		return LockMode.RMW;
	}

	/**
	 * 例外を変換する.
	 * リトライ対象の場合例外をスローしない。
	 * @param e データストア例外
	 * @param requestInfo リクエスト情報
	 */
	public static void convertError(DatabaseException e, String key,
			RequestInfo requestInfo)
	throws IOException {
		if (isInputError(e)) {
			String msg = editErrorMsgWithKey(e, key);
			throw new IllegalParameterException(msg, e);
		}
		if (isRetryError(e, requestInfo)) {
			return;
		}

		convertIOError(e, key);
	}

	/**
	 * 入力エラー判定.
	 * @param e DatabaseException
	 * @return 入力エラーの場合true
	 */
	public static boolean isInputError(DatabaseException e) {
		return false;
	}

	/**
	 * リトライエラー判定.
	 * @param e DatabaseException
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(DatabaseException e, RequestInfo requestInfo) {
		if (e instanceof LockConflictException) {
			return true;
		}
		return false;
	}

	/**
	 * 致命的なエラー判定.
	 * @param e DatabaseException
	 * @return 致命的なエラーの場合true
	 */
	public static boolean isFailureError(DatabaseException e, RequestInfo requestInfo) {
		if (e instanceof EnvironmentFailureException) {
			return true;
		}
		return false;
	}

	/**
	 * DatabaseExceptionをIOExceptionに変換してスローする。
	 * @param e DatabaseException
	 * @param key エラーが発生したキー
	 */
	public static void convertIOError(DatabaseException e, String key)
	throws IOException {
		String msg = editErrorMsgWithKey(e, key);
		throw new IOException(msg, e);
	}

	/**
	 * スリープ処理.
	 * @param waitMillis スリープ時間(ミリ秒)
	 */
	public static void sleep(long waitMillis) {
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * BDBへのアクセスログを出力するかどうか.
	 * @return BDBへのアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return ReflexEnvUtil.getSystemPropBoolean(
				BDBEnvConst.BDB_ENABLE_ACCESSLOG, false);
	}

	/**
	 * 処理開始ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param bdbDb テーブル
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getStartLog(String serviceName, String method, BDBDatabase bdbDb, String key) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : svc=");
		sb.append(serviceName);
		sb.append(", table=");
		sb.append(bdbDb.getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		return sb.toString();
	}

	/**
	 * 処理開始ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param bdbDb テーブル
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getStartLog(String serviceName, String method, BDBSequence bdbSeq, String key) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : svc=");
		sb.append(serviceName);
		sb.append(", sequence=");
		sb.append(bdbSeq.getDatabase().getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		return sb.toString();
	}

	/**
	 * 処理終了ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param bdbDb テーブル
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getEndLog(String serviceName, String method, BDBDatabase bdbDb, String key,
			long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" end");
		sb.append(" : svc=");
		sb.append(serviceName);
		sb.append(", table=");
		sb.append(bdbDb.getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 処理終了ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param bdbSeq シーケンス
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getEndLog(String serviceName, String method, BDBSequence bdbSeq, String key,
			long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : svc=");
		sb.append(serviceName);
		sb.append(", sequence=");
		sb.append(bdbSeq.getDatabase().getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * エラーメッセージにエラー発生キーを追加
	 * @param e 例外
	 * @param key エラー発生キー
	 * @return 編集したメッセージ
	 */
	public static String editErrorMsgWithKey(Throwable e, String key) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getMessage());
		if (!StringUtils.isBlank(key)) {
			sb.append(" ");
			sb.append(key);
		}
		return sb.toString();
	}

	/**
	 * 処理開始ログを編集
	 * @param namespace 名前空間
	 * @param method メソッド
	 * @param bdbDb テーブル
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getStartLogNs(String namespace, String method, BDBDatabase bdbDb, String key) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : ns=");
		sb.append(namespace);
		sb.append(", table=");
		sb.append(bdbDb.getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		return sb.toString();
	}

	/**
	 * 処理開始ログを編集
	 * @param namespace 名前空間
	 * @param method メソッド
	 * @param bdbDb テーブル
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getStartLogNs(String namespace, String method, BDBSequence bdbSeq, String key) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : ns=");
		sb.append(namespace);
		sb.append(", sequence=");
		sb.append(bdbSeq.getDatabase().getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		return sb.toString();
	}

	/**
	 * 処理終了ログを編集
	 * @param namespace 名前空間
	 * @param method メソッド
	 * @param bdbDb テーブル
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getEndLogNs(String namespace, String method, BDBDatabase bdbDb, String key,
			long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : ns=");
		sb.append(namespace);
		sb.append(", table=");
		sb.append(bdbDb.getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 処理終了ログを編集
	 * @param namespace 名前空間
	 * @param method メソッド
	 * @param bdbSeq シーケンス
	 * @param key キー
	 * @return ログ文字列
	 */
	public static String getEndLogNs(String namespace, String method, BDBSequence bdbSeq, String key,
			long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(method);
		sb.append("]");
		sb.append(" start");
		sb.append(" : ns=");
		sb.append(namespace);
		sb.append(", sequence=");
		sb.append(bdbSeq.getDatabase().getDatabaseName());
		sb.append(", key=");
		sb.append(key);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * 参照用。指定された名前空間のBDB環境が存在しない時は新規作成せずエラーとする。
	 * @param dbNames テーブル名リスト
	 * @param namespace 名前空間
	 * @return BDB環境情報
	 */
	public static BDBEnv getBDBEnvByNamespace(List<String> dbNames, String namespace)
	throws IOException, TaggingException {
		return getBDBEnvByNamespace(dbNames, namespace, false);
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * @param dbNames テーブル名リスト
	 * @param namespace 名前空間
	 * @param isCreate 指定された名前空間のBDB環境が存在しない時作成する場合true
	 * @return BDB環境情報
	 */
	public static BDBEnv getBDBEnvByNamespace(List<String> dbNames, String namespace,
			boolean isCreate)
	throws IOException, TaggingException {
		boolean setAccesstime = false;	// TODO
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getBDBEnvByNamespace(dbNames,
				namespace, isCreate, setAccesstime);
	}

}
