package jp.reflexworks.taggingservice.api;

import java.io.IOException;

import jp.reflexworks.servlet.ReflexServlet;
import jp.reflexworks.taggingservice.servlet.TaggingServletUtil;

/**
 * TaggingService用サーブレット基底クラス.
 * Entry、Feedのレスポンス、コンテンツのレスポンスをサポートします。
 */
public abstract class ReflexServletBase extends ReflexServlet {
	
	/**
	 * エントリーのコンテンツのみレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param data コンテンツ
	 */
	protected void doContent(ReflexRequest req, ReflexResponse resp, 
			ReflexContentInfo contentInfo)
	throws IOException {
		TaggingServletUtil.doContent(req, resp, contentInfo);
	}

	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * <p>
	 * ステータスは200(OK)を設定します.
	 * </p>
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 */
	protected void doResponse(ReflexRequest req, ReflexResponse resp,
			Object retObj) 
	throws IOException {
		TaggingServletUtil.doResponse(req, resp, retObj);
	}
	
	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 * @param status ステータスコード
	 */
	protected void doResponse(ReflexRequest req, ReflexResponse resp,
			Object retObj, int status) 
	throws IOException {
		TaggingServletUtil.doResponse(req, resp, retObj, status);
	}

	/**
	 * オブジェクトをシリアライズしてレスポンスに出力します.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param retObj シリアライズ対象オブジェクト
	 * @param status ステータスコード
	 * @param contentType Content-Type
	 */
	protected void doResponse(ReflexRequest req, ReflexResponse resp,
			Object retObj, int status, String contentType) 
	throws IOException {
		TaggingServletUtil.doResponse(req, resp, retObj, status, contentType);
	}

}
