package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.taggingservice.model.ManifestRequestParam;

/**
 * BDBインデックスサーバ用リクエスト.
 */
public class ManifestRequest extends ReflexBDBRequest
implements ReflexBDBServletConst {

	/** リクエストパラメータ情報 */
	private ManifestRequestParam param;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public ManifestRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public ManifestRequestParam getRequestType() {
		if (param == null) {
			param = new ManifestRequestParam(this);
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
		ManifestRequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}

}
