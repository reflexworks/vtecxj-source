package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.Base64Util;
import jp.sourceforge.reflex.util.SHA256;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サービス管理クラス.
 */
public class AccessTokenManagerDefault implements AccessTokenManager {

	/** アクセスキーの文字列長 */
	private static final int RANDOM_STRING_LEN = 36;
	/** Contributorに設定するアクセスキー識別接頭辞の長さ */
	private static final int URN_PREFIX_ACCESSKEY_LEN = Constants.URN_PREFIX_ACCESSKEY.length();
	/** アクセストークンUID区切り文字 */
	private static final String ACCESSTOKEN_UID_DELIMITER = ReflexServletConst.ACCESSTOKEN_UID_DELIMITER;
	/** リンクトークン区切り文字 */
	private static final String LINKTOKEN_DELIMITER = "-";
	/** アクセストークンのキー区切り文字 */
	public static final String ACCESSTOKEN_KEY_DELIMITER = ReflexServletConst.ACCESSTOKEN_KEY_DELIMITER;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
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
	 * アクセストークン認証.
	 * @param accessToken アクセストークン
	 * @param reflexContext ReflexContext
	 * @return 認証OKの場合true
	 */
	@Override
	public boolean checkAccessToken(String accessToken,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		return checkLinkToken(accessToken, null, reflexContext);
	}

	/**
	 * リンクトークン認証.
	 * @param linkToken リンクトークン
	 * @param uri URI。アクセストークン認証の場合はnull。
	 * @param reflexContext ReflexContext
	 * @return 認証OKの場合true
	 */
	@Override
	public boolean checkLinkToken(String linkToken, String uri,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// UIDを取得
		String uid = getUidByAccessToken(linkToken);
		// アクセスキーを取得
		String accessKey = null;
		try {
			accessKey = getAccessKey(uid, reflexContext);
		} catch (IllegalParameterException e) {
			// 入力エラーはUID不正
			return false;
		}
		String checkUri = null;
		if (uri == null) {
			checkUri = Constants.URI_ROOT;
		} else {
			checkUri = uri;
		}

		// アクセストークン認証
		return equalsAccessToken(accessKey, checkUri, linkToken);
	}

