package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * OAuth管理プラグインクラス.
 * (一部TaggingServiceとの連携が必要な処理)
 */
public interface OAuthManager extends ReflexPlugin {
	
	/**
	 * 既存ユーザとソーシャルログインユーザの紐付け処理
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ
	 * @param rxid RXID
	 * @param reflexContext ReflexContext
	 * @return 更新後のユーザトップエントリー
	 */
	public EntryBase mergeUser(ReflexRequest req, ReflexResponse resp, 
			String provider, String rxid, ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * ユーザ削除.
	 * ソーシャルログインエントリーを削除する。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 */
	public void deleteUser(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException;

}
