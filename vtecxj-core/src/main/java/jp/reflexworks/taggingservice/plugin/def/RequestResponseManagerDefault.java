package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.HeaderUtil;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.blogic.ContentBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.servlet.TaggingRequest;
import jp.reflexworks.taggingservice.servlet.TaggingResponse;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエスト・レスポンス管理クラス.
 * このクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 */
public class RequestResponseManagerDefault implements RequestResponseManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * レスポンスについてGZIP圧縮を行うかどうかを取得.
	 * この値はシステムで固定となる想定。
	 * @return レスポンスデータをGZIP圧縮する場合true
	 */
	public boolean isGZip() {
		return true;	// 
	}

	/**
	 * レスポンスのXMLに名前空間を出力するかどうかを取得.
	 * この値はシステムで固定となる想定。
	 * @return レスポンスのXMLに名前空間を出力する場合true
	 */
	public boolean isPrintNamespace() {
		return false;	// 
	}

	/**
	 * レスポンスヘッダに、ブラウザにキャッシュを残さないオプションを付けるかどうかを取得.
	 * @return ブラウザにキャッシュを残さないオプションを付ける場合true
	 */
	public boolean isNoCache(ReflexRequest req) {
		return true;	// 
	}

	/**
	 * レスポンスヘッダに、フレームオプションのSameOrigin指定を付けるかどうかを取得.
	 * @return フレームオプションのSameOrigin指定を付ける場合true
	 */
	public boolean isSameOrigin(ReflexRequest req) {
		return true;	// 
	}

	/**
	 * ReflexRequestを生成
	 * @param httpReq リクエスト
	 * @return ReflexRequest
	 */
	public ReflexRequest createReflexRequest(HttpServletRequest httpReq)
	throws IOException {
		return new TaggingRequest(httpReq);
	}

	/**
	 * ReflexResponseを生成
	 * @param req リクエスト
	 * @param httpResp レスポンス
	 * @return ReflexResponse
	 */
	public ReflexResponse createReflexResponse(ReflexRequest req,
			HttpServletResponse httpResp)
	throws IOException {
		return new TaggingResponse(req, httpResp);
	}

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
	throws IOException, TaggingException {
		// 認証情報をリクエストにセット
		setAuthToRequest(req, auth);
		// 仮認証判定
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		return authManager.afterAutheticate(req, resp, auth);
	}

	/**
	 * 認証情報をリクエストにセット.
	 * @param req リクエスト
	 * @param auth 認証情報
	 */
	private void setAuthToRequest(ReflexRequest req, ReflexAuthentication auth) {
		if (req instanceof TaggingRequest) {
			TaggingRequest tReq = (TaggingRequest)req;
			tReq.setAuth(auth);
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(req.getRequestInfo()));
			sb.append("[setAuthToRequest] req is not TaggingRequest. ");
			sb.append(req.getClass().getName());
			logger.warn(sb.toString());
		}
	}

	/**
	 * コンテンツをレスポンスする.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param contentInfo コンテンツ情報
	 */
	public void doContent(ReflexRequest req, ReflexResponse resp,
			ReflexContentInfo contentInfo)
	throws IOException {
		if (contentInfo == null) {
			return;
		}

		// 更新情報を設定
		setEtag(resp, contentInfo);

		// Etagチェック
		if (isNotModified(req, contentInfo)) {
			// Not Modifiedを返す
			resp.setStatus(HttpStatus.SC_NOT_MODIFIED);
			return;
		}

		// ヘッダ設定
		Map<String, String> headers = contentInfo.getHeaders();
		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				resp.addHeader(header.getKey(), header.getValue());
			}
		}

		// ストリームはメソッド内でクローズする
		FileUtil.writeToOutputStream(contentInfo.getInputStream(), resp.getOutputStream());
	}

	/**
	 * コンテンツが更新されていないかどうかチェック.
	 * リクエストのEtagとコンテンツのEtagを比較します。
	 * @param req リクエスト
	 * @para contentInfo コンテンツ情報
	 * @return コンテンツが更新されていない場合true
	 */
	private boolean isNotModified(ReflexRequest req, ReflexContentInfo contentInfo) {
		ContentBlogic contentBlogic = new ContentBlogic();
		return contentBlogic.isNotModified(req, getEtag(contentInfo));
	}

	/**
	 * コンテンツ情報からEtagを取得.
	 * @param contentInfo コンテンツ情報
	 * @return Etag
	 */
	private String getEtag(ReflexContentInfo contentInfo) {
		if (contentInfo != null) {
			return contentInfo.getEtag();
		}
		return null;
	}

	/**
	 * 更新情報をレスポンスに設定
	 * @param resp レスポンス
	 * @param contentInfo コンテンツ情報
	 */
	private void setEtag(ReflexResponse resp, ReflexContentInfo contentInfo) {
		String etag = contentInfo.getEtag();
		if (!StringUtils.isBlank(etag)) {
			resp.addHeader(ReflexServletConst.HEADER_ETAG,
					HeaderUtil.addDoubleQuotation(etag));
		}
	}

}
