package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.activation.DataSource;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * メール管理インターフェース.
 */
public interface EMailManager extends ReflexPlugin {

	/**
	 * メール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 * @param inlineImages インライン画像
	 * @param attachments 添付ファイル
	 * @param to 送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param reflexContext ReflexContext
	 */
	public void sendMail(String title, String textMessage, String htmlMessage,
			Map<String, DataSource> inlineImages, List<DataSource> attachments,
			String[] to, String[] cc, String[] bcc, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * メッセージの指定部分をURLに変換.
	 * ${URL}の部分をURLに変換する。
	 * @param message メッセージ
	 * @param url URL
	 * @return 変換したメッセージ
	 */
	public String replaceURL(String message, String url) throws IOException, TaggingException;

	/**
	 * メッセージにRXID付きのURLを設定.
	 * ${RXID=/xxx}の部分をRXID付きURLに変換する。
	 * @param message メッセージ
	 * @param uid UID
	 * @param account アカウント
	 * @param url URL
	 * @param systemContext SystemContext
	 * @return 変換したメッセージ
	 */
	public String replaceRXID(String message, String uid, String account, String url,
			BaseReflexContext systemContext)
	throws IOException, TaggingException;

	/**
	 * メッセージにリンクトークン付きのURLを設定
	 * ${LINK=/xxx}の部分をリンクトークン付きURLに変換する。
	 * @param message メッセージ
	 * @param uid UID
	 * @param account アカウント
	 * @param url URL
	 * @param systemContext SystemContext
	 * @return 変換したメッセージ
	 */
	public String replaceLink(String message, String uid, String account, String url,
			BaseReflexContext systemContext)
	throws IOException, TaggingException;

}
