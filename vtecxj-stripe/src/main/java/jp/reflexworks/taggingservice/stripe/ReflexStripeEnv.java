package jp.reflexworks.taggingservice.stripe;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.Stripe;

import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SecretManager;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Stripeに使用するstatic情報保持クラス
 */
public class ReflexStripeEnv {

	/** シークレットキー */
	private String secretKey;
	/** pro環境サブスクリプションの価格ID */
	private String priceIdPro;
	/** Webhookエンドポイントシークレットキー */
	private String webhookSecretKey;
	/** 成功時のリダイレクトURL */
	private String successUrl;
	/** 失敗時のリダイレクトURL */
	private String cancelUrl;
	/** カスタマーポータル画面からの戻りURL */
	private String portalReturnUrl;

	/** ロガー. */
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理
	 */
	void init() {
		if (logger.isTraceEnabled()) {
			logger.info("[init] start.");
		}
		setup();
	}

	/**
	 * クローズ処理
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * シークレットキーを取得
	 * @return シークレットキー
	 */
	/*
	public String getSecretKey() {
		return secretKey;
	}
	*/

	/**
	 * pro環境サブスクリプションの価格IDを取得
	 * @return pro環境サブスクリプションの価格ID
	 */
	public String getPriceIdPro() {
		return priceIdPro;
	}

	/**
	 * pro環境サブスクリプションの価格IDを取得
	 * @return pro環境サブスクリプションの価格ID
	 */
	public String getWebhookSecretKey() {
		return webhookSecretKey;
	}

	/**
	 * 成功時のリダイレクトURLを取得
	 * @return 成功時のリダイレクトURL
	 */
	public String getSuccessUrl() {
		return successUrl;
	}

	/**
	 * 失敗時のリダイレクトURLを取得
	 * @return 失敗時のリダイレクトURL
	 */
	public String getCancelUrl() {
		return cancelUrl;
	}

	/**
	 * カスタマーポータル画面からの戻りURLを取得
	 * @return カスタマーポータル画面からの戻りURL
	 */
	public String getPotalReturnUrl() {
		return portalReturnUrl;
	}

	/**
	 * SecretManagerの読み直し
	 */
	public void reloadSecret() {
		setup();
	}
	
	/**
	 * 各設定の読み込み、保持
	 */
	private void setup() {
		this.successUrl = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_CHECKOUT_SUCCESSURL, null);
		this.cancelUrl = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_CHECKOUT_CANCELURL, null);
		this.portalReturnUrl = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_BILLINGPORTAL_RETURNURL, null);
		
		String secretKeyKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_SECRETKEY, null);
		String priceIdProKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_PRICEIDPRO, null);
		String WebhookSecretKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_WEBHOOKSECRET, null);
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		try {
			this.secretKey = secretManager.getSecretKey(secretKeyKey, null);
			this.priceIdPro = secretManager.getSecretKey(priceIdProKey, null);
			this.webhookSecretKey = secretManager.getSecretKey(WebhookSecretKey, null);
			Stripe.apiKey = secretKey;

			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[setup] secretKey=");
				sb.append(this.secretKey);
				sb.append(", priceIdPro=");
				sb.append(this.priceIdPro);
				sb.append(", webhookSecretKey=");
				sb.append(this.webhookSecretKey);
				sb.append(", successUrl=");
				sb.append(this.successUrl);
				sb.append(", cancelUrl=");
				sb.append(this.cancelUrl);
				sb.append(", portalReturnUrl=");
				sb.append(this.portalReturnUrl);
				logger.info(sb.toString());
			}

		} catch (TaggingException | IOException | RuntimeException e) {
			logger.warn("[setup] Error occured. " + e.getMessage(), e);
		}
	}
	
	/**
	 * Stripeが有効かどうか
	 * @return Stripeを使用できる場合true
	 */
	public boolean isEnabledStripe() {
		if (!StringUtils.isBlank(this.secretKey) &&
				!StringUtils.isBlank(this.priceIdPro) &&
				!StringUtils.isBlank(this.webhookSecretKey) &&
				!StringUtils.isBlank(this.successUrl) &&
				!StringUtils.isBlank(this.cancelUrl)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Stripe処理のアクセスログを出力するかどうかを取得.
	 * @return Stripe処理のアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return ReflexStripeUtil.isEnableAccessLog();
	}
}
