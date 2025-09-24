package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServlet;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.bdb.BDBEntryUtil;
import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.context.BDBEntryContext;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.model.BDBEntryRequestParam;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックス サーブレット.
 * リクエストでBDBに対するCRUD処理を実行するサーブレット
 */
public class BDBEntryServlet extends ReflexServlet {

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
		if (!(httpReq instanceof BDBEntryRequest)) {
			logger.warn("[doGet] HttpServletRequest is not BDBEntryRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof BDBEntryResponse)) {
			logger.warn("[doGet] HttpServletResponse is not BDBEntryRequest. " + httpResp.getClass().getName());
			return;
		}
		BDBEntryRequest req = (BDBEntryRequest)httpReq;
		BDBEntryResponse resp = (BDBEntryResponse)httpResp;
		BDBEntryRequestParam param = req.getRequestType();
		String serviceName = req.getServiceName();
		String namespace = req.getNamespace();
		ReflexBDBRequestInfo requestInfo = req.getRequestInfo();
		ReflexBDBConnectionInfo connectionInfo = req.getConnectionInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doGet start");
		}

		BDBEntryContext reflexContext = new BDBEntryContext(serviceName, namespace,
				requestInfo, connectionInfo);
		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			// Entry取得 : GET /b{ID}?e
			// BDBデータ確認 (管理用) : GET /b/?_list={テーブル名}
			// BDBの統計情報取得 (管理用) : GET /b/?_stats

			if (param.getOption(BDBEntryRequestParam.PARAM_ENTRY) != null) {
				// Entry取得
				if (param.getOption(BDBEntryRequestParam.PARAM_MULTIPLE) != null) {
					// 複数
					List<String> ids = BDBEntryUtil.getIdsFromHeader(req);
					retObj = reflexContext.getMultiple(ids);
				} else {
					// 1件
					String id = req.getPathInfo();
					retObj = reflexContext.get(id);
				}

			} else if (param.getOption(BDBEntryRequestParam.PARAM_LIST) != null) {
				// テーブルリスト取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_list");
				}

