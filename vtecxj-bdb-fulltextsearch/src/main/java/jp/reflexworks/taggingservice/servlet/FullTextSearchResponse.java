package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.taggingservice.api.ReflexRequest;

/**
 * TaggingService用レスポンス.
 */
public class FullTextSearchResponse extends ReflexBDBResponse {

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 * @param httpResp レスポンス
	 */
	public FullTextSearchResponse(ReflexRequest req, HttpServletResponse httpResp)
	throws IOException {
		super(req, httpResp);
	}

}
