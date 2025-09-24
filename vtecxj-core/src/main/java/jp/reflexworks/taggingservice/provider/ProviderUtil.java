package jp.reflexworks.taggingservice.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EntryContentInfo;
import jp.reflexworks.taggingservice.model.SendMailInfo;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 汎用API ユーティリティ
 */
public class ProviderUtil {
	
	/**
	 * APIKeyチェック.
	 * @param req リクエスト
	 */
	public static void checkAPIKey(ReflexRequest req) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		
		// リクエストに設定があるかどうか
		String apiKey = getAPIKeyFromRequest(req);
		if (StringUtils.isBlank(apiKey)) {
			String msg = "APIKey is required.";
			PermissionException pe = new PermissionException();
			pe.setSubMessage(msg);
			throw pe;
		}
		
		// APIKeyが正しいかどうか
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String serviceAPIKey = serviceBlogic.getAPIKey(serviceName, requestInfo, connectionInfo);
		if (!apiKey.equals(serviceAPIKey)) {
			String msg = "APIKey is invalid.";
			PermissionException pe = new PermissionException();
			pe.setSubMessage(msg);
			throw pe;
		}
	}

	/**
	 * リクエストヘッダからAPIKeyを取り出す。
	 * @param req リクエスト
	 * @return APIKey
	 */
	 private static String getAPIKeyFromRequest(ReflexRequest req) {
		// Authorization: APIKey {APIKey}
		String apiKey = ReflexServletUtil.getHeaderValue(req,
				ReflexServletConst.HEADER_AUTHORIZATION,
				ReflexServletConst.HEADER_AUTHORIZATION_APIKEY);
		// X-APIKEY: {APIKey}
		if (StringUtils.isBlank(apiKey)) {
			apiKey = req.getHeader(ProviderConst.HEADER_APIKEY);
			if (StringUtils.isBlank(apiKey)) {
				apiKey = req.getHeader(ProviderConst.HEADER_APIKEY_LOWER);
			}
		}
		return apiKey;
	}
	
	/**
	 * BigQuery削除処理の引数:キーリストを取得
	 * @param feed リクエスト引数
	 * @return BigQuery削除処理の引数:キーリスト
	 */
	static String[] getBqUris(FeedBase feed) {
		if (feed == null || feed.link == null) {
			return null;
		}
		List<String> uris = new ArrayList<>();
		for (Link link : feed.link) {
			if (!StringUtils.isBlank(link._$href)) {
				uris.add(link._$href);
			}
		}
		if (!uris.isEmpty()) {
			return uris.toArray(new String[0]);
		}
		return null;
	}
	 
	/**
	 * BigQuery登録・削除処理の引数:テーブルリストを取得
	 * @param feed リクエスト引数
	 * @return BigQuery登録・削除処理の引数:テーブルリスト
	 */
	static Map<String, String> getBqTables(FeedBase feed) {
		if (feed == null || StringUtils.isBlank(feed.title)) {
			return null;
		}
		String[] tmps = feed.title.split(",");
		Map<String, String> tableMap = new LinkedHashMap<>();
		for (String tmp : tmps) {
			int idx = tmp.indexOf(":");
			if (idx > 0) {
				tableMap.put(tmp.substring(0, idx), tmp.substring(idx + 1));
			}
		}
		if (!tableMap.isEmpty()) {
			return tableMap;
		}
		return null;
	}

	/**
	 * クエリ検索の実行結果をJSONに変換する.
	 * @param resultQuery クエリ検索実行結果
	 * @param parent 親項目
	 * @return JSON
	 */
	public static String convertResultQuery(List<Map<String, Object>> resultQuery, String parent) {
		String result = "[";
		if (resultQuery!=null) {
			for (int j = 0; j < resultQuery.size(); j++) {
				Map<String, Object> line = resultQuery.get(j);
				result += "{";
				if (parent!=null) {
					result += "\"" + parent + "\":{";
				}
				List<String> keys = new ArrayList<>(line.keySet());

				for(int i = 0; i < keys.size(); i++) {
					Object value = line.get(keys.get(i));
					if (value != null) {
						if (i != 0) {
							result += ",";
						}
						result += "\"" + keys.get(i) + "\":" + quote(value);
					}
				}
				if (parent != null) {
					result += "}";
				}
				result += "}";
				if (j < resultQuery.size() - 1) {
					result += ",";
				}
			}
		}
		result += "]";
		return result;
	}

	/**
	 * クエリ検索の実行結果をCSVに変換する.
	 * @param resultQuery クエリ検索実行結果
	 * @param header CSV先頭行
	 * @return CSV文字列配列
	 */
	public static String[] convertResultQuerycsv(List<Map<String, Object>> resultQuery, String header) {
		List<String> results = new ArrayList<>();
		if (resultQuery != null) {
			for (int j = 0; j < resultQuery.size(); j++) {
				Map<String, Object> line = resultQuery.get(j);
				List<String> keys = new ArrayList<>(line.keySet());
				// header
				if (j == 0) {
					if (header == null) {
						String row = "";
						for (int i = 0; i < keys.size(); i++) {
							row += keys.get(i);
							if (i<keys.size()-1) {
								row += ",";
							}
						}
						results.add(row);
					} else {
						results.add(header);
					}
				}
				String row = "";
				for (int i = 0; i < keys.size(); i++) {
					Object value = line.get(keys.get(i));
					if (value != null) {
						row += "\"" + value + "\"";
					} else {
						row += "\"\"";
					}
					if (i < keys.size() - 1) {
						row += ",";
					}
				}
				results.add(row);
			}
		}
		return results.toArray(new String[results.size()]);
	}

	/**
	 * クォート編集.
	 * 数値・boolean以外はクォートで囲む編集を行う。
	 * 配列の場合、大括弧で囲み、再帰的処理を行う。
	 * @param obj オブジェクト
	 * @return 編集した値
	 */
	private static String quote(Object obj) {
		if (obj instanceof Number | obj instanceof Boolean) {
			return "" + obj;
		} else if (obj != null && obj.getClass().isArray()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			int length = Array.getLength(obj);
			for (int i = 0; i < length; i++) {
				if (i > 0) {
					sb.append(",");
				}
			    Object tmp = Array.get(obj, i);
			    sb.append(quote(tmp));
			}
			sb.append("]");
			return sb.toString();
		}
		return escape("" + obj);
	}

	/**
	 * エスケープ編集.
	 * @param string 編集対象
	 * @return エスケープ編集を行った文字列
	 */
	private static String escape(String string) {
		if (string == null || string.length() == 0) {
			return "\"\"";
		}

		char c = 0;
		int i;
		int len = string.length();
		StringBuilder sb = new StringBuilder(len + 4);
		String t;

		sb.append('"');
		for (i = 0; i < len; i += 1) {
			c = string.charAt(i);
			switch (c) {
			case '\\':
			case '"':
				sb.append('\\');
				sb.append(c);
				break;
			case '/':
				sb.append('\\');
				sb.append(c);
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\r':
				sb.append("\\r");
				break;
			default:
				if (c < ' ') {
					t = "000" + Integer.toHexString(c);
					sb.append("\\u" + t.substring(t.length() - 4));
				} else {
					sb.append(c);
				}
			}
		}
		sb.append('"');
		return sb.toString();
	}
	
	/**
	 * クエリ検索の戻り値を編集.
	 * BigQuery、RDBのクエリ検索が対象。
	 * @param retQuery クエリ検索結果
	 * @param csv CSVを戻すかどうか。またCSVを戻す場合、ファイル名指定(任意)
	 * @param subtitle 戻り値JSONの親項目(任意)、またはCSVのヘッダ(任意)
	 * @return クエリ検索の戻り値を編集したもの
	 */
	static ReflexContentInfo getQueryResponse(List<Map<String, Object>> retQuery, 
			String csv, String subtitle) 
	throws IOException {
		byte[] data = null;
		Map<String, String> headers = new HashMap<>();
		if (csv == null) {
			// JSONで返す
			String json = convertResultQuery(retQuery, subtitle);
			if (!StringUtils.isBlank(json)) {
				data = json.getBytes(Constants.ENCODING);
			}
			headers.put(ReflexServletConst.HEADER_CONTENT_TYPE, ReflexServletConst.CONTENT_TYPE_JSON);
			
		} else {
			// CSVで返す
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			String[] csvData = convertResultQuerycsv(retQuery, subtitle);
			if (csvData != null && csvData.length > 0) {
				for (String line : csvData) {
					byte[] tmp = toSJIS(line + Constants.CRLF).getBytes("Windows-31J");
					bout.write(tmp);
				}
				data = bout.toByteArray();
			}
			headers.put(ReflexServletConst.HEADER_CONTENT_TYPE, ReflexServletConst.CONTENT_TYPE_CSV);
			if (!StringUtils.isBlank(csv)) {
				StringBuilder sb = new StringBuilder();
				sb.append(ReflexServletConst.HEADER_VALUE_ATTACHMENT_FILENAME_PREFIX);
				sb.append(UrlUtil.urlEncode(csv));
				sb.append(ReflexServletConst.DOUBLEQUOTE);
				headers.put(ReflexServletConst.HEADER_CONTENT_DISPOSITION, sb.toString());
			}
		}
		
		return new EntryContentInfo(null, data, headers, null);
	}

	/**
	 * 文字コードをSJISに変換
	 * @param s 文字列
	 * @return SJISに変換した文字列
	 */
	private static String toSJIS(String s) {
		StringBuffer sb = new StringBuffer();
		char c;

		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			switch (c) {
			case 0x301c:
				c = 0xff5e;
				break;
			case 0x2016:
				c = 0x2225;
				break;
			case 0x2212:
				c = 0xff0d;
				break;
			case 0x00a2:
				c = 0xffe0;
				break;
			case 0x00a3:
				c = 0xffe1;
				break;
			case 0x00ac:
				c = 0xffe2;
				break;
			case 0x2014:
				c = 0x2015;
				break;
			default:
				break;
			}

			sb.append(c);
		}
		return new String(sb);
	}
	
	/**
	 * 引数からメール送信情報を抽出し、modelクラスにまとめて返却.
	 * @param feed 引数Feed
	 * @return メール送信情報
	 */
	static SendMailInfo getSendMailInfo(FeedBase feed) {
		if (feed == null || feed.entry == null || feed.entry.isEmpty() ||
				feed.link == null || feed.link.isEmpty()) {
			return null;
		}
		
		EntryBase entry = feed.entry.get(0);
		List<String> toList = new ArrayList<>();
		List<String> ccList = new ArrayList<>();
		List<String> bccList = new ArrayList<>();
		List<String> attachmentList = new ArrayList<>();
		for (Link link : feed.link) {
			String rel = link._$rel;
			String href = link._$href;
			if (!StringUtils.isBlank(href)) {
				if (ProviderConst.SENDMAIL_TO.equals(rel)) {
					toList.add(href);
				} else if (ProviderConst.SENDMAIL_CC.equals(rel)) {
					ccList.add(href);
				} else if (ProviderConst.SENDMAIL_BCC.equals(rel)) {
					bccList.add(href);
				} else if (ProviderConst.SENDMAIL_ATTACHMENT.equals(rel)) {
					attachmentList.add(href);
				}
			}
		}
		String[] tos = null;
		String[] ccs = null;
		String[] bccs = null;
		String[] attachments = null;
		if (!toList.isEmpty()) {
			tos = toList.toArray(new String[0]);
		}
		if (!ccList.isEmpty()) {
			ccs = ccList.toArray(new String[0]);
		}
		if (!bccList.isEmpty()) {
			bccs = bccList.toArray(new String[0]);
		}
		if (!attachmentList.isEmpty()) {
			attachments = attachmentList.toArray(new String[0]);
		}
		return new SendMailInfo(entry, tos, ccs, bccs, attachments);
	}
	
	/**
	 * Push通知の送信先を抽出.
	 * @param feed Feed
	 * @return Push通知の送信先リスト
	 */
	static String[] getPushNotificationTo(FeedBase feed) {
		if (feed == null || feed.link == null || feed.link.isEmpty()) {
			return null;
		}
		List<String> toList = new ArrayList<>();
		for (Link link : feed.link) {
			String rel = link._$rel;
			String href = link._$href;
			if (!StringUtils.isBlank(href) && ProviderConst.PUSHNOTIFICATION_TO.equals(rel)) {
				toList.add(href);
			}
		}
		if (!toList.isEmpty()) {
			return toList.toArray(new String[0]);
		}
		return null;
	}
	
	/**
	 * PDFレスポンス情報を生成
	 * @param data PDFデータ
	 * @param filename PDFファイル名(任意)
	 * @return PDFレスポンス情報
	 */
	static ReflexContentInfo getPdfContentInfo(byte[] data, String filename) {
		Map<String, String> headers = new HashMap<>();
		headers.put(ReflexServletConst.HEADER_CONTENT_TYPE, ReflexServletConst.CONTENT_TYPE_PDF);
		if (!StringUtils.isBlank(filename)) {
			StringBuilder sb = new StringBuilder();
			sb.append(ReflexServletConst.HEADER_VALUE_ATTACHMENT_FILENAME_PREFIX);
			sb.append(UrlUtil.urlEncode(filename));
			sb.append(ReflexServletConst.DOUBLEQUOTE);
			headers.put(ReflexServletConst.HEADER_CONTENT_DISPOSITION, sb.toString());
		}
		return new EntryContentInfo(null, data, headers, null);
	}
	
	/**
	 * バイト配列を文字列に変換
	 * @param strData バイト配列
	 * @return 文字列
	 */
	static String getString(byte[] strData) 
	throws IOException {
		if (strData == null || strData.length == 0) {
			return null;
		}
		return new String(strData, Constants.ENCODING);
	}
	
	/**
	 * サービス連携のサービス名を取得.
	 * @param req リクエスト
	 * @return サービス連携のサービス名
	 */
	public static String getServiceLinkage(ReflexRequest req) {
		String targetServiceName = req.getHeader(ProviderConst.HEADER_SERVICELINKAGE);
		if (StringUtils.isBlank(targetServiceName)) {
			targetServiceName = req.getHeader(ProviderConst.HEADER_SERVICELINKAGE_LOWER);
		}
		if (req.getServiceName().equals(targetServiceName)) {
			return null;	// サービス名と同じ場合は指定なし
		}
		return targetServiceName;
	}
	
	/**
	 * サービス連携のサービスキーを取得.
	 * @param req リクエスト
	 * @param targetServiceName 連携サービス名
	 * @return サービス連携のサービスキー
	 */
	public static String getServiceKey(ReflexRequest req, String targetServiceName) {
		if (StringUtils.isBlank(targetServiceName)) {
			return null;	// サービス連携指定なしの場合はサービスキーもnullで返す
		}
		String serviceKey = req.getHeader(ProviderConst.HEADER_SERVICEKEY);
		if (StringUtils.isBlank(serviceKey)) {
			serviceKey = req.getHeader(ProviderConst.HEADER_SERVICEKEY_LOWER);
		}
		return serviceKey;
	}
	
	/**
	 * サービス連携に指定されたサービスチェック
	 * @param targetServiceName 対象サービス
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public static void checkServiceLinkage(String targetServiceName, RequestInfo requestInfo, 
			ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		// サービス稼働チェック
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		boolean isEnabledService = serviceManager.isEnabled(null, targetServiceName,
				requestInfo, connectionInfo);
		if (isEnabledService) {
			// このノードでサービス情報を保持していない場合、設定処理を行う。
			serviceManager.settingServiceIfAbsent(targetServiceName, requestInfo,
					connectionInfo);
		} else {
			// 指定サービスエラー
			throw new NotInServiceException(targetServiceName);
		}
	}

}
