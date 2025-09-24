package jp.reflexworks.taggingservice.subscription;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;

/**
 * Deployment または Pod の再起動呼び出しクラス
 */
public class RestartBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 再起動処理.
	 * 設定エラーの場合もあるため、以下の順で処理を行う。
	 * (コネクション枯渇を防ぐため、Redisの参照は最低限とする。)
	 *   1. リクエストの内容チェック
	 *   2. 認証
	 *   3. 再起動処理
	 * @param req
	 * @throws IOException
	 * @throws TaggingException
	 */
	public void subscription(ReflexRequest req) throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				req.getRequestInfo(), req.getConnectionInfo());

		// リクエスト内容チェック
		String json = ReflexServletUtil.getBody(req);
		String data = SubscriptionUtil.getMessage(json);
		String podName = SubscriptionUtil.getPodnameIfOom(data);
		if (podName != null) {
			// OOM
			if (logger.isInfoEnabled()) {
				String tmpMsg = data.replace(SubscriptionConst.MESSAGE_OOM_PREFIX, "○○○");
				StringBuilder sb = new StringBuilder();
				sb.append("[subscription] OOM message. podName=");
				sb.append(podName);
				sb.append(", msg=");
				sb.append(tmpMsg);
				logger.info(sb.toString());
			}

		} else {
			if (logger.isInfoEnabled()) {
				String tmpMsg = data.replace(SubscriptionConst.MESSAGE_OOM_PREFIX, "○○○");
				logger.info("[subscription] The message is not OOM. " + tmpMsg);
			}
			return;
		}

		// 認証 (JWT)
		boolean isAuth = SubscriptionUtil.authenticate(req, systemContext);
		if (!isAuth) {
			return;
		}

		// 再起動処理
		callRestart(podName, systemContext);

	}

	/**
	 * 再起動処理呼び出し.
	 * @param podName Pod名
	 * @param systemContext SystemContext
	 */
	private void callRestart(String podName, SystemContext systemContext)
	throws IOException, TaggingException {
		if (logger.isInfoEnabled()) {
			logger.info("[callRestart] podName=" + podName);
		}

		// 排他チェック
		if (!SubscriptionUtil.isRebooting(podName, systemContext)) {
			if (logger.isInfoEnabled()) {
				logger.info("[callRestart] restart. " + podName);
			}

			try {
				SubscriptionUtil.setRebooting(podName, systemContext);
				SubscriptionCommandUtil.restart(podName);
			} catch (IOException | RuntimeException | Error e) {
				// 再起動処理に失敗した場合、再起動フラグを削除する。
				if (logger.isInfoEnabled()) {
					logger.info("[callRestart] restart is failed.");
				}
				SubscriptionUtil.deleteRebooting(podName, systemContext);
				throw e;
			}

		} else {
			if (logger.isInfoEnabled()) {
				logger.info("[callRestart] The pod is rebooting. " + podName);
			}
		}
	}

}
