package jp.reflexworks.taggingservice.stripe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSearchResult;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionSearchParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.auth.AuthenticationConst;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.OptimisticLockingException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.InUseSecretManager;
import jp.reflexworks.taggingservice.plugin.PaymentManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Stripeによる決済管理クラス.
 */
public class ReflexStripeManager implements PaymentManager, InUseSecretManager {

	/** ロガー */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * init
	 */
	@Override
	public void init() {
		// Stripe用static情報をメモリに格納
		ReflexStripeEnv stripeEnv = new ReflexStripeEnv();
		try {
			ReflexStatic.setStatic(ReflexStripeConst.STATIC_NAME_STRIPE, stripeEnv);
			// Stripe用static情報新規生成
			stripeEnv.init();

		} catch (StaticDuplicatedException e) {
			// Do nothing. Stripe用static情報は他の処理ですでに生成済み
		}
	}

	/**
	 * close
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * SecretManagerの再読み込み.
	 */
	@Override
	public void reloadSecret() {
		try {
			ReflexStripeEnv stripeEnv = ReflexStripeUtil.getStripeEnv();
			stripeEnv.reloadSecret();
		} catch (Throwable e) {
			logger.warn("[reloadSecret] Error occured. " + e.getMessage(), e);
		}
	}

	/**
	 * 課金処理.
	 * 支払い手続きのための処理を行い。カード決済のリダイレクトURLを返却する。
	 * @param targetServiceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param reflexContext ReflexContext
	 * @param リダイレクトURL
	 */
	public String registerPayment(String targetServiceName, EntryBase serviceEntry, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);
		
		// Stripe設定がなければ課金処理を行わず処理を抜ける。
		ReflexStripeEnv stripeEnv = ReflexStripeUtil.getStripeEnv();
		if (!stripeEnv.isEnabledStripe()) {
			if (isEnableAccessLog()) {
				logger.info("[registerPayment] no settings.");
			}
			updateServiceStatus(serviceEntry, Constants.SERVICE_STATUS_PRODUCTION, systemContext);
			return null;
		}

		// ログインユーザがVirtual Technology開発者の場合処理を抜ける。 
		// /_group/$vtecxグループに所属している場合、課金対象外。
		AclBlogic aclBlogic = new AclBlogic();
		boolean isGroupVtecx = aclBlogic.isInTheGroup(auth, Constants.URI_GROUP_VTECX);
		if (isGroupVtecx) {
			if (isEnableAccessLog()) {
				logger.info("[registerPayment] vtecx group member.");
			}
			updateServiceStatus(serviceEntry, Constants.SERVICE_STATUS_PRODUCTION, systemContext);
			return null;
		}

		// ログインユーザのStripe顧客情報があるかどうかチェック
		String uid = auth.getUid();
		String userStripeUri = ReflexStripeUtil.getUserStripeUri(uid);
		EntryBase userStripeEntry = reflexContext.getEntry(userStripeUri);
		String customerId = ReflexStripeUtil.getCustomerId(userStripeEntry);
		if (StringUtils.isBlank(customerId)) {
			// Stripe顧客情報がない場合、登録する。
			customerId = postCustomer(reflexContext);
		} else {
			// Stripeに登録されているかチェック
			boolean exists = existCustomer(customerId);
			if (!exists) {
				// 顧客情報の登録し直し
				customerId = postCustomer(reflexContext);
			}
		}
		
		// サービスにすでにサブスクリプション契約情報がある場合は処理を抜ける
		boolean existSub = existSubscription(targetServiceName, serviceEntry, systemContext);
		if (existSub) {
			return null;
		}

