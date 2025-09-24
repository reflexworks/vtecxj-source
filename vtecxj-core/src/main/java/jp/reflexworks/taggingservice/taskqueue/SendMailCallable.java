package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.activation.DataSource;
import jp.reflexworks.atom.util.MailUtil;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.EMailConst;
import jp.reflexworks.taggingservice.blogic.PropertyBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メール送信非同期処理.
 */
public class SendMailCallable extends ReflexCallable<Boolean> {

	// メール送信内容
	/** 題名 */
	private String title;
	/** テキストメッセージ */
	private String sendTextMessage;
	/** HTMLメッセージ */
	private String sendHtmlMessage;
	/** インライン画像 */
	private Map<String, DataSource> inlineImages;
	/** 添付ファイル */
	private List<DataSource> attachments;
	/** 送信先アドレス配列 */
	private String[] to;
	/** CC送信先アドレス配列 */
	private String[] cc;
	/** BCC送信先アドレス配列 */
	private String[] bcc;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param title 題名
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 * @param inlineImages インライン画像
	 * @param attachments 添付ファイル
	 * @param to 送信先アドレス配列
	 * @param cc CC送信先アドレス配列
	 * @param bcc BCC送信先アドレス配列
	 */
	public SendMailCallable(String title, String sendTextMessage, String sendHtmlMessage,
			Map<String, DataSource> inlineImages, List<DataSource> attachments,
			String[] to, String[] cc, String[] bcc) {
		this.title = title;
		this.sendTextMessage = sendTextMessage;
		this.sendHtmlMessage = sendHtmlMessage;
		this.inlineImages = inlineImages;
		this.attachments = attachments;
		this.to = to;
		this.cc = cc;
		this.bcc = bcc;
	}

	/**
	 * メール送信処理
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[call] start.");
		}
		String serviceName = getServiceName();
		// 送信元情報をサービスごとの設定から取得
		String from = null;
		String fromPersonal = null;
		String user = null;
		String password = null;
		String host = null;
		String port = null;
		String protocol = null;
		String isStarttlsStr = null;
		String isAuthStr = null;
		if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
			from = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_FROM, null);
			fromPersonal = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_FROM_PERSONAL, null);
			user = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_USER, null);
			password = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_PASSWORD, null);
			host = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_SMTP_HOST, null);
			port = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_SMTP_PORT, null);
			protocol = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_TRANSPORT_PROTOCOL, null);
			isStarttlsStr = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_SMTP_STARTTLS, null);
			isAuthStr = TaggingEnvUtil.getSystemProp(EMailConst.PROP_U_SMTP_AUTH, null);
		} else {
			PropertyBlogic propertyBlogic = new PropertyBlogic();
			from = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_FROM);
			fromPersonal = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_FROM_PERSONAL);
			user = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_USER);
			password = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_PASSWORD);
			host = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_SMTP_HOST);
			port = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_SMTP_PORT);
			protocol = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_TRANSPORT_PROTOCOL);
			isStarttlsStr = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_SMTP_STARTTLS);
			isAuthStr = propertyBlogic.getSettingValue(serviceName, EMailConst.PROP_U_SMTP_AUTH);
		}
		Boolean isStarttls = null;
		if (!StringUtils.isBlank(isStarttlsStr)) {
			isStarttls = StringUtils.booleanValue(isStarttlsStr, true);
		}
		Boolean isAuth = null;
		if (!StringUtils.isBlank(isAuthStr)) {
			isAuth = StringUtils.booleanValue(isAuthStr, true);
		}

		String sendmailInfo = getSendmailInfo(from);
		// メール送信
		try {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[call] send mail: " + sendmailInfo);
			}

			MailUtil.send(title, sendTextMessage, sendHtmlMessage, inlineImages,
					attachments, to, null, cc, null, bcc, null,
					from, fromPersonal, user, password,
					host, port, protocol, isStarttls, isAuth, false);

			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[call] end.");
			}
			return true;
			
		} catch (IOException e) {
			// メール送信でのIO例外は、各サービスの設定による。
			StringBuilder sb = new StringBuilder();
			sb.append("Send mail error. ");
			sb.append(sendmailInfo);
			String msg = sb.toString();
			throw new InvalidServiceSettingException(e, msg);
		}
	}

	/**
	 * 送信メール情報を取得
	 * @param from 送信元
	 * @return 送信メール情報文字列
	 */
	private String getSendmailInfo(String from) {
		StringBuilder sb = new StringBuilder();
		sb.append("[from]");
		sb.append(from);
		if (to != null && to.length > 0) {
			sb.append(" [to]");
			boolean isFirst = true;
			for (String addr : to) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(addr);
			}
		}
		if (cc != null && cc.length > 0) {
			sb.append(" [cc]");
			boolean isFirst = true;
			for (String addr : cc) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(addr);
			}
		}
		if (bcc != null && bcc.length > 0) {
			sb.append(" [bcc]");
			boolean isFirst = true;
			for (String addr : bcc) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(addr);
			}
		}
		sb.append(" [title]");
		sb.append(title);
		return sb.toString();
	}

}
