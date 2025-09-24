package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AllocateIdsManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 自動採番のためのビジネスロジッククラス.
 */
public class AllocateIdsBlogic {

	/**
	 * 採番
	 * @param uri URI
	 * @param num 採番数
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return titleに採番番号を設定。複数の場合はカンマでつなぐ。
	 */
	public FeedBase allocids(String uri, int num,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		checkAllocids(uri, num);
		CheckUtil.checkCommonUri(uri, serviceName);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// ACLチェック
		// R・U・Cのいずれかの権限で採番可能
		AclBlogic aclBlogic = new AclBlogic();
		try {
			aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, tmpAuth,
					requestInfo, connectionInfo);
		} catch (PermissionException per) {
			try {
				aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, tmpAuth,
						requestInfo, connectionInfo);
			} catch (PermissionException peu) {
				aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_CREATE, tmpAuth,
						requestInfo, connectionInfo);
			}
		}

		// 採番
		AllocateIdsManager allocateIdsManager = TaggingEnvUtil.getAllocateIdsManager();
		List<String> ids = allocateIdsManager.allocateIds(uri, num, tmpAuth,
				requestInfo, connectionInfo);

		// 戻り値編集
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String allocid : ids) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(",");
			}
			sb.append(allocid);
		}
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = sb.toString();
		return feed;
	}

	/**
	 * 自動採番入力チェック
	 * @param uri URI
	 * @param num 採番数
	 */
	private void checkAllocids(String uri, int num) {
		CheckUtil.checkUri(uri);
		CheckUtil.checkAllocNumber(num);
	}

	/**
	 * 採番数の文字列指定を数値に変換.
	 * リクエストに指定された文字列を変換するのに使用する。
	 * @param str 採番数文字列
	 * @return 採番数
	 * @throws IllegalParameterException 採番数文字列が未指定、または数値でない場合。
	 */
	public static int intValue(String str) {
		if (!StringUtils.isBlank(str)) {
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException e) {
				// Do nothing.
			}
		}
		throw new IllegalParameterException("Please specify in number format. " + str);
	}

}
