package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServlet;
import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.context.InnerIndexContext;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.model.InnerIndexRequestParam;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックス サーブレット.
 * リクエストでBDBに対するCRUD処理を実行するサーブレット
 */
public class InnerIndexServlet extends ReflexServlet {

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
	 * シャットダウン時の処理.
	 * (2022.10.4)ServletContextListener.contextDestroyed が実行されないのでこちらに移動。
	 */
	@Override
	public void destroy() {
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] start.");
		}
		super.destroy();
		BDBEnvUtil.destroy();
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] end.");
		}
	}

	/**
	 * GETメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof InnerIndexRequest)) {
			logger.warn("[doGet] HttpServletRequest is not InnerIndexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof InnerIndexResponse)) {
			logger.warn("[doGet] HttpServletResponse is not InnerIndexResponse. " + httpResp.getClass().getName());
			return;
		}
		InnerIndexRequest req = (InnerIndexRequest)httpReq;
		InnerIndexResponse resp = (InnerIndexResponse)httpResp;
		InnerIndexRequestParam param = req.getRequestType();
		String serviceName = req.getServiceName();
		String namespace = req.getNamespace();
		String distkeyItem = req.getDistkeyItem();
		String distkeyValue = req.getDistkeyValue();
		ReflexBDBRequestInfo requestInfo = req.getRequestInfo();
		ReflexBDBConnectionInfo connectionInfo = req.getConnectionInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doGet start");
		}

		InnerIndexContext reflexContext = new InnerIndexContext(serviceName, namespace,
				requestInfo, connectionInfo);
		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			// Feed検索 : GET /b{親キー}?f&{項目名}-{演算子}-{値}&l={件数}&p={カーソル}
			// 件数取得 : GET /b{親キー}?c&{項目名}-{演算子}-{値}&l={件数}&p={カーソル}
			// BDBデータ確認 (管理用) : GET /b/?_list={テーブル名}
			// BDBの統計情報取得 (管理用) : GET /b/?_stats

			if (param.getOption(InnerIndexRequestParam.PARAM_LIST) != null) {
				// テーブルリスト取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_list");
				}

				FeedBase feed = reflexContext.getList(param);
				retObj = feed;
				if (retObj == null) {
					status = HttpStatus.SC_NO_CONTENT;
				}

			} else if (param.getOption(InnerIndexRequestParam.PARAM_COUNT) != null) {
				// 件数取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "getCount");
				}
				FeedBase feed = reflexContext.getCount(param, distkeyItem, distkeyValue);
				if (feed == null) {
					throw new NoEntryException();
				}
				retObj = feed;

			} else if (param.getOption(InnerIndexRequestParam.PARAM_FEED) != null) {
				// Feed取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "getFeed");
				}
				FeedBase feed = reflexContext.getFeed(param, distkeyItem, distkeyValue);
				if (feed == null) {
					throw new NoEntryException();
				}
				retObj = feed;

			} else if (param.getOption(InnerIndexRequestParam.PARAM_STATS) != null) {
				// BDB環境統計情報取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_stats");
				}

				FeedBase feed = reflexContext.getStats(param);
				retObj = feed;

			} else if (param.getOption(InnerIndexRequestParam.PARAM_DISKUSAGE) != null) {
				// ディスク使用率を取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_diskusage");
				}

				FeedBase feed = reflexContext.getDiskUsage(param);
				retObj = feed;

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
			}

			if (retObj != null) {
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

		// なし

		throw new MethodNotAllowedException("Method not allowed. " + httpReq.getMethod());

	}

	/**
	 * PUTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (!(httpReq instanceof InnerIndexRequest)) {
			logger.warn("[doPut] HttpServletRequest is not InnerIndexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof InnerIndexResponse)) {
			logger.warn("[doPut] HttpServletResponse is not InnerIndexResponse. " + httpResp.getClass().getName());
			return;
		}
		InnerIndexRequest req = (InnerIndexRequest)httpReq;
		InnerIndexResponse resp = (InnerIndexResponse)httpResp;
		InnerIndexRequestParam param = req.getRequestType();
		String serviceName = req.getServiceName();
		String namespace = req.getNamespace();
		ReflexBDBRequestInfo requestInfo = req.getRequestInfo();
		ReflexBDBConnectionInfo connectionInfo = req.getConnectionInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doPut start");
		}

		InnerIndexContext reflexContext = new InnerIndexContext(serviceName, namespace,
				requestInfo, connectionInfo);
		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			// PUT /b/?_index[&_partical] リクエストデータにFeed
			// PUT /b/?_backup BDBバックアップ

			if (param.getOption(InnerIndexRequestParam.PARAM_INDEX) != null) {
				// データ更新
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "put");
				}
				FeedBase feed = req.getFeed();
				if (feed == null || feed.entry == null || feed.entry.isEmpty()) {
					throw new IllegalParameterException("Feed is required.");
				}
				reflexContext.put(feed, param);
				retObj = createMessageFeed("Updated.");

			} else if (param.getOption(InnerIndexRequestParam.PARAM_BACKUP) != null) {
				// BDBバックアップ
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "backup");
				}

				FeedBase feed = req.getFeed();
				if (feed == null || StringUtils.isBlank(feed.title)) {
					throw new IllegalArgumentException("Storage URL is required.");
				}
				String storageUrl = feed.title;
				reflexContext.backupBDB(storageUrl);

				retObj = createMessageFeed("Backup complete. " + storageUrl);

			} else {
				throw new MethodNotAllowedException("Invalid parameter.");
			}

			if (retObj != null) {
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
		if (!(httpReq instanceof InnerIndexRequest)) {
			logger.warn("[doDelete] HttpServletRequest is not InnerIndexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof InnerIndexResponse)) {
			logger.warn("[doDelete] HttpServletResponse is not InnerIndexResponse. " + httpResp.getClass().getName());
			return;
		}
		InnerIndexRequest req = (InnerIndexRequest)httpReq;
		InnerIndexResponse resp = (InnerIndexResponse)httpResp;
		InnerIndexRequestParam param = req.getRequestType();
		String serviceName = req.getServiceName();
		String namespace = req.getNamespace();
		ReflexBDBRequestInfo requestInfo = req.getRequestInfo();
		ReflexBDBConnectionInfo connectionInfo = req.getConnectionInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doDelete start");
		}

		InnerIndexContext reflexContext = new InnerIndexContext(serviceName, namespace,
				requestInfo, connectionInfo);
		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			// BDBクリーン(指定のない名前空間削除処理も行う) : DELETE /b/?_clean
			// BDB環境クローズ : DELETE /b/?_close

			if (param.getOption(InnerIndexRequestParam.PARAM_CLEAN) != null) {
				// BDBクリーン
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_clean");
				}
				reflexContext.cleanBDB(param);
				retObj = createMessageFeed("Accepted. clean");
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(InnerIndexRequestParam.PARAM_CLOSE) != null) {
				// BDB環境クローズ
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_close");
				}
				reflexContext.closeBDBEnv();
				retObj = createMessageFeed("Accepted. close : " + req.getNamespace());
				status = HttpStatus.SC_OK;

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			} else if (status != HttpStatus.SC_OK) {
				resp.setStatus(status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * <p>
	 * ステータスは200(OK)を設定します.
	 * </p>
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 */
	protected void doResponse(InnerIndexRequest req, InnerIndexResponse resp,
			Object retObj)
	throws IOException {
		doResponse(req, resp, retObj, SC_OK);
	}

	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 * @param status ステータスコード
	 */
	protected void doResponse(InnerIndexRequest req, InnerIndexResponse resp,
			Object retObj, int status)
	throws IOException {
		doResponse(req, resp, retObj, status, null);
	}

	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 * @param status ステータスコード
	 * @param contentType Content-Type
	 */
	protected void doResponse(InnerIndexRequest req, InnerIndexResponse resp,
			Object retObj, int status, String contentType)
	throws IOException {
		int format = req.getResponseFormat();
		Object respObj = retObj;
		// オブジェクトがEntry形式の場合、Feedで囲む。
		if (retObj instanceof EntryBase) {
			respObj = TaggingEntryUtil.createAtomFeed((EntryBase)retObj);
		}

		doResponse(req, resp, respObj, format,
				BDBEnvUtil.getAtomResourceMapper(),
				req.getConnectionInfo().getDeflateUtil(), status, contentType,
				BDBEnvUtil.isGZip(), BDBEnvUtil.isPrintNamespace(),
				BDBEnvUtil.isNoCache(req), BDBEnvUtil.isSameOrigin(req));
	}

	/**
	 * Feedを生成し、titleにメッセージを入れます。
	 * @param msg メッセージ
	 * @param serviceName サービス名
	 * @return Feed
	 */
	private FeedBase createMessageFeed(String msg) {
		if (msg == null) {
			return null;
		}
		FeedBase feed = TaggingEntryUtil.createAtomFeed();
		feed.setTitle(msg);
		return feed;
	}

}
