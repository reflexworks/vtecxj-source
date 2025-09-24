package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * ACL管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 */
public interface AclManager extends ReflexPlugin {

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
	throws IOException, TaggingException;

	/**
	 * 指定されたグループに参加しているかチェック.
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @throws PermissionException グループに参加していない場合
	 */
	public void checkAuthedGroup(ReflexAuthentication auth, String groupUri)
	throws IOException, TaggingException;

	/**
	 * 指定されたグループに参加しているかどうか.
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @return 指定されたグループに参加している場合true
	 */
	public boolean isInTheGroup(ReflexAuthentication auth, String groupUri)
	throws IOException, TaggingException;

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
	throws IOException;

	/**
	 * contributorリストの中に、指定されたACL対象と権限(CRUD等のいずれか)の設定があるかどうかチェック.
	 * @param contributors contributorリスト
	 * @param aclUser ACL対象
	 * @param action C, R, U or D
	 * @return 指定されたACL対象と権限がある場合true
	 */
	public boolean hasAclUserAndType(List<Contributor> contributors,
			String aclUser, String action);

}
