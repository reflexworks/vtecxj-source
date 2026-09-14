package jp.reflexworks.taggingservice.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * バッチジョブ実行者(疑似サービス管理者 UID=2 / {@code _serviceadmin_})のユーザ情報を
 * サービスの名前空間に登録するためのユーティリティ.
 * <p>
 * サービス登録({@code _createservice})、初期データ登録(vtecxj-init)、既存サービスへのパッチ
 * の各処理から共用する。
 * </p>
 */
public class ServiceAdminUserUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ServiceAdminUserUtil.class);

	/**
	 * バッチジョブ実行者(UID=2)のユーザ情報一式を登録する.
	 * <p>
	 * 登録するエントリー
	 * <ul>
	 * <li>{@code /_user/2} (title=_serviceadmin_、summary=Activated、ACL={@code /_group/$admin,CRUD} / {@code 2,R} / {@code 2,CUD/})</li>
	 * <li>{@code /_user/2/group} (グループフォルダ)</li>
	 * <li>{@code /_group/$admin/2} (エイリアス {@code /_user/2/group/$admin})</li>
	 * <li>{@code /_user/2/accesskey} (ランダムなアクセスキーを新規発行)</li>
	 * </ul>
	 * すでに {@code /_user/2} が存在する場合は何もしない(冪等)。
	 * 書き込みは SystemContext で行うため呼び出し側の認証情報の権限は問わない。
	 * </p>
	 * @param reflexContext ReflexContext (対象サービスのコンテキスト)
	 * @return 新規に登録した場合true、すでに存在したため何もしなかった場合false
	 */
	public static boolean registerServiceAdminUser(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		SystemContext systemContext = new SystemContext(serviceName,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		String uid = ServiceAuthenticationConst.UID_SERVICEADMIN;	// "2"
		String userTopUri = Constants.URI_USER + "/" + uid;			// /_user/2
		String userGroupUri = userTopUri + Constants.URI_LAYER_GROUP;	// /_user/2/group

		// 冪等チェック
		if (systemContext.getEntry(userTopUri, false) != null) {
			if (logger.isTraceEnabled()) {
				logger.info("[registerServiceAdminUser] already exists. serviceName=" + serviceName);
			}
			return false;
		}

		List<EntryBase> entries = new ArrayList<EntryBase>();

		// /_user/2
		EntryBase userTopEntry = TaggingEntryUtil.createEntry(serviceName);
		userTopEntry.setMyUri(userTopUri);
		userTopEntry.title = ServiceAuthenticationConst.ACCOUNT_SERVICEADMIN;	// _serviceadmin
		userTopEntry.summary = Constants.USERSTATUS_ACTIVATED;			// Activated
		userTopEntry.addContributor(TaggingEntryUtil.getAclContributor(
				Constants.URI_GROUP_ADMIN, Constants.ACL_TYPE_CRUD));
		userTopEntry.addContributor(TaggingEntryUtil.getAclContributor(
				uid, Constants.ACL_TYPE_RETRIEVE));
		userTopEntry.addContributor(TaggingEntryUtil.getAclContributor(
				uid, Constants.ACL_TYPE_CREATE + Constants.ACL_TYPE_UPDATE
				+ Constants.ACL_TYPE_DELETE + Constants.ACL_TYPE_LOW));
		entries.add(userTopEntry);

		// /_user/2/group
		EntryBase userGroupEntry = TaggingEntryUtil.createEntry(serviceName);
		userGroupEntry.setMyUri(userGroupUri);
		entries.add(userGroupEntry);

		// /_group/$admin/2 (エイリアス /_user/2/group/$admin)
		EntryBase groupAdminEntry = TaggingEntryUtil.createEntry(serviceName);
		groupAdminEntry.setMyUri(Constants.URI_GROUP_ADMIN + "/" + uid);
		groupAdminEntry.addAlternate(userGroupUri + Constants.URI_$ADMIN);
		entries.add(groupAdminEntry);

		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.entry = entries;
		systemContext.post(feed);

		// /_user/2/accesskey (アクセスキー新規発行)
		AccessTokenManager accessTokenManager = TaggingEnvUtil.getAccessTokenManager();
		String accessKey = accessTokenManager.createAccessKeyStr();
		accessTokenManager.putAccessKey(uid, accessKey, systemContext);

		if (logger.isTraceEnabled()) {
			logger.info("[registerServiceAdminUser] registered. serviceName=" + serviceName);
		}
		return true;
	}

}
