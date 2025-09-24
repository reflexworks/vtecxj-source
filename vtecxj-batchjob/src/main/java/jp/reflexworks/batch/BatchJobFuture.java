package jp.reflexworks.batch;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexContext;

/**
 * バッチジョブのFuture格納クラス.
 */
public class BatchJobFuture {

	/** サーバサイドJS名 */
	private String jsFunction;
	/** バッチジョブ管理エントリー */
	private EntryBase batchJobTimeEntry;
	/** バッチジョブのFuture */
	private Future<Boolean> future;
	/** 現在時刻 */
	private Date now;
	/** 現在時刻からの実行開始遅延時間(ミリ秒) */
	private int delay;
	/** 非同期実行処理呼び出し時刻 */
	private Date submitTime;
	/** サービス管理者のReflexContext */
	private ReflexContext reflexContext;

	/**
	 * コンストラクタ
	 * @param jsFunction サーバサイドJS名
	 * @param future バッチジョブのFuture
	 * @param now 現在時刻
	 * @param delay 現在時刻からの実行開始遅延時間(ミリ秒)
	 * @param reflexContext サービス管理者のReflexContext
	 */
	public BatchJobFuture(String jsFunction, EntryBase batchJobTimeEntry,
			Future<Boolean> future, Date now, int delay, ReflexContext reflexContext) {
		this.jsFunction = jsFunction;
		this.batchJobTimeEntry = batchJobTimeEntry;
		this.future = future;
		this.now = now;
		this.delay = delay;
		this.submitTime = new Date();
		this.reflexContext = reflexContext;
	}

	/**
	 * サーバサイドJS名を取得.
	 * @return サーバサイドJS名
	 */
	public String getJsFunction() {
		return jsFunction;
	}

	/**
	 * バッチジョブ管理エントリーを取得.
	 * @return バッチジョブ管理エントリー
	 */
	public EntryBase getBatchJobTimeEntry() {
		return batchJobTimeEntry;
	}

	/**
	 * バッチジョブのFutureを取得.
	 * @return バッチジョブのFuture
	 */
	public Future<Boolean> getFuture() {
		return future;
	}

	/**
	 * 現在時刻を取得.
	 * @return 現在時刻
	 */
	public Date getNow() {
		return now;
	}

	/**
	 * 現在時刻からの実行開始遅延時間(ミリ秒)を取得
	 * @return 現在時刻からの実行開始遅延時間(ミリ秒)
	 */
	public int getDelay() {
		return delay;
	}

	/**
	 * 非同期実行処理呼び出し時刻を取得.
	 * @return 非同期実行処理呼び出し時刻
	 */
	public Date getSubmitTime() {
		return submitTime;
	}

	/**
	 * サービス管理者のReflexContextを取得.
	 * @return サービス管理者のReflexContext
	 */
	public ReflexContext getReflexContext() {
		return reflexContext;
	}

	/**
	 * 必要に応じて計算が完了するまで待機し、その後、計算結果を取得します。
	 * @return 計算結果
	 */
	public Object get()
	throws InterruptedException, ExecutionException {
		return future.get();
	}

	/**
	 * 必要に応じて計算が完了するまで待機し、その後、計算結果を取得します。
	 * @param timeout タイムアウト
	 * @param unit タイムアウトの時間単位
	 * @return 計算結果
	 */
	public Object get(long timeout, TimeUnit unit)
	throws InterruptedException, ExecutionException, TimeoutException {
		return future.get(timeout, unit);
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return reflexContext.getServiceName();
	}

	/**
	 * ジョブ実行ステータスを取得.
	 * バッチジョブエントリーのtitleに格納されているジョブ実行ステータスを返却する。
	 * @return ジョブ実行ステータス
	 */
	public String getBatchJobStatus() {
		if (batchJobTimeEntry != null) {
			return batchJobTimeEntry.title;
		}
		return null;
	}

	/**
	 * ジョブの取り消し.
	 * @param mayInterruptIfRunning このタスクを実行しているスレッドに割り込む必要がある場合はtrue、
	 *                              そうでない場合は、実行中のタスクを完了できる
	 * @return タスクを取り消せなかった場合はfalse
	 *         (通常はタスクがすでに正常に完了していたため)、そうでない場合はtrue
	 */
	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	/**
	 * このクラスの文字列表現を取得.
	 * @return このクラスの文字列表現
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("serviceName=");
		sb.append(reflexContext.getServiceName());
		sb.append(", jsFunction=");
		sb.append(jsFunction);
		return sb.toString();
	}

}
