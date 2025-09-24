package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AclManager;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * ACLチェッククラス.
 */
public class AclBlogic {

	/**
	 * 認可チェック
	 * @param uri キー (フィード検索や自動採番登録の場合、selfidはダミー(#)をセットする。)
	 * @param action C, R, U or D
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @throws PermissionException 認可エラー
	 */
	public void checkAcl(String uri, String action,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		AclManager aclManager = TaggingEnvUtil.getAclManager();
		aclManager.checkAcl(uri, action, auth, requestInfo, connectionInfo);
	}

	/**
	 * 指定されたグループに参加しているかチェック.
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @throws PermissionException グループに参加していない場合
	 */
	public void checkAuthedGroup(ReflexAuthentication auth, String groupUri)
	throws IOException, TaggingException {
		AclManager aclManager = TaggingEnvUtil.getAclManager();
		aclManager.checkAuthedGroup(auth, groupUri);
	}

	/**
	 * 指定されたグループに参加しているかどうか
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @return グループに参加している場合true
	 */
	public boolean isInTheGroup(ReflexAuthentication auth, String groupUri)
	throws IOException, TaggingException {
		AclManager aclManager = TaggingEnvUtil.getAclManager();
		return aclManager.isInTheGroup(auth, groupUri);
	}

	/**
	 * 自分自身の権限チェック
	 * <p>
	 * フィード検索を対象に、上位階層のACL権限があることを前提にチェックする。<br>
	 * <ul>
	 * <li>ACLの設定がない場合はtrueを返却する。</li>
	 * <li>ACLの設定があり、認証情報のACL設定がある場合はtrueを返却する。</li>
	 * <li>ACLの設定があり、認証情報のACL設定がない場合はfalseを返却する。</li>
	 * </ul>
	 * </p>
	 * @param entry Entry
	 * @param auth 認証情報
	 * @return 権限あり:true, 権限なし:false
	 * @throws IOException
	 */
	public boolean hasAuthoritySelf(EntryBase entry, ReflexAuthentication auth)
	throws IOException {
		AclManager aclManager = TaggingEnvUtil.getAclManager();
		return aclManager.hasAuthoritySelf(entry, auth);
	}

	/**
	 * contributorリストの中に、指定されたACL対象と権限(CRUD等のいずれか)の設定があるかどうかチェック.
	 * @param contributors contributorリスト
	 * @param aclUser ACL対象
	 * @param action C, R, U or D
	 * @return 指定されたACL対象と権限がある場合true
	 */
	public boolean hasAclUserAndType(List<Contributor> contributors,
			String aclUser, String action) {
		AclManager aclManager = TaggingEnvUtil.getAclManager();
		return aclManager.hasAclUserAndType(contributors, aclUser, action);
	}

	/**
	 * ACLチェックで親階層指定の場合、selfidにダミーを設定したURIでチェックする。
	 * (フィード検索・カウント取得・自動採番登録・Hash更新)
	 * @param parentUri 親階層
	 * @return ACLチェックURI
	 */
	public String getDummySelfidUri(String parentUri) {
		return TaggingEntryUtil.getUri(parentUri, AclConst.URI_VALUE_DUMMY);
	}

	/**
	 * ダミーURIかどうかチェックする。
	 * (ACLチェックで親階層指定の場合、selfidにダミーを設定したURIでチェックする。)
	 * @param URI
	 * @return ダミーURIの場合true
	 */
	public boolean isDummySelfidUri(String uri) {
		if (uri != null && uri.endsWith(AclConst.URI_LAYER_DUMMY)) {
			return true;
		}
		return false;
	}

	/**
	 * ダミーURIの場合、末尾のダミー部分を削除して返却する.
	 * ダミーURIでない場合はそのまま返却する。
	 * @param uri URI
	 * @return 編集したURI
	 */
	public String cutDummySelfidUri(String uri) {
		if (AclConst.URI_LAYER_DUMMY.equals(uri)) {
			return uri.substring(0, uri.length() - AclConst.URI_VALUE_DUMMY_LENGTH);
		} else if (isDummySelfidUri(uri)) {
			return uri.substring(0, uri.length() - AclConst.URI_LAYER_DUMMY_LENGTH);
		}
		return uri;
	}

	/**
	 * ユーザ認証されているかどうかチェック.
	 * @param auth 認証情報
	 * @return ユーザ認証されている場合true
	 */
	public boolean isAuthUser(ReflexAuthentication auth) {
		if (auth != null && auth.getAccount() != null && auth.getUid() != null) {
			return true;
		}
		return false;
	}

}
