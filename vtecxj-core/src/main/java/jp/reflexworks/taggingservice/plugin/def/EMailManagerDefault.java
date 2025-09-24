package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.activation.DataSource;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.EMailConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.EMailManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.SendMailCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メール管理クラス.
 */
public class EMailManagerDefault implements EMailManager {

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン時の終了処理
	 */
	public void close() {
		// Do nothing.
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
	public void sendMail(String title, String textMessage, String htmlMessage,
			Map<String, DataSource> inlineImages, List<DataSource> attachments,
			String[] to, String[] cc, String[] bcc, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		// メッセージ変換
		// ${URL}、${RXID=/xxx}、${LINK=/xxx}を変換する。
		// 認証情報の変換はTOが1件の場合のみ
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String msgTo = null;
		if (to != null && to.length == 1) {
			msgTo = to[0];
		}
		String sendTextMessage = convertMessage(textMessage, msgTo, reflexContext.getAuth(),
				requestInfo, connectionInfo);
		String sendHtmlMessage = convertMessage(htmlMessage, msgTo, reflexContext.getAuth(),
				requestInfo, connectionInfo);

		// メール送信
		// 非同期でメール送信する。
		ReflexAuthentication auth = reflexContext.getAuth();
		SendMailCallable sendMailCallable = new SendMailCallable(
				title, sendTextMessage, sendHtmlMessage, inlineImages, attachments,
				to, cc, bcc);
		TaskQueueUtil.addTask(sendMailCallable, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * メッセージ変換.
	 * ${URL}、${RXID=/xxx}、${LINK=/xxx}を変換する。
	 * @param message メッセージ
	 * @param to 送信先
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 変換後のメッセージ
	 */
	private String convertMessage(String message, String to, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(message)) {
			return message;
		}
		String serviceName = auth.getServiceName();

		// URL取得
		String url = getUrl(serviceName, requestInfo, connectionInfo);

		// URL変換
		String retMessage = replaceURL(message, url);

		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);
		// アカウントからUIDを取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String tmpAccount = UserUtil.editAccount(to);
		String uid = userManager.getUidByAccount(tmpAccount, systemContext);
		String account = null;
		if (!StringUtils.isBlank(uid)) {
			account = tmpAccount;
		}

		// RXID変換
		retMessage = replaceRXID(retMessage, uid, account, url, systemContext);
		// LINK変換
		retMessage = replaceLink(retMessage, uid, account, url, systemContext);

		return retMessage;
	}

	/**
	 * URLを取得.
	 * コンテキストパスまで
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	private String getUrl(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		return serviceBlogic.getRedirectUrlContextPath(serviceName,
				requestInfo, connectionInfo);
	}

	/**
	 * メッセージの指定部分をURLに変換.
	 * ${URL}の部分をURLに変換する。
	 * @param message メッセージ
	 * @param url URL
	 * @return 変換したメッセージ
	 */
	public String replaceURL(String message, String url)
	throws IOException, TaggingException {
		return StringUtils.replaceAll(message, EMailConst.REPLACE_REGEX_URL,
				StringUtils.null2blank(url));
	}

	/**
	 * メッセージの指定部分をRXIDに変換.
	 * ${RXID}の部分をRXIDに変換する。
	 * この指定はURLを付加しない。
	 * @param message メッセージ
	 * @param rxid RXID
	 * @return 変換したメッセージ
	 */
	public String replaceRXID(String message, String rxid)
	throws IOException, TaggingException {
		return StringUtils.replaceAll(message, EMailConst.REPLACE_REGEX_RXID,
				StringUtils.null2blank(rxid));
	}

	/**
	 * メッセージにRXID付きのURLを設定.
	 * ${RXID}をRXIDに変換する。
	 * ${RXID=/xxx}の部分をRXID付きURLに変換する。
	 * @param message メッセージ
	 * @param uid UID
	 * @param account アカウント
	 * @param url URL
	 * @param systemContext SystemContext
	 * @return 変換したメッセージ
	 */
	public String replaceRXID(String message, String uid, String account, String url,
			BaseReflexContext baseReflexContext)
	throws IOException, TaggingException {
		SystemContext systemContext = (SystemContext)baseReflexContext;
		// ${RXID}をRXIDに変換する。
		String rxid = createRXID(account, systemContext);
		message = replaceRXID(message, rxid);
		// ${RXID=/xxx}の部分をRXID付きURLに変換する。
		return replaceConversionStr(message, uid, account, url,
				EMailConst.REPLACE_RXID_PREFIX, RequestParam.PARAM_RXID,
				(SystemContext)systemContext);
	}

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
	throws IOException, TaggingException {
		return replaceConversionStr(message, uid, account, url,
				EMailConst.REPLACE_LINK_PREFIX, RequestParam.PARAM_TOKEN,
				(SystemContext)systemContext);
	}

	/**
	 * 文字列変換.
	 * メッセージの${RXID=/xxx}または${LINK=/xxx}の部分を、
	 * URL + /xxx + ?RXID=yyyまたは?token=yyy に置き換えます。
	 * PathInfoの部分に#があればUIDに置き換えます。
	 * リンクトークンの場合、指定されたURI(/xxx)でリンクトークンを作成します。
	 * @param message メッセージ
	 * @param uid UID
	 * @param account アカウント
	 * @param url URL
	 * @param conversionStr 変換箇所を表すパラメータ
	 * @param conversionParam 変換後のパラメータ名
	 * @param systemContext SystemContext
	 * @return 変換後のメッセージ
	 */
	private String replaceConversionStr(String message, String uid, String account,
			String url, String conversionStr, String conversionParam,
			SystemContext systemContext)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(message) || StringUtils.isBlank(conversionStr)) {
			return message;
		}
		// 指定された文字の変換
		int conversionLen = conversionStr.length();
		String tmpMessage = message;
		int beginIdx = 0;
		int idx = tmpMessage.indexOf(conversionStr, beginIdx);
		while (idx > -1) {
			// キーを取り出す
			int idxEnd = tmpMessage.indexOf(EMailConst.REPLACE_SUFFIX, idx);
			String uri = tmpMessage.substring(idx + conversionLen, idxEnd);
			uri = TaggingEntryUtil.editHeadSlash(uri);
			String pathInfo = "";
			if (uri != null) {
				uri = uri.replaceAll(EMailConst.REPLACE_UID, StringUtils.null2blank(uid));
				int pIdx = uri.indexOf(ReflexServletConst.ACCESSTOKEN_KEY_DELIMITER);
				if (pIdx == -1) {
					pathInfo = uri;
				} else {
					// キー複数指定の場合、先頭のキーをURLに使用する。
					pathInfo = uri.substring(0, pIdx);
				}
			}
			String paramVal = null;
			// 値の編集
			if (conversionStr.equals(EMailConst.REPLACE_LINK_PREFIX)) {
				// リンクトークンの生成
				paramVal = createLinkToken(uri, uid, systemContext);
			} else {
				// RXIDの生成
				paramVal = createRXID(account, systemContext);
			}
			paramVal = StringUtils.null2blank(paramVal);

			StringBuilder sb = new StringBuilder();
			sb.append(tmpMessage.substring(0, idx));
			sb.append(url);
			// サーブレットパス -> 指定しない
			sb.append(pathInfo);
			if (pathInfo.indexOf("?") > -1) {
				sb.append("&");
			} else {
				sb.append("?");
			}
			sb.append(conversionParam);
			sb.append("=");
			sb.append(paramVal);
			sb.append(tmpMessage.substring(idxEnd + 1));
			tmpMessage = sb.toString();

			beginIdx = idxEnd + 1;
			idx = tmpMessage.indexOf(conversionStr, beginIdx);
		}
		return tmpMessage;
	}

	/**
	 * リンクトークンの生成.
	 *  ${LINK=/xxx}で指定されたURIのリンクトークンを作成する。
	 * @param uri URI
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return リンクトークン
	 */
	private String createLinkToken(String uri, String uid, SystemContext systemContext)
	throws IOException, TaggingException {
		String linkToken = null;
		// リンクトークンの生成
		if (!StringUtils.isBlank(uri) && !StringUtils.isBlank(uid)) {
			// リンクトークン用URIに編集(QueryStringを除去)
			StringBuilder sb = new StringBuilder();
			String[] tmpUris = uri.split(ReflexServletConst.ACCESSTOKEN_KEY_DELIMITER);
			boolean isFirst = true;
			for (String tmpUri : tmpUris) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(ReflexServletConst.ACCESSTOKEN_KEY_DELIMITER);
				}
				sb.append(UrlUtil.getPathInfo(tmpUri));
			}
			String linkTokenUri = sb.toString();

			AccessTokenManager accessTokenManager =
					TaggingEnvUtil.getAccessTokenManager();
			linkToken = accessTokenManager.getLinkToken(uid, linkTokenUri, systemContext);
		}
		return linkToken;
	}

	/**
	 * RXIDの生成
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return RXID
	 */
	private String createRXID(String account, SystemContext systemContext)
	throws IOException, TaggingException {
		if (!StringUtils.isBlank(account)) {
			UserManager userManager = TaggingEnvUtil.getUserManager();
			return userManager.createRXIDByAccount(account, systemContext);
		}
		return null;
	}

}
