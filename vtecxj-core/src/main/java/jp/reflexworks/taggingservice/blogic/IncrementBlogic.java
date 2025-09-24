package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.IncrementManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インクリメントのためのビジネスロジッククラス.
 */
public class IncrementBlogic {

	/** 加算枠の正規表現 */
	public static final String PATTERN_RANGE_STR =
			"^(\\-?[0-9]+)(\\-(\\-?[0-9]+)(\\!?))*$";
	/** 加算枠のPattern */
	public static Pattern PATTERN_RANGE = Pattern.compile(PATTERN_RANGE_STR);

	/**
	 * インクリメント
	 * @param uri URI
	 * @param num 加算数。0の場合は現在の番号を取得。マイナス可。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return titleにインクリメント後の数を設定。
	 */
	public FeedBase addids(String uri, long num,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		checkAddids(uri, num);
		CheckUtil.checkCommonUri(uri, serviceName);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		String action = AclConst.ACL_TYPE_UPDATE;
		if (num == 0) {
			action = AclConst.ACL_TYPE_RETRIEVE;
		}
		aclBlogic.checkAcl(uri, action, tmpAuth, requestInfo, connectionInfo);

		// インクリメント
		IncrementManager incrementManager = TaggingEnvUtil.getIncrementManager();
		long retNum = incrementManager.increment(uri, num, tmpAuth,
				requestInfo, connectionInfo);

		// 戻り値編集
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = String.valueOf(retNum);
		return feed;
	}

	/**
	 * インクリメント値を指定する.
	 * @param uri URI
	 * @param val 値
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return titleに設定値を設定。
	 */
	public FeedBase setids(String uri, long val,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		checkAddids(uri, val);
		CheckUtil.checkCommonUri(uri, serviceName);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, tmpAuth,
				requestInfo, connectionInfo);

		// 値指定
		IncrementManager incrementManager = TaggingEnvUtil.getIncrementManager();
		long retNum = incrementManager.setNumber(uri, val, tmpAuth,
				requestInfo, connectionInfo);

		// 戻り値編集
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = String.valueOf(retNum);
		return feed;
	}

	/**
	 * 加算枠設定.
	 * <p>
	 * 加算範囲を「{最小値}-{最大値}!」の形式で指定します。<br>
	 * 「-{最大値}」「!」は任意です。
	 * 加算枠をサイクルしない場合、加算範囲の後ろに!を指定します。
	 * デフォルトは最大値まで採番した場合、最小値に戻って加算を続けます。
	 * 枠が変更された場合、インクリメント値は最小値に設定されます。
	 * nullで枠が削除された場合、インクリメント値はそのままです。
	 * </p>
	 * @param uri URI
	 * @param val 加算範囲。nullの場合は加算枠を削除する。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return titleに加算枠を設定。
	 */
	public FeedBase rangeids(String uri, String val,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		checkRangeids(uri, val);
		CheckUtil.checkCommonUri(uri, serviceName);

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 加算枠指定
		IncrementManager incrementManager = TaggingEnvUtil.getIncrementManager();
		String ret = incrementManager.setRange(uri, val, auth,
				requestInfo, connectionInfo);

		// 戻り値編集
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = ret;
		return feed;
	}

	/**
	 * 加算枠取得.
	 * @param uri URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return titleに加算枠を設定
	 */
	public FeedBase getRangeids(String uri,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		checkGetRangeids(uri);
		CheckUtil.checkCommonUri(uri, serviceName);

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 加算枠取得
		IncrementManager incrementManager = TaggingEnvUtil.getIncrementManager();
		String ret = incrementManager.getRange(uri, auth,
				requestInfo, connectionInfo);

		// 戻り値編集
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = ret;
		return feed;
	}

	/**
	 * インクリメント入力チェック
	 * @param uri URI
	 * @param num 加算数
	 */
	private void checkAddids(String uri, long num) {
		CheckUtil.checkUri(uri);
		// numは正数、0、負数いずれも可。
	}

	/**
	 * 加算枠設定入力チェック
	 * @param uri URI
	 * @param val 加算枠
	 */
	private void checkRangeids(String uri, String val) {
		CheckUtil.checkUri(uri);
		// 加算枠フォーマットチェック
		if (StringUtils.isBlank(val)) {
			return;	// nullの場合は枠削除
		}
		// フォーマットチェック
		Matcher matcher = CheckUtil.checkPatternMatch(PATTERN_RANGE, val, "rangeIds");
		// 範囲が最小値-最大値となっているかチェック
		String minStr = matcher.group(1);
		String maxStr = matcher.group(3);
		CheckUtil.checkCompare(minStr, maxStr, false);
	}

	/**
	 * 加算枠取得入力チェック
	 * @param uri URI
	 */
	private void checkGetRangeids(String uri) {
		CheckUtil.checkUri(uri);
	}

	/**
	 * 加算数の文字列指定を数値に変換.
	 * リクエストに指定された文字列を変換するのに使用する。
	 * @param str 採番数文字列
	 * @return 加算数
	 * @throws IllegalParameterException 採番数文字列が未指定、または数値でない場合。
	 */
	public static long longValue(String str) {
		if (StringUtils.isBlank(str)) {
			return 0;
		}
		try {
			return Long.parseLong(str);
		} catch (NumberFormatException e) {
			throw new IllegalParameterException("Please specify in number format. " + str);
		}
	}

	/**
	 * 加算枠設定Feedから加算枠文字列を抽出して返却.
	 * @param feed 加算枠Feed
	 * @return 加算枠
	 */
	public static String getRange(FeedBase feed) {
		if (feed != null) {
			return feed.title;
		}
		return null;
	}

}