	/**
	 * アクセストークン取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return アクセストークン
	 */
	@Override
	public String getAccessTokenByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return getAccessLinkToken(uid, Constants.URI_ROOT, false, reflexContext);
	}

	/**
	 * リンクトークン取得.
	 * @param uid UID
	 * @param uri URI。複数指定の場合はカンマでつなぐ。
	 * @param reflexContext ReflexContext
	 * @return リンクトークン
	 */
	@Override
	public String getLinkToken(String uid, String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return getAccessLinkToken(uid, uri, true, reflexContext);
	}

	/**
	 * アクセストークン・リンクトークン取得.
	 * @param uid UID
	 * @param uri URI。複数指定の場合はカンマでつなぐ。
	 * @param isLinkToken リンクトークンの場合true
	 * @param reflexContext ReflexContext
	 * @return アクセストークンまたはリンクトークン
	 */
	private String getAccessLinkToken(String uid, String uri, boolean isLinkToken,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 未入力チェック
		CheckUtil.checkLinkToken(uri);
		// URIが複数の場合に対応 /xxx/yyy,/aaa/bbb
		String[] uris = uri.split(ACCESSTOKEN_KEY_DELIMITER);
		// キーチェック
		if (isLinkToken) {
			CheckUtil.checkLinkToken(uris);
		}
		// アクセスキーを取得
		String accessKey = getAccessKey(uid, reflexContext);
		// アクセストークン生成
		return createAccessToken(uid, accessKey, uris);
	}

	/**
	 * ユーザ識別情報、アクセスキーとuriからアクセストークンを生成
	 * @param user ユーザ識別情報
	 * @param accessKey アクセスキー
	 * @param uris uri(複数指定可)
	 * @param serviceName
	 * @return アクセストークン
	 */
	private String createAccessToken(String user, String accessKey, String[] uris) {
		if (StringUtils.isBlank(user) || StringUtils.isBlank(accessKey) ||
				uris == null || uris.length == 0) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(user);
		sb.append(ACCESSTOKEN_UID_DELIMITER);
		boolean isFirst = true;
		for (String tmpUri : uris) {
			// uriは、先頭に/を付加し、/@{サービス名} を省略
			tmpUri = TaggingEntryUtil.editHeadSlash(tmpUri);
			tmpUri = TaggingEntryUtil.removeLastSlash(tmpUri);
			String tmpStr = createAccessTokenPart(accessKey, tmpUri);
			if (!StringUtils.isBlank(tmpStr)) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(LINKTOKEN_DELIMITER);
				}
				sb.append(tmpStr);
			}
		}
		if (!isFirst) {
			return sb.toString();
		}
		return null;
	}

	/**
	 * 一つのURIに対するアクセストークンを生成.
	 * 返却するアクセストークンは、指定されたアクセストークンをハイフンで繋いだもの。
	 * @param accessKey アクセスキー
	 * @param uri URI
	 * @return 一つのURIに対するアクセストークン
	 */
	private String createAccessTokenPart(String accessKey, String uri) {
		if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(uri)) {
			return null;
		}
		uri = TaggingEntryUtil.editHeadSlash(uri);
		String tmpStr = accessKey + uri;
		try {
			byte[] tmpBytes = tmpStr.getBytes(Constants.ENCODING);
			String tmpStr2 = SHA256.hashString(tmpBytes);
			if (!StringUtils.isBlank(tmpStr2)) {
				return Base64Util.removeSymbol(tmpStr2);
			}
		} catch (UnsupportedEncodingException e) {
			logger.warn(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * リンクトークンをURIごとに分割する
	 * @param accessToken リンクトークン
	 * @return リンクトークンをURIごとに分割した配列
	 */
	private String[] getAccessTokenParts(String accessToken) {
		int idx = accessToken.indexOf(ACCESSTOKEN_UID_DELIMITER);
		return accessToken.substring(idx + 1).split(LINKTOKEN_DELIMITER);
	}

	/**
	 * アクセストークン認証.
	 * @param accessKey アクセスキー
	 * @param uri URI
	 * @param accessToken アクセストークン
	 * @return アクセスキー+URIのアクセストークンが、指定されたアクセストークンと等しい場合true
	 */
	private boolean equalsAccessToken(String accessKey, String uri,
			String accessToken) {
		String[] accessTokenParts = getAccessTokenParts(accessToken);
		if (!StringUtils.isBlank(uri)) {
			String editAccessTokenPart = createAccessTokenPart(accessKey, uri);
			if (!StringUtils.isBlank(editAccessTokenPart)) {
				for (String accessTokenPart : accessTokenParts) {
					if (editAccessTokenPart.equals(accessTokenPart)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * アクセスキー更新.
	 * @param uid UID
	 * @param accessKey アクセスキー
	 * @param reflexContext ReflexContext
	 * @return アクセスキー情報
	 */
	@Override
	public FeedBase putAccessKey(String uid, String accessKey, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String accesskeyUri = userManager.getAccessKeyUriByUid(uid);
		EntryBase accesskeyEntry = reflexContext.getEntry(accesskeyUri, true);
		boolean existAccesskeyEntry = false;
		if (accesskeyEntry == null) {
			accesskeyEntry = TaggingEntryUtil.createEntry(serviceName);
			accesskeyEntry.setMyUri(accesskeyUri);
		} else {
			existAccesskeyEntry = true;
		}

		Contributor contributor = null;
		if (accesskeyEntry.contributor != null) {
			contributor = getAccessKeyContributor(accesskeyEntry);
		} else {
			accesskeyEntry.contributor = new ArrayList<Contributor>();
		}
		if (contributor == null) {
			contributor = new Contributor();
			accesskeyEntry.contributor.add(contributor);
		}
		contributor.uri = Constants.URN_PREFIX_ACCESSKEY + accessKey;

		if (existAccesskeyEntry) {
			reflexContext.put(accesskeyEntry);
		} else {
			reflexContext.post(accesskeyEntry);
		}

		// 戻り値編集
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.addEntry(accesskeyEntry);
		return feed;
	}

	/**
	 * UIDからアクセスキーを取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return アクセスキー
	 */
	private String getAccessKey(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// アクセスキーエントリーを取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String uri = userManager.getAccessKeyUriByUid(uid);
		EntryBase entry = reflexContext.getEntry(uri, true);

		// エントリーからアクセスキーを取得
		if (entry != null) {
			Contributor contributor = getAccessKeyContributor(entry);
			return getAccessKeyFromContributor(contributor);
		}
		return null;
	}

	/**
	 * アクセスキー設定を取得.
	 * @param accesskeyEntry アクセスキー
	 * @return アクセスキー設定Contributor
	 */
	private Contributor getAccessKeyContributor(EntryBase accesskeyEntry) {
		if (accesskeyEntry != null && accesskeyEntry.contributor != null) {
			for (Contributor cont : accesskeyEntry.contributor) {
				if (cont.uri != null &&
						cont.uri.startsWith(Constants.URN_PREFIX_ACCESSKEY)) {
					return cont;
				}
			}
		}
		return null;
	}

	/**
	 * 対象のContributorからアクセスキーを取得.
	 * @param contributor アクセスキーの指定があるContributor
	 * @return アクセスキー
	 */
	private String getAccessKeyFromContributor(Contributor contributor) {
		if (contributor != null && contributor.uri != null &&
				contributor.uri.startsWith(Constants.URN_PREFIX_ACCESSKEY)) {
			return contributor.uri.substring(URN_PREFIX_ACCESSKEY_LEN);
		}
		return null;
	}

	/**
	 * アクセスキーを生成.
	 * ランダム値を生成し返却します。
	 * @return アクセスキー
	 */
	public String createAccessKeyStr() {
		return UserUtil.createRandomString(RANDOM_STRING_LEN);
	}

	/**
	 * アクセストークンからUIDを取得
	 * @param accessToken アクセストークン
	 * @return UID
	 */
	public String getUidByAccessToken(String accessToken) {
		if (accessToken != null) {
			int idx = accessToken.indexOf(ACCESSTOKEN_UID_DELIMITER);
			if (idx > 0) {
				return accessToken.substring(0, idx);
			}
		}
		return null;
	}

	/**
	 * リクエストヘッダからアクセストークンを取り出す。
	 * @param req リクエスト
	 * @return アクセストークン
	 */
	public String getAccessTokenFromRequest(ReflexRequest req) {
		return ReflexServletUtil.getHeaderValue(req,
				ReflexServletConst.HEADER_AUTHORIZATION,
				ReflexServletConst.HEADER_AUTHORIZATION_TOKEN);
	}

	/**
	 * リクエストパラメータからリンクトークンを取り出す。
	 * @param req リクエスト
	 * @return リンクトークン
	 */
	public String getLinkTokenFromRequest(ReflexRequest req) {
		return req.getParameter(RequestParam.PARAM_TOKEN);
	}

}
