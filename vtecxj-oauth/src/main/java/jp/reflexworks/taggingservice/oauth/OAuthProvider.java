package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * OAuth2 provider interface
 */
public interface OAuthProvider {
	
	/**
	 * OAuthプロバイダへ認証要求開始
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void oauth(ReflexRequest req, ReflexResponse resp) 
	throws IOException, TaggingException;
	
	/**
	 * OAuthプロバイダからcallback.
	 * ユーザ識別情報を取得するところまで行う。
	 * @param req リクエスト
	 * @return OAuth認証で取得したユーザ情報
	 */
	public OAuthInfo callback(ReflexRequest req)
	throws IOException, TaggingException;
	
	/**
	 * OAuthプロバイダ名を取得.
	 */
	public String getProviderName();

}
