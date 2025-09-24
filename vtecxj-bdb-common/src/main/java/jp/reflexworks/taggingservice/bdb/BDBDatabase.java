package jp.reflexworks.taggingservice.bdb;

import java.util.List;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseStats;
import com.sleepycat.je.DiskOrderedCursor;
import com.sleepycat.je.DiskOrderedCursorConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Get;
import com.sleepycat.je.JoinConfig;
import com.sleepycat.je.JoinCursor;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationResult;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.Put;
import com.sleepycat.je.ReadOptions;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.WriteOptions;

/**
 * データベース(テーブル)ラッパークラス.
 */
public class BDBDatabase {

	/** データベース */
	private Database database;
	
	/**
	 * コンストラクタ.
	 * @param database データベース
	 */
	BDBDatabase(Database database) {
		this.database = database;
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		database.close();
	}
	
	/**
	 * 登録更新
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data 値
	 * @return OperationStatus.SUCCESS.
	 */
	public OperationStatus put(BDBTransaction bdbTxn, DatabaseEntry key, DatabaseEntry data) {
		Transaction txn = getTransaction(bdbTxn);
		return database.put(txn, key, data);
	}
	
	/**
	 * 登録更新
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data 値
	 * @param putType put type
	 * @param options 設定
	 * @return the OperationResult if the record is written, else null.
	 */
	public OperationResult put(BDBTransaction bdbTxn, DatabaseEntry key, DatabaseEntry data,
			Put putType, WriteOptions options) {
		Transaction txn = getTransaction(bdbTxn);
		return database.put(txn, key, data, putType, options);
	}
	
	/**
	 * 登録.
	 * Stores the key/data pair into the database if it does not already appear in the database.
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data 値
	 * @return OperationStatus.KEYEXIST if the key/data pair already appears in the database, else OperationStatus.SUCCESS
	 */
	public OperationStatus putNoDupData(BDBTransaction bdbTxn, DatabaseEntry key, DatabaseEntry data) {
		Transaction txn = getTransaction(bdbTxn);
		return database.putNoDupData(txn, key, data);
	}
	
	/**
	 * 登録.
	 * Stores the key/data pair into the database if the key does not already appear in the database.
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data 値
	 * @return OperationStatus.KEYEXIST if the key already appears in the database, else OperationStatus.SUCCESS
	 */
	public OperationStatus putNoOverwrite(BDBTransaction bdbTxn, DatabaseEntry key, DatabaseEntry data) {
		Transaction txn = getTransaction(bdbTxn);
		return database.putNoOverwrite(txn, key, data);
	}

	/**
	 * 削除
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @return The method will return OperationStatus.NOTFOUND if the given key is not found in the database; otherwise OperationStatus.SUCCESS.
	 */
	public OperationStatus delete(BDBTransaction bdbTxn, DatabaseEntry key) {
		Transaction txn = getTransaction(bdbTxn);
		return database.delete(txn, key);
	}
	
	/**
	 * 削除
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param options オプション
	 * @return the OperationResult if the record is deleted, else null if the given key was not found in the database.
	 */
	public OperationResult delete(BDBTransaction bdbTxn, DatabaseEntry key,
			WriteOptions options) {
		Transaction txn = getTransaction(bdbTxn);
		return database.delete(txn, key, options);
	}
	
	/**
	 * 検索
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus get(BDBTransaction bdbTxn, DatabaseEntry key,
			DatabaseEntry data, LockMode lockMode) {
		Transaction txn = getTransaction(bdbTxn);
		return database.get(txn, key, data, lockMode);
	}
	
	/**
	 * 検索
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return the OperationResult if the record requested is found, else null.
	 */
	public OperationResult get(BDBTransaction bdbTxn, DatabaseEntry key,
			DatabaseEntry data, Get getType, ReadOptions options) {
		Transaction txn = getTransaction(bdbTxn);
		return database.get(txn, key, data, getType, options);
	}

	/**
	 * 検索.
	 * Retrieves the key/data pair with the given key and data value, that is, both the key and data items must match.
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getSearchBoth(BDBTransaction bdbTxn, DatabaseEntry key,
			DatabaseEntry data, LockMode lockMode) {
		Transaction txn = getTransaction(bdbTxn);
		return database.getSearchBoth(txn, key, data, lockMode);
	}
	
	/**
	 * カーソルを開く
	 * @param cursorConfig カーソル設定
	 * @return カーソル
	 */
	public DiskOrderedCursor openCursor(DiskOrderedCursorConfig cursorConfig) {
		return database.openCursor(cursorConfig);
	}
	
