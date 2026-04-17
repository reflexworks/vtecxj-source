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
	 * シークレットキーが変更されたかどうかチェック
	 * @return シークレットキーが変更された場合true
	 */
	boolean changeSecretKey(String tmpSecretKey) {
		if (StringUtils.isBlank(secretKey)) {
			return !StringUtils.isBlank(tmpSecretKey);
		} else {
			return !secretKey.equals(tmpSecretKey);
		}
	}
	
	/**
	 * 各設定の読み込み、保持
	 */
	void setup() {
		String secretKeyKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_SECRETKEY, null);
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		try {
			this.secretKey = secretManager.getSecretKey(secretKeyKey, null);
			Stripe.apiKey = secretKey;

			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[setup] secretKey=");
				sb.append(this.secretKey);
				logger.info(sb.toString());
			}

		} catch (TaggingException | IOException | RuntimeException e) {
			logger.warn("[setup] Error occured. " + e.getMessage(), e);
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
