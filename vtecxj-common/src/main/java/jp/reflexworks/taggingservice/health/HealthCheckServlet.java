package jp.reflexworks.taggingservice.health;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServlet;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;

/**
 * ヘルスチェック サーブレット.
 */
public class HealthCheckServlet extends ReflexServlet {
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	@Override
	public void init() throws ServletException {
		super.init();
	}

	/**
	 * GETメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (logger.isTraceEnabled()) {
			logger.debug("[doGet] start.");
		}
		// 初期起動が正常に行われていればステータス200、その他は503を返す。
		if (ReflexEnvUtil.isRunning()) {
			doResponse(HttpStatus.SC_OK, httpResp);
		} else {
			logger.warn("[doGet] The server is not running.");
			doResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, httpResp);
		}
	}

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		doResponse(HttpStatus.SC_METHOD_NOT_ALLOWED, httpResp);
	}

	/**
	 * PUTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		doResponse(HttpStatus.SC_METHOD_NOT_ALLOWED, httpResp);
	}

	/**
	 * DELETEメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doDelete(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		doResponse(HttpStatus.SC_METHOD_NOT_ALLOWED, httpResp);
	}

	/**
	 * HEADメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doHead(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		doGet(httpReq, httpResp);
	}

	/**
	 * レスポンスにステータスを設定する.
	 * @param status
	 * @param httpResp レスポンス
	 */
	protected void doResponse(int status, HttpServletResponse httpResp)
	throws IOException {
		httpResp.setStatus(status);
	}

}
