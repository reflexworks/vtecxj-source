package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexServletBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.AllocateIdsBlogic;
import jp.reflexworks.taggingservice.blogic.DatastoreBlogic;
import jp.reflexworks.taggingservice.blogic.IncrementBlogic;
import jp.reflexworks.taggingservice.blogic.LogBlogic;
import jp.reflexworks.taggingservice.blogic.MonitorBlogic;
import jp.reflexworks.taggingservice.blogic.RefreshBlogic;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.exception.SignatureInvalidException;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * TaggingService サーブレット.
 * リクエストでデータストアに対するCRUD処理を実行するサーブレット
 */
public class TaggingServlet extends ReflexServletBase {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	@Override
	public void init() throws ServletException {
		super.init();
	}

	/**
	 * GETメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doGet] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doGet] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doGet start");
		}
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
		reflexContext.getAuth().setExternal(false);
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;
			ReflexContentInfo contentInfo = null;

			if (param.getOption(RequestParam.PARAM_MONITOR) != null) {
				// モニター
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_monitor");
				}
				MonitorBlogic monitorBlogic = new MonitorBlogic();
				retObj = monitorBlogic.monitor(req, resp);

			} else if (param.getOption(RequestParam.PARAM_UID) != null) {
				// UID取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_uid");
				}
				// 認証済みかどうかチェック
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.checkAuth(reflexContext.getAuth());
				// UID取得処理
				String uid = reflexContext.getUid();
				retObj = MessageUtil.createMessageFeed(uid, serviceName);

			} else if (param.getOption(RequestParam.PARAM_ACCOUNT) != null) {
				// アカウント取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_account");
				}
				// 認証済みかどうかチェック
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.checkAuth(reflexContext.getAuth());
				// アカウント取得処理
				String account = reflexContext.getAccount();
				retObj = MessageUtil.createMessageFeed(account, serviceName);

			} else if (param.getOption(RequestParam.PARAM_WHOAMI) != null) {
				// whoami(ログインユーザの情報取得)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_whoami");
				}
				retObj = reflexContext.whoami();

			} else if (param.getOption(RequestParam.PARAM_SERVICE) != null) {
				// 自サービスのステータス取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_service");
				}
				// 認証済みかどうかチェック
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.checkAuth(reflexContext.getAuth());
				// サービス取得
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				retObj = serviceBlogic.getService(reflexContext);

			} else if (param.getOption(RequestParam.PARAM_GETRXID) != null) {
				// RXID取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_getrxid");
				}
				String rxid = reflexContext.getRXID();
				retObj = createMessageFeed(rxid, serviceName);

			} else if (param.getOption(RequestParam.PARAM_ACCESSTOKEN) != null) {
				// アクセストークン取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_accesstoken");
				}
				String ret = reflexContext.getAccessToken();
				retObj = createMessageFeed(ret, serviceName);

			} else if (param.getOption(RequestParam.PARAM_LINKTOKEN) != null) {
				// リンクトークン取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_linktoken");
				}
				String ret = reflexContext.getLinkToken(param.getUri());
				retObj = createMessageFeed(ret, serviceName);

			} else if (param.getOption(RequestParam.PARAM_SIGNATURE) != null) {
				// 署名検証
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signature");
				}
				boolean chk = reflexContext.checkSignature(param.getUri());
				if (chk) {
					retObj = createMessageFeed(msgManager.getMsgValidSignature(serviceName), serviceName);
				} else {
					throw new SignatureInvalidException();
				}

			} else if (param.getOption(RequestParam.PARAM_USERSTATUS) != null) {
				// ユーザステータス参照
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_userstatus");
				}
				String email = param.getOption(RequestParam.PARAM_USERSTATUS);
				if (!StringUtils.isBlank(email)) {
					// 指定されたユーザのステータスを取得
					retObj = reflexContext.getUserstatus(email);
				} else {
					// ユーザステータス一覧を取得
					retObj = reflexContext.getUserstatusList(param);
				}
				// ユーザが登録されていない場合はエラー
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(RequestParam.PARAM_ALLOCIDS) != null) {
				// 自動採番
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_allocids");
				}
				int num = AllocateIdsBlogic.intValue(
						param.getOption(RequestParam.PARAM_ALLOCIDS));
				retObj = reflexContext.allocids(param.getUri(), num);

			} else if (param.getOption(RequestParam.PARAM_RANGEIDS) != null) {
				// 加算枠取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_rangeids");
				}
				retObj = reflexContext.getRangeids(param.getUri());

			} else if (param.getOption(RequestParam.PARAM_CACHEFEED) != null) {
				// Feed形式キャッシュ取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachefeed");
				}
				retObj = reflexContext.getCacheFeed(param.getUri());

			} else if (param.getOption(RequestParam.PARAM_CACHEENTRY) != null) {
				// Entry形式キャッシュ取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheentry");
				}
				retObj = reflexContext.getCacheEntry(param.getUri());

			} else if (param.getOption(RequestParam.PARAM_CACHESTRING) != null) {
				// 文字列形式キャッシュ取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachestring");
				}
				String retStr = reflexContext.getCacheString(param.getUri());
				retObj = MessageUtil.createMessageFeed(retStr, serviceName);

			} else if (param.getOption(RequestParam.PARAM_CACHELONG) != null) {
				// 数値形式キャッシュ取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachelong");
				}
				Long retNum = reflexContext.getCacheLong(param.getUri());
				retObj = MessageUtil.createMessageFeed(retNum, serviceName);

			} else if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				if (param.getOption(RequestParam.PARAM_SIGNEDURL) != null) {
					// 署名付きURL取得
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signedurl");
					}
					retObj = reflexContext.getContentSignedUrl(param.getUri());
					
				} else {
					// コンテンツ取得
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content");
					}
					contentInfo = reflexContext.getContent(param.getUri());
					if (contentInfo == null) {
						throw new NoEntryException();
					}
				}

			} else if (param.getOption(RequestParam.PARAM_ADDIDS) != null) {
				// 現在番号取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addids");
				}
				Set<String> ignores = new HashSet<String>();
				ignores.add("0");
				CheckUtil.checkNotSpecified(param.getOption(RequestParam.PARAM_ADDIDS),
						"The addition number", ignores);
				retObj = reflexContext.addids(param.getUri(), 0);

			} else if (param.getOption(RequestParam.PARAM_GETIDS) != null) {
				// 現在番号取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_getids");
				}
				retObj = reflexContext.getids(param.getUri());

			} else if (param.getOption(RequestParam.PARAM_REFRESHCACHE) != null) {
				// データストアキャッシュリフレッシュ
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_refreshcache");
				}
				RefreshBlogic refreshBlogic = new RefreshBlogic();
				refreshBlogic.refreshCache(req, reflexContext.getAuth(),
						reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
				status = HttpStatus.SC_ACCEPTED;
				retObj = MessageUtil.createMessageFeed("To refresh the cache accepted.", serviceName);

			} else if (param.getOption(RequestParam.PARAM_GETTOTP) != null) {
				// ２段階認証(TOTP)参照
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_gettotp");
				}
				UserBlogic userBlogic = new UserBlogic();
				retObj = userBlogic.getTotp(req, reflexContext);

			} else if (param.getOption(RequestParam.PARAM_GROUP) != null) {
				// 参加グループリストを返す
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_group");
				}
				// 認証済みかどうかチェック
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.checkAuth(reflexContext.getAuth());
				// group取得処理
				retObj = reflexContext.getGroups();
				// グループに参加していない場合はエラー
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(RequestParam.PARAM_IS_GROUP_MEMBER) != null) {
				// 指定されたグループのメンバーかどうか判定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_is_group_member");
				}
				// 認証済みかどうかチェック
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.checkAuth(reflexContext.getAuth());
				// グループ参加判定
				boolean isGroupMember = reflexContext.isGroupMember(param.getUri());
				retObj = createMessageFeed(String.valueOf(isGroupMember), serviceName);

			} else if (param.getOption(RequestParam.PARAM_NO_GROUP_MEMBER) != null) {
				// グループの配下のキーのエントリーで署名のないものを返す
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_no_group_member");
				}
				retObj = reflexContext.getNoGroupMember(param.getUri());

			} else if (param.getOption(RequestParam.PARAM_PAGINATION) != null) {
				// ページング
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_pagination");
				}
				// 同期処理
				retObj = reflexContext.pagination(param);

			} else if (param.getOption(RequestParam.PARAM_NUMBER) != null) {
				// ページ指定検索
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getPage");
				}
				retObj = reflexContext.getPage(param);
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(RequestParam.PARAM_COUNT) != null) {
				// 件数取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getCount");
				}
				retObj = reflexContext.getCount(param);
				status = getFeedStatus((FeedBase)retObj);

			} else if (param.getOption(RequestParam.PARAM_ENTRY) != null) {
				// Entry検索
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getEntry");
				}
				retObj = reflexContext.getEntry(param);
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(RequestParam.PARAM_CHECKINDEX) != null) {
				// Feed検索のインデックス使用チェック
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_checkindex");
				}
				retObj = reflexContext.checkIndex(param);

			} else if (param.getOption(RequestParam.PARAM_FEED) != null) {
				// Feed検索
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getFeed");
				}
				retObj = reflexContext.getFeed(param);
				if (retObj == null) {
					throw new NoEntryException();
				}
				status = getFeedStatus((FeedBase)retObj);

			} else {
				// コンテンツ取得 (Entryが存在しない場合はコンテンツ登録先を参照しない)
				// Etagが等しい場合本体は取得しない。
				contentInfo = reflexContext.getContent(param.getUri(), true);
				if (contentInfo == null) {
					throw new NoEntryException();
				}
			}

			if (contentInfo != null) {
				doContent(req, resp, contentInfo);
			} else if (retObj != null) {
				doResponse(req, resp, retObj, status);
			} else if (status != HttpStatus.SC_OK) {
				resp.setStatus(status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doPost] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doPost] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doPost start");
		}
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
		reflexContext.getAuth().setExternal(false);
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			Object retObj = null;
			int status = HttpStatus.SC_OK;

			String contentType = req.getContentType();
			if (contentType != null &&
					contentType.indexOf(CONTENT_TYPE_MULTIPART_FORMDATA) > -1) {
				// multipart/formdataによるコンテンツ登録
				FeedBase contentFeed = null;
				if (param.getOption(RequestParam.PARAM_BYSIZE) != null) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "put content bysize (multipart/form-data)");
					}
					contentFeed = reflexContext.putContentBySize();
				} else {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "put content (multipart/form-data)");
					}
					contentFeed = reflexContext.putContent();
				}
				retObj = MessageUtil.getUrisMessageFeed(contentFeed, serviceName);

			} else if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				if (param.getOption(RequestParam.PARAM_SIGNEDURL) != null) {
					// 自動採番し署名付きURL取得
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signedurl");
					}
					retObj = reflexContext.postContentSignedUrl(param.getUri(),
							param.getOption(RequestParam.PARAM_EXT));
					
				} else {
					// コンテンツ自動採番登録
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content");
					}
					FeedBase contentFeed = reflexContext.postContent(param.getUri(),
							param.getOption(RequestParam.PARAM_EXT));
					retObj = MessageUtil.getUrisMessageFeed(contentFeed, serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_ADDUSER) != null) {
				// ユーザ登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_adduser");
				}
				FeedBase feed = req.getFeed();
				UserBlogic userBlogic = new UserBlogic();
				EntryBase userTopEntry = userBlogic.adduser(feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgAdduser(userTopEntry, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_PASSRESET) != null) {
				// パスワードリセットメール送信
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_passreset");
				}
				FeedBase feed = req.getFeed();
				UserBlogic userBlogic = new UserBlogic();
				EntryBase userTopEntry = userBlogic.passreset(feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgPassreset(userTopEntry, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_ADDUSER_BYADMIN) != null) {
				// 管理者によるユーザ登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_adduserByAdmin");
				}
				FeedBase feed = req.getFeed();
				FeedBase userTopEntriesFeed = reflexContext.adduserByAdmin(feed);
				retObj = createMessageFeed(msgManager.getMsgAdduserByAdmin(userTopEntriesFeed, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_ADDUSER_BYGROUPADMIN) != null) {
				// グループ管理者によるユーザ登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_adduserByGroupadmin");
				}
				String groupName = param.getOption(RequestParam.PARAM_ADDUSER_BYGROUPADMIN);
				FeedBase feed = req.getFeed();
				FeedBase userTopEntriesFeed = reflexContext.adduserByGroupadmin(feed, groupName);
				retObj = createMessageFeed(msgManager.getMsgAdduserByAdmin(userTopEntriesFeed, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CREATEGROUPADMIN) != null) {
				// グループ管理者登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_creategroupadmin");
				}
				FeedBase feed = req.getFeed();
				FeedBase userTopEntriesFeed = reflexContext.createGroupadmin(feed);
				retObj = createMessageFeed(msgManager.getMsgCreateGroupadmin(userTopEntriesFeed, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_ADDGROUP) != null) {
				// グループへの参加登録(署名なし)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addgroup");
				}
				retObj = reflexContext.addGroup(param.getUri(), 
						param.getOption(RequestParam.PARAM_SELFID)); 

			} else if (param.getOption(RequestParam.PARAM_ADDGROUP_BYADMIN) != null) {
				// 管理者によるグループへの参加登録(署名なし)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addgroupByAdmin");
				}
				FeedBase feed = req.getFeed();
				retObj = reflexContext.addGroupByAdmin(param.getUri(), 
						param.getOption(RequestParam.PARAM_SELFID), feed); 

			} else if (param.getOption(RequestParam.PARAM_CREATESERVICE) != null) {
				// サービス登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_createservice");
				}
				FeedBase feed = req.getFeed();
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				String newServiceName = serviceBlogic.createservice(feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgCreateservice(newServiceName, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_RANGEIDS) != null) {
				// 加算枠範囲設定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_rangeids");
				}
				FeedBase feed = req.getFeed();
				String range = IncrementBlogic.getRange(feed);
				reflexContext.rangeids(param.getUri(), range);
				retObj = createMessageFeed(msgManager.getMsgRangeids(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_LOG) != null) {
				// ログ出力
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_log");
				}
				FeedBase feed = req.getFeed();
				LogBlogic logBlogic = new LogBlogic();
				logBlogic.writeLogEntryByAdmin(feed, req);
				retObj = createMessageFeed(msgManager.getMsgWriteLog(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_ADDSERVER) != null) {
				// サーバの追加
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addserver");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_ADDSERVER);
				FeedBase feed = req.getFeed();
				DatastoreBlogic datastoreBlogic = new DatastoreBlogic();
				datastoreBlogic.addServer(targetServiceName, feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_CREATETOTP) != null) {
				// ２段階認証(TOTP)登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_createtotp");
				}
				UserBlogic userBlogic = new UserBlogic();
				retObj = userBlogic.createTotp(req, reflexContext);

			} else {
				// 登録処理
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "post");
				}
				FeedBase feed = req.getFeed();
				FeedBase insFeed = reflexContext.post(feed, param);
				retObj = MessageUtil.getUrisMessageFeed(insFeed, serviceName);
				status = HttpStatus.SC_CREATED;
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * PUTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doPut] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doPut] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doPut start");
		}
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
		reflexContext.getAuth().setExternal(false);
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			Object retObj = null;
			int status = HttpStatus.SC_OK;
			if (param.getOption(RequestParam.PARAM_ADDUSER_BYADMIN) != null) {
				// 管理者によるユーザ登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_adduserByAdmin");
				}
				FeedBase feed = req.getFeed();
				retObj = reflexContext.adduserByAdmin(feed);

			} else if (param.getOption(RequestParam.PARAM_CHANGEPASS) != null) {
				// パスワード変更
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_changephash");
				}
				FeedBase feed = req.getFeed();
				UserBlogic userBlogic = new UserBlogic();
				EntryBase userTopEntry = userBlogic.changepass(feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgChangepass(userTopEntry, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CHANGEPASS_BYADMIN) != null) {
				// 管理者によるパスワード変更
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_changephashByAdmin");
				}
				FeedBase feed = req.getFeed();
				FeedBase retFeed = reflexContext.changepassByAdmin(feed);
				retObj = createMessageFeed(msgManager.getMsgChangepassByAdmin(retFeed, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CHANGEACCOUNT) != null) {
				// アカウント変更
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_changeaccount");
				}
				FeedBase feed = req.getFeed();
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.changeaccount(feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgChangeaccount(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CHANGEACCOUNT_VERIFY) != null) {
				// アカウント更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_changeaccount_verify");
				}
				String verifyCode = param.getOption(RequestParam.PARAM_CHANGEACCOUNT_VERIFY);
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.verifyChangeaccount(verifyCode, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgChangeaccountVerify(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_ACCESSKEY) != null) {
				// アクセスキー更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_accesskey");
				}
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.changeAccessKey(reflexContext);
				retObj = createMessageFeed(msgManager.getMsgChangeAccesskey(reflexContext.getAuth(), serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CREATESERVICE) != null) {
				// サービス登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_createservice");
				}
				FeedBase feed = req.getFeed();
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				String newServiceName = serviceBlogic.createservice(feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgCreateservice(newServiceName, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_DELETESERVICE) != null) {
				// サービス削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_deleteservice");
				}
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				String delServiceName = param.getOption(RequestParam.PARAM_DELETESERVICE);
				serviceBlogic.deleteservice(delServiceName, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgDeleteservice(delServiceName, serviceName), serviceName);
				// ステータスはAccepted.
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_APIKEY) != null) {
				// APIKey更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_apikey");
				}
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				String apiKey = serviceBlogic.changeAPIKey(reflexContext);
				retObj = createMessageFeed(apiKey, serviceName);

			} else if (param.getOption(RequestParam.PARAM_SERVICEKEY) != null) {
				// サービスキー更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_servicekey");
				}
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				String serviceKey = serviceBlogic.changeServiceKey(reflexContext);
				retObj = createMessageFeed(serviceKey, serviceName);

			} else if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				// コンテンツ登録
				FeedBase contentFeed = null;
				if (param.getOption(RequestParam.PARAM_BYSIZE) != null) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content bysize");
					}
					contentFeed = reflexContext.putContentBySize();
					
				} else if (param.getOption(RequestParam.PARAM_SIGNEDURL) != null) {
					// 署名付きURL取得
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signedurl");
					}
					retObj = reflexContext.putContentSignedUrl();

				} else {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content");
					}
					contentFeed = reflexContext.putContent();
				}
				if (retObj == null) {
					retObj = createMessageFeed(msgManager.getMsgPutContent(contentFeed, serviceName), 
							serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_ALLOCIDS) != null) {
				// 採番
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_allocids");
				}
				int num = AllocateIdsBlogic.intValue(
						param.getOption(RequestParam.PARAM_ALLOCIDS));
				retObj = reflexContext.allocids(param.getUri(), num);

			} else if (param.getOption(RequestParam.PARAM_ADDIDS) != null) {
				// 加算
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addids");
				}
				long num = IncrementBlogic.longValue(
						param.getOption(RequestParam.PARAM_ADDIDS));
				retObj = reflexContext.addids(param.getUri(), num);

			} else if (param.getOption(RequestParam.PARAM_SETIDS) != null) {
				// 加算処理の値設定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_setids");
				}
				long num = IncrementBlogic.longValue(
						param.getOption(RequestParam.PARAM_SETIDS));
				retObj = reflexContext.setids(param.getUri(), num);

			} else if (param.getOption(RequestParam.PARAM_RANGEIDS) != null) {
				// 加算枠範囲設定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_rangeids");
				}
				FeedBase feed = req.getFeed();
				String range = IncrementBlogic.getRange(feed);
				reflexContext.rangeids(param.getUri(), range);
				retObj = createMessageFeed(msgManager.getMsgRangeids(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CACHEFEED) != null) {
				// Feed形式キャッシュ更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachefeed");
				}
				FeedBase feed = req.getFeed();
				Integer expire = StringUtils.parseInteger(param.getOption(
						RequestParam.PARAM_EXPIRE), null);
				if (expire != null && feed == null) {
					// expire指定かつデータ未指定の場合、有効時間設定
					boolean ret = reflexContext.setExpireCacheFeed(param.getUri(), expire);
					if (ret) {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					reflexContext.setCacheFeed(param.getUri(), feed, expire);
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHEENTRY) != null) {
				// Entry形式キャッシュ更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheentry");
				}
				EntryBase entry = TaggingEntryUtil.getFirstEntry(req.getFeed());
				Integer expire = StringUtils.parseInteger(param.getOption(
						RequestParam.PARAM_EXPIRE), null);
				if (expire != null && entry == null) {
					// expire指定かつデータ未指定の場合、有効時間設定
					boolean ret = reflexContext.setExpireCacheEntry(param.getUri(), expire);
					if (ret) {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					reflexContext.setCacheEntry(param.getUri(), entry, expire);
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHESTRING) != null) {
				// 文字列形式キャッシュ更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachestring");
				}
				FeedBase feed = req.getFeed();
				Integer expire = StringUtils.parseInteger(param.getOption(
						RequestParam.PARAM_EXPIRE), null);
				if (expire != null && feed == null) {
					// expire指定かつデータ未指定の場合、有効時間設定
					boolean ret = reflexContext.setExpireCacheString(param.getUri(), expire);
					if (ret) {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					String title = TaggingEntryUtil.getTitle(feed);
					reflexContext.setCacheString(param.getUri(), title, expire);
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHELONG) != null) {
				// 数値形式キャッシュ更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachelong");
				}
				FeedBase feed = req.getFeed();
				Integer expire = StringUtils.parseInteger(param.getOption(
						RequestParam.PARAM_EXPIRE), null);
				if (expire != null && feed == null) {
					// expire指定かつデータ未指定の場合、有効時間設定
					boolean ret = reflexContext.setExpireCacheLong(param.getUri(), expire);
					if (ret) {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					String title = TaggingEntryUtil.getTitle(feed);
					String name = "title";
					CheckUtil.checkNotNull(title, name);
					CheckUtil.checkLong(title, name);
					reflexContext.setCacheLong(param.getUri(),
							StringUtils.longValue(title), expire);
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgPutCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHEINCR) != null) {
				// 数値形式キャッシュインクリメント
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheincr");
				}
				String numStr = param.getOption(RequestParam.PARAM_CACHEINCR);
				String name = "parameter";
				CheckUtil.checkNotNull(numStr, name);
				CheckUtil.checkLong(numStr, name);
				long retLong = reflexContext.incrementCache(param.getUri(), StringUtils.longValue(numStr));
				retObj = MessageUtil.createMessageFeed(retLong, serviceName);

			} else if (param.getOption(RequestParam.PARAM_SIGNATURE) != null) {
				// 署名設定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signature");
				}
				String revisionStr = param.getOption(RequestParam.PARAM_REVISION);
				CheckUtil.checkRevision(revisionStr);
				Integer revision = StringUtils.parseInteger(revisionStr);
				FeedBase feed = req.getFeed();
				if (TaggingEntryUtil.isExistData(feed)) {
					reflexContext.putSignatures(feed);
					retObj = createMessageFeed(msgManager.getMsgPutSignatures(serviceName), serviceName);
				} else {
					reflexContext.putSignature(param.getUri(), revision);
					retObj = createMessageFeed(msgManager.getMsgPutSignature(serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_REVOKEUSER) != null) {
				// ユーザ無効
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_revokeuser");
				}
				String paramVal = param.getOption(RequestParam.PARAM_REVOKEUSER);
				boolean isDeleteGroups = param.getOption(RequestParam.PARAM_DELETEGROUP) != null;
				if (!StringUtils.isBlank(paramVal)) {
					// 1件指定
					retObj = reflexContext.revokeUser(paramVal, isDeleteGroups);
				} else {
					// feed指定
					FeedBase feed = req.getFeed();
					retObj = reflexContext.revokeUser(feed, isDeleteGroups);
				}

			} else if (param.getOption(RequestParam.PARAM_ACTIVATEUSER) != null) {
				// ユーザ有効
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_activateuser");
				}
				String paramVal = param.getOption(RequestParam.PARAM_ACTIVATEUSER);
				if (!StringUtils.isBlank(paramVal)) {
					// 1件指定
					retObj = reflexContext.activateUser(paramVal);
				} else {
					// feed指定
					FeedBase feed = req.getFeed();
					retObj = reflexContext.activateUser(feed);
				}

			} else if (param.getOption(RequestParam.PARAM_BULK) != null) {
				// 一括更新(並列実行)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_bulk");
				}
				FeedBase feed = req.getFeed();
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				reflexContext.bulkPut(feed, param, async);
				if (async) {
					// 非同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
					// ステータスはAccepted.
					status = HttpStatus.SC_ACCEPTED;
				} else {
					// 同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_BULKSERIAL) != null) {
				// 一括直列更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_bulkserial");
				}
				FeedBase feed = req.getFeed();
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				reflexContext.bulkSerialPut(feed, param, async);
				if (async) {
					// 非同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
					// ステータスはAccepted.
					status = HttpStatus.SC_ACCEPTED;
				} else {
					// 同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_ADDACL) != null) {
				// ACL追加
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addacl");
				}
				FeedBase feed = req.getFeed();
				reflexContext.addAcl(feed);
				retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_REMOVEACL) != null) {
				// ACL削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_removeacl");
				}
				FeedBase feed = req.getFeed();
				reflexContext.removeAcl(feed);
				retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_ADDALIAS) != null) {
				// エイリアス追加
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addalias");
				}
				FeedBase feed = req.getFeed();
				reflexContext.addAlias(feed);
				retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_REMOVEALIAS) != null) {
				// エイリアス削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_removealias");
				}
				FeedBase feed = req.getFeed();
				reflexContext.removeAlias(feed);
				retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_JOINGROUP) != null) {
				// グループへの参加署名
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_joingroup");
				}
				retObj = reflexContext.joinGroup(param.getUri(), 
						param.getOption(RequestParam.PARAM_SELFID)); 

			} else if (param.getOption(RequestParam.PARAM_LEAVEGROUP) != null) {
				// グループからの退会
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_leavegroup");
				}
				retObj = reflexContext.leaveGroup(param.getUri()); 

			} else if (param.getOption(RequestParam.PARAM_LEAVEGROUP_BYADMIN) != null) {
				// 管理者によるグループからの退会
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_leaveGroupByAdmin");
				}
				FeedBase feed = req.getFeed();
				retObj = reflexContext.leaveGroupByAdmin(param.getUri(), feed); 

			} else if (param.getOption(RequestParam.PARAM_SERVICETOPRODUCTION) != null) {
				// サービス公開
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_servicetoproduction");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_SERVICETOPRODUCTION);
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				serviceBlogic.serviceToProduction(targetServiceName, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgProductionService(targetServiceName, serviceName), serviceName);
				// ステータスはAccepted.
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_SERVICETOSTAGING) != null) {
				// サービスステータスを開発に更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_servicetostaging");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_SERVICETOSTAGING);
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				serviceBlogic.serviceToStaging(targetServiceName, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgStagingService(targetServiceName, serviceName), serviceName);
				// ステータスはAccepted.
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_UPDATEINDEX) != null ||
					param.getOption(RequestParam.PARAM_DELETEINDEX) != null) {
				// インデックス更新または削除
				boolean isDelete = param.getOption(RequestParam.PARAM_DELETEINDEX) != null;
				if (logger.isInfoEnabled()) {
					String tmpOpt = null;
					if (isDelete) {
						tmpOpt = RequestParam.PARAM_DELETEINDEX;
					} else {
						tmpOpt = RequestParam.PARAM_UPDATEINDEX;
					}
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + tmpOpt);
				}
				FeedBase feed = req.getFeed();
				reflexContext.putIndex(feed, isDelete);
				if (isDelete) {
					retObj = createMessageFeed(msgManager.getMsgDeleteIndex(serviceName),
							serviceName);
				} else {
					retObj = createMessageFeed(msgManager.getMsgUpdateIndex(serviceName),
							serviceName);
				}
				// ステータスはAccepted.
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_CHANGETDID) != null) {
				// 信頼できる端末に設定する値(TDID)の更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_changetdid");
				}
				UserBlogic userBlogic = new UserBlogic();
				retObj = userBlogic.changeTdid(req, reflexContext);

			} else if (param.getOption(RequestParam.PARAM_MERGEOAUTHUSER) != null) {
				// 既存ユーザとソーシャルログインユーザを紐付ける
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_mergeoauthuser");
				}
				FeedBase feed = req.getFeed();
				UserBlogic userBlogic = new UserBlogic();
				retObj = userBlogic.mergeOAuthUser(req, resp, feed, reflexContext);

			} else {
				// 更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "put");
				}
				FeedBase feed = req.getFeed();
				reflexContext.put(feed, param);
				retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * DELETEメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doDelete(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doDelete] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doDelete] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doDelete start");
		}
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
		reflexContext.getAuth().setExternal(false);
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			Object retObj = null;
			int status = HttpStatus.SC_OK;
			if (param.getOption(RequestParam.PARAM_DELETESERVICE) != null) {
				// サービス削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_deleteservice");
				}
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				String delServiceName = param.getOption(RequestParam.PARAM_DELETESERVICE);
				serviceBlogic.deleteservice(delServiceName, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgDeleteservice(delServiceName, serviceName), serviceName);
				// ステータスはAccepted. -> 同期処理に変更
				//status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_DELETEUSER) != null) {
				// ユーザ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_deleteuser");
				}
				String delUsername = param.getOption(RequestParam.PARAM_DELETEUSER);
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				if (!StringUtils.isBlank(delUsername)) {
					// 1件指定
					retObj = reflexContext.deleteUser(delUsername, async);
				} else {
					// feed指定
					FeedBase feed = req.getFeed();
					retObj = reflexContext.deleteUser(feed, async);
				}
				retObj = createMessageFeed(msgManager.getMsgDeleteuser(delUsername, serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CANCELUSER) != null) {
				// ユーザ退会
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_canceluser");
				}
				boolean isDeleteGroups = param.getOption(RequestParam.PARAM_DELETEGROUP) != null;
				UserBlogic userBlogic = new UserBlogic();
				userBlogic.cancelUser(isDeleteGroups, reflexContext, req, resp);
				retObj = createMessageFeed(msgManager.getMsgCanceluser(reflexContext.getAuth(), serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				// コンテンツ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content");
				}
				reflexContext.deleteContent(param.getUri());
				retObj = createMessageFeed(msgManager.getMsgDeleteContent(param.getUri(), serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_CACHEFEED) != null) {
				// Feed形式キャッシュ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachefeed");
				}
				boolean ret = reflexContext.deleteCacheFeed(param.getUri());
				if (ret) {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgDeleteCache(param.getUri(), serviceName), serviceName);
				} else {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHEENTRY) != null) {
				// Entry形式キャッシュ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheentry");
				}
				boolean ret = reflexContext.deleteCacheEntry(param.getUri());
				if (ret) {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgDeleteCache(param.getUri(), serviceName), serviceName);
				} else {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHESTRING) != null) {
				// 文字列形式キャッシュ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachestring");
				}
				boolean ret = reflexContext.deleteCacheString(param.getUri());
				if (ret) {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgDeleteCache(param.getUri(), serviceName), serviceName);
				} else {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CACHELONG) != null) {
				// 数値形式キャッシュ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachelong");
				}
				boolean ret = reflexContext.deleteCacheLong(param.getUri());
				if (ret) {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgDeleteCache(param.getUri(), serviceName), serviceName);
				} else {
					retObj = MessageUtil.createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_SIGNATURE) != null) {
				// 署名削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signature");
				}
				String revisionStr = param.getOption(RequestParam.PARAM_REVISION);
				CheckUtil.checkRevision(revisionStr);
				Integer revision = StringUtils.parseInteger(revisionStr);
				reflexContext.deleteSignature(param.getUri(), revision);
				retObj = createMessageFeed(msgManager.getMsgDeleteSignature(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_RF) != null) {
				// フォルダ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_rf");
				}
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				boolean isParallel = true;
				reflexContext.deleteFolder(param, async, isParallel);
				if (async) {
					// 非同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
					// ステータスはAccepted.
					status = HttpStatus.SC_ACCEPTED;
				} else {
					// 同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgDeleteFolder(serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_CLEARFOLDER) != null) {
				// フォルダクリア
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_clearfolder");
				}
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				boolean isParallel = true;
				reflexContext.clearFolder(param, async, isParallel);
				if (async) {
					// 非同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
					// ステータスはAccepted.
					status = HttpStatus.SC_ACCEPTED;
				} else {
					// 同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgClearFolder(serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_REMOVESERVER) != null) {
				// サーバの削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_removeserver");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_REMOVESERVER);
				FeedBase feed = req.getFeed();
				DatastoreBlogic datastoreBlogic = new DatastoreBlogic();
				datastoreBlogic.removeServer(targetServiceName, feed, reflexContext);
				retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_CACHEFLUSHALL) != null) {
				// キャッシュの全データ削除(メンテナンス用)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheflushall");
				}
				reflexContext.cacheFlushAll();
				retObj = MessageUtil.createMessageFeed(msgManager.getMsgDelete(serviceName), serviceName);

			} else if (param.getOption(RequestParam.PARAM_DELETETOTP) != null) {
				// ２段階認証(TOTP)削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_deletetotp");
				}
				UserBlogic userBlogic = new UserBlogic();
				retObj = userBlogic.deleteTotp(param.getOption(RequestParam.PARAM_DELETETOTP),
						req, reflexContext);

			} else if (param.getOption(RequestParam.PARAM_DELETEGROUPADMIN) != null) {
				// グループ管理削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_deletegroupadmin");
				}
				String groupName = param.getOption(RequestParam.PARAM_DELETEGROUPADMIN);
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				FeedBase feed = null;
				if (!StringUtils.isBlank(groupName)) {
					// 1件指定
					reflexContext.deleteGroupadmin(groupName, async);
				} else {
					// feed指定
					feed = req.getFeed();
					reflexContext.deleteGroupadmin(feed, async);
				}
				if (async) {
					// 非同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
					// ステータスはAccepted.
					status = HttpStatus.SC_ACCEPTED;
				} else {
					// 同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgDeleteGroupadmin(feed, serviceName), 
							serviceName);
				}

			} else {
				FeedBase feed = req.getFeed();
				if (feed != null) {
					reflexContext.delete(feed);
				} else {
					reflexContext.delete(param);
				}
				retObj = createMessageFeed(msgManager.getMsgDelete(serviceName), serviceName);
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * シャットダウン時の処理.
	 * (2022.10.4)ServletContextListener.contextDestroyed が実行されないのでこちらに移動。
	 */
	@Override
	public void destroy() {
		/* (2022.11.4)汎用API機能に対応し、ServletでなくFilterでシャットダウン処理を行うよう修正。
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] start.");
		}
		super.destroy();
		TaggingEnvUtil.destroy();
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] end.");
		}
		*/
	}

	/**
	 * 戻り値のメッセージFeedを作成.
	 * @param msg メッセージ
	 * @return メッセージFeed
	 */
	private FeedBase createMessageFeed(String msg, String serviceName) {
		return MessageUtil.createMessageFeed(msg, serviceName);
	}

	/**
	 * Feed検索のステータス取得.
	 * 通常は200
	 * フェッチ件数制限超過の場合、206
	 * @param feed 戻り値
	 * @return ステータス
	 */
	private int getFeedStatus(FeedBase feed) {
		if (feed != null && Constants.MARK_FETCH_LIMIT.equals(feed.rights)) {
			return HttpStatus.SC_PARTIAL_CONTENT;
		}
		return HttpStatus.SC_OK;
	}

}
