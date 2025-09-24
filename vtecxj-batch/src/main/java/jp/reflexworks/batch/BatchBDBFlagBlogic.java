package jp.reflexworks.batch;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.MetadataUtil;

/**
 * 夜間バッチ実行フラグON・OFF.
 *  ・アクセスカウンタをRedisからデータストアに移動する。
 *  ・ストレージ容量をRedisに登録する。
 */
public class BatchBDBFlagBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]start または end
	 *             [1]URI
	 *             [2]有効期限(秒)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// 引数チェック
		if (args == null) {
			throw new IllegalStateException("引数がnullです。");
		}
		if (args.length < 2) {
			throw new IllegalStateException("引数が不足しています。[0]フラグ(start または end)、[1]URI");
		}
		String flg = args[0];
		String uri = args[1];
		if (!BatchBDBConst.BATCH_BDB_FLAG_START.equals(flg) &&
				!BatchBDBConst.BATCH_BDB_FLAG_END.equals(flg)) {
			throw new IllegalStateException("引数[0]には start または end を指定してください。");
		}

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		SystemContext systemContext = new SystemContext(systemService,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		try {
			if (BatchBDBConst.BATCH_BDB_FLAG_START.equals(flg)) {
				// start
				String text = MetadataUtil.getCurrentTime(BatchBDBConst.TIMEZONE_ID);
				int sec = TaggingEnvUtil.getSystemPropInt(
							BatchBDBConst.BATCH_BDB_EXPIRE_SEC,
							BatchBDBConst.BATCH_BDB_EXPIRE_SEC_DEFAULT);
				systemContext.setCacheString(uri, text, sec);
				logger.info("BDB batch flag -- ON");
			} else {
				// end
				systemContext.deleteCacheString(uri);
				logger.info("BDB batch flag -- OFF");
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

}
