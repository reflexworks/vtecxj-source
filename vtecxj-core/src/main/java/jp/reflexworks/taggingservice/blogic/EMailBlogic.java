package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.activation.DataSource;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.EMailManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メール送信ビジネスロジック.
 */
public class EMailBlogic {

	/** インライン画像指定タグ開始 */
	public static final String IMG_SRC_CID = "<img src=\"cid:";
	/** インライン画像指定終了 */
	public static final String IMG_SRC_CID_END = "\"";
	/** インライン画像指定タグ開始文字列の長さ */
	public static final int IMG_SRC_CID_LEN = IMG_SRC_CID.length();

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * メール送信.
	 * @param entry メールの送信内容
	 *              title : メールのタイトル
	 *              summary : テキストメッセージ
	 *              content : HTMLメッセージ
	 * @param attachmentKeys 添付ファイルのコンテンツキーリスト
	 * @param to  送信先アドレス
	 * @param reflexContext ReflexContext
	 */
	public void sendMail(EntryBase entry, String[] attachmentKeys, String to,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// toを配列に変換
		CheckUtil.checkMailTo(to);
		String[] tos = new String[]{to};
		sendMail(entry, attachmentKeys, tos, null, null, reflexContext);
	}

	/**
	 * メール送信.
	 * @param entry メールの送信内容
	 *              title : メールのタイトル
	 *              summary : テキストメッセージ
	 *              content : HTMLメッセージ
	 * @param attachmentKeys 添付ファイルのコンテンツキーリスト
	 * @param to  送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param reflexContext ReflexContext
	 */
	public void sendMail(EntryBase entry, String[] attachmentKeys, String[] to,
			String[] cc, String[] bcc, ReflexContext reflexContext)
	throws IOException, TaggingException {
		CheckUtil.checkNotNull(entry, "entry");
		String title = entry.title;
		String textMessage = entry.summary;
		String htmlMessage = entry.getContentText();
		sendMail(title, textMessage, htmlMessage, attachmentKeys, to, cc, bcc,
				reflexContext);
	}

	/**
	 * メール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 * @param attachmentKeys 添付ファイルのキーリスト
	 * @param to 送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param reflexContext ReflexContext
	 */
	public void sendMail(String title, String textMessage, String htmlMessage,
			String[] attachmentKeys, String[] to, String[] cc, String[] bcc,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 添付ファイルの抽出
		List<DataSource> attachments = getAttachments(attachmentKeys, reflexContext);
		sendMail(title, textMessage, htmlMessage, attachments, to, cc, bcc,
				reflexContext);
	}

	/**
	 * メール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 * @param attachments 添付ファイル
	 * @param to 送信先アドレス
	 * @param reflexContext ReflexContext
	 */
	public void sendMail(String title, String textMessage, String htmlMessage,
			List<DataSource> attachments, String to, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// toを配列に変換
		CheckUtil.checkMailTo(to);
		String[] tos = new String[]{to};

		// メール送信
		sendMail(title, textMessage, htmlMessage, attachments, tos, null, null,
				reflexContext);
	}

	/**
	 * メール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 * @param attachments 添付ファイル
	 * @param to 送信先アドレス
	 * @param reflexContext ReflexContext
	 */
	public void sendMail(String title, String textMessage, String htmlMessage,
			List<DataSource> attachments, String[] to, String[] cc, String[] bcc,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// インライン画像の抽出
		Map<String, DataSource> inlineImages = getInlineImages(htmlMessage,
				reflexContext);

		// メール送信
		sendMailProc(title, textMessage, htmlMessage, inlineImages, attachments,
				to, cc, bcc, reflexContext);
	}

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
	private void sendMailProc(String title, String textMessage, String htmlMessage,
			Map<String, DataSource> inlineImages, List<DataSource> attachments,
			String[] to, String[] cc, String[] bcc, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 送信先アドレスの入力チェック
		boolean existDestination = existMailAddress(to);
		boolean tmpExist = existMailAddress(cc);
		if (!existDestination && tmpExist) {
			existDestination = true;
		}
		tmpExist = existMailAddress(bcc);
		if (!existDestination && tmpExist) {
			existDestination = true;
		}
		if (!existDestination) {
			// 送信先未設定エラー
			CheckUtil.checkNotNull(null, "Mail address");
		}

		// 設定チェック
		checkFromInfo(reflexContext);

		// メール管理クラスの送信処理を実行
		EMailManager emailManager = TaggingEnvUtil.getEMailManager();
		emailManager.sendMail(title, textMessage, htmlMessage, inlineImages,
				attachments, to, cc, bcc, reflexContext);
	}

