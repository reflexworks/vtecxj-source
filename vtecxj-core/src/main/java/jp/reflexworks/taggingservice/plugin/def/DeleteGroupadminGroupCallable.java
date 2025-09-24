package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * グループ管理サービスの削除.
 */
public class DeleteGroupadminGroupCallable extends ReflexCallable<Boolean> {

	/** 削除対象グループ名リスト */
	private List<String> groupNames;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param settingService 設定サービス
	 */
	public DeleteGroupadminGroupCallable(List<String> groupNames) {
		this.groupNames = groupNames;
	}

	/**
	 * グループ管理サービスの削除.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		String groupadminGroupsStr = getGroupNamesStr();
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[delete groupadmin group call] start. " + groupadminGroupsStr);
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		UserManagerDefault userManagerDefault = new UserManagerDefault();
		userManagerDefault.deleteGroupadminProc(groupNames, reflexContext);

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[delete groupadmin group call] end. " + groupadminGroupsStr);
		}

		return true;
	}
	
	/**
	 * 削除対象グループ管理グループ名のリストを文字列に変換
	 * @return 削除対象グループ管理グループ名のリストを文字列に変換したもの
	 */
	private String getGroupNamesStr() {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String groupName : groupNames) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			sb.append(groupName);
		}
		return sb.toString();
	}

}
