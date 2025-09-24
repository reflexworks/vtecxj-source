package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーブレットのユーティリティ.
 * 主にレスポンス処理
 */
public class TaggingServletUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(TaggingServletUtil.class);

	/**
	 * エントリーのコンテンツのみレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param data コンテンツ
	 */
	public static void doContent(ReflexRequest req, ReflexResponse resp,
			ReflexContentInfo contentInfo)
	throws IOException {
		// ブラウザのクロスサイトスクリプティングのフィルタ機能を使用
		resp.addHeader(ReflexServletConst.HEADER_XSS_PROTECTION, ReflexServletConst.HEADER_XSS_PROTECTION_MODEBLOCK);
		// HTTPレスポンス全体を検査（sniffing）してコンテンツ タイプを判断し、「Content-Type」を無視した動作を行うことを防止する。(IE対策)
		resp.addHeader(ReflexServletConst.HEADER_CONTENT_TYPE_OPTIONS, ReflexServletConst.HEADER_CONTENT_TYPE_OPTIONS_NOSNIFF);

		RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
		reqRespManager.doContent(req, resp, contentInfo);
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
	public static void doResponse(ReflexRequest req, ReflexResponse resp,
			Object retObj)
	throws IOException {
		doResponse(req, resp, retObj, HttpStatus.SC_OK);
	}

	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 * @param status ステータスコード
	 */
	public static void doResponse(ReflexRequest req, ReflexResponse resp,
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
	public static void doResponse(ReflexRequest req, ReflexResponse resp,
			Object retObj, int status, String contentType)
	throws IOException {
		RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();

		int format = req.getResponseFormat();
		Object respObj = retObj;
		boolean startArrayBracket = getStartArrayBracket(req);
		if (retObj != null) {
			if (retObj instanceof FeedBase) {
				// titleに値が設定されている場合は StartArrayBracket を有効にしない。
				FeedBase feed = (FeedBase)retObj;
				if (feed == null || !StringUtils.isBlank(feed.title) ||
						!StringUtils.isBlank(feed.subtitle)) {
					startArrayBracket = false;
				}
			} else if (retObj instanceof EntryBase) {
				// オブジェクトがEntry形式の場合、以下の条件であればFeedで囲む。
				// ・startArrayBracket=false または MessagePack形式
				if (!startArrayBracket || format == ReflexServletConst.FORMAT_MESSAGEPACK) {
					respObj = TaggingEntryUtil.createFeed(req.getServiceName(), (EntryBase)retObj);
				}
			}
			if (respObj instanceof FeedBase) {
				((FeedBase)respObj).setStartArrayBracket(startArrayBracket);
				// カーソルはレスポンスヘッダに設定する。
				setCursorToResponseHeader((FeedBase)respObj, resp, startArrayBracket);
			}
		}

		ReflexServletUtil.doResponse(req, resp, respObj, format,
				TaggingEnvUtil.getResourceMapper(req.getServiceName()),
				req.getConnectionInfo().getDeflateUtil(), status,
				reqRespManager.isGZip(), reqRespManager.isPrintNamespace(),
				reqRespManager.isNoCache(req), reqRespManager.isSameOrigin(req),
				contentType);
	}

	/**
	 * StartArrayBracketに設定する値を取得.
	 * 戻り値がJSON形式の場合に有効となる設定。
	 * @param req リクエスト
	 * @return startArrayBracketに設定する値.
	 */
	private static boolean getStartArrayBracket(ReflexRequest req) {
		String serviceName = req.getServiceName();
		boolean startArrayBracket = TaggingEnvConst.JSON_STARTARRAYBRACKET_DEFAULT;
		try {
			startArrayBracket = TaggingEnvUtil.getPropBoolean(serviceName,
					SettingConst.JSON_STARTARRAYBRACKET,
					TaggingEnvConst.JSON_STARTARRAYBRACKET_DEFAULT);
		} catch (InvalidServiceSettingException e) {
			logger.warn("[setStartArrayBracket] InvalidServiceSettingException: " +
					SettingConst.JSON_STARTARRAYBRACKET, e);
		}
		return startArrayBracket;
	}

	/**
	 * Feedにカーソルが設定されている場合、レスポンスヘッダに設定する。
	 * @param feed Feed
	 * @param resp レスポンス
	 * @param startArrayBracket JSONにてfeed.entryを省略する場合true
	 */
	private static void setCursorToResponseHeader(FeedBase feed, ReflexResponse resp,
			boolean startArrayBracket) {
		if (feed != null && feed.link != null && !feed.link.isEmpty()) {
			for (Link link : feed.link) {
				if (Link.REL_NEXT.equals(link._$rel) && !StringUtils.isBlank(link._$href)) {
					String cursor = UrlUtil.urlEncode(link._$href);
					resp.addHeader(Constants.HEADER_NEXTPAGE, cursor);
				}
			}
		}
	}

}
