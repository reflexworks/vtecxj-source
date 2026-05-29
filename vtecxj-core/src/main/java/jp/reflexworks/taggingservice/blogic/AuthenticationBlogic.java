package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;

/**
 * 認証系処理クラス.
 */
public class AuthenticationBlogic {
	
	/**
	 * アクセストークン認証でセッション生成
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return SID
	 */
	public String createSession(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		AuthenticationManager authenticationManager = TaggingEnvUtil.getAuthenticationManager();
		return authenticationManager.createSession(req, resp);
	}

}
