package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.taggingservice.model.FullTextSearchRequestParam;
import jp.reflexworks.taggingservice.util.ReflexBDBServiceUtil;

/**
 * 全文検索インデックスサーバ用リクエスト.
 */
public class FullTextSearchRequest extends ReflexBDBRequest
implements ReflexBDBServletConst {

	/** SID */
	private String sid;

	/** リクエストパラメータ情報 */
	private FullTextSearchRequestParam param;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public FullTextSearchRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);

		init(httpReq);
	}

	/**
	 * TaggingService用リクエスト生成処理.
	 * @param httpReq リクエスト
	 */
	private void init(HttpServletRequest httpReq) throws IOException {
		// SID取得
		sid = ReflexBDBServiceUtil.getSid(this, requestInfo, connectionInfo);
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public FullTextSearchRequestParam getRequestType() {
		if (param == null) {
			param = new FullTextSearchRequestParam(this);
		}
		return param;
	}

	/**
	 * レスポンスフォーマットを取得.
	 * <ul>
	 * <li>1: XML</li>
	 * <li>2: JSON</li>
	 * <li>3: MessagePack</li>
	 * </ul>
	 */
	public int getResponseFormat() {
		FullTextSearchRequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}

	/**
	 * SIDを取得.
	 * @return SID
	 */
	public String getSid() {
		return sid;
	}

}