	/**
	 * カーソルを開く
	 * @param bdbTxn トランザクション
	 * @param cursorConfig カーソル設定
	 * @return カーソル
	 */
	public BDBCursor openCursor(BDBTransaction bdbTxn, CursorConfig cursorConfig) {
		Transaction txn = getTransaction(bdbTxn);
		return new BDBCursor(database.openCursor(txn, cursorConfig));
	}
	
	/**
	 * シーケンスを開く
	 * @param bdbTxn トランザクション
	 * @param key キー
	 * @param config シーケンス設定
	 * @return シーケンス
	 */
	public BDBSequence openSequence(BDBTransaction bdbTxn, DatabaseEntry key, SequenceConfig config) {
		Transaction txn = getTransaction(bdbTxn);
		return new BDBSequence(database.openSequence(txn, key, config));
	}
	
	/**
	 * シーケンス削除
	 * @param bdbTxn トランザクション
	 * @param key キー
	 */
	public void removeSequence(BDBTransaction bdbTxn, DatabaseEntry key) {
		Transaction txn = getTransaction(bdbTxn);
		database.removeSequence(txn, key);
	}

	/**
	 * 設定取得
	 * @return 設定
	 */
	public DatabaseConfig getConfig() {
		return database.getConfig();
	}
	
	/**
	 * データベース名取得
	 * @return データベース名
	 */
	public String getDatabaseName() {
		return database.getDatabaseName();
	}
	
	/**
	 * 環境取得
	 * @return 環境
	 */
	public Environment getEnvironment() {
		return database.getEnvironment();
	}
	
	/**
	 * データベース取得.
	 * @return データベース
	 */
	public Database getDatabase() {
		return database;
	}

	/**
	 * セカンダリインデックス取得.
	 * @return セカンダリインデックスリスト
	 */
	public List<SecondaryDatabase> getSecondaryDatabases() {
		return database.getSecondaryDatabases();
	}
	
	/**
	 * カウント
	 * @return The count of key/data pairs in the database.
	 */
	public long count() {
		return database.count();
	}
	
	/**
	 * カウント
	 * @param memoryLimit 制限
	 * @return The count of key/data pairs in the database.
	 */
	public long count(long memoryLimit) {
		return database.count(memoryLimit);
	}
	
	/**
	 * 比較
	 * @param entry1 DatabaseEntry
	 * @param entry2 DatabaseEntry
	 * @return 1 if entry1 compares less than entry2, 0 if entry1 compares equal to entry2, 1 if entry1 compares greater than entry2
	 */
	public int compareDuplicates(DatabaseEntry entry1, DatabaseEntry entry2) {
		return database.compareDuplicates(entry1, entry2);
	}
	
	/**
	 * キー比較
	 * @param entry1 DatabaseEntry
	 * @param entry2 DatabaseEntry
	 * @return if entry1 compares less than entry2, 0 if entry1 compares equal to entry2, 1 if entry1 compares greater than entry2
	 */
	public int compareKeys(DatabaseEntry entry1, DatabaseEntry entry2) {
		return database.compareKeys(entry1, entry2);
	}

	/**
	 * プレロードキャッシュ.
	 * @param config 設定
	 * @return A PreloadStats object with various statistics about the preload() operation.
	 */
	public PreloadStats preload(PreloadConfig config) {
		return database.preload(config);
	}
	
	/**
	 * Verifies the integrity of the database.
	 * @param config 設定
	 * @return Database statistics.
	 */
	public DatabaseStats verify(VerifyConfig config) {
		return database.verify(config);
	}
	
	/**
	 * Bツリーのノードカウントを取得.
	 * @param config 設定
	 * @return the DatabaseStats object, which is currently always a BtreeStats object.
	 */
	public DatabaseStats getStats(StatsConfig config) {
		return database.getStats(config);
	}
	
	/**
	 * Creates a specialized join cursor for use in performing equality or natural joins on secondary indices.
	 * @param cursors カーソル
	 * @param config 設定
	 * @return a specialized cursor that returns the results of the equality join operation.
	 */
	public JoinCursor join(Cursor[] cursors, JoinConfig config) {
		return database.join(cursors, config);
	}

	/**
	 * トランザクションオブジェクトを取得.
	 * @param bdbTxn トランザクションラッパー
	 * @return トランザクション
	 */
	protected Transaction getTransaction(BDBTransaction bdbTxn) {
		if (bdbTxn != null) {
			return bdbTxn.getTransaction();
		}
		return null;
	}
}
