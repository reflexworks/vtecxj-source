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
	 * 既存ユーザとソーシャルログインユーザの紐付けリクエスト.
	 * WSSE認証を行い、対象アカウントのメールアドレス宛に確認コードを送信する。
	 * この時点ではユーザ情報・セッション情報の更新は行わない。
	 * @param provider OAuthプロバイダ
	 * @param wsse WSSE
	 * @param reflexContext ReflexContext
	 */
	public void mergeUser(String provider, String wsse, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 既存ユーザとソーシャルログインユーザの紐付け実行.
	 * 確認コードを照合し、一致すれば紐付けを実行する。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param verifyCode 確認コード
	 * @param reflexContext ReflexContext
	 * @return 更新後のユーザトップエントリー
	 */
	public EntryBase verifyMergeUser(ReflexRequest req, ReflexResponse resp,
			String verifyCode, ReflexContext reflexContext)
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
