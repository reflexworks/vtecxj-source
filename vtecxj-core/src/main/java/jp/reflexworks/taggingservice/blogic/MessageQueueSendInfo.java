package jp.reflexworks.taggingservice.blogic;

import java.util.List;
import java.util.Set;

import jp.reflexworks.atom.entry.EntryBase;

/**
 * メッセージキュー送信情報.
 * 入力チェック・送信先抽出(同期)の結果を、メッセージキュー登録処理(同期または非同期)に渡す。
 */
class MessageQueueSendInfo {

	/** メッセージ */
	final EntryBase messageEntry;
	/** チャネル */
	final String channel;
	/** チャネルグループ */
	final String channelGroup;
	/** 送信先UID (送信者本人を除く) */
	final Set<String> toUids;
	/** Push通知送信対象UID (グループ未参加の個別宛) */
	final List<String> pushNotificationUids;
	/** グループ宛の場合true */
	final boolean isGroup;
	/** Push通知対象メッセージの場合true */
	final boolean pushMsg;
	/** メッセージキューの番号 (入力チェック後に採番。全送信先で同じ番号を使用する) */
	long mqNum;

	/**
	 * コンストラクタ.
	 * @param messageEntry メッセージ
	 * @param channel チャネル
	 * @param channelGroup チャネルグループ
	 * @param toUids 送信先UID
	 * @param pushNotificationUids Push通知送信対象UID
	 * @param isGroup グループ宛の場合true
	 * @param pushMsg Push通知対象メッセージの場合true
	 */
	MessageQueueSendInfo(EntryBase messageEntry, String channel, String channelGroup,
			Set<String> toUids, List<String> pushNotificationUids, boolean isGroup,
			boolean pushMsg) {
		this.messageEntry = messageEntry;
		this.channel = channel;
		this.channelGroup = channelGroup;
		this.toUids = toUids;
		this.pushNotificationUids = pushNotificationUids;
		this.isGroup = isGroup;
		this.pushMsg = pushMsg;
	}

}