	/**
	 * メールアドレス存在チェック.
	 * メールアドレスが設定されている場合、フォーマットチェックを行います。
	 * @param mailAddresses メールアドレスリスト
	 * @return メールアドレスが設定されている場合true
	 * @throws IllegalParameterException メールアドレスフォーマットエラー
	 */
	private boolean existMailAddress(String[] mailAddresses) {
		boolean existAddr = false;
		if (mailAddresses != null && mailAddresses.length > 0) {
			for (String addr : mailAddresses) {
				if (!StringUtils.isBlank(addr)) {
					existAddr = true;
					CheckUtil.checkMailAddress(addr);
				}
			}
		}
		return existAddr;
	}

	/**
	 * HTMLメッセージからインライン画像指定の箇所を抽出し、
	 * コンテンツ取得でインライン画像を取得する。
	 * @param htmlMessage HTMLメッセージ
	 * @param reflexContext ReflexContext
	 * @return インライン画像マップ (キー:URI、値:画像DataSource)
	 */
	private Map<String, DataSource> getInlineImages(String htmlMessage,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(htmlMessage)) {
			return null;
		}

		RequestInfo requestInfo = reflexContext.getRequestInfo();
		// メッセージを小文字に変換する
		String tmpMessage = htmlMessage.toLowerCase(Locale.ENGLISH);

		Map<String, DataSource> inlineImages = null;
		int beginIdx = 0;
		int idx = tmpMessage.indexOf(IMG_SRC_CID, beginIdx);
		while (idx > -1) {
			beginIdx = idx + IMG_SRC_CID_LEN;
			int endIdx = tmpMessage.indexOf(IMG_SRC_CID_END, beginIdx);
			if (endIdx > idx) {
				String uri = tmpMessage.substring(beginIdx, endIdx);
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[getInlineImages] uri = " + uri);
				}
				ReflexContentInfo contentInfo = reflexContext.getContent(uri);
				if (contentInfo != null) {
					// Content-Typeの指定があるもののみインライン画像指定可能
					if (!StringUtils.isBlank(contentInfo.getContentType())) {
						if (inlineImages == null) {
							inlineImages = new HashMap<String, DataSource>();
						}
						inlineImages.put(uri, contentInfo);
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[getInlineImages] Content-Type is null. " + uri);
						}
					}
				}
			}
			idx = tmpMessage.indexOf(IMG_SRC_CID, beginIdx);
		}
		return inlineImages;
	}

	/**
	 * 添付ファイルの抽出.
	 * @param attachmentKeys 添付ファイルキーリスト
	 * @param reflexContext ReflexContext
	 * @return 添付ファイルリスト
	 */
	private List<DataSource> getAttachments(String[] attachmentKeys,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		List<DataSource> attachments = null;
		if (attachmentKeys != null && attachmentKeys.length > 0) {
			// 添付ファイルの抽出
			attachments = new ArrayList<DataSource>();
			for (String attachmentKey : attachmentKeys) {
				ReflexContentInfo contentInfo = reflexContext.getContent(attachmentKey);
				if (contentInfo != null) {
					attachments.add(contentInfo);
				}
			}
		}
		return attachments;
	}

	/**
	 * メールの送信元情報チェック
	 * @param reflexContext ReflexContext
	 */
	public void checkFromInfo(ReflexContext reflexContext)
	throws InvalidServiceSettingException {
		String from = getFrom(reflexContext);
		String host = getHost(reflexContext);
		// 設定チェック
		if (StringUtils.isBlank(from)) {
			throw new InvalidServiceSettingException("Mail setting is required : " + EMailConst.PROP_U_FROM);
		}
		if (StringUtils.isBlank(host)) {
			throw new InvalidServiceSettingException("Mail setting is required : " + EMailConst.PROP_U_SMTP_HOST);
		}
	}

	/**
	 * メールの送信元アドレスを取得
	 * @param serviceName サービス名
	 * @return メールの送信元アドレス
	 */
	private String getFrom(ReflexContext reflexContext) {
		return getFromInfo(reflexContext, EMailConst.PROP_U_FROM);
	}

	/**
	 * メールの送信元ホストを取得
	 * @param serviceName サービス名
	 * @return メールの送信元ホスト
	 */
	private String getHost(ReflexContext reflexContext) {
		return getFromInfo(reflexContext, EMailConst.PROP_U_SMTP_HOST);
	}

	/**
	 * メールの送信元情報を取得
	 * @param serviceName サービス名
	 * @param プロパティ名
	 * @return メールの送信元情報
	 */
	private String getFromInfo(ReflexContext reflexContext, String name) {
		String serviceName = reflexContext.getServiceName();
		String val = null;
		if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
			val = TaggingEnvUtil.getSystemProp(name, null);
		} else {
			val = reflexContext.getSettingValue(name);
		}
		return val;
	}

}
