package jp.reflexworks.taggingservice.stripe;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import jp.reflexworks.servlet.ReflexServlet;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.sourceforge.reflex.util.FileUtil;

/**
 * StripeからのWebhookを受信するサーブレット
 */
public class ReflexStripeServlet extends ReflexServlet {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {

		try {
			RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
			// リクエスト・レスポンスをラップ
			ReflexRequest req = reqRespManager.createReflexRequest(httpReq);
			RequestInfo requestInfo = req.getRequestInfo();
			ConnectionInfo connectionInfo = req.getConnectionInfo();

			if (logger.isTraceEnabled()) {
				logger.info("[doPost] Request URL: " + req.getRequestURL().toString());
			}

			String payloadStr = FileUtil.readString(req.getInputStream());
			String sigHeader = req.getHeader(ReflexStripeConst.HEADER_STRIPE_SIGNATURE);
			String webhookSecret = ReflexStripeUtil.getWebhookSecretKey(requestInfo, connectionInfo);

			Event event = Webhook.constructEvent(payloadStr, sigHeader, webhookSecret);

			String eventType = event.getType();

			if (isEnableAccessLog()) {
				logger.info("[doPost] event type: " + eventType);
			}

			if ("checkout.session.completed".equals(eventType)) {
				if (logger.isInfoEnabled()) {
					logger.info("[doPost] checkout.session.completed.");
				}
				// サブスクリプション支払い完了
				ReflexStripeManager stripeManager = new ReflexStripeManager();
				stripeManager.completePayment(event, requestInfo, connectionInfo);
				
			} else if ("customer.subscription.deleted".equals(eventType)) {
				if (logger.isInfoEnabled()) {
					logger.info("[doPost] customer.subscription.deleted.");
				}
				// サブスクリプション終了
				ReflexStripeManager stripeManager = new ReflexStripeManager();
				stripeManager.endPayment(event, requestInfo, connectionInfo);
				
			} else if ("invoice.payment_failed".equals(eventType)) {
				if (logger.isInfoEnabled()) {
					logger.info("[doPost] invoice.payment_failed.");
				}
				// 支払い失敗
				ReflexStripeManager stripeManager = new ReflexStripeManager();
				stripeManager.failedPayment(event, requestInfo, connectionInfo);
				
			} else if ("invoice.paid".equals(eventType)) {
				if (logger.isInfoEnabled()) {
					logger.info("[doPost] invoice.paid.");
				}
				// 支払い成功
				ReflexStripeManager stripeManager = new ReflexStripeManager();
				stripeManager.paid(event, requestInfo, connectionInfo);
			}

		} catch (SignatureVerificationException e) {
			// Invalid signature
			StringBuilder sb = new StringBuilder();
			sb.append("[doPost] Error occured. ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			logger.warn(sb.toString(), e);
			httpResp.setStatus(SC_BAD_REQUEST);

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String errmsg = e.getMessage();
			if ((e instanceof NullPointerException && 
					errmsg != null && errmsg.startsWith("Cannot invoke ")) ||
					e instanceof SignatureVerificationException) {
				// 以下の例外はスタックトレースを出力しない。
				//  * リクエスト内容が原因のStripeのNullPointerExceptionは
				//  * Webhookシークレットのエラー
				StringBuilder sb = new StringBuilder();
				sb.append("[doPost] Webhook.constructEvent failed. ");
				sb.append(errmsg);
				logger.warn(sb.toString());
			} else {
				// エラーコードは返さない。
				StringBuilder sb = new StringBuilder();
				sb.append("[doPost] Error occured. ");
				sb.append(e.getClass().getName());
				sb.append(": ");
				sb.append(errmsg);
				logger.warn(sb.toString(), e);
			}
		}
	}
	
	/**
	 * Stripe処理のアクセスログを出力するかどうかを取得.
	 * @return Stripe処理のアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return ReflexStripeUtil.isEnableAccessLog();
	}

}
