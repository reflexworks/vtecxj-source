package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.PushNotificationManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.GroupUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メッセージキュービジネスロジッククラス.
 */
public class MessageQueueBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * メッセージキュー使用ON/OFF設定を取得
	 * @param channel チャネル
	 * @param reflexContext ReflexContext
	 * @return ONの場合true、OFFの場合false
	 */
	public boolean getMessageQueueStatus(String channel, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();
		// 入力チェック
		checkChannel(channel);
		// ログインしていない場合はエラー
		checkAuth(auth);

		boolean ret = false;
		// メッセージキュー使用設定
		// キー: `/_user/{UID}/mqstatus/{チャネルの変換値}
		String uri = getMessageQueueStatusUri(channel, uid);
		EntryBase entry = reflexContext.getEntry(uri);
		// summaryにメッセージキュー使用ON/OFF設定を格納
		if (entry != null && !StringUtils.isBlank(entry.summary)) {
			ret = StringUtils.booleanValue(entry.summary);
		}
		return ret;
	}

	/**
	 * メッセージキュー使用ON/OFF設定
	 * @param flag メッセージキューを使用する場合true
	 * @param channel チャネル
	 * @param reflexContext ReflexContext
	 */
	public void setMessageQueueStatus(boolean flag, String channel, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		// 入力チェック
		checkChannel(channel);
		// ログインしていない場合はエラー
		checkAuth(auth);
		
		// メッセージキュー使用設定の親階層存在チェック
		String parentUri = getMessageQueueStatusParentUri(uid);
		EntryBase parentEntry = reflexContext.getEntry(parentUri);
		if (parentEntry == null) {
			try {
				parentEntry = TaggingEntryUtil.createEntry(serviceName);
				parentEntry.setMyUri(parentUri);
				reflexContext.post(parentEntry);
			} catch (EntryDuplicatedException e) {
				// 重複エラーは別のスレッドで登録されているので何もしない
				if (isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
							"[setMessageQueueStatus] EntryDuplicatedException: " + parentUri);
				}
			}
		}
		
		// メッセージキュー使用設定を更新する。
		// キー: `/_user/{UID}/mqstatus/{チャネルの変換値}
		String uri = getMessageQueueStatusUri(channel, uid);
		EntryBase statusEntry = TaggingEntryUtil.createEntry(serviceName);
		statusEntry.setMyUri(uri);
		// titleにチャネルを設定
		// summaryにflagを設定
		statusEntry.title = channel;
		statusEntry.summary = String.valueOf(flag);
		reflexContext.put(statusEntry);
		
		// デバッグログ
		if (isDebugLog(serviceName) || isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[setMessageQueueStatus] channel=");
			sb.append(channel);
			sb.append(", uid=");
			sb.append(uid);
			sb.append(", flag=");
			sb.append(flag);
			String logMsg = sb.toString();
			
			if (isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + logMsg);
			}
			if (isDebugLog(serviceName)) {
				SystemContext systemContext = new SystemContext(auth, requestInfo, 
						reflexContext.getConnectionInfo());
				systemContext.log(MessageQueueConst.DEBUGLOG_TITLE, 
						MessageQueueConst.DEBUGLOG_SUBTITLE, logMsg);
			}
		}
	}
	
	/**
	 * チャネルの入力チェック.
	 * @param channel チャネル
	 */
	private void checkChannel(String channel) {
		// 入力チェック
		CheckUtil.checkUri(channel, "channel");
		// チャネル変換文字が使用されていればエラー
		int idx = channel.indexOf(MessageQueueConst.REPLACEMENT_SLASH);
		if (idx > -1) {
			throw new IllegalParameterException("Includes invalid characters : " + MessageQueueConst.REPLACEMENT_SLASH);
		}
	}

	/**
	 * メッセージキューへメッセージ送信
	 * @param messageFeed メッセージ
	 * @param channel チャネル
	 * @param reflexContext ReflexContext
	 */
	public void setMessageQueue(FeedBase messageFeed, String channel, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// 入力チェック
		checkChannel(channel);
		CheckUtil.checkNotNull(messageFeed, "messageFeed");
		CheckUtil.checkNotNull(messageFeed.entry, "message");
		// 認可チェック・チャネルグループ取得
		String channelGroup = checkAuthAndGetChannelGroup(channel, auth, requestInfo, connectionInfo);
		// チャネルグループのメンバーUIDリストを取得
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		List<String> channelGroupUids = getGroupMemberUids(channelGroup, systemContext);
		
		for (EntryBase messageEntry : messageFeed.entry) {
			setMessageQueue(messageEntry, channel, channelGroup, channelGroupUids, 
					systemContext, reflexContext);
		}
	}

	/**
	 * メッセージキューへメッセージ送信
	 * @param messageEntry メッセージ
	 * @param channel チャネル
	 * @param channelGroup チャネルグループ
	 * @param channelGroupUids チャネルグループの参加UIDリスト
	 * @param systemContext SystemContext (フォルダ登録用)
	 * @param reflexContext ReflexContext (メッセージ登録用)
	 */
	private void setMessageQueue(EntryBase messageEntry, String channel, String channelGroup, 
			List<String> channelGroupUids, SystemContext systemContext,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		// Push通知管理クラス
		PushNotificationManager pushNotificationManager =
				TaggingEnvUtil.getPushNotificationManager();
		if (pushNotificationManager == null) {
			logger.warn("[setMessageQueue] PushNotificationManager setting is nothing.");
		}
		// このメッセージがPush通知対象かどうか
		boolean pushMsg = !MessageQueueConst.TRUE.equals(messageEntry.rights) && 
				!StringUtils.isBlank(messageEntry.getContentText()) &&
				pushNotificationManager != null;
		// Push通知送信対象UID
		List<String> pushNotificationUids = new ArrayList<>();
		
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[setMessageQueue] channel = ");
			sb.append(channel);
			sb.append(", channelGroup = ");
			sb.append(channelGroup);
			sb.append(", uids = ");
			sb.append(channelGroupUids);
			logger.debug(sb.toString());
		}

		// link rel="to"のhref属性に、送信先
		//   "uid or useraccount or *"
		//   または "#" の場合、ポーリングのため送信なし (WebSocketの名残)
		List<String> tos = new ArrayList<>();
		boolean isPolling = false;
		boolean isGroup = false;
		for (Link link : messageEntry.link) {
			if (MessageQueueConst.REL_TO.equals(link._$rel) &&
					!StringUtils.isBlank(link._$href)) {
				if (MessageQueueConst.POLLING.equals(link._$href)) {
					// 送信先が # の場合はポーリングのため送信なし
					isPolling = true;
				} else if (MessageQueueConst.WILDCARD.equals(link._$href)) {
					// 送信先が * の場合はチャネルグループ指定
					isGroup = true;
				} else {
					// UIDまたはアカウント
					// フォーマットチェック
					checkTo(link._$href);
					tos.add(link._$href);
				}
			}
		}
		if (isPolling && tos.isEmpty()) {
			return;	// ポーリングのため送信なし
		}
		if (!isGroup) {
			CheckUtil.checkNotNull(tos, "destination");
		}

		// summary に送信メッセージ
		String message = messageEntry.summary;
		CheckUtil.checkNotNull(message, "message");

		// 送信対象をユーザリストにする
		Set<String> toUids = new HashSet<>();
		if (isGroup && channelGroupUids != null && !channelGroupUids.isEmpty()) {
			toUids.addAll(channelGroupUids);
		}
		for (String to : tos) {
			// UIDまたはアカウントから、UIDを取得する。
			String uid = getUidByUser(to, systemContext);
			// このユーザがチャネルグループのグループに参加していない場合はエラー
			if (!channelGroupUids.contains(uid)) {
				// グループエントリーがあり未署名の場合はPush通知対象
				if (!isPushNotificationTarget(channelGroup, uid, systemContext)) {
					throw new IllegalParameterException("Please specify a member of the group.");
				}
				// Push通知受信設定をチェック
				if (pushMsg && !isDisablePushNotification(channelGroup, uid, systemContext)) {
					pushNotificationUids.add(uid);
				}
			} else {
				toUids.add(uid);
			}
		}

		// 自分には送信しないため、送信先ユーザリストから外す。
		String myUid = auth.getUid();
		if (toUids.contains(myUid)) {
			toUids.remove(myUid);
		}

		List<String> messageQueueUids = new ArrayList<>();
		for (String toUid : toUids) {
			// メッセージキュー使用設定をEntry検索する。
			// キー: /_user/{UID}/mqstatus/{チャネルの変換値}
			String mqstatusUri = getMessageQueueStatusUri(channel, toUid);
			EntryBase mqstatusEntry = systemContext.getEntry(mqstatusUri);
			boolean useMq = false;
			if (mqstatusEntry != null && MessageQueueConst.TRUE.equals(mqstatusEntry.summary)) {
				useMq = true;
			}
			
			if (useMq) {
				// メッセージキュー使用設定がONの場合、メッセージキューにメッセージを登録する。
				EntryBase tmpMessageEntry = copyEntry(messageEntry, serviceName);
				setMessageQueue(tmpMessageEntry, channel, channelGroup, toUid,
						systemContext, reflexContext);
				messageQueueUids.add(toUid);
			} else {
				// メッセージキュー使用設定がOFFの場合、Push通知を送信する。
				// (メッセージキュー使用設定が登録されていない場合、OFFとみなす。)
				// Push通知受信設定をチェック
				if (pushMsg && !isDisablePushNotification(channelGroup, toUid, systemContext)) {
					pushNotificationUids.add(toUid);
				}
			}
		}
		
		// グループエントリーの登録があり、署名がまだのユーザにもPush送信を行う。
		if (pushMsg && isGroup) {
			List<String> allUidsOfGroup = getGroupEntryUids(
					channelGroup, systemContext);
			if (allUidsOfGroup != null) {
				for (String uidOfGroup : allUidsOfGroup) {
					if (!myUid.equals(uidOfGroup) && !toUids.contains(uidOfGroup)) {
						// Push通知受信設定をチェック
						if (!isDisablePushNotification(channelGroup, 
								uidOfGroup, systemContext)) {
							pushNotificationUids.add(uidOfGroup);
						}
					}
				}
			}
		}
		
		// Push通知
		if (pushMsg && !pushNotificationUids.isEmpty()) {
			pushNotificationManager.pushNotification(messageEntry, pushNotificationUids,
					auth, requestInfo, systemContext.getConnectionInfo());
		}
		
		// デバッグログ
		if (isDebugLog(serviceName) || isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[setMessageQueue] channel=");
			sb.append(channel);
			sb.append(", myUid=");
			sb.append(myUid);
			sb.append(", toUids=");
			boolean isFirst = true;
			for (String toUid : toUids) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(toUid);
			}
			sb.append(", messageQueue uids=");
			isFirst = true;
			for (String tmpUid : messageQueueUids) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(tmpUid);
			}
			sb.append(", pushNotification target uids (If there is no setting, it cannot be sent.) =");
			isFirst = true;
			for (String tmpUid : pushNotificationUids) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(tmpUid);
			}
			sb.append(", message entry=");
			sb.append(TaggingEnvUtil.getResourceMapper(serviceName).toJSON(messageEntry));
			String logMsg = sb.toString();
			
			if (isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + logMsg);
			}
			if (isDebugLog(serviceName)) {
				systemContext.log(MessageQueueConst.DEBUGLOG_TITLE, 
						MessageQueueConst.DEBUGLOG_SUBTITLE, logMsg);
			}
		}
	}
	
	/**
	 * メッセージキューを登録.
	 * @param messageEntry メッセージ
	 * @param channel チャネル
	 * @param channelGroup チャネルグループ
	 * @param toUid 送信先UID
	 * @param systemContext SystemContext (親フォルダ登録用)
	 * @param reflexContext ReflexContext
	 */
	private void setMessageQueue(EntryBase messageEntry, String channel, String channelGroup,
			String toUid, SystemContext systemContext, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		// 親階層の存在チェック
		String mqParentUri = getMessageQueueParentUri(channel, toUid);
		EntryBase mqParentEntry = systemContext.getEntry(mqParentUri);
		if (mqParentEntry == null) {
			try {
				mqParentEntry = TaggingEntryUtil.createEntry(serviceName);
				mqParentEntry.setMyUri(mqParentUri);
				TaggingEntryUtil.addAclToEntry(mqParentEntry, channelGroup, 
						MessageQueueConst.ACL_TYPE_MQ_GROUP);
				TaggingEntryUtil.addAclToEntry(mqParentEntry, toUid, 
						MessageQueueConst.ACL_TYPE_MQ_UID);
				systemContext.post(mqParentEntry);
				
			} catch (EntryDuplicatedException e) {
				// 重複エラーは別のスレッドで登録されているので何もしない
				if (isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
							"[setMessageQueue] EntryDuplicatedException: " + mqParentUri);
				}
			}
		}
		
		// メッセージキュー登録
		// メッセージキューのキーを生成、セット
		String mqUri = getMqUri(systemContext, mqParentUri);
		messageEntry.setMyUri(mqUri);
		// キー: /_mq/{チャネルの変換値}@{UID}/{連番0埋め}
		reflexContext.post(messageEntry, mqParentUri);
	}
	
	/**
	 * メッセージキューのキーを取得.
	 * @param reflexContext ReflexContext
	 * @param mqParentUri メッセージキューフォルダ
	 * @return メッセージキューのキー
	 */
	private String getMqUri(ReflexContext reflexContext, String mqParentUri) 
	throws IOException, TaggingException {
		String mqNum = getMqNum(reflexContext, mqParentUri);
		StringBuilder sb = new StringBuilder();
		sb.append(mqParentUri);
		sb.append("/");
		sb.append(mqNum);
		return sb.toString();
	}

	/**
	 * メッセージキューのキーのselfidの取得.
	 * 登録順に並べる必要があるため桁を揃える。
	 * @param reflexContext ReflexContext
	 * @param addidsUri メッセージキューフォルダ
	 * @return メッセージキューのキーのselfid
	 */
	private String getMqNum(ReflexContext reflexContext, String addidsUri)
	throws IOException, TaggingException {
		FeedBase feed = reflexContext.addids(addidsUri, 1);
		if (feed != null) {
			String num = feed.title;
			if (!StringUtils.isBlank(num)) {
				long numl = StringUtils.longValue(num);
				if (numl > 0) {
					return StringUtils.zeroPadding(numl, MessageQueueConst.MQ_NUM_LEN);
				}
			}
		}
		throw new IllegalStateException("Failed to addids.");
	}

	/**
	 * ログインチェック.
	 * @param auth 認証情報
	 */
	private void checkAuth(ReflexAuthentication auth) throws TaggingException {
		// 認証情報なしはエラー
		String uid = auth.getUid();
		if (StringUtils.isBlank(uid)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Not logged in with message queue.");
			throw pe;
		}
	}
	
	/**
	 * メッセージキュー利用認可チェックを行い、チャネルグループを返却する.
	 * @param channel チャネル
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return チャネルグループ
	 */
	private String checkAuthAndGetChannelGroup(String channel, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 認証情報なしはエラー
		checkAuth(auth);

		// このユーザが指定したグループのメンバーでなければエラー
		String channelGroup = getChannelGroup(channel);
		if (!isMemberOfGroup(auth, channelGroup)) {
			StringBuilder sb = new StringBuilder();
			sb.append("The user is not in this group. ");
			sb.append(channelGroup);
			sb.append(" [mygroup] ");
			sb.append(auth.getGroups());
			PermissionException pe = new PermissionException();
			pe.setSubMessage(sb.toString());
			throw pe;
		}

		return channelGroup;
	}

	/**
	 * チャネルグループを取得.
	 * URIの第一階層がチャネル、第二階層以降がチャネルグループ
	 * @param uri URI
	 * @return チャネルグループ
	 */
	private String getChannelGroup(String uri) {
		String group = null;
		int idx = uri.indexOf("/", 1);
		if (idx > 0) {
			group = uri.substring(idx);
			return group;
		}
		return null;
	}

	/**
	 * 指定された認証情報のユーザは指定されたグループのメンバーかどうか判定.
	 * @param auth 認証情報
	 * @param channelGroup グループ
	 * @return グループのメンバーであればtrue
	 */
	private boolean isMemberOfGroup(ReflexAuthentication auth, String channelGroup) {
		List<String> groups = auth.getGroups();
		if (groups != null && groups.contains(channelGroup)) {
			return true;
		}
		return false;
	}
	
	/**
	 * メッセージキュー使用設定の親URIを取得.
	 *   /_user/{UID}/mqstatus
	 * @param uid UID
	 * @return メッセージキュー使用設定URI
	 */
	private String getMessageQueueStatusParentUri(String uid) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		StringBuilder sb = new StringBuilder();
		sb.append(userManager.getUserTopUriByUid(uid));
		sb.append(MessageQueueConst.URI_LAYER_MQSTATUS);
		return sb.toString();
	}

	/**
	 * メッセージキュー使用設定URIを取得.
	 *   /_user/{UID}/mqstatus/{チャネルの変換値}
	 * @param channel チャネル
	 * @param uid UID
	 * @return メッセージキュー使用設定URI
	 */
	private String getMessageQueueStatusUri(String channel, String uid) {
		String tmpChannel = convertChannel(channel);
		StringBuilder sb = new StringBuilder();
		sb.append(getMessageQueueStatusParentUri(uid));
		sb.append("/");
		sb.append(tmpChannel);
		return sb.toString();
	}
	
	/**
	 * メッセージキューの親URIを取得.
	 *   /_mq/{チャネルの変換値}@{UID}
	 * @param channel チャネル
	 * @param uid UID
	 * @return メッセージキューの親URI
	 */
	private String getMessageQueueParentUri(String channel, String uid) {
		String tmpChannel = convertChannel(channel);
		StringBuilder sb = new StringBuilder();
		sb.append(MessageQueueConst.URI_MQ);
		sb.append("/");
		sb.append(tmpChannel);
		sb.append(MessageQueueConst.DELIMITER_CHANNEL_URI);
		sb.append(uid);
		return sb.toString();
	}
	
	/**
	 * チャネルを階層なしの文字列に変換する.
	 *  "/"を"___"に変換する。
	 * @param channel チャネル
	 * @return 変換した文字列
	 */
	private String convertChannel(String channel) {
		return channel.replaceAll("\\/", MessageQueueConst.REPLACEMENT_SLASH);
	}

	/**
	 * グループメンバーのUIDリストを取得します.
	 * @param groupParentUri グループ
	 * @param systemContext SystemContext
	 * @return グループリスト
	 */
	private List<String> getGroupMemberUids(String groupParentUri, SystemContext systemContext)
	throws IOException, TaggingException {
		return GroupUtil.getGroupMemberUids(groupParentUri, systemContext);
	}

	/**
	 * メッセージ送信先の入力チェック
	 * @param to UIDまたはアカウント
	 */
	private void checkTo(String to) {
		if (to.startsWith("/")) {
			throw new IllegalParameterException("Please specify UID or account. " + to);
		}
	}

	/**
	 * UIDまたはアカウントの存在チェックを行い、UIDを返却する.
	 * @param user UIDまたはアカウント
	 * @param reflexContext ReflexContext
	 * @return UID
	 */
	private String getUidByUser(String user, SystemContext systemContext)
	throws IOException, TaggingException {
		UserBlogic userBlogic = new UserBlogic();
		return userBlogic.getUidByUser(user, systemContext);
	}
	
	/**
	 * UIDがPush通知の対象かどうか判定.
	 * UIDがグループに登録されているかどうか、グループメンバーエントリーを取得する。
	 * 自身の署名の有無は判定せず、グループ管理者の署名がある場合グループ登録されているとみなす。
	 * ただし自身のグループエイリアスがない場合はPush通知対象でない。（自身による脱退）
	 * @param groupUri グループ
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return Push通知の対象である場合true
	 */
	private boolean isPushNotificationTarget(String groupUri, String uid,
			SystemContext systemContext) 
	throws IOException, TaggingException {
		String groupMemberUri = groupUri + "/" + uid;
		EntryBase groupEntry = systemContext.getEntry(groupMemberUri);
		return GroupUtil.isValidGroupAdmin(groupEntry, systemContext);
	}
	
	/**
	 * Push通知をオフにしているかどうかを判定.
	 * @param groupUri グループURI
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return Push通知がオフの場合true
	 */
	private boolean isDisablePushNotification(String groupUri, String uid, 
			ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String disablePushNotificationUri = getDisablePushNotificationUri(groupUri, uid);
		EntryBase disablePushNotificationEntry = reflexContext.getEntry(disablePushNotificationUri);
		if (disablePushNotificationEntry != null) {
			List<Contributor> contributors = disablePushNotificationEntry.getContributor();
			if (contributors != null) {
				for (Contributor contributor : contributors) {
					if (isDisablePushNotification(contributor.uri)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * プッシュ通知がオフかどうか判定.
	 * @param urn contributor.uriの値
	 * @return プッシュ通知がオフの場合true
	 */
	private boolean isDisablePushNotification(String urn) {
		if (!StringUtils.isBlank(urn) && urn.startsWith(
				MessageQueueConst.URN_DISABLE_NOTIFICATION)) {
			String val = urn.substring(MessageQueueConst.URN_DISABLE_NOTIFICATION_LEN);
			return MessageQueueConst.TRUE.equals(val);
		}
		return false;
	}

	/**
	 * {グループURI}/{UID}/disable_notification を返却する.
	 * @param groupUri グループURI
	 * @param uid UID
	 * @return {グループURI}/{UID}/disable_notification
	 */
	private String getDisablePushNotificationUri(String groupUri, String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(groupUri);
		sb.append("/");
		sb.append(uid);
		sb.append(MessageQueueConst.URI_DISABLE_NOTIFICATION);
		return sb.toString();
	}
	
	/**
	 * メッセージキューからメッセージを受信.
	 * @param channel
	 * @param reflexContext
	 * @return メッセージリスト
	 */
	public FeedBase getMessageQueue(String channel, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// 入力チェック
		checkChannel(channel);
		// ログイン・認可チェック
		checkAuthAndGetChannelGroup(channel, auth, requestInfo, connectionInfo);
		// メッセージキュー検索
		String uid = auth.getUid();
		String mqParentUri = getMessageQueueParentUri(channel, uid);
		// 親フォルダが存在しない場合は処理を抜ける (そのままFeed検索すると認可エラーが発生するため)
		SystemContext systemContext = new SystemContext(auth, 
				requestInfo, connectionInfo);
		EntryBase mqParentEntry = systemContext.getEntry(mqParentUri);
		if (mqParentEntry == null) {
			return null;
		}

		List<EntryBase> retEntries = new ArrayList<>();
		String cursorStr = null;
		int fetchLimit = TaggingEnvUtil.getFetchLimit();
		int cnt = 0;
		List<Future> futures = new ArrayList<>();
		do {
			String tmpUri = TaggingEntryUtil.addCursorToUri(mqParentUri, 
					cursorStr);

			FeedBase mqFeed = reflexContext.getFeed(tmpUri);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(mqFeed);
			// メッセージを抽出
			if (TaggingEntryUtil.isExistData(mqFeed)) {
				for (EntryBase mqEntry : mqFeed.entry) {
					EntryBase retEntry = TaggingEntryUtil.createEntry(serviceName);
					retEntry.summary = mqEntry.summary;
					retEntries.add(retEntry);
				}
				cnt += mqFeed.entry.size();
				
				// メッセージを返却したメッセージキューを削除する。(非同期)
				List<Future<List<UpdatedInfo>>> tmpFutures = systemContext.bulkDelete(mqFeed, true);
				if (tmpFutures != null) {
					futures.addAll(tmpFutures);
				}
			}
			
		} while (!StringUtils.isBlank(cursorStr) && cnt < fetchLimit);
		
		int size = retEntries.size();
		FeedBase retFeed = null;
		if (size > 0) {
			retFeed = TaggingEntryUtil.createFeed(serviceName);
			retFeed.entry = retEntries;
			if (!StringUtils.isBlank(cursorStr)) {
				retFeed.rights = MessageQueueConst.MARK_FETCH_LIMIT;
			} 
		}
		
		// メッセージキュー削除処理の終了を待つ。(メッセージ二重取得を防ぐため)
		int sleepMillis = TaggingEnvUtil.getSystemPropInt(
				MessageQueueConst.MESSAGEQUEUE_DELETE_WAITMILLIS, 
				MessageQueueConst.MESSAGEQUEUE_DELETE_WAITMILLIS_DEFAULT);
		int i = -1;
		int futruesSize = futures.size();
		for (Future future : futures) {
			if (logger.isDebugEnabled()) {
				i++;
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getMessageQueue] delete message ");
				sb.append(i);
				sb.append("/");
				sb.append(futruesSize);
				logger.debug(sb.toString());
			}
			int sleepCnt = 0;
			while (!future.isDone()) {
				if (logger.isDebugEnabled()) {
					sleepCnt++;
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getMessageQueue] delete message - sleep ");
					sb.append(sleepCnt);
					logger.debug(sb.toString());
				}
				RetryUtil.sleep(sleepMillis);
			}
		}

		// デバッグログ
		if (isDebugLog(serviceName) || isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getMessageQueue] channel=");
			sb.append(channel);
			sb.append(", uid=");
			sb.append(uid);
			sb.append(" get ");
			sb.append(size);
			if (size > 1) {
				sb.append(" messages.");
			} else {
				sb.append(" message.");
			}
			if (size > 0) {
				sb.append(" messageQueue feed=");
				sb.append(TaggingEnvUtil.getResourceMapper(serviceName).toJSON(retFeed));
			}
			
			String logMsg = sb.toString();
			
			if (isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + logMsg);
			}
			if (isDebugLog(serviceName)) {
				systemContext.log(MessageQueueConst.DEBUGLOG_TITLE, 
						MessageQueueConst.DEBUGLOG_SUBTITLE, logMsg);
			}
		}

		return retFeed;
	}
	
	/**
	 * グループに登録されているUIDリストを取得.
	 * 自身の署名の有無は判定せず、グループ管理者の署名のあるすべてのUIDを返却する。
	 * @param groupUri グループ
	 * @param systemContext SystemContext
	 * @return グループに登録されているUIDリスト
	 */
	private List<String> getGroupEntryUids(String groupUri, SystemContext systemContext) 
	throws IOException, TaggingException {
		List<String> uids = new ArrayList<String>();
		String cursorStr = null;
		do {
			FeedBase feed = systemContext.getFeed(groupUri, true);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (TaggingEntryUtil.isExistData(feed)) {
				for (EntryBase groupEntry : feed.entry) {
					// グループ管理者の署名のみチェック
					boolean isValid = GroupUtil.isValidGroupAdmin(groupEntry, systemContext);
					if (isValid) {
						String uid = GroupUtil.getUidByGroup(groupEntry);
						uids.add(uid);
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));
		
		return uids;
	}
	
	/**
	 * メッセージキュー未送信チェック.
	 * @param systemContext 対象サービスのSystemContext
	 */
	public void checkMessageQueue(SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// 現在時刻を取得し、未送信チェックによるPush通知時間(分)を差し引く。
		Date now = new Date();
		int expireMin = TaggingEnvUtil.getPropInt(serviceName,
				MessageQueueConst.MESSAGEQUEUE_EXPIRE_MIN, 
				MessageQueueConst.MESSAGEQUEUE_EXPIRE_MIN_DEFAULT);
		Date checkDate = DateUtil.addTime(now, 0, 0, 0, 0, 0 - expireMin, 0, 0);
		String checkDateStr = DateUtil.getDateTimeMillisec(checkDate, 
				TimeZone.getDefault().getID());
		
		// メッセージキューをFeed検索
		// キー: /_mq
		String cursorStr = null;
		do {
			String tmpUri = TaggingEntryUtil.addCursorToUri(MessageQueueConst.URI_MQ, 
					cursorStr);
			FeedBase mqParentFeed = systemContext.getFeed(tmpUri);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(mqParentFeed);
			// 配下にデータが存在する場合、さらにFeed検索
			if (TaggingEntryUtil.isExistData(mqParentFeed)) {
				for (EntryBase mqParentEntry : mqParentFeed.entry) {
					checkMessageQueue(mqParentEntry.getMyUri(), checkDateStr, systemContext);
				}
			}
			
		} while (!StringUtils.isBlank(cursorStr));
	}
	
	/**
	 * メッセージキュー未送信チェック.
	 *  /_mq 配下のエントリーのさらに配下を検索する。(キー: /_mq/{チャネルの変換値}@{UID})
	 * @param mqParentUri メッセージキュー親階層URI
	 * @param checkDateStr 比較時刻文字列
	 * @param systemContext 対象サービスのSystemContext
	 */
	private void checkMessageQueue(String mqParentUri, String checkDateStr, 
			SystemContext systemContext) 
	throws IOException, TaggingException {
		// メッセージキューをFeed検索
		// キー: /_mq
		String cursorStr = null;
		do {
			String tmpUri = TaggingEntryUtil.addCursorToUri(mqParentUri, 
					cursorStr);
			FeedBase mqFeed = systemContext.getFeed(tmpUri);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(mqFeed);
			if (TaggingEntryUtil.isExistData(mqFeed)) {
				for (EntryBase mqEntry : mqFeed.entry) {
					checkMessageQueue(mqEntry, checkDateStr, systemContext);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));
	}
	
	/**
	 * メッセージキュー未送信チェック.
	 *  updatedが「現在時刻」-「未送信チェックによるPush通知時間(秒)」より過去の場合処理を行う。
	 * @param messageEntry メッセージデータ
	 * @param checkDateStr 比較時刻文字列
	 * @param systemContext 対象サービスのSystemContext
	 */
	private void checkMessageQueue(EntryBase messageEntry, String checkDateStr, 
			SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		String messageUri = messageEntry.getMyUri();
		String uid = getUidByMessageQueueUri(messageUri);
		String channel = getChannelByMessageQueueUri(messageUri);
		String channelGroup = getChannelGroup(channel);
		// updatedチェック
		if (messageEntry.updated.compareTo(checkDateStr) <= 0) {
			// 「現在時刻」-「未送信チェックによるPush通知時間(秒)」より過去に登録された場合
			// Push通知管理クラス
			PushNotificationManager pushNotificationManager =
					TaggingEnvUtil.getPushNotificationManager();
			if (pushNotificationManager == null) {
				logger.warn("[checkMessageQueue] PushNotificationManager setting is nothing.");
			}
			// このメッセージがPush通知対象かどうか
			boolean pushMsg = !MessageQueueConst.TRUE.equals(messageEntry.rights) && 
					pushNotificationManager != null &&
					!isDisablePushNotification(channelGroup, uid, systemContext);
			// Push通知・ログ用に不要な情報を削除したEntryを生成
			EntryBase tmpMessageEntry = createMessageEntryEdited(messageEntry, serviceName);
			if (pushMsg) {
				// rightsがtrueでない場合、Push通知を送信する。
				List<String> uids = new ArrayList<>();
				uids.add(uid);
				pushNotificationManager.pushNotification(tmpMessageEntry, uids,
						systemContext.getAuth(), requestInfo, 
						systemContext.getConnectionInfo());
			}
			// メッセージデータを削除する。
			systemContext.delete(messageUri);

			// デバッグログ
			if (isDebugLog(serviceName) || isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[checkMessageQueue] deleted. channel=");
				sb.append(channel);
				sb.append(", uid=");
				sb.append(uid);
				if (pushMsg) {
					sb.append(" - pushNotification target. (If there is no setting, it cannot be sent.)");
				}
				sb.append(" message entry=");
				sb.append(TaggingEnvUtil.getResourceMapper(serviceName).toJSON(tmpMessageEntry));

				String logMsg = sb.toString();
				
				if (isEnableAccessLog()) {
					logger.debug(logMsg);
				}
				if (isDebugLog(serviceName)) {
					systemContext.log(MessageQueueConst.DEBUGLOG_TITLE, 
							MessageQueueConst.DEBUGLOG_SUBTITLE, logMsg);
				}
			}
		}
	}
	
	/**
	 * メッセージキューURIからUIDを取得.
	 *   /_mq/{チャネルの変換値}@{UID}/{連番}
	 * @param uri メッセージキューURI
	 * @return UID
	 */
	private String getUidByMessageQueueUri(String uri) {
		String parentUri = TaggingEntryUtil.removeLastSlash(
				TaggingEntryUtil.getParentUri(uri));
		int idx = parentUri.lastIndexOf(MessageQueueConst.DELIMITER_CHANNEL_URI);
		return parentUri.substring(idx + 1);
	}
	
	/**
	 * メッセージキューURIからチャネルを取得.
	 *   /_mq/{チャネルの変換値}@{UID}/{連番}
	 * @param uri メッセージキューURI
	 * @return チャネル
	 */
	private String getChannelByMessageQueueUri(String uri) {
		String parentUri = TaggingEntryUtil.removeLastSlash(
				TaggingEntryUtil.getParentUri(uri));
		int idx = parentUri.lastIndexOf(MessageQueueConst.DELIMITER_CHANNEL_URI);
		String convertedChannel = parentUri.substring(MessageQueueConst.URI_MQ_LEN1, idx);
		return convertedChannel.replaceAll(MessageQueueConst.REPLACEMENT_SLASH_REGEX, "/");
	}

	/**
	 * Notificationデバッグログエントリー出力処理を行うかどうか.
	 * @return Notificationデバッグログエントリー出力処理を行う場合true
	 */
	private boolean isDebugLog(String serviceName) {
		try {
			return TaggingEnvUtil.getPropBoolean(serviceName,
					MessageQueueConst.DEBUGLOG_NOTIFICATION, false);
		} catch (InvalidServiceSettingException e) {
			return false;
		}
	}
	
	/**
	 * メッセージキュー機能のアクセスログを出力するかどうか.
	 * @return メッセージキュー機能へのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return logger.isDebugEnabled() && TaggingEnvUtil.getSystemPropBoolean(
				MessageQueueConst.MESSAGEQUEUE_ENABLE_ACCESSLOG, false);
	}
	
	/**
	 * Entryのコピー.
	 * @param messageEntry Entry
	 * @param serviceName サービス名
	 * @return コピーしたEntry
	 */
	private EntryBase copyEntry(EntryBase messageEntry, String serviceName) {
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		return TaggingEntryUtil.copyEntry(messageEntry, mapper);
	}
	
	/**
	 * Push通知用・ログ出力用に不要な情報を削除したEntryを生成する.
	 * @param messageEntry メッセージエントリー
	 * @param serviceName サービス名
	 * @return Push通知用・ログ出力用に不要な情報を削除したEntry
	 */
	private EntryBase createMessageEntryEdited(EntryBase messageEntry, String serviceName) {
		EntryBase retEntry = copyEntry(messageEntry, serviceName);
		retEntry.author = null;
		retEntry.contributor = null;
		retEntry.id = null;
		if (retEntry.link != null) {
			Deque<Integer> deque = new ArrayDeque<>();
			int size = retEntry.link.size();
			for (int i = 0; i < size; i++) {
				Link link = retEntry.link.get(i);
				if (!MessageQueueConst.REL_TO.equals(link._$rel)) {
					deque.push(i);
				}
			}
			while(!deque.isEmpty()) {
				int i = deque.pop();
				retEntry.link.remove(i);
			}
		}
		retEntry.published = null;
		retEntry.updated = null;
		return retEntry;
	}

}
