package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.taggingservice.model.BDBEntryRequestParam;

/**
 * BDBインデックスサーバ用リクエスト.
 */
public class BDBEntryRequest extends ReflexBDBRequest
implements ReflexBDBServletConst {

	/** リクエストパラメータ情報 */
	private BDBEntryRequestParam param;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public BDBEntryRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public BDBEntryRequestParam getRequestType() {
		if (param == null) {
			param = new BDBEntryRequestParam(this);
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
		BDBEntryRequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}

}
