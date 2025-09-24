package jp.reflexworks.taggingservice.plugin.def;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.util.MessageConst;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * メッセージ管理クラス.
 */
public class MessageManagerDefault implements MessageManager {

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * 更新のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPut(String serviceName) {
		return MessageConst.MSG_PUT;
	}

	/**
	 * 削除のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDelete(String serviceName) {
		return MessageConst.MSG_DELETE;
	}

	/**
	 * 受付完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgAccept(String serviceName) {
		return MessageConst.MSG_ACCEPT;
	}

	/**
	 * フォルダ削除時のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteFolder(String serviceName) {
		return MessageConst.MSG_DELETE_FOLDER;
	}

	/**
	 * フォルダクリア時のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgClearFolder(String serviceName) {
		return MessageConst.MSG_CLEAR_FOLDER;
	}

	/**
	 * 加算範囲指定完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgRangeids(String serviceName) {
		return MessageConst.MSG_RANGEIDS;
	}

	/**
	 * 加算範囲の値指定完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgSetids(String serviceName) {
		return MessageConst.MSG_SETIDS;
	}

	/**
	 * 署名検証OKのメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgValidSignature(String serviceName) {
		return MessageConst.MSG_VALID_SIGNATURE;
	}

	/**
	 * 署名完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutSignature(String serviceName) {
		return MessageConst.MSG_PUT_SIGNATURE;
	}

	/**
	 * 複数署名完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutSignatures(String serviceName) {
		return MessageConst.MSG_PUT_SIGNATURES;
	}

	/**
	 * 署名削除のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteSignature(String serviceName) {
		return MessageConst.MSG_DELETE_SIGNATURE;
	}

	/**
	 * ユーザ無効のメッセージを取得.
	 * @param user 処理対象ユーザ
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgRevokeUser(String user, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_REVOKED);
		sb.append(" ");
		sb.append(user);
		return sb.toString();
	}

	/**
	 * ユーザ有効のメッセージを取得.
	 * @param user 処理対象ユーザ
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgActivateUser(String user, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_ACTIVATED);
		sb.append(" ");
		sb.append(user);
		return sb.toString();
	}

	/**
	 * メール送信完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgSendMail(String serviceName) {
		return MessageConst.MSG_SENDEMAIL;
	}

	/**
	 * サービス登録完了のメッセージを取得.
	 * @param newServiceName 新サービス名
	 * @param systemService サービス名
	 * @return メッセージ
	 */
	public String getMsgCreateservice(String newServiceName, String systemService) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_SERVICE_CREATED);
		sb.append(" ");
		sb.append(newServiceName);
		return sb.toString();
	}

	/**
	 * サービス削除完了のメッセージを取得.
	 * @param delServiceName 削除サービス名
	 * @param systemService システム管理サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteservice(String delServiceName, String systemService) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_SERVICE_DELETED);
		//sb.append(MessageConst.MSG_DELETESERVICE_ACCEPTED);	// サービス削除を同期処理に変更
		sb.append(" ");
		sb.append(delServiceName);
		return sb.toString();
	}

	/**
	 * サービスリセット完了のメッセージを取得.
	 * @param resetServiceName リセットサービス名
	 * @param systemService システム管理サービス名
	 * @return メッセージ
	 */
	public String getMsgResetservice(String resetServiceName, String systemService) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_SERVICE_RESET);
		sb.append(" ");
		sb.append(resetServiceName);
		return sb.toString();
	}

	/**
	 * ログイン完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgLogin(String serviceName) {
		return MessageConst.MSG_LOGIN;
	}

	/**
	 * ログアウト完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgLogout(String serviceName) {
		return MessageConst.MSG_LOGOUT;
	}

	/**
	 * 管理者によるユーザ登録のメッセージを取得.
	 * @param feed ユーザトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgAdduserByAdmin(FeedBase feed, String serviceName) {
		// メッセージはUID
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		if (feed != null && feed.entry != null) {
			UserManager userManager = TaggingEnvUtil.getUserManager();
			for (EntryBase entry : feed.entry) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				String uid = userManager.getUidByUri(entry.getMyUri());
				sb.append(uid);
			}
		}
		return sb.toString();
	}

	/**
	 * ユーザ登録のメッセージを取得.
	 * 仮登録完了
	 * @param entry ユーザトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgAdduser(EntryBase entry, String serviceName) {
		return MessageConst.MSG_SENDEMAIL;
	}

	/**
	 * パスワードリセットのメッセージを取得.
	 * パスワードリセットのためのメール送信完了
	 * @param entry ユーザトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPassreset(EntryBase entry, String serviceName) {
		return MessageConst.MSG_SENDEMAIL;
	}

	/**
	 * パスワード変更完了のメッセージを取得.
	 * @param entry ユーザトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangepass(EntryBase entry, String serviceName) {
		return MessageConst.MSG_CHANGEPASS;
	}

	/**
	 * 管理者によるパスワード変更完了のメッセージを取得.
	 * @param feed パスワード変更情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangepassByAdmin(FeedBase feed, String serviceName) {
		return MessageConst.MSG_CHANGEPASS_BYADMIN;
	}

	/**
	 * アカウント変更のためのメール送信のメッセージを取得.
	 * アカウント変更のためのメール送信完了
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangeaccount(String serviceName) {
		return MessageConst.MSG_SENDEMAIL;
	}

	/**
	 * アカウント変更完了のメッセージを取得.
	 * アカウント変更完了
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangeaccountVerify(String serviceName) {
		return MessageConst.MSG_CHANGEACCOUNT_VERIFY;
	}

	/**
	 * ユーザ退会完了のメッセージを取得.
	 * @param auth 認証情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgCanceluser(ReflexAuthentication auth, String serviceName) {
		return MessageConst.MSG_CANCELUSER;
	}

	/**
	 * アクセスキー変更完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangeAccesskey(ReflexAuthentication auth, String serviceName) {
		return MessageConst.MSG_CHANGE_ACCESSKEY;
	}

	/**
	 * コンテンツ登録完了のメッセージを取得.
	 * @param feed コンテンツFeed
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutContent(FeedBase feed, String serviceName) {
		String msg = null;
		if (feed == null || feed.entry == null || feed.entry.isEmpty()) {
			msg = MessageConst.MSG_NOT_MODIFIED;
		} else {
			msg = MessageConst.MSG_PUT_CONTENT;
		}
		return msg;
	}

	/**
	 * コンテンツ削除完了のメッセージを取得.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteContent(String uri, String serviceName) {
		return MessageConst.MSG_DELETE_CONTENT;
	}

	/**
	 * キャッシュ登録完了のメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutCache(String uri, String serviceName) {
		return MessageConst.MSG_PUT_CACHE;
	}

	/**
	 * キャッシュの有効時間設定完了のメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutCacheExpire(String uri, String serviceName) {
		return MessageConst.MSG_PUT_CACHE_EXPIRE;
	}

	/**
	 * キャッシュ削除完了のメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteCache(String uri, String serviceName) {
		return MessageConst.MSG_DELETE_CACHE;
	}

	/**
	 * キャッシュデータが存在しないメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgNotExistCache(String uri, String serviceName) {
		return MessageConst.MSG_NOT_EXIST_CACHE;
	}

	/**
	 * ユーザ削除完了のメッセージを取得.
	 * @param delAccount 削除アカウント
	 * @param serviceName シサービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteuser(String delAccount, String serviceName) {
		return MessageConst.MSG_USER_DELETED;
	}

	/**
	 * サービス公開.
	 * @param targetServiceName 捜査対象サービス
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgProductionService(String targetServiceName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_PRODUCTIONSERVICE);
		sb.append(" ");
		sb.append(targetServiceName);
		return sb.toString();
	}

	/**
	 * サービス非公開.
	 * @param targetServiceName 捜査対象サービス
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgStagingService(String targetServiceName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_STAGINGSERVICE);
		sb.append(" ");
		sb.append(targetServiceName);
		return sb.toString();
	}

	/**
	 * インデックス更新.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgUpdateIndex(String serviceName) {
		return MessageConst.MSG_UPDATEINDEX;
	}

	/**
	 * インデックス削除.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteIndex(String serviceName) {
		return MessageConst.MSG_DELETEINDEX;
	}

	/**
	 * ログ出力.
	 * @param targetServiceName 捜査対象サービス
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgWriteLog(String serviceName) {
		return MessageConst.MSG_WRITELOG;
	}

	/**
	 * グループ管理者登録のメッセージを取得.
	 * @param feed グループ管理者と管理するグループ情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgCreateGroupadmin(FeedBase feed, String serviceName) {
		// メッセージは管理対象グループ
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_CREATEGROUPADMIN);
		boolean isFirst = true;
		if (TaggingEntryUtil.isExistData(feed)) {
			sb.append(" ");
			for (EntryBase entry : feed.entry) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(". ");
				}
				sb.append(entry.getMyUri());
			}
		}
		return sb.toString();
	}

	/**
	 * グループ管理グループ削除のメッセージを取得.
	 * @param feed 削除するグループ情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteGroupadmin(FeedBase feed, String serviceName) {
		// メッセージは管理対象グループ
		StringBuilder sb = new StringBuilder();
		sb.append(MessageConst.MSG_DELETEGROUPADMIN);
		boolean isFirst = true;
		if (TaggingEntryUtil.isExistData(feed)) {
			sb.append(" ");
			for (EntryBase entry : feed.entry) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(". ");
				}
				sb.append(entry.getMyUri());
			}
		}
		return sb.toString();
	}

}
