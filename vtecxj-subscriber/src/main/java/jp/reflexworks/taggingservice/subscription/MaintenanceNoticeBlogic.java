package jp.reflexworks.taggingservice.subscription;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * クラスタのメンテナンス通知 ビジネスロジッククラス
 */
public class MaintenanceNoticeBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 通知処理.
	 * @param req リクエスト
	 */
	public void notification(ReflexRequest req) throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				req.getRequestInfo(), req.getConnectionInfo());

		// 認証 (JWT)
		boolean isAuth = SubscriptionUtil.authenticate(req, systemContext);
		if (!isAuth) {
			return;
		}

		// 通知内容をメールする
		sendMail(req, systemContext);
	}

	/**
	 * 通知内容をメールする
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	private void sendMail(ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException {
		// メール内容は /_settings/maintenance_notice から取得する
		EntryBase mailEntry = systemContext.getEntry(SubscriptionConst.URI_SETTINGS_MAINTENANCE_NOTICE);
		if (mailEntry == null) {
			logger.warn("[sendMail] setting does not exist. " + SubscriptionConst.URI_SETTINGS_MAINTENANCE_NOTICE);
			return;
		}

		// 送信先
		List<String> tmpEmails = new ArrayList<>();
		if (mailEntry.contributor != null) {
			for (Contributor contributor : mailEntry.contributor) {
				if (!StringUtils.isBlank(contributor.email)) {
					tmpEmails.add(contributor.email);
				}
			}
		}
		if (tmpEmails.isEmpty()) {
			logger.warn("[sendMail] destination does not exist. " + SubscriptionConst.URI_SETTINGS_MAINTENANCE_NOTICE);
			return;
		}
		String[] to = tmpEmails.toArray(new String[0]);

		// メッセージ編集
		String title = mailEntry.title;
		String textMessage = mailEntry.summary;
		String htmlMessage = mailEntry.getContentText();

		String noticeMessage = null;
		StringBuilder sb = new StringBuilder();
		InputStream in = req.getInputStream();
		if (in != null) {
			boolean isFirst = true;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(in, Constants.ENCODING))){
				String line;
				while ((line = br.readLine()) != null) {
					if (isFirst) {
						isFirst = false;
					} else {
						sb.append(Constants.NEWLINE);
					}
					sb.append(line);
				}
			}
		}
		noticeMessage = sb.toString();

		if (logger.isDebugEnabled()) {
			logger.debug("[sendMail] noticeMessage: " + Constants.NEWLINE + noticeMessage);
		}

		if (!StringUtils.isBlank(textMessage)) {
			textMessage = replaceNotice(textMessage, noticeMessage);
		}
		if (!StringUtils.isBlank(htmlMessage)) {
			htmlMessage = replaceNotice(htmlMessage, noticeMessage);
		}

		systemContext.sendHtmlMail(title, textMessage, htmlMessage, to, null, null, null);
	}

	/**
	 * メッセージの指定部分を通知内容に変換.
	 * ${NOTICE}の部分を通知内容に変換する。
	 * @param message メッセージ
	 * @param notice 通知内容
	 * @return 変換したメッセージ
	 */
	private String replaceNotice(String message, String notice)
	throws IOException, TaggingException {
		return StringUtils.replaceAll(message, SubscriptionConst.REPLACE_REGEX_NOTICE,
				StringUtils.null2blank(notice));
	}

}