		// Stripeにサブスクリプション決済のためのリクエストを行う。
		String url = createCheckoutSession(targetServiceName, uid, customerId);
		return url;
	}

	/**
	 * 課金処理の期間終了.
	 * サービスステータスをproductionからstagingに変更したときに呼び出される処理
	 * @param targetServiceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param reflexContext ReflexContext
	 */
	public void cancelPayment(String targetServiceName, EntryBase serviceEntry, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);
		
		// Stripe設定がなければ課金処理を行わず処理を抜ける。
		ReflexStripeEnv stripeEnv = ReflexStripeUtil.getStripeEnv();
		if (!stripeEnv.isEnabledStripe()) {
			if (isEnableAccessLog()) {
				logger.info("[cancelPayment] no settings.");
			}
			return;
		}

		// サブスクリプション課金対象外の場合、何もせず処理を抜ける。
		String subscriptionId = getSubscriptionId(serviceEntry);
		if (StringUtils.isBlank(subscriptionId)) {
			if (isEnableAccessLog()) {
				logger.info("[cancelPayment] There is no subscription id. serviceName=" + targetServiceName);
			}
			return;
		}
		
		// サブスクリプション期間終了解約処理済みの場合、何もせず処理を抜ける。
		String cancelAtStr = getCancelAt(serviceEntry);
		if (!StringUtils.isBlank(cancelAtStr)) {
			if (isEnableAccessLog()) {
				logger.info("[cancelPayment] Cancellation has already been initiated. serviceName=" + 
						targetServiceName + ", cancelAt=" + cancelAtStr);
			}
			return;
		}

		String uid = auth.getUid();
		try {
			Subscription resource = Subscription.retrieve(subscriptionId);
			SubscriptionUpdateParams params =
			  SubscriptionUpdateParams.builder()
			    .putMetadata(ReflexStripeConst.METADATA_SERVICE, targetServiceName)
			    .putMetadata(ReflexStripeConst.METADATA_UID, uid)
			    .setCancelAtPeriodEnd(true)
			    .build();
			Subscription subscription = resource.update(params);
			
			FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
			List<EntryBase> putEntries = new ArrayList<>();
			putFeed.entry = putEntries;
			// サブスクリプション期間終了手続き中情報を、サービスエントリーのcontributor.uriに設定する。
			//   urn:vte.cx:stripe:cancel:{cancel_at(エポック秒)}
			Long cancelAt = subscription.getCancelAt();
			String urnCancel = ReflexStripeUtil.getUrnCancel(cancelAt);
			Contributor contributor = new Contributor();
			contributor.uri = urnCancel;
			serviceEntry.addContributor(contributor);
			putEntries.add(serviceEntry);
			// サブスクリプションエントリー更新
			String subscriptionUri = ReflexStripeUtil.getStripeSubscriptionUri(subscriptionId);
			EntryBase subscriptionEntry = systemContext.getEntry(subscriptionUri);
			if (subscriptionEntry != null) {
				// contributor.uriに`urn:vte.cx:stripe:cancel:{cancel_at(エポック秒)の日時変換}`を追加
				subscriptionEntry.addContributor(contributor);
				putEntries.add(subscriptionEntry);
			} else {
				if (isEnableAccessLog()) {
					logger.info("[cancelPayment] subscriptionEntry does not exist. " + subscriptionUri);
				}
			}
			
			systemContext.put(putFeed);
			
		} catch (StripeException se) {
			if (isEnableAccessLog()) {
				logger.info("[cancelPayment] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * 課金処理の削除.
	 * productionサービスが削除されたときに呼び出される処理
	 * @param targetServiceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param reflexContext ReflexContext
	 */
	public void deletePayment(String targetServiceName, EntryBase serviceEntry, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {

		// Stripe設定がなければ課金処理を行わず処理を抜ける。
		ReflexStripeEnv stripeEnv = ReflexStripeUtil.getStripeEnv();
		if (!stripeEnv.isEnabledStripe()) {
			if (isEnableAccessLog()) {
				logger.info("[deletePayment] no settings.");
			}
			return;
		}

		// サブスクリプション課金対象外の場合、何もせず処理を抜ける。
		String subscriptionId = getSubscriptionId(serviceEntry);
		if (StringUtils.isBlank(subscriptionId)) {
			if (isEnableAccessLog()) {
				logger.info("[deletePayment] There is no subscription id. serviceName=" + targetServiceName);
			}
			return;
		}

		// サブスクリプション即時解約
		try {
			Subscription resource = Subscription.retrieve(subscriptionId);
			SubscriptionCancelParams params = SubscriptionCancelParams.builder().build();
			Subscription subscription = resource.cancel(params);
			
		} catch (StripeException se) {
			if (isEnableAccessLog()) {
				logger.info("[deletePayment] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * カスタマーポータル画面のリンク発行処理.
	 * @param reflexContext ReflexContext
	 * @param リダイレクトURL
	 */
	public String billingPortal(ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		
		// ログインユーザのStripe顧客情報を取得。顧客情報が登録されていない場合はエラー
		String uid = auth.getUid();
		String userStripeUri = ReflexStripeUtil.getUserStripeUri(uid);
		EntryBase userStripeEntry = reflexContext.getEntry(userStripeUri);
		if (userStripeEntry == null) {
			if (isEnableAccessLog()) {
				logger.info("[billingPortal] userStripeEntry does not exist. " + userStripeUri);
			}
			return null;
		}
		String customerId = ReflexStripeUtil.getCustomerId(userStripeEntry);
		if (StringUtils.isBlank(customerId)) {
			if (isEnableAccessLog()) {
				logger.info("[billingPortal] customer id does not registered. " + userStripeUri);
			}
			return null;
		}

		ReflexStripeEnv stripeEnv = ReflexStripeUtil.getStripeEnv();
		String returnUrl = stripeEnv.getPotalReturnUrl();
		try {
			com.stripe.param.billingportal.SessionCreateParams params =
					com.stripe.param.billingportal.SessionCreateParams.builder()
					.setCustomer(customerId)
					.setReturnUrl(returnUrl)
					.build();
	
			com.stripe.model.billingportal.Session session =
					com.stripe.model.billingportal.Session.create(params);
	
			return session.getUrl();

		} catch (StripeException se) {
			if (isEnableAccessLog()) {
				logger.info("[billingPortal] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * サービスステータス更新
	 * @param serviceEntry サービスエントリー
	 * @param serviceStatus サービスステータス
	 * @param systemContext SystemContext
	 */
	private void updateServiceStatus(EntryBase serviceEntry, String serviceStatus, 
			SystemContext systemContext)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.setServiceStatus(serviceEntry, serviceStatus);
		serviceEntry = systemContext.put(serviceEntry);
	}

	/**
	 * 顧客情報があるかどうか
	 * @param customerId 顧客情報ID
	 * @return 顧客情報が存在する場合true
	 */
	private boolean existCustomer(String customerId) 
	throws IOException, TaggingException {
		Customer customer = getCustomer(customerId);
		return isEnabledCustomer(customer);
	}
	
	/**
	 * 顧客情報が有効かどうか
	 * @param customer 顧客情報
	 * @return 顧客情報が有効である場合true
	 */
	private boolean isEnabledCustomer(Customer customer) {
		if (customer != null) {
			// 顧客ステータスはないので、存在すれば有効とする
			return true;
		}
		return false;
	}

	/**
	 * 顧客情報を取得
	 * @param customerId 顧客ID
	 * @return 顧客情報
	 */
	private Customer getCustomer(String customerId) 
	throws IOException, TaggingException {
		try {
			return Customer.retrieve(customerId);
		} catch (StripeException se) {
			// 存在しない場合、InvalidRequestException がスローされる。(code: resource_missing)
			if (se instanceof InvalidRequestException) {
				InvalidRequestException ire = (InvalidRequestException)se;
				if (ReflexStripeConst.STRIPE_CODE_RESOURCE_MISSING.equals(ire.getCode())) {
					if (isEnableAccessLog()) {
						logger.info("[getCustomer] InvalidRequestException: " + se.getMessage());
					}
					return null;	// データなし
				}
			}
			
			if (isEnableAccessLog()) {
				logger.info("[getCustomer] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * Stripeにログインユーザの顧客情報を登録し、vte.cxにも登録する。
	 * @param reflexContext ReflexContext
	 * @return 顧客ID
	 */
	private String postCustomer(ReflexContext reflexContext) 
	throws IOException, TaggingException {
		// メールアドレスを取得
		String email = ReflexStripeUtil.getEmail(reflexContext);
		String uid = reflexContext.getAuth().getUid();
		// Stripeに顧客情報を登録
		CustomerCreateParams params =
				CustomerCreateParams.builder()
				.setEmail(email)
				.putMetadata(ReflexStripeConst.METADATA_UID, uid)
				.build();
		try {
			Customer customer = Customer.create(params);
			String customerId = customer.getId();
			// 顧客IDをvte.cxに登録
			// キー: /_user/{UID}/stripe
			String userStripeUri = ReflexStripeUtil.getUserStripeUri(uid);
			EntryBase userStripeEntry = TaggingEntryUtil.createEntry(TaggingEnvUtil.getSystemService());
			userStripeEntry.setMyUri(userStripeUri);
			Contributor contributor = new Contributor();
			// urn:vte.cx:stripe:cus:{顧客ID}
			contributor.uri = ReflexStripeConst.URN_STRIPE_CUS_PREFIX + customerId;
			userStripeEntry.addContributor(contributor);
			reflexContext.put(userStripeEntry);
			return customerId;
			
		} catch (StripeException se) {
			if (isEnableAccessLog()) {
				logger.info("[postCustomer] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * CheckoutSessionを生成する.
	 * @param targetService 対象サービス名
	 * @param uid UID
	 * @param customerId 顧客ID
	 * @return リダイレクトURL
	 */
	private String createCheckoutSession(String targetServiceName, String uid, String customerId) 
	throws IOException, TaggingException {
		ReflexStripeEnv stripeEnv = ReflexStripeUtil.getStripeEnv();
		String successUrl = stripeEnv.getSuccessUrl();
		String cancelUrl = stripeEnv.getCancelUrl();
		String priceIdPro = stripeEnv.getPriceIdPro();
		String messageBase = stripeEnv.getCheckoutMessage();
		String message = messageBase.replaceAll("\\@", targetServiceName);

		SessionCreateParams.LineItem.Builder lineItemBuilder = SessionCreateParams.LineItem.builder()
				.setPrice(priceIdPro);
		long quantity = stripeEnv.getQuantity();
		if (quantity > 0) {
			lineItemBuilder.setQuantity(quantity);
		}
		
		SessionCreateParams params =
				SessionCreateParams.builder()
				.setSuccessUrl(successUrl)
				.setCancelUrl(cancelUrl)
				.addLineItem(lineItemBuilder.build())
				.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
				.setCustomer(customerId)
				// -d "saved_payment_method_options[payment_method_save]"="enabled"
				.setSavedPaymentMethodOptions(
						SessionCreateParams.SavedPaymentMethodOptions.builder()
						.setPaymentMethodSave(
								SessionCreateParams.SavedPaymentMethodOptions.PaymentMethodSave.ENABLED
								)
						.build()
						)

				// Checkout Session 自体の metadata
				.putMetadata(ReflexStripeConst.METADATA_SERVICE, targetServiceName)
				.putMetadata(ReflexStripeConst.METADATA_UID, uid)

				// 作成される Subscription の metadata
				.setSubscriptionData(
						SessionCreateParams.SubscriptionData.builder()
						.putMetadata(ReflexStripeConst.METADATA_SERVICE, targetServiceName)
						.putMetadata(ReflexStripeConst.METADATA_UID, uid)
						.build()
						)

				.setCustomText(
						SessionCreateParams.CustomText.builder()
						.setSubmit(
								SessionCreateParams.CustomText.Submit.builder()
								.setMessage(message)
								.build()
								)
						.build()
						)
				.build();
		try {
			Session session = Session.create(params);
			return session.getUrl();
		} catch (StripeException se) {
			if (isEnableAccessLog()) {
				logger.info("[createCheckoutSession] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * サブスクリプション支払い完了通知
	 * @param stripeObject StripeObject
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void completePayment(Event event, RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String uid;
		String serviceName;
		String subscriptionId;

		if (isEnableAccessLog()) {
			StringBuilder sb =  new StringBuilder();
			sb.append("[completePayment] event.getType() = ");
			sb.append(event.getType());
			sb.append(", event.getApiVersion() = ");
			sb.append(event.getApiVersion());
			sb.append(", Stripe.API_VERSION = ");
			sb.append(com.stripe.Stripe.API_VERSION);
			logger.info(sb.toString());
		}
		
		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		Optional<StripeObject> optionalObject = deserializer.getObject();
		if (optionalObject.isPresent() && optionalObject.get() instanceof Session) {
		    Session session = (Session) optionalObject.get();

			// リクエストデータの metadata.uid からUID、metadata.service から対象サービス名を取得する。
			Map<String, String> metadata = session.getMetadata();
			uid = metadata != null ? metadata.get(ReflexStripeConst.METADATA_UID) : null;
			serviceName = metadata != null ? metadata.get(ReflexStripeConst.METADATA_SERVICE) : null;
			subscriptionId = session.getSubscription();
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[completePayment] service=");
				sb.append(serviceName);
				sb.append(", uid=");
				sb.append(uid);
				sb.append(", subscriptionId=");
				sb.append(subscriptionId);
				logger.info(sb.toString());
			}
		} else {
			if (isEnableAccessLog()) {
				logger.info("[completePayment] The stripeObject does not have a Session.");
			}
			return;
		}
		// サブスクリプション情報を登録
		// キー: /_service/{対象サービス名}
		// サブスクリプションID(sub_xxx)をcontributor.uriに設定して更新する。
		//     urn:vte.cx:stripe:sub:{サブスクリプションID}
		// サービスステータスをproductionに更新する。
		SystemContext systemContext = createSystemContext(uid, requestInfo, connectionInfo);
		EntryBase serviceEntry = getServiceEntry(serviceName, systemContext);
		if (serviceEntry == null) {
			if (isEnableAccessLog()) {
				logger.info("[completePayment] The service does not exist. " + serviceName);
			}
			return;
		}
		
		// サブスクリプション情報の登録
		// contributor.uriに`urn:vte.cx:stripe:created:{created(エポック秒)の日時変換}`
		registerSubscription(serviceName, subscriptionId, event.getCreated(), serviceEntry, systemContext);
	}

	/**
	 * サブスクリプション終了通知
	 * @param stripeObject StripeObject
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void endPayment(Event event, RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		String uid;
		String serviceName;
		String subscriptionId;

		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		Optional<StripeObject> optionalObject = deserializer.getObject();
		if (optionalObject.isPresent() && optionalObject.get() instanceof Subscription) {
			Subscription subscription = (Subscription) optionalObject.get();

			// リクエストデータの metadata.uid からUID、metadata.service から対象サービス名を取得する。
			Map<String, String> metadata = subscription.getMetadata();
			uid = metadata != null ? metadata.get(ReflexStripeConst.METADATA_UID) : null;
			serviceName = metadata != null ? metadata.get(ReflexStripeConst.METADATA_SERVICE) : null;
			subscriptionId = subscription.getId();
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[endPayment] service=");
				sb.append(serviceName);
				sb.append(", uid=");
				sb.append(uid);
				sb.append(", subscriptionId=");
				sb.append(subscriptionId);
				logger.info(sb.toString());
			}
		} else {
			if (isEnableAccessLog()) {
				logger.info("[endPayment] The stripeObject does not have a Subscription.");
			}
			return;
		}
		
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		List<EntryBase> putEntries = new ArrayList<>();
		putFeed.entry = putEntries;
		// サブスクリプション情報を登録
		// キー: /_service/{対象サービス名}
		// 以下のcontributor.uriを削除して更新する。
		//     urn:vte.cx:stripe:sub:{サブスクリプションID}
		//     urn:vte.cx:stripe:cancel:{cancel_at(エポック秒)}
		// サービスステータスが`production`の場合、`staging`に更新する。`deleting`や`deleted`の場合はそのままとする。
		SystemContext systemContext = createSystemContext(uid, requestInfo, connectionInfo);

		int numRetries = ReflexStripeUtil.getStripeUpdateentryRetryCount();
		int waitMillis = ReflexStripeUtil.getStripeUpdateentryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
	
				EntryBase serviceEntry = getServiceEntry(serviceName, systemContext);
				if (serviceEntry == null) {
					if (isEnableAccessLog()) {
						logger.info("[endPayment] The service does not exist. " + serviceName);
					}
					return;
				}
				String subscriptionUrn = ReflexStripeUtil.getUrnSubscription(subscriptionId);
				if (serviceEntry.contributor != null) {
					List<Contributor> newContributors = new ArrayList<>();
					for (Contributor contributor : serviceEntry.contributor) {
						boolean processed = false;
						if (contributor.uri != null) {
							if (contributor.uri.equals(subscriptionUrn)) {
								// サブスクリプションIDのurn削除
								processed = true;
							} else if (contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_CANCEL_PREFIX) ||
									contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX)) {
								// 期日待ち中、支払い失敗のurn削除
								processed = true;
							}
						}
						if (!processed) {
							newContributors.add(contributor);
						}
					}
					serviceEntry.contributor = newContributors;
				}
				ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
				String currentServiceStatus = TaggingServiceUtil.getServiceStatus(serviceEntry);
				if (Constants.SERVICE_STATUS_PRODUCTION.equals(currentServiceStatus)) {
					serviceManager.setServiceStatus(serviceEntry, Constants.SERVICE_STATUS_STAGING);
				} else {
					// サービス削除の場合、idチェックを行わない。
					serviceEntry.id = null;
				}
				putEntries.add(serviceEntry);
				
				// サブスクリプションエントリー更新
				String subscriptionUri = ReflexStripeUtil.getStripeSubscriptionUri(subscriptionId);
				EntryBase subscriptionEntry = systemContext.getEntry(subscriptionUri);
				if (subscriptionEntry != null) {
					// subtitleに"deleted"を設定
					subscriptionEntry.subtitle = ReflexStripeConst.STRIPE_STATUS_DELETED;
					// contributor.uriに`urn:vte.cx:stripe:deleted:{event.created(エポック秒)の日時変換}`を追加
					Contributor contributor = new Contributor();
					String urn = ReflexStripeUtil.getUrnDeleted(event.getCreated());
					contributor.uri = urn;
					subscriptionEntry.addContributor(contributor);
					putEntries.add(subscriptionEntry);
				} else {
					if (isEnableAccessLog()) {
						logger.info("[endPayment] subscriptionEntry does not exist. " + subscriptionUri);
					}
				}
	
				systemContext.put(putFeed);
				break;

			} catch (OptimisticLockingException e) {
				// リトライ回数を超えるとエラー
				if (r >= numRetries) {
					throw e;
				}
				// 楽観的排他エラーの場合、スリープして再試行
				RetryUtil.sleep(waitMillis);
			}
		}
	}

	/**
	 * サブスクリプション支払い失敗通知
	 * @param stripeObject StripeObject
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void failedPayment(Event event, RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		String uid;
		String serviceName;
		String subscriptionId;

		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		Optional<StripeObject> optionalObject = deserializer.getObject();
		if (optionalObject.isPresent() && optionalObject.get() instanceof Invoice) {
			Invoice invoice = (Invoice) optionalObject.get();
			if (invoice.getParent() != null
					&& invoice.getParent().getSubscriptionDetails() != null) {
				// リクエストデータの metadata.uid からUID、metadata.service から対象サービス名を取得する。
				Map<String, String> metadata = invoice.getParent().getSubscriptionDetails().getMetadata();
				uid = metadata != null ? metadata.get(ReflexStripeConst.METADATA_UID) : null;
				serviceName = metadata != null ? metadata.get(ReflexStripeConst.METADATA_SERVICE) : null;
				subscriptionId = invoice.getParent()
						.getSubscriptionDetails()
						.getSubscription();
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[failedPayment] service=");
					sb.append(serviceName);
					sb.append(", uid=");
					sb.append(uid);
					sb.append(", subscriptionId=");
					sb.append(subscriptionId);
					logger.info(sb.toString());
				}
			} else {
				if (isEnableAccessLog()) {
					logger.info("[failedPayment] The stripeObject does not have a parent Subscription details.");
				}
				return;
			}
		} else {
			if (isEnableAccessLog()) {
				logger.info("[failedPayment] The stripeObject does not have a Invoice.");
			}
			return;
		}
		
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		List<EntryBase> putEntries = new ArrayList<>();
		putFeed.entry = putEntries;
		// サービスエントリー`/_service/{サービス名}`に支払い失敗情報を設定し更新
		// キー: /_service/{対象サービス名}
		//   contributor.uriに`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`を設定
		SystemContext systemContext = createSystemContext(uid, requestInfo, connectionInfo);

		int numRetries = ReflexStripeUtil.getStripeUpdateentryRetryCount();
		int waitMillis = ReflexStripeUtil.getStripeUpdateentryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				EntryBase serviceEntry = getServiceEntry(serviceName, systemContext);
				if (serviceEntry == null) {
					if (isEnableAccessLog()) {
						logger.info("[failedPayment] The service does not exist. " + serviceName);
					}
					return;
				}
		
				// contributor.uriに`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`を設定
				if (serviceEntry.contributor != null) {
					List<Contributor> newContributors = new ArrayList<>();
					for (Contributor contributor : serviceEntry.contributor) {
						if (contributor.uri == null || 
								!contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX)) {
							newContributors.add(contributor);
						}
					}
					serviceEntry.contributor = newContributors;
				}
				String failedPaymentUrn = ReflexStripeUtil.getUrnPaymentFailed(event.getCreated());
				Contributor contributor = new Contributor();
				contributor.uri = failedPaymentUrn;
				String serviceStatus = TaggingServiceUtil.getServiceStatus(serviceEntry);
				if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
					// サブスクリプション請求再試行の最終回では、先に`customer.subscription.deleted`が届く場合があるため、
					// サービスステータスがproductionの場合のみサービスエントリーに書き込む。
					serviceEntry.addContributor(contributor);
					putEntries.add(serviceEntry);
				}
				
				// サブスクリプションエントリー更新
				String subscriptionUri = ReflexStripeUtil.getStripeSubscriptionUri(subscriptionId);
				EntryBase subscriptionEntry = systemContext.getEntry(subscriptionUri);
				if (subscriptionEntry != null) {
					// contributor.uriに`urn:vte.cx:stripe:deleted:{event.created(エポック秒)の日時変換}`を追加
					subscriptionEntry.addContributor(contributor);
					putEntries.add(subscriptionEntry);
				} else {
					if (isEnableAccessLog()) {
						logger.info("[failedPayment] subscriptionEntry does not exist. " + subscriptionUri);
					}
				}
		
				systemContext.put(putFeed);
				break;

			} catch (OptimisticLockingException e) {
				// リトライ回数を超えるとエラー
				if (r >= numRetries) {
					throw e;
				}
				// 楽観的排他エラーの場合、スリープして再試行
				RetryUtil.sleep(waitMillis);
			}
		}
	}

	/**
	 * サブスクリプション支払い成功通知
	 * @param stripeObject StripeObject
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void paid(Event event, RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		String uid;
		String serviceName;
		String subscriptionId;

		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		Optional<StripeObject> optionalObject = deserializer.getObject();
		if (optionalObject.isPresent() && optionalObject.get() instanceof Invoice) {
			Invoice invoice = (Invoice) optionalObject.get();
			if (invoice.getParent() != null
					&& invoice.getParent().getSubscriptionDetails() != null) {
				// リクエストデータの metadata.uid からUID、metadata.service から対象サービス名を取得する。
				Map<String, String> metadata = invoice.getParent().getSubscriptionDetails().getMetadata();
				uid = metadata != null ? metadata.get(ReflexStripeConst.METADATA_UID) : null;
				serviceName = metadata != null ? metadata.get(ReflexStripeConst.METADATA_SERVICE) : null;
				subscriptionId = invoice.getParent()
						.getSubscriptionDetails()
						.getSubscription();
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[paid] service=");
					sb.append(serviceName);
					sb.append(", uid=");
					sb.append(uid);
					sb.append(", subscriptionId=");
					sb.append(subscriptionId);
					logger.info(sb.toString());
				}
			} else {
				if (isEnableAccessLog()) {
					logger.info("[paid] The stripeObject does not have a parent Subscription details.");
				}
				return;
			}
		} else {
			if (isEnableAccessLog()) {
				logger.info("[paid] The stripeObject does not have a Invoice.");
			}
			return;
		}
		
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		List<EntryBase> putEntries = new ArrayList<>();
		putFeed.entry = putEntries;
		// サービスエントリー`/_service/{サービス名}`に支払い失敗情報を設定し更新
		// キー: /_service/{対象サービス名}
		//   contributor.uriに`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`を設定
		SystemContext systemContext = createSystemContext(uid, requestInfo, connectionInfo);
		EntryBase serviceEntry = getServiceEntry(serviceName, systemContext);
		if (serviceEntry == null) {
			if (isEnableAccessLog()) {
				logger.info("[paid] The service does not exist. " + serviceName);
			}
			return;
		}
		
		// contributor.uriの`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`を削除
		boolean isChange = false;
		List<Contributor> newContributors = new ArrayList<>();
		if (serviceEntry.contributor != null) {
			for (Contributor contributor : serviceEntry.contributor) {
				if (contributor.uri != null && 
						contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX)) {
					isChange = true;
				} else {
					newContributors.add(contributor);
				}
			}
		}
		if (isChange) {
			serviceEntry.contributor = newContributors;
			putEntries.add(serviceEntry);

			// サブスクリプションエントリー更新
			String subscriptionUri = ReflexStripeUtil.getStripeSubscriptionUri(subscriptionId);
			EntryBase subscriptionEntry = systemContext.getEntry(subscriptionUri);
			if (subscriptionEntry != null) {
				// contributor.uriの`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`を削除
				newContributors = new ArrayList<>();
				if (subscriptionEntry.contributor != null) {
					for (Contributor contributor : subscriptionEntry.contributor) {
						if (contributor.uri != null && 
								contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX)) {
							// 削除のためリストに加えない
						} else {
							newContributors.add(contributor);
						}
					}
				}
				subscriptionEntry.contributor = newContributors;
				putEntries.add(subscriptionEntry);
			} else {
				if (isEnableAccessLog()) {
					logger.info("[paid] subscriptionEntry does not exist. " + subscriptionUri);
				}
			}
			systemContext.put(putFeed);
		}
	}

	/**
	 * サブスクリプション情報更新通知
	 * @param stripeObject StripeObject
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	/*
	void updateSubscription(Event event, RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String uid;
		String serviceName;
		String subscriptionId;

		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		Optional<StripeObject> optionalObject = deserializer.getObject();
		if (optionalObject.isPresent() && optionalObject.get() instanceof Subscription) {
			Subscription subscription = (Subscription) optionalObject.get();
			
			// `status`からステータスを取得。`canceled`でなければ処理を抜ける。
			String subscriptionStatus = subscription.getStatus();
			if (!ReflexStripeConst.STRIPE_STATUS_CANCELED.equals(subscriptionStatus)) {
				return;
			}

			// リクエストデータの metadata.uid からUID、metadata.service から対象サービス名を取得する。
			Map<String, String> metadata = subscription.getMetadata();
			uid = metadata != null ? metadata.get(ReflexStripeConst.METADATA_UID) : null;
			serviceName = metadata != null ? metadata.get(ReflexStripeConst.METADATA_SERVICE) : null;
			subscriptionId = subscription.getId();
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[updateSubscription] service=");
				sb.append(serviceName);
				sb.append(", uid=");
				sb.append(uid);
				sb.append(", subscriptionId=");
				sb.append(subscriptionId);
				logger.info(sb.toString());
			}
		} else {
			if (isEnableAccessLog()) {
				logger.info("[updateSubscription] The stripeObject does not have a Subscription.");
			}
			return;
		}
		
		// サービスエントリーを取得
		SystemContext systemContext = createSystemContext(uid, requestInfo, connectionInfo);
		EntryBase serviceEntry = getServiceEntry(serviceName, systemContext);
		if (serviceEntry == null) {
			if (isEnableAccessLog()) {
				logger.info("[updateSubscription] The service does not exist. " + serviceName);
			}
			return;
		}
		
		// サブスクリプションキャンセル情報をvte.cx側に登録
		canceledSubscription(event.getCreated(), serviceName, uid, subscriptionId, serviceEntry, 
				systemContext);
	}
	*/

	/**
	 * サブスクリプション情報の登録
	 * @param targetServiceName 対象サービス名
	 * @param subscriptionId サブスクリプションID
	 * @param subscriptionUrn サブスクリプションエントリーに登録するURN
	 * @param serviceEntry サービスエントリー
	 * @param systemContext SystemContext
	 */
	private void registerSubscription(String targetServiceName, String subscriptionId, Long created, 
			EntryBase serviceEntry, SystemContext systemContext)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		List<EntryBase> putEntries = new ArrayList<>();
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		putFeed.entry = putEntries;
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.setServiceStatus(serviceEntry, Constants.SERVICE_STATUS_PRODUCTION);
		String suscriptionUrn = ReflexStripeUtil.getUrnSubscription(subscriptionId);
		Contributor contributor = new Contributor();
		contributor.uri = suscriptionUrn;
		serviceEntry.addContributor(contributor);
		putEntries.add(serviceEntry);
		
		// 同時に、サブスクリプションとサービスの対応データも登録する。
		String subscriptionUri = ReflexStripeUtil.getStripeSubscriptionUri(subscriptionId);
		EntryBase subscriptionEntry = TaggingEntryUtil.createEntry(systemService);
		subscriptionEntry.setMyUri(subscriptionUri);
		// titleにサービス名
		subscriptionEntry.title = targetServiceName;
		// subtitleに"active"
		subscriptionEntry.subtitle = ReflexStripeConst.STRIPE_STATUS_ACTIVE;
		// contributor.uriに`urn:vte.cx:stripe:created:{created(エポック秒)の日時変換}`
		String createdUrn = ReflexStripeUtil.getUrnCreated(created);
		Contributor createdContributor = new Contributor();
		createdContributor.uri = createdUrn;
		subscriptionEntry.addContributor(createdContributor);
		putEntries.add(subscriptionEntry);
		
		systemContext.put(putFeed);
	}

	/**
	 * SystemContextを生成.
	 * StripeからのWebhook受信時に作成する。uidを指定する。
	 * @param uid UID
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return SystemContext
	 */
	private SystemContext createSystemContext(String uid, RequestInfo requestInfo, 
			ConnectionInfo connectionInfo) {
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		ReflexAuthentication auth = authManager.createAuth(
				AuthenticationConst.ACCOUNT_SERVICEADMIN,
				uid, null, Constants.AUTH_TYPE_SYSTEM, TaggingEnvUtil.getSystemService());
		return new SystemContext(auth, requestInfo, connectionInfo);
	}
	
	/**
	 * サービスエントリーを取得
	 * @param serviceName サービス名
	 * @param systemContext SystemContext
	 * @return サービスエントリー
	 */
	private EntryBase getServiceEntry(String serviceName, SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceUri = TaggingServiceUtil.getServiceUri(serviceName);
		return systemContext.getEntry(serviceUri);
	}
	
	/**
	 * サービスエントリーからサブスクリプションIDを抽出
	 * @param serviceEntry サービスエントリー
	 * @return サブスクリプションID
	 */
	private String getSubscriptionId(EntryBase serviceEntry) {
		if (serviceEntry != null && serviceEntry.contributor != null) {
			for (Contributor contributor : serviceEntry.contributor) {
				if (contributor.uri != null &&
						contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_SUB_PREFIX)) {
					return contributor.uri.substring(ReflexStripeConst.URN_STRIPE_SUB_PREFIX_LEN);
				}
			}
		}
		return null;
	}
	
	/**
	 * サービスエントリーから期間終了日エポック秒を抽出
	 * @param serviceEntry サービスエントリー
	 * @return 期間終了日エポック秒
	 */
	private String getCancelAt(EntryBase serviceEntry) {
		if (serviceEntry != null && serviceEntry.contributor != null) {
			for (Contributor contributor : serviceEntry.contributor) {
				if (contributor.uri != null &&
						contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_CANCEL_PREFIX)) {
					return contributor.uri.substring(ReflexStripeConst.URN_STRIPE_CANCEL_PREFIX_LEN);
				}
			}
		}
		return null;
	}
	
	/**
	 * すでに有効なサブスクリプションがあるかどうか
	 * @param targetServiceName サービス名
	 * @param serviceEntry サービスエントリー
	 * @param systemContext SystemContext
	 * @return すでに有効なサブスクリプションがある場合true
	 */
	private boolean existSubscription(String targetServiceName, EntryBase serviceEntry, SystemContext systemContext) 
	throws IOException, TaggingException {
		// サービスエントリーのサブスクリプションID抽出
		List<String> subIds = new ArrayList<>();
		if (serviceEntry.contributor != null) {
			for (Contributor contributor : serviceEntry.contributor) {
				String tmpSubscriptionId = ReflexStripeUtil.getSubscriptionIdByUrn(contributor.uri);
				if (!StringUtils.isBlank(tmpSubscriptionId)) {
					subIds.add(tmpSubscriptionId);
				}
			}
		}
		
		// Stripe上に、サービスに紐づいたサブスクリプションがあるかどうかチェック
		SubscriptionSearchResult result = searchSubscriptions(targetServiceName, 
				ReflexStripeConst.STRIPE_STATUS_ACTIVE);
		List<Subscription> subscriptions = result.getData();
		if (subscriptions != null && !subscriptions.isEmpty()) {
			// 存在するならサブスクリプションIDを取得
			Subscription subscription = subscriptions.get(0);
			String subscriptionId = subscription.getId();
			if (!StringUtils.isBlank(subscriptionId)) {
				// サービスエントリーに登録されているかチェック
				if (!subIds.contains(subscriptionId)) {
					// サブスクリプションIDの登録がなければ登録する。
					// サブスクリプションエントリーも登録すること
					registerSubscription(targetServiceName, subscriptionId, subscription.getCreated(), 
							serviceEntry, systemContext);
				}
				return true;
			}
		}
		
		// サービスエントリーにサブスクリプション情報が残っている場合、Stripeに対し存在チェックを行う
		for (String subscriptionId : subIds) {
			if (existSubscription(subscriptionId)) {
				// 有効
				return true;
			} else {
				// 無効のため、サービスエントリーから削除する
			}
		}
		
		return false;
	}
	
	/**
	 * Stripeから指定されたサービス名をmetadataに持つサブスクリプションを検索
	 * @param targetServiceName サービス名
	 * @param stripeStatus サブスクリプションのステータス
	 * @return 検索結果
	 */
	private SubscriptionSearchResult searchSubscriptions(String targetServiceName, String stripeStatus) 
	throws IOException, TaggingException {
		// Stripe上に、サービスに紐づいたサブスクリプションがあるかどうかチェック
		StringBuilder sb = new StringBuilder();
		sb.append("status:'");
		sb.append(stripeStatus);
		sb.append("' AND metadata['");
		sb.append(ReflexStripeConst.METADATA_SERVICE);
		sb.append("']:'");
		sb.append(targetServiceName);
		sb.append("'");
		String query = sb.toString();
		try {
			SubscriptionSearchParams params =
					  SubscriptionSearchParams.builder()
					    .setQuery(query)
					    .build();
			return Subscription.search(params);
	          
		} catch (StripeException se) {
			if (isEnableAccessLog()) {
				logger.info("[searchSubscriptions] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}

	/**
	 * すでに有効なサブスクリプションがあるかどうか
	 * @param subscriptionId サブスクリプションID
	 * @return すでに有効なサブスクリプションがある場合true
	 */
	private boolean existSubscription(String subscriptionId) 
	throws IOException, TaggingException {
		Subscription subscription = getSubscription(subscriptionId);
		return isEnabledSubscription(subscription);
	}
	
	/**
	 * サブスクリプションが有効かどうか
	 * @param subscription サブスクリプション情報
	 * @return サブスクリプションが有効である場合true
	 */
	private boolean isEnabledSubscription(Subscription subscription) {
		if (subscription != null) {
			String subscriptionStatus = subscription.getStatus();
			if (ReflexStripeConst.STRIPE_STATUS_ACTIVE.equals(subscriptionStatus) ||
					ReflexStripeConst.STRIPE_STATUS_TRIALING.equals(subscriptionStatus)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * サブスクリプション情報を取得
	 * @param subscriptionId サブスクリプションID
	 * @return サブスクリプション情報
	 */
	private Subscription getSubscription(String subscriptionId) 
	throws IOException, TaggingException {
		try {
			return Subscription.retrieve(subscriptionId);
		} catch (StripeException se) {
			// 存在しない場合、InvalidRequestException がスローされる。(code: resource_missing)
			if (se instanceof InvalidRequestException) {
				InvalidRequestException ire = (InvalidRequestException)se;
				if (ReflexStripeConst.STRIPE_CODE_RESOURCE_MISSING.equals(ire.getCode())) {
					if (isEnableAccessLog()) {
						logger.info("[getSubscription] InvalidRequestException: " + se.getMessage());
					}
					return null;	// データなし
				}
			}
			
			if (isEnableAccessLog()) {
				logger.info("[getSubscription] StripeException: " + se.getMessage(), se);
			}
			TaggingException te = ReflexStripeUtil.convertTaggingException(se);
			if (te != null) {
				throw te;
			}
			throw new IOException(se);
		}
	}
	
	/**
	 * サブスクリプションの、支払い失敗によるキャンセル
	 * @param eventCreated イベント発生エポック秒
	 * @param serviceName 対象サービス名
	 * @param uid UID
	 * @param subscriptionId サブスクリプションID
	 * @param serviceEntry サービスエントリー
	 * @param systemContext SystemContext
	 */
	/*
	private void canceledSubscription(long eventCreated, String serviceName, String uid, 
			String subscriptionId, EntryBase serviceEntry, SystemContext systemContext) 
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		
		// サービスステータスが`production`で、
		// contributor.uriに`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`がある場合、
		// 支払い不履行でサブスクリプションキャンセルとみなす。それ以外は処理を抜ける。
		String serviceStatus = TaggingServiceUtil.getServiceStatus(serviceEntry);
		if (!Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[canceledSubscription] Do nothing. targetServiceName=");
				sb.append(serviceName);
				sb.append(", serviceStatus=");
				sb.append(serviceStatus);
				logger.info(sb.toString());
			}
			return;
		}
		boolean isFailedPayment = false;
		if (serviceEntry.contributor != null) {
			for (Contributor contributor : serviceEntry.contributor) {
				if (contributor.uri != null && 
						contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX)) {
					isFailedPayment = true;
					break;
				}
			}
		}
		if (!isFailedPayment) {
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[canceledSubscription] No failed payment. targetServiceName=");
				sb.append(serviceName);
				sb.append(", serviceStatus=");
				sb.append(serviceStatus);
				logger.info(sb.toString());
			}
			return;
		}
		
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		List<EntryBase> putEntries = new ArrayList<>();
		putFeed.entry = putEntries;
		// サービスエントリー`/_service/{サービス名}`を更新
		// サービスステータスをstagingに変更
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.setServiceStatus(serviceEntry, Constants.SERVICE_STATUS_STAGING);
		// contributor.uriの`urn:vte.cx:stripe:sub:{サブスクリプションID}`を削除
		String subscriptionUrn = ReflexStripeUtil.getUrnSubscription(subscriptionId);
		// contributor.uriの`urn:vte.cx:stripe:payment_failed:{event.created(エポック秒)の日時変換}`を削除
		if (serviceEntry.contributor != null) {
			List<Contributor> newContributors = new ArrayList<>();
			for (Contributor contributor : serviceEntry.contributor) {
				if (contributor.uri == null || 
						!contributor.uri.startsWith(ReflexStripeConst.URN_STRIPE_PAYMENTFAILED_PREFIX) ||
						!contributor.uri.equals(subscriptionUrn)) {
					newContributors.add(contributor);
				}
			}
			serviceEntry.contributor = newContributors;
		}
		putEntries.add(serviceEntry);

		// サブスクリプションエントリー`/stripe/subscription/{サブスクリプションID}`を更新
		String subscriptionUri = ReflexStripeUtil.getStripeSubscriptionUri(subscriptionId);
		EntryBase subscriptionEntry = systemContext.getEntry(subscriptionUri);
		if (subscriptionEntry != null) {
			// contributor.uriに`urn:vte.cx:stripe:canceled:{event.created(エポック秒)の日時変換}`を追加
			String canceledUrn = ReflexStripeUtil.getUrnCanceled(eventCreated);
			Contributor contributor = new Contributor();
			contributor.uri = canceledUrn;
			subscriptionEntry.addContributor(contributor);
			// subtitleに"canceled"を設定
			subscriptionEntry.subtitle = ReflexStripeConst.STRIPE_STATUS_CANCELED;
			putEntries.add(subscriptionEntry);
		} else {
			if (isEnableAccessLog()) {
				logger.info("[canceledSubscription] subscriptionEntry does not exist. " + subscriptionUri);
			}
		}

		systemContext.put(putFeed);
	}
	*/
	
	/**
	 * Stripe処理のアクセスログを出力するかどうかを取得.
	 * @return Stripe処理のアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return ReflexStripeUtil.isEnableAccessLog();
	}
}
