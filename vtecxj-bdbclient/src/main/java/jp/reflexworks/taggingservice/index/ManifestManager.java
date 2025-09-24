package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * Manifest管理クラス.
 */
public class ManifestManager extends IndexCommonManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Manifest検索
	 * @param editedCondition 検索条件
	 * @param cursorStr カーソル
	 * @param limit 最大取得件数
	 * @param isCount 件数取得の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public FeedBase getByManifest(EditedCondition editedCondition,
			String cursorStr, int limit, boolean isCount,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getFeedByIndex(editedCondition, cursorStr, limit, isCount,
				BDBIndexType.MANIFEST, auth, requestInfo, connectionInfo);
	}

	/**
	 * Entry複数検索.
	 * @param uris URIリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return IDリスト
	 */
	public FeedBase getEntryIds(List<String> uris, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (uris == null || uris.isEmpty()) {
			return null;
		}
		String serviceName = auth.getServiceName();
		List<Link> links = new ArrayList<>();
		for (String uri : uris) {
			Link link = new Link();
			link._$rel = Link.REL_SELF;
			link._$href = uri;
			links.add(link);
		}
		FeedBase reqFeed = TaggingEntryUtil.createAtomFeed();
		reqFeed.link = links;

		String requestUri = "?" + RequestParam.PARAM_ENTRY;
		String method = Constants.POST;

		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<FeedBase> bdbResponseInfo = bdbRequester.requestToManifest(requestUri,
				method, reqFeed, serviceName, requestInfo, connectionInfo);
		return bdbResponseInfo.data;
	}

	/**
	 * Manifestの環境クローズ
	 * @param serviceName サービス名
	 * @param reflexContext ReflexContext
	 * @return リクエストを送った場合true、対象サーバが無い場合false
	 */
	public boolean closeManifest(String serviceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return closeIndex(BDBIndexType.MANIFEST, reflexContext);
	}

}
