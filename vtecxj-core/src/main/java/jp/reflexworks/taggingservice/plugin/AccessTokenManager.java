package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * アクセストークン生成・管理インターフェース.
 */
public interface AccessTokenManager extends ReflexPlugin {
	
	/**
	 * アクセストークン認証.
	 * @param accessToken アクセストークン
	 * @param reflexContext ReflexContext
	 * @return 認証OKの場合true
	 */
	public boolean checkAccessToken(String accessToken, ReflexContext reflexContext) 
	throws IOException, TaggingException;
	
	/**
	 * リンクトークン認証.
	 * @param linkToken リンクトークン
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return 認証OKの場合true
	 */
	public boolean checkLinkToken(String linkToken, String uri, 
			ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アクセストークン取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return アクセストークン
	 */
	public String getAccessTokenByUid(String uid, ReflexContext reflexContext) 
	throws IOException, TaggingException;
	
	/**
	 * リンクトークン取得.
	 * @param uid UID
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return リンクトークン
	 */
	public String getLinkToken(String uid, String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException;
	
	/**
	 * アクセスキー更新.
	 * @param uid UID
	 * @param accessKey アクセスキー
	 * @param reflexContext ReflexContext
	 * @return アクセスキー情報
	 */
	public FeedBase putAccessKey(String uid, String accessKey, ReflexContext reflexContext) 
	throws IOException, TaggingException;
	
	/**
	 * アクセスキー生成.
	 * @return アクセスキー
	 */
	public String createAccessKeyStr()
	throws IOException, TaggingException;
	
	/**
	 * アクセストークンからUIDを取得.
	 * @param accessToken アクセストークン
	 * @return UID
	 */
	public String getUidByAccessToken(String accessToken)
	throws IOException, TaggingException;
	
	/**
	 * リクエストヘッダからアクセストークンを取り出す。
	 * @param req リクエスト
	 * @return アクセストークン
	 */
	public String getAccessTokenFromRequest(ReflexRequest req)
	throws IOException, TaggingException;
	
	/**
	 * リクエストパラメータからリンクトークンを取り出す。
	 * @param req リクエスト
	 * @return リンクトークン
	 */
	public String getLinkTokenFromRequest(ReflexRequest req)
	throws IOException, TaggingException;

}
