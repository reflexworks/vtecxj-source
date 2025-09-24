package jp.reflexworks.taggingservice.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import jp.reflexworks.taggingservice.blogic.IncrementBlogic;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.exception.SignatureInvalidException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestParamInfo;
import jp.reflexworks.taggingservice.model.SendMailInfo;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 汎用API サーブレット.
 */
public class ProviderServlet extends ReflexServletBase {

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
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doGet start");
		}
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			checkAndExternalAuth(req);
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// External
			String targetServiceName = ProviderUtil.getServiceLinkage(req);
			String targetServiceKey = ProviderUtil.getServiceKey(req, targetServiceName);
			checkServiceLinkage(req, targetServiceName);
			// ここでリクエストパラメータ取得(サービス連携対応)
			RequestParam param = null;
			if (StringUtils.isBlank(targetServiceName)) {
				param = (RequestParam)req.getRequestType();
			} else {
				param = new RequestParamInfo(req.getPathInfoQuery(), targetServiceName);
			}
			if (param == null) {
				return;
			}

			int status = HttpStatus.SC_OK;
			Object retObj = null;
			ReflexContentInfo contentInfo = null;

			if (param.getOption(RequestParam.PARAM_ALLOCIDS) != null) {
				// 自動採番
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_allocids");
				}
				int num = AllocateIdsBlogic.intValue(
						param.getOption(RequestParam.PARAM_ALLOCIDS));
				retObj = reflexContext.allocids(param.getUri(), num, targetServiceName, targetServiceKey);

			} else if (param.getOption(RequestParam.PARAM_GETIDS) != null) {
				// 現在番号取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_getids");
				}
				retObj = reflexContext.getids(param.getUri(), targetServiceName, targetServiceKey);

			} else if (param.getOption(RequestParam.PARAM_RANGEIDS) != null) {
				// 加算枠取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_rangeids");
				}
				retObj = reflexContext.getRangeids(param.getUri());

			} else if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				if (param.getOption(RequestParam.PARAM_SIGNEDURL) != null) {
					// 署名付きURL取得
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_signedurl");
					}
					retObj = reflexContext.getContentSignedUrl(param.getUri());
					
				} else {
					// コンテンツ取得 (Entryが存在しない場合はコンテンツ登録先を参照しない)
					// Etagが等しい場合本体は取得しない。
					contentInfo = reflexContext.getContent(param.getUri(), true);
					if (contentInfo == null) {
						throw new NoEntryException();
					}
				}

			} else if (param.getOption(ProviderConst.PARAM_SESSIONFEED) != null) {
				// Feed形式セッション値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionfeed");
				}
				retObj = reflexContext.getSessionFeed(param.getOption(ProviderConst.PARAM_SESSIONFEED));
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(ProviderConst.PARAM_SESSIONENTRY) != null) {
				// Entry形式セッション値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionentry");
				}
				retObj = reflexContext.getSessionEntry(param.getOption(ProviderConst.PARAM_SESSIONENTRY));
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(ProviderConst.PARAM_SESSIONSTRING) != null) {
				// 文字列形式セッション値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionstring");
				}
				String retStr = reflexContext.getSessionString(param.getOption(ProviderConst.PARAM_SESSIONSTRING));
				if (StringUtils.isBlank(retStr)) {
					throw new NoEntryException();
				}
				retObj = createMessageFeed(retStr, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONLONG) != null) {
				// 数値形式セッション値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionlong");
				}
				Long retNum = reflexContext.getSessionLong(param.getOption(ProviderConst.PARAM_SESSIONLONG));
				if (retNum == null) {
					throw new NoEntryException();
				}
				retObj = createMessageFeed(retNum, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHEFEED) != null) {
				// Feed形式キャッシュ値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachefeed");
				}
				retObj = reflexContext.getCacheFeed(param.getOption(ProviderConst.PARAM_CACHEFEED));
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(ProviderConst.PARAM_CACHEENTRY) != null) {
				// Entry形式キャッシュ値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheentry");
				}
				retObj = reflexContext.getCacheEntry(param.getOption(ProviderConst.PARAM_CACHEENTRY));
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(ProviderConst.PARAM_CACHESTRING) != null) {
				// 文字列形式キャッシュ値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachestring");
				}
				String retStr = reflexContext.getCacheString(param.getOption(ProviderConst.PARAM_CACHESTRING));
				if (StringUtils.isBlank(retStr)) {
					throw new NoEntryException();
				}
				retObj = createMessageFeed(retStr, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHELONG) != null) {
				// 数値形式キャッシュ値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachelong");
				}
				Long retNum = reflexContext.getCacheLong(param.getOption(ProviderConst.PARAM_CACHELONG));
				if (retNum == null) {
					throw new NoEntryException();
				}
				retObj = createMessageFeed(retNum, serviceName);

			} else if (param.getOption(RequestParam.PARAM_PAGINATION) != null) {
				// ページング
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_pagination");
				}
				retObj = reflexContext.pagination(param, targetServiceName, targetServiceKey);

			} else if (param.getOption(RequestParam.PARAM_NUMBER) != null) {
				// ページ指定検索
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getPage");
				}
				retObj = reflexContext.getPage(param, targetServiceName, targetServiceKey);
				if (retObj == null) {
					throw new NoEntryException();
				}

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

			} else if (param.getOption(ProviderConst.PARAM_MESSAGEQUEUE) != null) {
				// メッセージキューからメッセージ受信
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_mq");
				}
				retObj = reflexContext.getMessageQueue(param.getUri());

			} else if (param.getOption(ProviderConst.PARAM_MESSAGEQUEUE_STATUS) != null) {
				// メッセージキューステータス取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_mqstatus");
				}
				boolean ret = reflexContext.getMessageQueueStatus(param.getUri()); 
				retObj = createMessageFeed(String.valueOf(ret), serviceName);

			} else if (param.getOption(ProviderConst.PARAM_PROPERTY) != null) {
				// プロパティ値取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_property");
				}
				String ret = reflexContext.getSettingValue(param.getOption(
						ProviderConst.PARAM_PROPERTY)); 
				if (ret == null) {
					throw new NoEntryException();
				}
				retObj = createMessageFeed(ret, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_ENTRY) != null) {
				// Entry検索
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getEntry");
				}
				retObj = reflexContext.getEntry(param, targetServiceName, targetServiceKey);
				if (retObj == null) {
					throw new NoEntryException();
				}

			} else if (param.getOption(RequestParam.PARAM_COUNT) != null) {
				// 件数取得
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getCount");
				}
				retObj = reflexContext.getCount(param, targetServiceName, targetServiceKey);
				status = getFeedStatus((FeedBase)retObj);

			} else if (param.getOption(ProviderConst.PARAM_FEED) != null) {
				// Feed検索
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "getFeed");
				}
				retObj = reflexContext.getFeed(param, targetServiceName, targetServiceKey);
				if (retObj == null) {
					throw new NoEntryException();
				}
				status = getFeedStatus((FeedBase)retObj);

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
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
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			checkAndExternalAuth(req);
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// External
			String targetServiceName = ProviderUtil.getServiceLinkage(req);
			String targetServiceKey = ProviderUtil.getServiceKey(req, targetServiceName);
			checkServiceLinkage(req, targetServiceName);

			Object retObj = null;
			int status = HttpStatus.SC_OK;

			if (param.getOption(RequestParam.PARAM_LOG) != null) {
				// ログ出力
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_log");
				}
				FeedBase feed = req.getFeed();
				reflexContext.log(feed);
				retObj = createMessageFeed(msgManager.getMsgWriteLog(serviceName), serviceName);

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

			} else if (param.getOption(ProviderConst.PARAM_BIGQUERY) != null) {
				// BigQuery登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_bq");
				}
				FeedBase feed = req.getFeed();
				boolean async = param.getOption(ProviderConst.PARAM_ASYNC) != null;
				Map<String, String> tableNames = ProviderUtil.getBqTables(feed);
				reflexContext.postBq(feed, tableNames, async);
				String msg = null;
				if (async) {
					msg = ProviderConst.MSG_POST_BIGQUERY_ASYNC;
					status = HttpStatus.SC_ACCEPTED;
				} else {
					msg = ProviderConst.MSG_POST_BIGQUERY;
				}
				retObj = createMessageFeed(msg, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_BDBQ) != null) {
				// BDB+BigQuery登録
				String parentUri = param.getUri();
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("_bdbq parentUri=");
					sb.append(parentUri);
					logger.debug(sb.toString());
				}
				FeedBase feed = req.getFeed();
				boolean async = param.getOption(ProviderConst.PARAM_ASYNC) != null;
				Map<String, String> tableNames = ProviderUtil.getBqTables(feed);
				retObj = reflexContext.postBdbq(feed, parentUri, tableNames, async);

			} else if (param.getOption(ProviderConst.PARAM_SENDMAIL) != null) {
				// メール送信
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sendmail");
				}
				FeedBase feed = req.getFeed();
				SendMailInfo sendMailInfo = ProviderUtil.getSendMailInfo(feed);
				CheckUtil.checkNotNull(sendMailInfo, "parameter");
				reflexContext.sendMail(sendMailInfo.entry, sendMailInfo.to, sendMailInfo.cc, 
						sendMailInfo.bcc, sendMailInfo.attachments);
				retObj = createMessageFeed(ProviderConst.MSG_SENDMAIL, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_PUSHNOTIFICATION) != null) {
				// プッシュ通知
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_pushnotification");
				}
				FeedBase feed = req.getFeed();
				String[] to = ProviderUtil.getPushNotificationTo(feed);
				CheckUtil.checkNotNull(to, "parameter");
				reflexContext.pushNotification(feed, to);
				retObj = createMessageFeed(ProviderConst.MSG_PUSHNOTIFICATION, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_MESSAGEQUEUE) != null) {
				// メッセージキューへメッセージ送信
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_mq");
				}
				FeedBase feed = req.getFeed();
				reflexContext.setMessageQueue(feed, param.getUri()); 
				retObj = createMessageFeed(ProviderConst.MSG_POST_MESSAGEQUEUE, serviceName);

			} else if (param.getOption(RequestParam.PARAM_ADDGROUP) != null) {
				// グループへの参加登録(署名はなし)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addgroup");
				}
				retObj = reflexContext.addGroup(param.getUri(), 
						param.getOption(ProviderConst.PARAM_SELFID)); 

			} else if (param.getOption(RequestParam.PARAM_ADDGROUP_BYADMIN) != null) {
				// 管理者によるグループへの参加登録(署名はなし)
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addgroupByAdmin");
				}
				FeedBase feed = req.getFeed();
				retObj = reflexContext.addGroupByAdmin(param.getUri(), 
						param.getOption(ProviderConst.PARAM_SELFID), feed); 

			} else if (param.getOption(RequestParam.PARAM_ENTRY) != null) {
				// 登録処理
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "post");
				}
				FeedBase feed = req.getFeed(targetServiceName);
				retObj = reflexContext.post(feed, param, targetServiceName, targetServiceKey);
				status = HttpStatus.SC_CREATED;

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
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
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			checkAndExternalAuth(req);
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// External
			String targetServiceName = ProviderUtil.getServiceLinkage(req);
			String targetServiceKey = ProviderUtil.getServiceKey(req, targetServiceName);
			checkServiceLinkage(req, targetServiceName);

			Object retObj = null;
			ReflexContentInfo contentInfo = null;
			int status = HttpStatus.SC_OK;

			if (param.getOption(RequestParam.PARAM_ADDIDS) != null) {
				// 加算
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_addids");
				}
				long num = IncrementBlogic.longValue(
						param.getOption(RequestParam.PARAM_ADDIDS));
				retObj = reflexContext.addids(param.getUri(), num, targetServiceName, targetServiceKey);

			} else if (param.getOption(RequestParam.PARAM_SETIDS) != null) {
				// 加算処理の値設定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_setids");
				}
				long num = IncrementBlogic.longValue(
						param.getOption(RequestParam.PARAM_SETIDS));
				retObj = reflexContext.setids(param.getUri(), num, targetServiceName, targetServiceKey);

			} else if (param.getOption(RequestParam.PARAM_RANGEIDS) != null) {
				// 加算枠範囲設定
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_rangeids");
				}
				FeedBase feed = req.getFeed();
				String range = IncrementBlogic.getRange(feed);
				retObj = reflexContext.rangeids(param.getUri(), range);

			} else if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				// コンテンツ登録
				if (param.getOption(RequestParam.PARAM_BYSIZE) != null) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content bysize");
					}
					retObj = reflexContext.putContentBySize();
					
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
					retObj = reflexContext.putContent();
				}

			} else if (param.getOption(ProviderConst.PARAM_SESSIONFEED) != null) {
				// Feed形式セッション値更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionfeed");
				}
				FeedBase feed = req.getFeed();
				retObj = reflexContext.setSessionFeed(
						param.getOption(ProviderConst.PARAM_SESSIONFEED), feed);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONENTRY) != null) {
				// Entry形式セッション値更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionentry");
				}
				EntryBase entry = TaggingEntryUtil.getFirstEntry(req.getFeed());
				retObj = reflexContext.setSessionEntry(
						param.getOption(ProviderConst.PARAM_SESSIONENTRY), entry);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONSTRING) != null) {
				// 文字列形式セッション値更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionstring");
				}
				FeedBase feed = req.getFeed();
				String title = TaggingEntryUtil.getTitle(feed);
				String retStr = reflexContext.setSessionString(param.getOption(
						ProviderConst.PARAM_SESSIONSTRING), title);
				retObj = createMessageFeed(retStr, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONLONG) != null) {
				// 数値形式セッション値更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionlong");
				}
				FeedBase feed = req.getFeed();
				String title = TaggingEntryUtil.getTitle(feed);
				String name = "title";
				CheckUtil.checkNotNull(title, name);
				CheckUtil.checkLong(title, name);
				Long retNum = reflexContext.setSessionLong(
						param.getOption(ProviderConst.PARAM_SESSIONLONG), StringUtils.longValue(title));
				retObj = createMessageFeed(retNum, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONINCR) != null) {
				// 数値形式セッション値インクリメント
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionincr");
				}
				String numStr = param.getOption(ProviderConst.PARAM_NUM);
				String name = "parameter";
				CheckUtil.checkNotNull(numStr, name);
				CheckUtil.checkLong(numStr, name);
				long retLong = reflexContext.incrementSession(
						param.getOption(ProviderConst.PARAM_SESSIONINCR), StringUtils.longValue(numStr));
				retObj = createMessageFeed(retLong, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHEFEED) != null) {
				// Feed形式キャッシュ値更新
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
						retObj = createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					retObj = reflexContext.setCacheFeed(param.getUri(), feed, expire);
				}

			} else if (param.getOption(ProviderConst.PARAM_CACHEENTRY) != null) {
				// Entry形式キャッシュ値更新
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
						retObj = createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					retObj = reflexContext.setCacheEntry(param.getUri(), entry, expire);
				}

			} else if (param.getOption(ProviderConst.PARAM_CACHESTRING) != null) {
				// 文字列形式キャッシュ値更新
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
						retObj = createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					String title = TaggingEntryUtil.getTitle(feed);
					reflexContext.setCacheString(param.getUri(), title, expire);
					retObj = createMessageFeed(msgManager.getMsgPutCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(ProviderConst.PARAM_CACHELONG) != null) {
				// 数値形式キャッシュ値更新
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
						retObj = createMessageFeed(msgManager.getMsgPutCacheExpire(param.getUri(), serviceName), serviceName);
					} else {
						retObj = createMessageFeed(msgManager.getMsgNotExistCache(param.getUri(), serviceName), serviceName);
					}
				} else {
					// キャッシュ更新
					String title = TaggingEntryUtil.getTitle(feed);
					String name = "title";
					CheckUtil.checkNotNull(title, name);
					CheckUtil.checkLong(title, name);
					reflexContext.setCacheLong(param.getUri(),
							StringUtils.longValue(title), expire);
					retObj = createMessageFeed(msgManager.getMsgPutCache(param.getUri(), serviceName), serviceName);
				}

			} else if (param.getOption(ProviderConst.PARAM_CACHEINCR) != null) {
				// 数値形式キャッシュ値インクリメント
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheincr");
				}
				String numStr = param.getOption(RequestParam.PARAM_CACHEINCR);
				String name = "parameter";
				CheckUtil.checkNotNull(numStr, name);
				CheckUtil.checkLong(numStr, name);
				long retLong = reflexContext.incrementCache(param.getUri(), StringUtils.longValue(numStr));
				retObj = createMessageFeed(retLong, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_QUERY_BIGQUERY) != null) {
				// BigQuery SQL実行
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_querybq");
				}
				FeedBase feed = req.getFeed();
				CheckUtil.checkNotNull(feed, "parameter");
				CheckUtil.checkNotNull(feed.entry, "parameter");
				EntryBase entry = feed.entry.get(0);
				String sql = entry.title;
				String subtitle = entry.subtitle;
				String csv = param.getOption(ProviderConst.PARAM_CSV);
				List<Map<String, Object>> retBq = reflexContext.queryBq(sql);
				contentInfo = ProviderUtil.getQueryResponse(retBq, csv, subtitle);

			} else if (param.getOption(ProviderConst.PARAM_BDBQ) != null) {
				// BDB更新+BigQuery登録
				String parentUri = param.getUri();
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("_bdbq parentUri=");
					sb.append(parentUri);
					logger.debug(sb.toString());
				}

				FeedBase feed = req.getFeed();
				Map<String, String> tableNames = ProviderUtil.getBqTables(feed);
				boolean async = param.getOption(ProviderConst.PARAM_ASYNC) != null;
				boolean bulk = param.getOption(ProviderConst.PARAM_BULK) != null;
				if (bulk) {
					retObj = reflexContext.bulkPutBdbq(feed, param.getUri(), tableNames, async);
				} else {
					retObj = reflexContext.putBdbq(feed, param.getUri(), tableNames, async);
				}

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
					retObj = reflexContext.putSignatures(feed);
				} else {
					retObj = reflexContext.putSignature(param.getUri(), revision);
				}

			} else if (param.getOption(ProviderConst.PARAM_MESSAGEQUEUE_STATUS) != null) {
				// メッセージキューステータス更新
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_mqstatus");
				}
				String mqstatusStr = param.getOption(ProviderConst.PARAM_MESSAGEQUEUE_STATUS);
				CheckUtil.checkBoolean(mqstatusStr, "mqstatus");
				reflexContext.setMessageQueueStatus(StringUtils.booleanValue(mqstatusStr), 
						param.getUri()); 
				retObj = createMessageFeed(ProviderConst.MSG_PUT_MESSAGEQUEUE_STATUS, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_JOINGROUP) != null) {
				// グループへの参加署名
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_joingroup");
				}
				retObj = reflexContext.joinGroup(param.getUri(), 
						param.getOption(ProviderConst.PARAM_SELFID)); 

			} else if (param.getOption(ProviderConst.PARAM_LEAVEGROUP) != null) {
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

			} else if (param.getOption(RequestParam.PARAM_PDF) != null) {
				// PDF生成
				byte[] htmlTempalteData = req.getPayload();
				byte[] data = reflexContext.toPdf(ProviderUtil.getString(htmlTempalteData));
				contentInfo = ProviderUtil.getPdfContentInfo(data, 
						param.getOption(RequestParam.PARAM_PDF));

			} else if (param.getOption(ProviderConst.PARAM_QUERY_RDB) != null) {
				// RDB QuerySQL実行
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_queryrdb");
				}
				FeedBase feed = req.getFeed();
				CheckUtil.checkNotNull(feed, "parameter");
				CheckUtil.checkNotNull(feed.entry, "parameter");
				EntryBase entry = feed.entry.get(0);
				String sql = entry.title;
				String subtitle = entry.subtitle;
				String csv = param.getOption(ProviderConst.PARAM_CSV);
				List<Map<String, Object>> retBq = reflexContext.queryRdb(sql);
				contentInfo = ProviderUtil.getQueryResponse(retBq, csv, subtitle);

			} else if (param.getOption(ProviderConst.PARAM_EXEC_RDB) != null) {
				// RDB 更新系SQL実行
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_execrdb");
				}
				FeedBase feed = req.getFeed();
				CheckUtil.checkNotNull(feed, "parameter");
				CheckUtil.checkNotNull(feed.entry, "parameter");
				List<String> sqlList = new ArrayList<>();
				for (EntryBase entry : feed.entry) {
					String sql = entry.title;
					if (!StringUtils.isBlank(sql)) {
						sqlList.add(sql);
					}
				}
				String[] sqls = sqlList.toArray(new String[0]);
				boolean async = param.getOption(ProviderConst.PARAM_ASYNC) != null;
				boolean bulk = param.getOption(ProviderConst.PARAM_BULK) != null;
				if (async) {
					if (bulk) {
						reflexContext.bulkExecRdbAsync(sqls);
					} else {
						reflexContext.execRdbAsync(sqls);
					}
					retObj = createMessageFeed(ProviderConst.MSG_EXEC_RDB_ASYNC, serviceName);
				} else {
					if (bulk) {
						reflexContext.bulkExecRdb(sqls);
					} else {
						reflexContext.execRdb(sqls);
					}
					retObj = createMessageFeed(ProviderConst.MSG_EXEC_RDB, serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_ENTRY) != null) {
				// 更新
				FeedBase feed = req.getFeed(targetServiceName);
				if (param.getOption(RequestParam.PARAM_BULK) != null) {
					// 一括更新(並列実行)
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_bulk");
					}
					boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
					reflexContext.bulkPut(feed, param, async, targetServiceName, targetServiceKey);
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
					boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
					reflexContext.bulkSerialPut(feed, param, async, targetServiceName, targetServiceKey);
					if (async) {
						// 非同期処理の場合
						retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
						// ステータスはAccepted.
						status = HttpStatus.SC_ACCEPTED;
					} else {
						// 同期処理の場合
						retObj = createMessageFeed(msgManager.getMsgPut(serviceName), serviceName);
					}

				} else {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "put");
					}
					retObj = reflexContext.put(feed, param, targetServiceName, targetServiceKey);
				}

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
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
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			checkAndExternalAuth(req);
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// External
			String targetServiceName = ProviderUtil.getServiceLinkage(req);
			String targetServiceKey = ProviderUtil.getServiceKey(req, targetServiceName);
			checkServiceLinkage(req, targetServiceName);

			Object retObj = null;
			int status = HttpStatus.SC_OK;

			if (param.getOption(RequestParam.PARAM_CONTENT) != null) {
				// コンテンツ削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_content");
				}
				retObj = reflexContext.deleteContent(param.getUri());

			} else if (param.getOption(ProviderConst.PARAM_SESSIONFEED) != null) {
				// Feed形式セッション値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionfeed");
				}
				reflexContext.deleteSessionFeed(param.getOption(ProviderConst.PARAM_SESSIONFEED));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_SESSION_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONENTRY) != null) {
				// Entry形式セッション値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionentry");
				}
				reflexContext.deleteSessionEntry(param.getOption(ProviderConst.PARAM_SESSIONENTRY));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_SESSION_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONSTRING) != null) {
				// 文字列形式セッション値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionstring");
				}
				reflexContext.deleteSessionString(param.getOption(ProviderConst.PARAM_SESSIONSTRING));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_SESSION_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_SESSIONLONG) != null) {
				// 数値形式セッション値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_sessionlong");
				}
				reflexContext.deleteSessionLong(param.getOption(ProviderConst.PARAM_SESSIONLONG));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_SESSION_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHEFEED) != null) {
				// Feed形式キャッシュ値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachefeed");
				}
				reflexContext.deleteCacheFeed(param.getOption(ProviderConst.PARAM_CACHEFEED));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_CACHE_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHEENTRY) != null) {
				// Entry形式キャッシュ値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cacheentry");
				}
				reflexContext.deleteCacheEntry(param.getOption(ProviderConst.PARAM_CACHEENTRY));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_CACHE_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHESTRING) != null) {
				// 文字列形式キャッシュ値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachestring");
				}
				reflexContext.deleteCacheString(param.getOption(ProviderConst.PARAM_CACHESTRING));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_CACHE_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_CACHELONG) != null) {
				// 数値形式キャッシュ値削除
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_cachelong");
				}
				reflexContext.deleteCacheLong(param.getOption(ProviderConst.PARAM_CACHELONG));
				retObj = createMessageFeed(ProviderConst.MSG_DELETE_CACHE_VALUE, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_BIGQUERY) != null) {
				// BigQuery削除（削除データの登録）
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_bq");
				}
				FeedBase feed = req.getFeed();
				String[] uris  = ProviderUtil.getBqUris(feed);
				Map<String, String> tableNames = ProviderUtil.getBqTables(feed);
				boolean async = param.getOption(ProviderConst.PARAM_ASYNC) != null;
				reflexContext.deleteBq(uris, tableNames, async);
				String msg = null;
				if (async) {
					msg = ProviderConst.MSG_DELETE_BIGQUERY_ASYNC;
					status = HttpStatus.SC_ACCEPTED;
				} else {
					msg = ProviderConst.MSG_DELETE_BIGQUERY;
				}
				retObj = createMessageFeed(msg, serviceName);

			} else if (param.getOption(ProviderConst.PARAM_BDBQ) != null) {
				// BDB削除+BigQuery削除データ登録
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "_bdbq");
				}
				FeedBase feed = req.getFeed();
				String[] uris  = ProviderUtil.getBqUris(feed);
				Map<String, String> tableNames = ProviderUtil.getBqTables(feed);
				boolean async = param.getOption(ProviderConst.PARAM_ASYNC) != null;
				retObj = reflexContext.deleteBdbq(uris, tableNames, async);

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
				reflexContext.deleteFolder(param, async, isParallel, targetServiceName, targetServiceKey);
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
				reflexContext.clearFolder(param, async, isParallel, targetServiceName, targetServiceKey);
				if (async) {
					// 非同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
					// ステータスはAccepted.
					status = HttpStatus.SC_ACCEPTED;
				} else {
					// 同期処理の場合
					retObj = createMessageFeed(msgManager.getMsgClearFolder(serviceName), serviceName);
				}

			} else if (param.getOption(RequestParam.PARAM_ENTRY) != null) {
				// エントリー削除
				FeedBase feed = req.getFeed(targetServiceName);
				if (feed != null) {
					retObj = reflexContext.delete(feed, targetServiceName, targetServiceKey);
				} else {
					retObj = reflexContext.delete(param, targetServiceName, targetServiceKey);
				}

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
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
	 * 戻り値のメッセージFeedを作成.
	 * @param msg メッセージ
	 * @return メッセージFeed
	 */
	private FeedBase createMessageFeed(Long msg, String serviceName) {
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

	/**
	 * APIKeyのチェックと、External権限の付与
	 * @param req リクエスト
	 */
	private void checkAndExternalAuth(ReflexRequest req) 
			throws IOException, TaggingException {
		ProviderUtil.checkAPIKey(req);
		req.getAuth().setExternal(true);
	}

	/**
	 * サービス連携に指定されたサービスのチェック
	 * @param req リクエスト
	 * @param targetServiceName 対象サービス
	 */
	private void checkServiceLinkage(ReflexRequest req, String targetServiceName) 
			throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		// サービス連携の指定がないか、自サービスの場合処理を抜ける。
		if (StringUtils.isBlank(targetServiceName) || serviceName.equals(targetServiceName)) {
			return;
		}
		ProviderUtil.checkServiceLinkage(targetServiceName, req.getRequestInfo(), 
				req.getConnectionInfo());
		// 引数チェックが自サービスのテンプレートで行われているため、
		// サービス連携の場合はIllegalParameterExceptionをスローしないようtry-catchする。
		try {
			req.getRequestType();
		} catch (IllegalParameterException e) {
			// Do nothing.
		}
	}

}
