package jp.reflexworks.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.storage.CloudStorageUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 課金のためのカウンタ情報集計処理.
 *  ・アクセスカウンタをRedisからBDBに移動する。
 *  ・ストレージ容量をRedisに登録する。
 */
public class AccessCountBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** アクセスカウンタ年月フォーマット */
	private static final String FORMAT_YYYYMM = "yyyyMM";
	
	/** Storageサイズ取得時のバケット未存在エラーメッセージ */
	private static final String OUTERR_BUCKETNOTFOUND_PREFIX = "BucketNotFoundException: 404";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]gsutilの格納ディレクトリ
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// 引数チェック
		if (args == null) {
			throw new IllegalStateException("引数がnullです。");
		}
		if (args.length < 1) {
			throw new IllegalStateException("引数が不足しています。[0]gsutilの格納ディレクトリ");
		}
		String gsutilDir = args[0];

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		SystemContext systemContext = new SystemContext(systemService,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		try {
			// サービス一覧を取得
			StringBuilder sb = new StringBuilder();
			sb.append(Constants.URI_SERVICE);
			sb.append("?");
			sb.append(RequestParam.PARAM_LIMIT);
			sb.append("=");
			sb.append(RequestParam.WILDCARD);
			FeedBase serviceFeed = systemContext.getFeed(sb.toString());
			if (serviceFeed == null || serviceFeed.entry == null || serviceFeed.entry.isEmpty()) {
				throw new IllegalStateException("The services are not found.");
			}

			// サービスごとにアクセスカウンタのバッチ処理を行う。
			for (EntryBase serviceEntry : serviceFeed.entry) {
				execForEachService(systemContext, serviceEntry, gsutilDir);
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	/**
	 * サービスごとの処理
	 * @param systemContext SystemContext
	 * @param serviceEntry サービスエントリー
	 * @param gsutilDir gsutilの格納ディレクトリ
	 */
	private void execForEachService(SystemContext systemContext, EntryBase serviceEntry,
			String gsutilDir)
	throws IOException, TaggingException {
		String serviceName = TaggingServiceUtil.getServiceNameFromServiceUri(serviceEntry.getMyUri());
		try {
			String serviceStatus = TaggingServiceUtil.getServiceStatus(serviceEntry);
			// サービスステータスが削除または失敗の場合、処理を抜ける。
			if (Constants.SERVICE_STATUS_DELETED.equals(serviceStatus) ||
					Constants.SERVICE_STATUS_FAILURE.equals(serviceStatus)) {
				return;
			}

			// ・アクセスカウンタ
			//  Redisからtodayのアクセスカウンタを取得
			String todayUri = TaggingServiceUtil.getAccessCountTodayUri(serviceName);
			Long todaysCount = systemContext.getCacheLong(todayUri);
			if (todaysCount != null && todaysCount > 0) {
				if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
					// サービスがproductionの場合、以下の処理を行う。
					// 昨日の年月を取得
					String accessCountYMUri = getAccessCountYMUri(serviceName);
					// BDBにアクセスカウンタをインクリメント
					if (logger.isTraceEnabled()) {
						logger.debug("[addAccessCount] uri = " + accessCountYMUri + " , addids = " + todaysCount);
					}
					systemContext.addids(accessCountYMUri, todaysCount);
				}

				// Redisのtodayのアクセスカウンタを0に設定(0以外の場合)
				systemContext.setCacheLong(todayUri, 0);
			}

			// ・ストレージ容量取得
			//  gsutilでストレージの容量を取得
			Long storageTotalsize = getStorageTotalsize(systemContext, serviceName, gsutilDir);

			//  Redisにストレージ容量を設定
			if (storageTotalsize != null) {
				String storageTotalsizeUri = TaggingServiceUtil.getStorageTotalsizeUri(serviceName);
				systemContext.setCacheLong(storageTotalsizeUri, storageTotalsize);
			}

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// サービス単位でエラーをワーニング出力し、処理を続ける。
			StringBuilder sb = new StringBuilder();
			sb.append("[execForEachService] serviceName=");
			sb.append(serviceName);
			sb.append(" Error occured. ");
			sb.append(e.getClass().getName());
			sb.append(" : ");
			sb.append(e.getMessage());
			logger.warn(sb.toString(), e);
		}
	}

	/**
	 * BDBのアクセスカウンタURIを取得
	 * @param serviceName サービス名
	 * @return BDBのアクセスカウンタURI
	 */
	private String getAccessCountYMUri(String serviceName) {
		// YYYYMMの算出
		// (現在時刻 - PROP_ACCESSCOUNT_INTERVAL_HOUR) で計算された日時の年月
		int intervalHour = TaggingEnvUtil.getSystemPropInt
				(VtecxBatchConst.PROP_ACCESSCOUNT_INTERVAL_HOUR,
						VtecxBatchConst.ACCESSCOUNT_INTERVAL_HOUR_DEFAULT);
		int minusHour = 0 - intervalHour;

		Date now = new Date();
		Date aggregateDate = DateUtil.addTime(now, 0, 0, 0, minusHour, 0, 0, 0);
		String ym = DateUtil.getDateTimeFormat(aggregateDate, FORMAT_YYYYMM);

		// /_service/{サービス名}/access_count/{yyyyMM}
		StringBuilder sb = new StringBuilder();
		sb.append(TaggingServiceUtil.getAccessCountUri(serviceName));
		sb.append("/");
		sb.append(ym);
		return sb.toString();
	}

	/**
	 * ストレージのデータ容量を取得.
	 * @param systemContext SystemContext
	 * @param serviceName サービス名
	 * @param gsutilDir gsutilの格納ディレクトリ
	 * @return ストレージのデータ容量
	 */
	private Long getStorageTotalsize(SystemContext systemContext, String serviceName, String gsutilDir)
	throws IOException, TaggingException {
		// バケット名取得
		String bucketName = CloudStorageUtil.getBucketNameByEntry(serviceName, systemContext);
		if (StringUtils.isBlank(bucketName)) {
			if (logger.isTraceEnabled()) {
				logger.debug("[getStorageTotalsize] bucketName is null. serviceName = " + serviceName);
			}
			return null;
		}
		// ログ用接頭辞
		StringBuilder prefixsb = new StringBuilder();
		prefixsb.append("[getStorageTotalsize] serviceName = ");
		prefixsb.append(serviceName);
		prefixsb.append(" , bucketName = ");
		prefixsb.append(bucketName);
		String logprefix = prefixsb.toString();

		// バケットの容量を取得
		String[] command = {gsutilDir + "/gsutil", "du", "-s", "gs://" + bucketName}; // 起動コマンドを指定する
		Runtime runtime = Runtime.getRuntime(); // ランタイムオブジェクトを取得する
		BufferedReader br = null;
		InputStream in = null;
		try {
			Process process = runtime.exec(command); // 指定したコマンドを実行する

			// 標準出力
			in = process.getInputStream();
			StringBuilder sb = new StringBuilder();
			br = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line + Constants.NEWLINE);
			}
			String str = sb.toString();
			if (logger.isTraceEnabled()) {
				logger.debug(logprefix + " [out] " + str);
			}

			br.close();
			in.close();

			// エラー出力
			in = process.getErrorStream();
			StringBuilder err = new StringBuilder();
			br = new BufferedReader(new InputStreamReader(in));
			while ((line = br.readLine()) != null) {
				err.append(line + Constants.NEWLINE);
			}
			String errStr = err.toString();
			if (!StringUtils.isBlank(errStr)) {
				// エラー文字列の最初が「BucketNotFoundException: 404」であればログ出力しない
				if (errStr.startsWith(OUTERR_BUCKETNOTFOUND_PREFIX)) {
					return null;
				}
				StringBuilder logsb = new StringBuilder();
				logsb.append(logprefix);
				logsb.append(" [Error out] ");
				logsb.append(errStr);
				logger.warn(logsb.toString());
				throw new IllegalStateException(errStr);

				// リターンコード
				//logsb = new StringBuilder();
				//logsb.append(logprefix);
				//logsb.append(" [Return code] ");
				//logsb.append(process.waitFor());
				//logger.warn(logsb.toString());
			}

			// 容量を取り出す
			String usage = null;
			int idx = str.indexOf(" ");
			if (idx > 0) {
				usage = str.substring(0, idx);
			} else {
				usage = str;
			}
			if (logger.isTraceEnabled()) {
				logger.debug(logprefix + " [usage] " + usage);
			}

			// 戻り値
			if (StringUtils.isLong(usage)) {
				return Long.parseLong(usage);
			} else {
				return null;
			}

		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					logger.warn(logprefix + "close error.", e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					logger.warn(logprefix + "close error.", e);
				}
			}
		}
	}

}
