package jp.reflexworks.taggingservice.stripe;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * Stripe 定数クラス.
 */
public interface ReflexStripeConst {

	/** 設定 : 成功時のリダイレクトURL */
	public static final String STRIPE_CHECKOUT_SUCCESSURL = "stripe.checkout.successurl";
	/** 設定 : 失敗時のリダイレクトURL */
	public static final String STRIPE_CHECKOUT_CANCELURL = "stripe.checkout.cancelurl";
	/** 設定 : シークレットキーのSecret Manager格納キー */
	public static final String STRIPE_SECRETKEY_SECRETKEY = "stripe.secretkey.secretkey";
	/** 設定 : 価格IDのSecret Manager格納キー */
	public static final String STRIPE_SECRETKEY_PRICEIDPRO = "stripe.secretkey.priceidpro";
	/** 設定 : WebhookエンドポイントシークレットのSecret Manager格納キー */
	public static final String STRIPE_SECRETKEY_WEBHOOKSECRET = "stripe.secretkey.webhooksecret";
	/** 設定 : カスタマーポータル画面からの戻りURL */
	public static final String STRIPE_BILLINGPORTAL_RETURNURL = "stripe.billingportal.returnurl";
	/** 設定 : エントリー更新エラー時の総リトライ回数 */
	public static final String STRIPE_UPDATEENTRY_RETRY_COUNT = "stripe.updateentry.retry.count";
	/** 設定 : エントリー更新エラーリトライ時の待ち時間(ミリ秒) */
	public static final String STRIPE_UPDATEENTRY_RETRY_WAITMILLIS = "stripe.updateentry.retry.waitmillis";
	/** 設定 : Stripe処理のアクセスログを出力するかどうか */
	public static final String STRIPE_ENABLE_ACCESSLOG = "stripe.enable.accesslog";
	/** 設定 : 商品購入時に数量指定が必要な場合に設定 */
	public static final String STRIPE_ITEM_QUANTITY = "stripe.item.quantity";
	/** 設定 : 商品購入時の表示メッセージ */
	public static final String STRIPE_CHECKOUT_MESSAGE = "stripe.checkout.message";

	/** プロパティデフォルト値 : エラー時の総リトライ回数 */
	public static final int STRIPE_UPDATEENTRY_RETRY_COUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : エラーリトライ時の待ち時間(ミリ秒) */
	public static final int STRIPE_UPDATEENTRY_RETRY_WAITMILLIS_DEFAULT = 700;
	/** プロパティデフォルト値 : 商品購入時の表示メッセージ */
	public static final String STRIPE_CHECKOUT_MESSAGE_DEFAULT = "サービス @ をPro環境にアップグレードします";

	/** メモリ上のstaticオブジェクト格納キー : Stripe用設定値 */
	public static final String STATIC_NAME_STRIPE = "_stripe";
	
	/** URI : stripe */
	public static final String URI_STRIPE = "/stripe";
	/** URI : /stripe/subscription */
	public static final String URI_STRIPE_SUBSCRIPTION = URI_STRIPE + "/subscription";
	
	/** URN : stripe urn:vte.cx:stripe: */
	public static final String URN_STRIPE_PREFIX = Constants.URN_PREFIX + "stripe:";
	/** URN : 顧客ID urn:vte.cx:stripe:cus:{顧客ID} */
	public static final String URN_STRIPE_CUS_PREFIX = URN_STRIPE_PREFIX + "cus:";
	/** URN : サブスクリプションID urn:vte.cx:stripe:sub:{サブスクリプションID} */
	public static final String URN_STRIPE_SUB_PREFIX = URN_STRIPE_PREFIX + "sub:";
	/** URN : サブスクリプションキャンセル urn:vte.cx:stripe:cancel:{サブスクリプション期間終了日時} */
	public static final String URN_STRIPE_CANCEL_PREFIX = URN_STRIPE_PREFIX + "cancel:";
	/** URN : サブスクリプション作成 urn:vte.cx:stripe:created:{サブスクリプション作成日時} */
	public static final String URN_STRIPE_CREATED_PREFIX = URN_STRIPE_PREFIX + "created:";
	/** URN : サブスクリプション削除 urn:vte.cx:stripe:deleted:{サブスクリプション削除日時} */
	public static final String URN_STRIPE_DELETED_PREFIX = URN_STRIPE_PREFIX + "deleted:";
	/** URN : サブスクリプション支払い失敗 urn:vte.cx:stripe:payment_failed:{サブスクリプション支払い失敗日時} */
	public static final String URN_STRIPE_PAYMENTFAILED_PREFIX = URN_STRIPE_PREFIX + "payment_failed:";
	/** URN : サブスクリプションキャンセル(支払い失敗による) urn:vte.cx:stripe:canceled:{サブスクリプションキャンセル日時} */
	public static final String URN_STRIPE_CANCELED_PREFIX = URN_STRIPE_PREFIX + "canceled:";
	/** URN文字列長 : stripe urn:vte.cx:stripe: */
	public static final int URN_STRIPE_PREFIX_LEN = URN_STRIPE_PREFIX.length();
	/** URN文字列長 : 顧客ID urn:vte.cx:stripe:cus:{顧客ID} */
	public static final int URN_STRIPE_CUS_PREFIX_LEN = URN_STRIPE_CUS_PREFIX.length();
	/** URN文字列長 : サブスクリプションID urn:vte.cx:stripe:sub:{サブスクリプションID} */
	public static final int URN_STRIPE_SUB_PREFIX_LEN = URN_STRIPE_SUB_PREFIX.length();
	/** URN文字列長 : サブスクリプションキャンセル urn:vte.cx:stripe:cancel:{サブスクリプション期間終了エポック秒} */
	public static final int URN_STRIPE_CANCEL_PREFIX_LEN = URN_STRIPE_CANCEL_PREFIX.length();
	
	/** Stripe Webhook header : 署名 */
	public static final String HEADER_STRIPE_SIGNATURE = "Stripe-Signature";

	/** Stripe metadata : サービス名 */
	public static final String METADATA_SERVICE = "service";
	/** Stripe metadata : UID */
	public static final String METADATA_UID = "uid";
	
	/** Stripe サブスクリプションのステータス : active */
	public static final String STRIPE_STATUS_ACTIVE = "active";
	/** Stripe サブスクリプションのステータス : trialing */
	public static final String STRIPE_STATUS_TRIALING = "trialing";
	/** Stripe サブスクリプションのステータス : deleted */
	public static final String STRIPE_STATUS_DELETED = "deleted";
	/** Stripe サブスクリプションのステータス : canceled */
	public static final String STRIPE_STATUS_CANCELED = "canceled";
	
	/** Stripe エラーコード : resource_missing */
	public static final String STRIPE_CODE_RESOURCE_MISSING = "resource_missing";
	
}
