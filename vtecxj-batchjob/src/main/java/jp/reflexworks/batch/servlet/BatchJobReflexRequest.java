package jp.reflexworks.batch.servlet;

import java.io.IOException;
import java.util.Date;
import java.util.zip.DataFormatException;

import jakarta.servlet.http.HttpServletRequest;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.batch.BatchJobConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.model.RequestParamInfo;

/**
 * バッチジョブ実行用Reflexリクエスト
 */
public class BatchJobReflexRequest extends ReflexRequest {

	/** サービス名 */
	private String serviceName;
	/** 認証情報 */
	private ReflexAuthentication auth;
	/** リクエスト情報 */
	private RequestInfo requestInfo;
	/** コネクション情報 */
	private ConnectionInfo connectionInfo;
	/** リクエストパラメータ情報 */
	private RequestParam param;

	/** アクセス開始時間 */
	private long startTime;

	/**
	 * コンストラクタ.
	 * @param httpReq リクエスト
	 */
	public BatchJobReflexRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);
		// アクセス開始時間
		this.startTime = new Date().getTime();
	}

	/**
	 * サービス名を設定
	 * @param serviceName サービス名
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * 認証情報を設定.
	 * 合わせてリクエスト情報を設定する。
	 * @param auth 認証情報
	 */
	public void setAuth(ReflexAuthentication auth) {
		this.auth = auth;
		// 合わせてリクエスト情報を設定する。
		this.requestInfo = new RequestInfoImpl(serviceName,
				getRemoteAddr(),	// ip
				auth.getUid(), auth.getAccount(),
				BatchJobConst.METHOD, getRequestURLWithQueryString());
	}

	/**
	 * コネクション情報を設定
	 * @param connectionInfo コネクション情報
	 */
	public void setConnectionInfo(ConnectionInfo connectionInfo) {
		this.connectionInfo = connectionInfo;
	}

	@Override
	public String getServiceName() {
		return serviceName;
	}

	@Override
	public FeedBase getFeed() throws IOException, ClassNotFoundException, DataFormatException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public FeedBase getFeed(String targetServiceName) throws IOException, ClassNotFoundException, DataFormatException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public byte[] getPayload() throws IOException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public ReflexAuthentication getAuth() {
		return auth;
	}

	@Override
	public RequestParam getRequestType() {
		if (param == null) {
			param = new RequestParamInfo(this);
			// 例外があれば最初だけスローする。
			((RequestParamInfo)param).throwExceptionIfInvalidParameter();
		}
		return param;
	}

	@Override
	public String getPathInfoQuery() {
		String pathInfo = getPathInfo();
		String queryString = getQueryString();
		StringBuilder sb = new StringBuilder();
		sb.append(pathInfo);
		if (queryString != null) {
			sb.append("?");
			sb.append(queryString);
		}
		return sb.toString();
	}

	@Override
	public RequestInfo getRequestInfo() {
		return requestInfo;
	}

	@Override
	public ConnectionInfo getConnectionInfo() {
		return connectionInfo;
	}

	@Override
	public int getResponseFormat() {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	@Override
	public void close() {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public long getElapsedTime() {
		long now = new Date().getTime();
		return now - startTime;
	}

	@Override
	public String getLastForwardedAddr() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

}
