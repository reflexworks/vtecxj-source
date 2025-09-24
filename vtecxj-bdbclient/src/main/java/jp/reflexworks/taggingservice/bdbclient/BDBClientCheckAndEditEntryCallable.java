package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * Entry更新のためのチェック・編集処理.
 */
public class BDBClientCheckAndEditEntryCallable extends ReflexCallable<UpdatedInfo> {

	/** Entry */
	private EntryBase entry;
	/** 更新区分 */
	private OperationType flg;
	/** 現在のEntry */
	private EntryBase currentEntry;
	/** 現在時刻 */
	private String currentTime;
	/** 同一トランザクションで更新予定のEntryリスト */
	private List<EntryBase> entries;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param entry Entry
	 * @param flg 更新区分
	 * @param currentEntry 現在のEntry
	 * @param currentTime 現在時刻
	 * @param entries 同一トランザクションで更新予定のEntryリスト
	 */
	public BDBClientCheckAndEditEntryCallable(EntryBase entry, OperationType flg,
			EntryBase currentEntry, String currentTime, List<EntryBase> entries) {
		this.entry = entry;
		this.flg = flg;
		this.currentEntry = currentEntry;
		this.currentTime = currentTime;
		this.entries = entries;
	}

	/**
	 * Entry更新のためのチェック・編集処理.
	 * @return 更新対象Entry
	 */
	@Override
	public UpdatedInfo call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[check and edit entry before update call] start.");
		}

		try {
			BDBClientUpdateManager updateManager = new BDBClientUpdateManager();
			return updateManager.checkAndEditEntry(entry, flg, currentEntry, currentTime, entries,
					getAuth(), requestInfo, getConnectionInfo());

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[check and edit entry before update call] end.");
			}
		}
	}

}
