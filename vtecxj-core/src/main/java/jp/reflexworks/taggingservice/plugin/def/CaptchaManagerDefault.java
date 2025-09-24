package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * キャプチャ管理クラス.
 * reCAPTCHAによるキャプチャ認証を行います。
 */
public class CaptchaManagerDefault implements CaptchaManager {

	/** reCAPTCHA判定URL */
	private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
	/** reCAPTCHA判定パラメータ : secret */
	private static final String VERIFY_PARAM_SECRET = "?secret=";
	/** reCAPTCHA判定パラメータ : response */
	private static final String VERIFY_PARAM_RESPONSE = "&response=";

	/** 設定 : reCAPTCHAのsecretkey (v3) */
	private static final String PROP_RECAPTCHA_SECRETKEY = SettingConst.RECAPTCHA_SECRETKEY;
	/** 設定 : reCAPTCHAのsecretkey (v2) */
	private static final String PROP_RECAPTCHA_SECRETKEY_V2 = SettingConst.RECAPTCHA_SECRETKEY_V2;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
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
	 * キャプチャ判定.
	 * @param req リクエスト
	 * @param action キャプチャチェックアクション
	 * @throws AuthenticationException キャプチャ認証エラー
	 */
	public void verify(ReflexRequest req, String action)
	throws IOException, TaggingException {
		// (2025.3.17)旧reCAPTCHA判定を廃止
	}

	/**
	 * キャプチャ不要なWSSE認証回数を取得.
	 * @param serviceName サービス名
	 * @return キャプチャ不要なWSSE認証回数
	 */
	public int getWsseWithoutCaptchaCount(String serviceName) {
		String secretkey = getReCaptchaSecretkey(serviceName);
		if (StringUtils.isBlank(secretkey)) {
			secretkey = getReCaptchaSecretkeyV2(serviceName);
			if (StringUtils.isBlank(secretkey)) {
				return -1;	// キャプチャチェックを行わない
			}
		}
		try {
			return TaggingEnvUtil.getPropInt(serviceName,
					SettingConst.WSSE_WITHOUT_CAPTCHA,
					TaggingEnvUtil.getSystemPropInt(
							SettingConst.WSSE_WITHOUT_CAPTCHA,
							TaggingEnvConst.WSSE_WITHOUT_CAPTCHA_DEFAULT));
		} catch (InvalidServiceSettingException e) {
			return TaggingEnvConst.WSSE_WITHOUT_CAPTCHA_DEFAULT;
		}
	}

	/**
	 * reCAPTCHAのシークレットキーを取得
	 * @param serviceName サービス名
	 * @return reCAPTCHAのシークレットキー(v3)
	 */
	private String getReCaptchaSecretkey(String serviceName) {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getReCaptchaSecretkey] serviceName=");
			sb.append(serviceName);
			sb.append(", service secretkey=");
			sb.append(TaggingEnvUtil.getProp(serviceName, PROP_RECAPTCHA_SECRETKEY, null));
			logger.debug(sb.toString());
		}
		// (2025.3.12)デフォルトはreCAPTCHAチェック無しに変更
		return TaggingEnvUtil.getProp(serviceName, PROP_RECAPTCHA_SECRETKEY, null);
	}

	/**
	 * reCAPTCHAのシークレットキーを取得
	 * @param serviceName サービス名
	 * @return reCAPTCHAのシークレットキー(v2)
	 */
	private String getReCaptchaSecretkeyV2(String serviceName) {
		// (2025.3.12)デフォルトはreCAPTCHAチェック無しに変更
		return TaggingEnvUtil.getProp(serviceName, PROP_RECAPTCHA_SECRETKEY_V2, null);  
	}

}
