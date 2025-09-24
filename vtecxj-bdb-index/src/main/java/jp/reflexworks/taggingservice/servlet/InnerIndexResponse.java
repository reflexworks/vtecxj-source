package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * TaggingService用レスポンス.
 */
public class InnerIndexResponse extends ReflexBDBResponse {

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 * @param httpResp レスポンス
	 */
	public InnerIndexResponse(InnerIndexRequest req, HttpServletResponse httpResp)
	throws IOException {
		super(req, httpResp);
	}

}
