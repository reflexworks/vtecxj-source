package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.taggingservice.model.InnerIndexRequestParam;

/**
 * BDBインデックスサーバ用リクエスト.
 */
public class InnerIndexRequest extends ReflexBDBRequest
implements ReflexBDBServletConst {

	/** リクエストパラメータ情報 */
	private InnerIndexRequestParam param;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public InnerIndexRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public InnerIndexRequestParam getRequestType() {
		if (param == null) {
			param = new InnerIndexRequestParam(this);
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
		InnerIndexRequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}

}
