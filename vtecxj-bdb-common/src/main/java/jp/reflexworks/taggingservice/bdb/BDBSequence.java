package jp.reflexworks.taggingservice.bdb;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;

/**
 * シーケンスラッパークラス.
 */
public class BDBSequence {
	
	/** シーケンス */
	private Sequence sequence;
	
	/**
	 * コンストラクタ.
	 * @param sequence シーケンス
	 */
	BDBSequence(Sequence sequence) {
		this.sequence = sequence;
	}
	
	/**
	 * クローズ
	 */
	public void close() {
		sequence.close();
	}
	
	/**
	 * このシーケンスオブジェクトを取得.
	 * @return シーケンス
	 */
	public Sequence getSequence() {
		return sequence;
	}
	
	/**
	 * シーケンス値を取得.
	 * @param bdbTxn トランザクション
	 * @param delta 加算値
	 * @return シーケンス値
	 */
	public long get(BDBTransaction bdbTxn, int delta) {
		Transaction txn = null;
		if (bdbTxn != null) {
			txn = bdbTxn.getTransaction();
		}
		return sequence.get(txn, delta);
	}

	/**
	 * このシーケンスのデータベースを取得
	 * @return データベース
	 */
	public Database getDatabase() {
		return sequence.getDatabase();
	}

	/**
	 * このシーケンスのキーを取得.
	 * @return キー
	 */
	public DatabaseEntry getKey() {
		return sequence.getKey();
	}

	/**
	 * SequenceStatsを取得
	 * @param config 設定
	 * @return Sequence statistics.
	 */
	public SequenceStats getStats(StatsConfig config) {
		return sequence.getStats(config);
	}

}
