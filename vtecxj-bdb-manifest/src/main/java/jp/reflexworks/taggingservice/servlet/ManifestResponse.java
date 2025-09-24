package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * TaggingService用レスポンス.
 */
public class ManifestResponse extends ReflexBDBResponse {

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 * @param httpResp レスポンス
	 */
	public ManifestResponse(ManifestRequest req, HttpServletResponse httpResp)
	throws IOException {
		super(req, httpResp);
	}

}
