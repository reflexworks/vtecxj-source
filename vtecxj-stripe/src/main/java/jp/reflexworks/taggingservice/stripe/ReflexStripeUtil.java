package jp.reflexworks.taggingservice.stripe;

import java.io.IOException;
import java.util.Date;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.SecretBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

public class ReflexStripeUtil {
	
	/**
	 * Stripeが有効かどうか
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Stripeを使用できる場合true
	 */
	static boolean isEnabledStripe(RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String secretKey = getSecretKey(requestInfo, connectionInfo);
		if (StringUtils.isBlank(secretKey) ||
				StringUtils.isBlank(getPriceIdPro(requestInfo, connectionInfo)) ||
				StringUtils.isBlank(getWebhookSecretKey(requestInfo, connectionInfo)) ||
				StringUtils.isBlank(getSuccessUrl()) ||
				StringUtils.isBlank(getCancelUrl())) {
			return false;
		}
		// secretKeyの更新チェック
		ReflexStripeEnv stripeEnv = getStripeEnv();
		if (stripeEnv.changeSecretKey(secretKey)) {
			stripeEnv.setup();
		}
		
		return true;
	}

	/**
	 * シークレットキーを取得
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return シークレットキー
	 */
	static String getSecretKey(RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String secretKeyKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_SECRETKEY, null);
		return getSecretValue(secretKeyKey, requestInfo, connectionInfo);
	}

	/**
	 * pro環境サブスクリプションの価格IDを取得
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return pro環境サブスクリプションの価格ID
	 */
	static String getPriceIdPro(RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String priceIdProKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_PRICEIDPRO, null);
		return getSecretValue(priceIdProKey, requestInfo, connectionInfo);
	}

	/**
	 * pro環境サブスクリプションの価格IDを取得
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return pro環境サブスクリプションの価格ID
	 */
	static String getWebhookSecretKey(RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String WebhookSecretKey = TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_SECRETKEY_WEBHOOKSECRET, null);
		return getSecretValue(WebhookSecretKey, requestInfo, connectionInfo);
	}
	
	/**
	 * シークレット読み込み.
	 * Redisキャッシュも使用する。同じスレッドで一度読み込んだものは使い回す。
	 * @param secretKey シークレットキー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return シークレットの値
	 */
	private static String getSecretValue(String secretKey, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		SecretBlogic secretBlogic = new SecretBlogic();
		return secretBlogic.getSecretKey(secretKey, null, requestInfo, connectionInfo);
	}

	/**
	 * 成功時のリダイレクトURLを取得
	 * @return 成功時のリダイレクトURL
	 */
	static String getSuccessUrl() {
		return TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_CHECKOUT_SUCCESSURL, null);
	}

	/**
	 * 失敗時のリダイレクトURLを取得
	 * @return 失敗時のリダイレクトURL
	 */
	static String getCancelUrl() {
		return TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_CHECKOUT_CANCELURL, null);
	}

	/**
	 * カスタマーポータル画面からの戻りURLを取得
	 * @return カスタマーポータル画面からの戻りURL
	 */
	static String getPotalReturnUrl() {
		return TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_BILLINGPORTAL_RETURNURL, null);
	}
	
	/**
	 * 商品購入時の数量を取得
	 * @return 商品購入時の数量
	 */
	static long getQuantity() {
		return TaggingEnvUtil.getSystemPropLong(ReflexStripeConst.STRIPE_ITEM_QUANTITY, 
				ReflexStripeConst.STRIPE_ITEM_QUANTITY_DEFAULT);
	}

	/**
	 * 商品購入時の表示メッセージを取得
	 * @return 商品購入時の表示メッセージ
	 */
	static String getCheckoutMessage() {
		return TaggingEnvUtil.getSystemProp(ReflexStripeConst.STRIPE_CHECKOUT_MESSAGE, 
				ReflexStripeConst.STRIPE_CHECKOUT_MESSAGE_DEFAULT);
	}

	/**
	 * 設定エラー等であればTaggingExceptionに変換する.
	 * @param se StripeException
	 * @return TaggingException または null
	 */
	static TaggingException convertTaggingException(StripeException se) {
		if (se instanceof InvalidRequestException) {
			String errmsg = se.getMessage();
			if (errmsg != null && errmsg.startsWith("No such customer: ")) {
				// /_user/{UID}/stripe エントリーの顧客番号がStripeに存在しない場合
				return new NoExistingEntryException(se.getMessage());
			}
		}
		// TODO
		return null;
	}
	
	/**
	 * ログインユーザのStripe顧客情報キーを取得
	 *   /_user/{UID}/stripe
	 * @param uid UID
	 * @return ログインユーザのStripe顧客情報キー
	 */
	static String getUserStripeUri(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_USER);
		sb.append("/");
		sb.append(uid);
		sb.append(ReflexStripeConst.URI_STRIPE);
		return sb.toString();
	}
	
	/**
	 * サブスクリプションとサービスの対応エントリーのキーを取得
	 * @param subscriptionId サブスクリプションID
	 * @return サブスクリプションエントリーのキー
	 */
	static String getStripeSubscriptionUri(String subscriptionId) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URI_STRIPE_SUBSCRIPTION);
		sb.append("/");
		sb.append(subscriptionId);
		return sb.toString();
	}
	
	/**
	 * Stripe顧客情報エントリーから、顧客IDを取得
	 *   contributor.uri の urn:vte.cx:stripe:cus:{顧客ID}
	 * @param userStripeEntry Stripe顧客情報エントリー
	 * @return 顧客ID
	 */
	static String getCustomerId(EntryBase userStripeEntry) {
		if (userStripeEntry != null && userStripeEntry.contributor != null) {
			for (Contributor contributor : userStripeEntry.contributor) {
				if (!StringUtils.isBlank(contributor.uri) && 
						contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_CUS_PREFIX)) {
					return contributor.uri.substring(ReflexStripeConst.URN_STRIPE_CUS_PREFIX_LEN);
				}
			}
		}
		return null;
	}
	
	/**
	 * ログインユーザのメールアドレスを取得.
	 * @param reflexContext ReflexContext
	 * @return メールアドレス
	 */
	static String getEmail(ReflexContext reflexContext) 
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		// メールアドレスを取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		EntryBase userEntry = userManager.getUserTopEntryByUid(auth.getUid(), reflexContext);
		String email = userManager.getEmail(userEntry);
		if (StringUtils.isBlank(email)) {
			email = auth.getAccount();
		}
		return email;
	}
	
	/**
	 * StripeのEventからリクエストデータをStripeObject形式にして取り出す
	 * @param event Event
	 * @return StripeObject
	 */
	static StripeObject getStripeObject(Event event) {
        return event.getDataObjectDeserializer().getObject().orElse(null);
	}
	
	/**
	 * urn:vte.cx:stripe:sub:{サブスクリプションID} を返す
	 * @param subscriptionId サブスクリプションID
	 * @return urn:vte.cx:stripe:sub:{サブスクリプションID}
	 */
	static String getUrnSubscription(String subscriptionId) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URN_STRIPE_SUB_PREFIX);
		sb.append(subscriptionId);
		return sb.toString();
	}
	
	/**
	 * urn:vte.cx:stripe:cancel:{cancel_at} を返す
	 * @param cancelAt キャンセル日時(エポック秒)
	 * @return urn:vte.cx:stripe:cancel:{キャンセル日時}
	 */
	static String getUrnCancel(Long cancelAt) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URN_STRIPE_CANCEL_PREFIX);
		sb.append(epochToDatetime(cancelAt));
		return sb.toString();
	}
	
	/**
	 * urn:vte.cx:stripe:created:{created} を返す
	 * @param created 作成日時(エポック秒)
	 * @return urn:vte.cx:stripe:created:{作成日時}
	 */
	static String getUrnCreated(Long created) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URN_STRIPE_CREATED_PREFIX);
		sb.append(epochToDatetime(created));
		return sb.toString();
	}
	
	/**
	 * urn:vte.cx:stripe:deleted:{created} を返す
	 * @param created 作成日時(エポック秒)
	 * @return urn:vte.cx:stripe:deleted:{作成日時}
	 */
	static String getUrnDeleted(Long created) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URN_STRIPE_DELETED_PREFIX);
		sb.append(epochToDatetime(created));
		return sb.toString();
	}
	
	/**
	 * urn:vte.cx:stripe:deleted:{created} を返す
	 * @param created 作成日時(エポック秒)
	 * @return urn:vte.cx:stripe:deleted:{作成日時}
	 */
	static String getUrnPaymentFailed(Long created) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX);
		sb.append(epochToDatetime(created));
		return sb.toString();
	}
	
	/**
	 * urn:vte.cx:stripe:canceled:{キャンセル日時} を返す
	 * @param cancelAt キャンセル日時(エポック秒)
	 * @return urn:vte.cx:stripe:canceled:{キャンセル日時}
	 */
	static String getUrnCanceled(Long cancelAt) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexStripeConst.URN_STRIPE_CANCELED_PREFIX);
		sb.append(epochToDatetime(cancelAt));
		return sb.toString();
	}

	/**
	 * エポック秒を「YYYY-MM-DD'T'hh:mm:ssZ」形式に変換する.
	 * @param epochSec エポック秒
	 * @return 「YYYY-MM-DD'T'hh:mm:ssZ」形式文字列
	 */
	static String epochToDatetime(Long epochSec) {
		if (epochSec == null) {
			return null;
		}
		Date date = new Date(epochSec * 1000);
		return DateUtil.getDateTime(date);
	}
	
	/**
	 * urn:vte.cx:stripe:sub:{サブスクリプションID} からサブスクリプションIDを返す
	 * @param subscriptionUrn サブスクリプションurn
	 * @return サブスクリプションID
	 */
	static String getSubscriptionIdByUrn(String subscriptionUrn) {
		if (subscriptionUrn != null && 
				subscriptionUrn.startsWith(ReflexStripeConst.URN_STRIPE_SUB_PREFIX)) {
			return subscriptionUrn.substring(ReflexStripeConst.URN_STRIPE_SUB_PREFIX_LEN);
		}
		return null;
	}

	/**
	 * エントリー更新失敗時リトライ総数を取得.
	 * @return エントリー更新失敗時リトライ総数
	 */
	static int getStripeUpdateentryRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(ReflexStripeConst.STRIPE_UPDATEENTRY_RETRY_COUNT,
				ReflexStripeConst.STRIPE_UPDATEENTRY_RETRY_COUNT_DEFAULT);
	}

	/**
	 * エントリー更新失敗時リトライ総数を取得.
	 * @return エントリー更新失敗時リトライ総数
	 */
	static int getStripeUpdateentryRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(ReflexStripeConst.STRIPE_UPDATEENTRY_RETRY_WAITMILLIS,
				ReflexStripeConst.STRIPE_UPDATEENTRY_RETRY_WAITMILLIS_DEFAULT);
	}
	
	/**
	 * Stripe用static情報保持オブジェクトを取得.
	 * @return Stripe用static情報保持オブジェクト
	 */
	static ReflexStripeEnv getStripeEnv() {
		return (ReflexStripeEnv)ReflexStatic.getStatic(ReflexStripeConst.STATIC_NAME_STRIPE);
	}

	/**
	 * Stripe処理のアクセスログを出力するかどうかを取得.
	 * @return Stripe処理のアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				ReflexStripeConst.STRIPE_ENABLE_ACCESSLOG, false);
	}

}
