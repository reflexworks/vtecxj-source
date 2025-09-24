package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.taggingservice.model.AllocidsRequestParam;

/**
 * BDBインデックスサーバ用リクエスト.
 */
public class AllocidsRequest extends ReflexBDBRequest
implements ReflexBDBServletConst {

	/** リクエストパラメータ情報 */
	private AllocidsRequestParam param;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public AllocidsRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public AllocidsRequestParam getRequestType() {
		if (param == null) {
			param = new AllocidsRequestParam(this);
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
		AllocidsRequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}

}
