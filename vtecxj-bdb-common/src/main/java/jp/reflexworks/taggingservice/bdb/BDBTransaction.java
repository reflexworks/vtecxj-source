package jp.reflexworks.taggingservice.bdb;

import java.util.concurrent.TimeUnit;

import com.sleepycat.je.CommitToken;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.Transaction.State;

/**
 * トランザクションラッパークラス.
 */
public class BDBTransaction {
	
	/** トランザクション */
	private Transaction txn;
	
	/**
	 * コンストラクタ.
	 * @param txn トランザクション
	 */
	public BDBTransaction(Transaction txn) {
		this.txn = txn;
	}
	
	/**
	 * トランザクションを取得.
	 * @return トランザクション
	 */
	public Transaction getTransaction() {
		return txn;
	}
	
	/**
	 * コミット.
	 */
	public void commit() {
		txn.commit();
	}
	
	/**
	 * コミット.
	 * @param durability Durability
	 */
	public void commit(Durability durability) {
		txn.commit(durability);
	}
	
	/**
	 * コミット.
	 */
	public void commitSync() {
		txn.commitSync();
	}
	
	/**
	 * コミット.
	 */
	public void commitNoSync() {
		txn.commitNoSync();
	}
	
	/**
	 * コミット.
	 */
	public void commitWriteNoSync() {
		txn.commitWriteNoSync();
	}

	/**
	 * アボート.
	 */
	public void abort() {
		txn.abort();
	}
	
	/**
	 * トランザクションが有効かどうかを取得.
	 * 注) トランザクションが生きていてもfalseを返す場合があるので使用しない。(DeadlockExceptionが発生してしまう。)
	 * @return トランザクションが有効かどうか
	 */
	public boolean isValid() {
		return txn.isValid();
	}

	/**
	 * トランザクションIDを取得
	 * @return トランザクションID
	 */
	public long getId() {
		return txn.getId();
	}
	
	/**
	 * トランザクション名を取得
	 * @return トランザクション名
	 */
	public String getName() {
		return txn.getName();
	}
	
	/**
	 * コミットトークンを取得
	 * @return コミットトークン
	 */
	public CommitToken getCommitToken() {
		return txn.getCommitToken();
	}
	
	/**
	 * ロックタイムアウトを取得
	 * @param unit タイムユニット
	 * @return ロックタイムアウト
	 */
	public long getLockTimeout(TimeUnit unit) {
		return txn.getLockTimeout(unit);
	}
	
	/**
	 * トランザクションタイムアウトを取得
	 * @param unit タイムユニット
	 * @return トランザクションタイムアウト
	 */
	public long getTxnTimeout(TimeUnit unit) {
		return txn.getTxnTimeout(unit);
	}
	
	/**
	 * トランザクションの状態を取得
	 * @return トランザクションの状態
	 */
	public State getState() {
		return txn.getState();
	}

	/**
	 * ロックタイムアウト指定.
	 * @param timeOut ロックタイムアウト
	 * @param unit タイムユニット
	 */
	public void setLockTimeout(long timeOut, TimeUnit unit) {
		txn.setLockTimeout(timeOut, unit);
	}

	/**
	 * トランザクションタイムアウト指定.
	 * @param timeOut トランザクションタイムアウト
	 * @param unit タイムユニット
	 */
	public void setTxnTimeout(long timeOut, TimeUnit unit) {
		txn.setTxnTimeout(timeOut, unit);
	}

}
