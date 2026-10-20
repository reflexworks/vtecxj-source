package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.FullTextIndexPutCallable;
import jp.reflexworks.taggingservice.index.InnerIndexPutCallable;
import jp.reflexworks.taggingservice.model.UpdatedInfo;

/**
 * フォルダ削除時のインデックス・全文検索インデックス更新をまとめて送信するバッファ.
 * <p>
 * フォルダ削除はEntryを1件ずつ更新するため、Entryごとにインデックスサーバへリクエストすると
 * リクエスト数が膨大になる。更新情報を一定件数ためてからまとめて送信する。
 * インデックスサーバは版(updated+リビジョン)を判定するため、送信が遅れたり順序が入れ替わっても
 * 古い版で上書きされることはない。
 * </p>
 * <p>
 * 複数スレッドから利用されるためスレッドセーフ。
 * フォルダ削除の終了時(エラー時を含む)に必ず {@link #flush()} を呼び出すこと。
 * flush後に追加された更新情報は即時送信する。
 * </p>
 */
public class DeleteFolderIndexBuffer {

	/** まとめて送信する更新情報の件数 */
	static final int BATCH_SIZE = 100;

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos = new ArrayList<>();
	/**
	 * flush済みの場合true.
	 * エラー終了時に並列削除スレッドがまだ動いていることがあるため、flush後に追加されたものは即時送信する。
	 */
	private boolean isFlushed;
	/** 認証情報 */
	private ReflexAuthentication auth;
	/** リクエスト情報 */
	private RequestInfo requestInfo;
	/** 共有部分のみのコネクション情報 */
	private ConnectionInfo sharingConnectionInfo;

	/**
	 * コンストラクタ
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public DeleteFolderIndexBuffer(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		this.auth = auth;
		this.requestInfo = requestInfo;
		this.sharingConnectionInfo = BDBClientUtil.copySharingConnectionInfo(
				requestInfo, connectionInfo);
	}

	/**
	 * 更新情報を追加.
	 * 一定件数たまった場合は送信する。
	 * @param tmpUpdatedInfos 更新情報リスト
	 */
	public void add(List<UpdatedInfo> tmpUpdatedInfos)
	throws IOException, TaggingException {
		if (tmpUpdatedInfos == null || tmpUpdatedInfos.isEmpty()) {
			return;
		}
		List<UpdatedInfo> sendInfos = null;
		synchronized (this) {
			updatedInfos.addAll(tmpUpdatedInfos);
			if (isFlushed || updatedInfos.size() >= BATCH_SIZE) {
				sendInfos = updatedInfos;
				updatedInfos = new ArrayList<>();
			}
		}
		send(sendInfos);
	}

	/**
	 * たまっている更新情報を送信.
	 */
	public void flush()
	throws IOException, TaggingException {
		List<UpdatedInfo> sendInfos = null;
		synchronized (this) {
			isFlushed = true;
			if (!updatedInfos.isEmpty()) {
				sendInfos = updatedInfos;
				updatedInfos = new ArrayList<>();
			}
		}
		send(sendInfos);
	}

	/**
	 * インデックス・全文検索インデックス更新の非同期処理を登録.
	 * @param sendInfos 更新情報リスト
	 */
	private void send(List<UpdatedInfo> sendInfos)
	throws IOException, TaggingException {
		if (sendInfos == null || sendInfos.isEmpty()) {
			return;
		}
		// インデックス登録更新スレッド実行
		InnerIndexPutCallable innerIndexPutCallable = new InnerIndexPutCallable(sendInfos);
		innerIndexPutCallable.addTask(auth, requestInfo, sharingConnectionInfo);

		// 全文検索インデックス登録更新スレッド実行
		FullTextIndexPutCallable fullTextIndexPutCallable = new FullTextIndexPutCallable(sendInfos);
		fullTextIndexPutCallable.addTask(auth, requestInfo, sharingConnectionInfo);
	}

}
