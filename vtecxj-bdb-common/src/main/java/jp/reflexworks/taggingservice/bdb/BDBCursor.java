package jp.reflexworks.taggingservice.bdb;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationResult;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Put;
import com.sleepycat.je.ReadOptions;
import com.sleepycat.je.WriteOptions;

/**
 * カーソルラッパークラス.
 */
public class BDBCursor {
	
	/** カーソル */
	private Cursor cursor;
	
	/**
	 * コンストラクタ.
	 * @param cursor カーソル
	 */
	BDBCursor(Cursor cursor) {
		this.cursor = cursor;
	}
	
	/**
	 * クローズ
	 */
	public void close() {
		cursor.close();
	}

	/**
	 * このカーソルを取得.
	 * @return カーソル
	 */
	public Cursor getCursor() {
		return cursor;
	}
	
	/**
	 * このカーソルのデータベースを取得
	 * @return データベース
	 */
	public Database getDatabase() {
		return cursor.getDatabase();
	}

	/**
	 * Moves the cursor to a record according to the specified Get type.
	 * @param key キー
	 * @param data データ
	 * @param getType 取得タイプ
	 * @param options オプション
	 * @return the OperationResult if the record requested is found, else null.
	 */
	public OperationResult get(DatabaseEntry key, DatabaseEntry data, Get getType,
			ReadOptions options) {
		return cursor.get(key, data, getType, options);
	}

	/**
	 * CacheModeを取得
	 * @return CacheMode
	 */
	public CacheMode getCacheMode() {
		return cursor.getCacheMode();
	}

	/**
	 * CursorConfigを取得
	 * @return CursorConfig
	 */
	public CursorConfig getConfig() {
		return cursor.getConfig();
	}

	/**
	 * Returns the key/data pair to which the cursor refers.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.KEYEMPTY if the key/pair at the cursor position has been deleted; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getCurrent(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getCurrent(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the first key/data pair of the database, and returns that pair. If the first key has duplicate values, the first data item in the set of duplicates is returned.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getFirst(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getFirst(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the last key/data pair of the database, and returns that pair. If the last key has duplicate values, the last data item in the set of duplicates is returned.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getLast(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getLast(key, data, lockMode);
	}

	/**
	 * 次のデータを読む。
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getNext(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getNext(key, data, lockMode);
	}

	/**
	 * If the next key/data pair of the database is a duplicate data record for the current key/data pair, moves the cursor to the next key/data pair of the database and returns that pair.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getNextDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getNextDup(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the next non-duplicate key/data pair and returns that pair. If the matching key has duplicate values, the first data item in the set of duplicates is returned.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getNextNoDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getNextNoDup(key, data, lockMode);
	}

	/**
	 * カーソルを１つ前に戻してデータ取得.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getPrev(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getPrev(key, data, lockMode);
	}

	/**
	 * If the previous key/data pair of the database is a duplicate data record for the current key/data pair, moves the cursor to the previous key/data pair of the database and returns that pair.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getPrevDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getPrevDup(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the previous non-duplicate key/data pair and returns that pair. If the matching key has duplicate values, the last data item in the set of duplicates is returned.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getPrevNoDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getPrevNoDup(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the specified key/data pair, where both the key and data items must match.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getSearchBoth(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getSearchBoth(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the specified key and closest matching data item of the database.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getSearchBothRange(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getSearchBothRange(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the given key of the database, and returns the datum associated with the given key. If the matching key has duplicate values, the first data item in the set of duplicates is returned.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getSearchKey(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getSearchKey(key, data, lockMode);
	}

	/**
	 * Moves the cursor to the closest matching key of the database, and returns the data item associated with the matching key. If the matching key has duplicate values, the first data item in the set of duplicates is returned.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.NOTFOUND if no matching key/data pair is found; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus getSearchKeyRange(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.getSearchKeyRange(key, data, lockMode);
	}

	/**
	 * データベースにキーと値を登録する.
	 * @param key キー
	 * @param data データ
	 * @return OperationStatus.SUCCESS.
	 */
	public OperationStatus put(DatabaseEntry key, DatabaseEntry data) {
		return cursor.put(key, data);
	}

