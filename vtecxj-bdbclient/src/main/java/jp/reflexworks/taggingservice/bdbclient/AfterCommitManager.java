package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * コミット後の同期処理
 */
public class AfterCommitManager {
	
	/**
	 * コミット後の同期処理.
	 * @param updatedInfos 更新情報リスト
	 * @param originalServiceName 実行元サービス名
	 * @param auth 認証情報
	 * @param systemContext SystemContext
	 */
	public void sync(List<UpdatedInfo> updatedInfos, String originalServiceName,
			ReflexAuthentication auth, SystemContext systemContext) 
	throws IOException, TaggingException {
		// グループ変更があれば、認証情報の更新を行う。
		String uid = auth.getUid();
		if (!StringUtils.isBlank(uid) && !uid.equals(SystemAuthentication.UID_SYSTEM) &&
				!uid.equals(Constants.NULL_UID)) {
			boolean isChangeGroup = false;
			UserManager userManager = TaggingEnvUtil.getUserManager();
			String groupParentUri = userManager.getGroupUriByUid(uid);
			for (UpdatedInfo updatedInfo : updatedInfos) {
				EntryBase prevEntry = updatedInfo.getPrevEntry();
				isChangeGroup = isChangeGroup(prevEntry, groupParentUri);
				if (isChangeGroup) {
					break;
				}
				EntryBase updEntry = updatedInfo.getUpdEntry();
				isChangeGroup = isChangeGroup(updEntry, groupParentUri);
				if (isChangeGroup) {
					break;
				}
			}
			if (isChangeGroup) {
				AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
				authManager.setGroups(auth, systemContext);
			}
		}
	}
	
	/**
	 * 自身のグループが変更されているかどうかチェック.
	 * @param entry Entry
	 * @param groupParentUri グループ親URI
	 * @return 自身のグループが変更されている場合true
	 */
	private boolean isChangeGroup(EntryBase entry, String groupParentUri) {
		if (entry == null) {
			return false;
		}
		String uri = entry.getMyUri();
		if (uri.startsWith(groupParentUri)) {
			return true;
		}
		List<String> aliases = entry.getAlternate();
		if (aliases != null) {
			for (String alias : aliases) {
				if (alias.startsWith(groupParentUri)) {
					return true;
				}
			}
		}
		return false;
	}

}
