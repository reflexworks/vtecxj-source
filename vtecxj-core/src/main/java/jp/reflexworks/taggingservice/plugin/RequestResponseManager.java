package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * リクエスト・レスポンス管理インターフェース
 */
public interface RequestResponseManager extends ReflexPlugin {

	/**
	 * レスポンスについてGZIP圧縮を行うかどうかを取得.
	 * この値はシステムで固定となる想定。
	 * @return レスポンスデータをGZIP圧縮する場合true
	 */
	public boolean isGZip();

	/**
	 * レスポンスのXMLに名前空間を出力するかどうかを取得.
	 * この値はシステムで固定となる想定。
	 * @return レスポンスのXMLに名前空間を出力する場合true
	 */
	public boolean isPrintNamespace();

	/**
	 * レスポンスヘッダに、ブラウザにキャッシュを残さないオプションを付けるかどうかを取得.
	 * @return ブラウザにキャッシュを残さないオプションを付ける場合true
	 */
	public boolean isNoCache(ReflexRequest req);

	/**
	 * レスポンスヘッダに、フレームオプションのSameOrigin指定を付けるかどうかを取得.
	 * @return フレームオプションのSameOrigin指定を付ける場合true
	 */
	public boolean isSameOrigin(ReflexRequest req);

	/**
	 * ReflexRequestを生成
	 * @param httpReq リクエスト
	 * @return ReflexRequest
	 */
	public ReflexRequest createReflexRequest(HttpServletRequest httpReq)
	throws IOException;

	/**
	 * ReflexResponseを生成
	 * @param req リクエスト
	 * @param httpResp レスポンス
	 * @return ReflexResponse
	 */
	public ReflexResponse createReflexResponse(ReflexRequest req,
			HttpServletResponse httpResp)
	throws IOException;

	/**
	 * 認証処理後の処理.
	 * 認証情報をリクエストにセット。
	 * 処理継続判定。仮認証の場合はレスポンスにステータス・メッセージをセットする。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param auth 認証情報
	 * @return 処理を継続する場合true。仮認証の場合false。
	 */
	public boolean afterAuthenticate(ReflexRequest req, ReflexResponse resp,
			ReflexAuthentication auth)
	throws IOException, TaggingException;

	/**
	 * コンテンツをレスポンスする.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param contentInfo コンテンツ情報
	 */
	public void doContent(ReflexRequest req, ReflexResponse resp,
			ReflexContentInfo contentInfo)
	throws IOException;

}