				FeedBase feed = reflexContext.getList(param);
				retObj = feed;
				if (retObj == null) {
					status = HttpStatus.SC_NO_CONTENT;
				}

			} else if (param.getOption(BDBEntryRequestParam.PARAM_STATS) != null) {
				// BDB環境統計情報取得
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_stats");
				}

				FeedBase feed = reflexContext.getStats(param);
				retObj = feed;

			} else if (param.getOption(BDBEntryRequestParam.PARAM_DISKUSAGE) != null) {
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
				if (retObj instanceof byte[]) {
					doResponseBytes(req, resp, (byte[])retObj);
				} else if (retObj instanceof List) {
					doResponseBytesList(req, resp, (List<byte[]>)retObj);
				} else {
					doResponse(req, resp, retObj, status);
				}
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
		if (!(httpReq instanceof BDBEntryRequest)) {
			logger.warn("[doPut] HttpServletRequest is not BDBEntryRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof BDBEntryResponse)) {
			logger.warn("[doPut] HttpServletResponse is not BDBEntryRequest. " + httpResp.getClass().getName());
			return;
		}
		BDBEntryRequest req = (BDBEntryRequest)httpReq;
		BDBEntryResponse resp = (BDBEntryResponse)httpResp;
		BDBEntryRequestParam param = req.getRequestType();
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

		BDBEntryContext reflexContext = new BDBEntryContext(serviceName, namespace,
				requestInfo, connectionInfo);
		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			// Entry登録更新 : PUT /b{ID}?e
			// PUT /b/?_backup BDBバックアップ

			if (param.getOption(BDBEntryRequestParam.PARAM_ENTRY) != null) {
				// Entry登録更新
				if (param.getOption(BDBEntryRequestParam.PARAM_MULTIPLE) != null) {
					// 複数
					List<String> ids = BDBEntryUtil.getIdsFromHeader(req);
					List<byte[]> dataList = BDBEntryUtil.getDataListFromRequest(req);
					reflexContext.putMultiple(ids, dataList);
					status = HttpStatus.SC_CREATED;
					retObj = createMessageFeed("Created or updated. " + req.getHeader(Constants.HEADER_ID));
				} else {
					// 1件
					String id = req.getPathInfo();
					byte[] data = req.getPayload();
					reflexContext.put(id, data);
					status = HttpStatus.SC_CREATED;
					retObj = createMessageFeed("Created or updated. " + id);
				}

			} else if (param.getOption(BDBEntryRequestParam.PARAM_BACKUP) != null) {
				// BDBバックアップ
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "backup");
				}

				FeedBase feed = req.getFeed();
				if (feed == null || StringUtils.isBlank(feed.title)) {
					throw new IllegalParameterException("Storage URL is required.");
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
		if (!(httpReq instanceof BDBEntryRequest)) {
			logger.warn("[doDelete] HttpServletRequest is not BDBEntryRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof BDBEntryResponse)) {
			logger.warn("[doDelete] HttpServletResponse is not BDBEntryRequest. " + httpResp.getClass().getName());
			return;
		}
		BDBEntryRequest req = (BDBEntryRequest)httpReq;
		BDBEntryResponse resp = (BDBEntryResponse)httpResp;
		BDBEntryRequestParam param = req.getRequestType();
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

		BDBEntryContext reflexContext = new BDBEntryContext(serviceName, namespace,
				requestInfo, connectionInfo);
		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			// Entry削除 : DELETE /b{ID}?e
			// BDBクリーン(指定のない名前空間削除処理も行う) : DELETE /b/?_clean
			// BDB環境クローズ : DELETE /b/?_close

			if (param.getOption(BDBEntryRequestParam.PARAM_ENTRY) != null) {
				// Entry削除
				if (param.getOption(BDBEntryRequestParam.PARAM_MULTIPLE) != null) {
					// 複数
					List<String> ids = BDBEntryUtil.getIdsFromHeader(req);
					reflexContext.deleteMultiple(ids);
					retObj = createMessageFeed("Deleted. " + req.getHeader(Constants.HEADER_ID));
				} else {
					// 1件
					String id = req.getPathInfo();
					reflexContext.delete(id);
					retObj = createMessageFeed("Deleted. " + id);
				}

			} else if (param.getOption(BDBEntryRequestParam.PARAM_CLEAN) != null) {
				// BDBクリーン
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_clean");
				}
				reflexContext.cleanBDB(param);
				retObj = createMessageFeed("Accepted. clean");
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(BDBEntryRequestParam.PARAM_CLOSE) != null) {
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
	 * バイト配列をそのままレスポンスする.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param respData バイト配列データ
	 */
	private void doResponseBytes(BDBEntryRequest req, BDBEntryResponse resp,
			byte[] respData)
	throws IOException {
		int status = HttpStatus.SC_OK;
		if (respData == null || respData.length == 0) {
			status = HttpStatus.SC_NO_CONTENT;
		}
		doResponseHeader(req, resp, status);
		doResponseData(req, resp, respData, status);
	}

	/**
	 * バイト配列リストをそのままレスポンスする.
	 * レスポンスヘッダにバイト配列長を設定する。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param respDataList バイト配列データリスト
	 */
	private void doResponseBytesList(BDBEntryRequest req, BDBEntryResponse resp,
			List<byte[]> respDataList)
	throws IOException {
		int status = HttpStatus.SC_OK;
		if (respDataList == null || respDataList.isEmpty()) {
			status = HttpStatus.SC_NO_CONTENT;
		}

		doResponseHeader(req, resp, status);
		String entryLengthStr = BDBEntryUtil.getHeaderEntryLength(respDataList);
		resp.addHeader(Constants.HEADER_ENTRY_LENGTH, entryLengthStr);

		byte[] allData = BDBEntryUtil.lineupData(respDataList);
		doResponseData(req, resp, allData, status);
	}

	/**
	 * Entryバイトレスポンス ヘッダの設定処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param status ステータス
	 */
	private void doResponseHeader(BDBEntryRequest req, BDBEntryResponse resp,
			int status) {
		boolean isNoCache = BDBEnvUtil.isNoCache(req);
		boolean isSameOrigin = BDBEnvUtil.isSameOrigin(req);

		// ステータスコードの設定
		resp.setStatus(status);

		// ブラウザにキャッシュしない場合
		if (isNoCache) {
			resp.addHeader(PRAGMA, NO_CACHE);
			resp.addHeader(CACHE_CONTROL, CACHE_CONTROL_VALUE);
			resp.addHeader(EXPIRES, PAST_DATE);
		}
		// SAMEORIGIN指定 (クリックジャッキング対策)
		if (isSameOrigin) {
			resp.addHeader(HEADER_FRAME_OPTIONS, SAMEORIGIN);
		}
		// ブラウザのクロスサイトスクリプティングのフィルタ機能を使用
		resp.addHeader(HEADER_XSS_PROTECTION, HEADER_XSS_PROTECTION_MODEBLOCK);
		// HTTPレスポンス全体を検査（sniffing）してコンテンツ タイプを判断し、「Content-Type」を無視した動作を行うことを防止する。(IE対策)
		resp.addHeader(HEADER_CONTENT_TYPE_OPTIONS, HEADER_CONTENT_TYPE_OPTIONS_NOSNIFF);
	}

	/**
	 * Entryバイトレスポンス データの設定処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param respData レスポンスデータ
	 * @param status ステータス
	 */
	private void doResponseData(BDBEntryRequest req, BDBEntryResponse resp, byte[] respData,
			int status)
	throws IOException {
		if (status == HttpStatus.SC_OK) {
			// Content-Type: messagepack
			resp.setContentType(CONTENT_TYPE_MESSAGEPACK);

			boolean isGZip = BDBEnvUtil.isGZip();
			boolean isRespGZip = isGZip && isGZip(req);
			boolean isDeflate = ReflexServletUtil.isSetHeader(req, HEADER_ACCEPT_ENCODING,
					HEADER_VALUE_DEFLATE);

			OutputStream out = null;
			try {
				// Deflateの場合はGZip圧縮しない。
				if (isDeflate) {
					resp.setHeader(HEADER_CONTENT_ENCODING, HEADER_VALUE_DEFLATE);
					out = resp.getOutputStream();
				} else if (isRespGZip) {
					setGZipHeader(resp);
					out = new GZIPOutputStream(resp.getOutputStream());
				} else {
					out = resp.getOutputStream();
				}
				out.write(respData);

			} finally {
				try {
					out.close();
				} catch (IOException e) {
					logger.warn("[doResponseData] close error.", e);
				}
			}
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
	protected void doResponse(BDBEntryRequest req, BDBEntryResponse resp,
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
	protected void doResponse(BDBEntryRequest req, BDBEntryResponse resp,
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
	protected void doResponse(BDBEntryRequest req, BDBEntryResponse resp,
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