	/**
	 * 更新
	 * @param key キー
	 * @param data データ
	 * @param putType 更新タイp
	 * @param options オプション
	 * @return the OperationResult if the record is written, else null.
	 */
	public OperationResult put(DatabaseEntry key, DatabaseEntry data, Put putType, WriteOptions options) {
		return cursor.put(key, data, putType, options);
	}

	/**
	 * 現在のカーソル位置のデータを置き換える。
	 * @param data データ
	 * @return OperationStatus.KEYEMPTY if the key/pair at the cursor position has been deleted; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus putCurrent(DatabaseEntry data) {
		return cursor.putCurrent(data);
	}

	/**
	 * Stores a key/data pair into the database. The database must be configured for duplicates.
	 * @param key キー
	 * @param data データ
	 * @return OperationStatus.KEYEXIST if the key/data pair already appears in the database, else OperationStatus.SUCCESS
	 */
	public OperationStatus putNoDupData(DatabaseEntry key, DatabaseEntry data) {
		return cursor.putNoDupData(key, data);
	}

	/**
	 * Stores a key/data pair into the database.
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return OperationStatus.KEYEXIST if the key already appears in the database, else OperationStatus.SUCCESS
	 */
	public OperationStatus putNoOverwrite(DatabaseEntry key, DatabaseEntry data) {
		return cursor.putNoOverwrite(key, data);
	}

	/**
	 * カウント取得
	 * @return A count of the number of data items for the key to which the cursor refers.
	 */
	public long count() {
		return cursor.count();
	}
	
	/**
	 * カーソルが参照するキーのデータ項目数の概算を返却.
	 * @return an estimate of the count of the number of data items for the key to which the cursor refers.
	 */
	public long countEstimate() {
		return cursor.countEstimate();
	}

	/**
	 * 削除.
	 * @return OperationStatus.KEYEMPTY if the record at the cursor position has already been deleted; otherwise, OperationStatus.SUCCESS.
	 */
	public OperationStatus delete() {
		return cursor.delete();
	}

	/**
	 * 削除.
	 * @param options オプション
	 * @return the OperationResult if the record is deleted, else null if the record at the cursor position has already been deleted.
	 */
	public OperationResult delete(WriteOptions options) {
		return cursor.delete(options);
	}

	/**
	 * Returns a new cursor with the same transaction and locker ID as the original cursor.
	 * @param samePosition If true, the newly created cursor is initialized to refer to the same position in the database as the original cursor (if any) and hold the same locks (if any). If false, or the original cursor does not hold a database position and locks, the returned cursor is uninitialized and will behave like a newly created cursor.
	 * @return A new cursor with the same transaction and locker ID as the original cursor.
	 */
	public Cursor dup(boolean samePosition) {
		return cursor.dup(samePosition);
	}

	/**
	 * Skips forward a given number of key/data pairs and returns the number by which the cursor is moved.
	 * @param maxCount 最大カウント
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return the number of key/data pairs skipped, i.e., the number by which the cursor has moved; if zero is returned, the cursor position is unchanged and the key/data pair is not returned.
	 */
	public long skipNext(long maxCount, DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.skipNext(maxCount, key, data, lockMode);
	}

	/**
	 * Skips backward a given number of key/data pairs and returns the number by which the cursor is moved.
	 * @param maxCount 最大カウント
	 * @param key キー
	 * @param data データ
	 * @param lockMode ロックモード
	 * @return the number of key/data pairs skipped, i.e., the number by which the cursor has moved; if zero is returned, the cursor position is unchanged and the key/data pair is not returned.
	 */
	public long skipPrev(long maxCount, DatabaseEntry key, DatabaseEntry data, LockMode lockMode) {
		return cursor.skipPrev(maxCount, key, data, lockMode);
	}


	/**
	 * キャッシュモードを設定.
	 * @param cacheMode キャッシュモード
	 */
	public void setCacheMode(CacheMode cacheMode) {
		cursor.setCacheMode(cacheMode);
	}

}
