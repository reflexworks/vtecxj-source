package jp.reflexworks.taggingservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ユーザごとのサービス一覧を取得する機能のためのユーティリティ.
 */
public class TaggingServiceUtil {

	/** サービスエントリーURI + "/" */
	private static final String URI_SERVICE_SLASH = Constants.URI_SERVICE + "/";
	/** サービスエントリーURI + "/" の長さ */
	private static final int URI_SERVICE_SLASH_LEN = URI_SERVICE_SLASH.length();

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(TaggingServiceUtil.class);

	/**
	 * サービスエントリーのキーを取得
	 *  /_service/{サービス名} を返却します。
	 * @param serviceName サービス名
	 * @return サービスエントリーのキー
	 */
	public static String getServiceUri(String serviceName) {
		if (StringUtils.isBlank(serviceName)) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_SERVICE);
		sb.append("/");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * ユーザごとのサービス一覧フォルダURIを取得
	 * @param uid UID
	 * @return ユーザごとのサービス一覧フォルダURI
	 */
	public static final String getUserServiceUri(String uid) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		StringBuilder sb = new StringBuilder();
		sb.append(userManager.getUserTopUriByUid(uid));
		sb.append(ServiceConst.URI_LAYER_SERVICE);
		return sb.toString();
	}

	/**
	 * アクセスカウンタのフォルダエントリーキーを取得
	 *   /_service/{サービス名}/access_count
	 * @param serviceName サービス名
	 * @return アクセスカウンタのフォルダエントリーキー
	 */
	public static String getAccessCountUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getServiceUri(serviceName));
		sb.append(ServiceConst.URI_LAYER_ACCESS_COUNT);
		return sb.toString();
	}

	/**
	 * ストレージ容量のエントリーキーを取得
	 *   /_service/{サービス名}/storage_totalsize
	 * @param serviceName サービス名
	 * @return ストレージ容量のエントリーキー
	 */
	public static String getStorageTotalsizeUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getServiceUri(serviceName));
		sb.append(ServiceConst.URI_LAYER_STORAGE_TOTALSIZE);
		return sb.toString();
	}

	/**
	 * 今日分のアクセスカウンタキーを取得.
	 * /_service/{サービス名}/access_count/today
	 * @param serviceName サービス名
	 * @return 今日分のアクセスカウンタキー
	 */
	public static String getAccessCountTodayUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getAccessCountUri(serviceName));
		sb.append(ServiceConst.URI_LAYER_TODAY);
		return sb.toString();
	}

	/**
	 * サービスEntry URIからサービス名を取得.
	 *  /_service/{サービス名} からサービス名の部分を抽出。
	 * @param serviceUri サービスエントリーURI
	 * @return サービス名
	 */
	public static String getServiceNameFromServiceUri(String serviceUri) {
		if (serviceUri == null || !serviceUri.startsWith(URI_SERVICE_SLASH)) {
			return null;
		}
		int idx = serviceUri.indexOf("/", URI_SERVICE_SLASH_LEN);
		if (idx == -1) {
			idx = serviceUri.length();
		}
		return serviceUri.substring(URI_SERVICE_SLASH_LEN, idx);
	}

	/**
	 * サービスエントリーからサービスステータスを取得
	 * @param serviceEntry サービスエントリー
	 * @return サービスステータス
	 */
	public static String getServiceStatus(EntryBase serviceEntry) {
		if (serviceEntry != null) {
			return serviceEntry.subtitle;
		}
		return null;
	}
	
	/**
	 * BaaSかどうかを返却.
	 * BaaSの場合、以下が有効になる。
	 *   1. アクセスカウンタを加算
	 *   2. サービス登録時、公開区分をstagingとする。
	 *   3. staging から production またはその逆への切り替え処理を有効にする。
	 * @return BaaSの場合true
	 */
	public static boolean isBaaS() {
		return TaggingEnvUtil.getSystemPropBoolean(ServiceConst.PROP_ENABLE_BAAS, false);
	}

}
