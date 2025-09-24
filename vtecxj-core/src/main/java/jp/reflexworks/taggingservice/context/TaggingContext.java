package jp.reflexworks.taggingservice.context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jakarta.activation.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.AllocateIdsBlogic;
import jp.reflexworks.taggingservice.blogic.BigQueryBlogic;
import jp.reflexworks.taggingservice.blogic.CacheBlogic;
import jp.reflexworks.taggingservice.blogic.ContentBlogic;
import jp.reflexworks.taggingservice.blogic.DatastoreBlogic;
import jp.reflexworks.taggingservice.blogic.EMailBlogic;
import jp.reflexworks.taggingservice.blogic.IncrementBlogic;
import jp.reflexworks.taggingservice.blogic.LogBlogic;
import jp.reflexworks.taggingservice.blogic.LoginLogoutBlogic;
import jp.reflexworks.taggingservice.blogic.MessageQueueBlogic;
import jp.reflexworks.taggingservice.blogic.PaginationBlogic;
import jp.reflexworks.taggingservice.blogic.PdfBlogic;
import jp.reflexworks.taggingservice.blogic.PropertyBlogic;
import jp.reflexworks.taggingservice.blogic.PushNotificationBlogic;
import jp.reflexworks.taggingservice.blogic.RDBBlogic;
import jp.reflexworks.taggingservice.blogic.SDKBlogic;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.blogic.SignatureBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.blogic.WebSocketBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.model.RequestParamInfo;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.GroupUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.NamespaceUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Reflex データ操作クラス.
 */
public class TaggingContext implements ReflexContext {

	/** データストアキャッシュ使用有無 デフォルト値 */
	private static final boolean USECACHE_DEFAULT = false;

	/** 認証情報 */
	private ReflexAuthentication auth;
	/** リクエスト情報 */
	private RequestInfoImpl requestInfo;
	/** コネクション情報 */
	private ConnectionInfo connectionInfo;
	/** current path */
	private String currentpath;

	/** リクエスト (saveFilesで使用) */
	private ReflexRequest req;

	/** 名前空間 */
	private String namespace;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * サービスから呼び出し時に使用するコンストラクタ.
	 * @param req リクエスト
	 * @param isExternal Externalの場合true
	 */
	public TaggingContext(ReflexRequest req, boolean isExternal) {
		// 引数チェック
		CheckUtil.checkArgNull(req, "req");

		// フィールド設定
		this.req = req;
		this.auth = copyAuth(req.getAuth());	// external指定をするため別途生成
		if (auth == null && !StringUtils.isBlank(req.getServiceName())) {
			// 認証エラーの場合はここを通る
			AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
			this.auth = authManager.createAuth(null, null, null, null, req.getServiceName());
		}
		this.requestInfo = (RequestInfoImpl)req.getRequestInfo();
		this.connectionInfo = req.getConnectionInfo();

		// 引数チェック
		CheckUtil.checkArgNull(auth, "req.auth");
		CheckUtil.checkArgNull(requestInfo, "req.requestInfo");
		CheckUtil.checkArgNull(connectionInfo, "req.connectionInfo");
		CheckUtil.checkArgNull(auth.getServiceName(), "req.auth.serviceName");

		// External設定
		this.auth.setExternal(isExternal);
	}

	/**
	 * コンストラクタ.
	 * 管理ユーザで処理を行う場合、このコンストラクタを使用します。
	 * @param req リクエスト
	 * @param auth 認証情報
	 * @param isExternal Externalの場合true
	 */
	public TaggingContext(ReflexRequest req, ReflexAuthentication auth, boolean isExternal) {
		// 引数チェック
		CheckUtil.checkArgNull(req, "req");
		CheckUtil.checkArgNull(auth, "auth");
		CheckUtil.checkArgNull(auth.getServiceName(), "auth.serviceName");

		// フィールド設定
		this.req = req;
		this.auth = copyAuth(auth);	// external指定をするため別途生成
		this.requestInfo = (RequestInfoImpl)req.getRequestInfo();
		this.connectionInfo = req.getConnectionInfo();

		// 引数チェック
		CheckUtil.checkArgNull(requestInfo, "req.requestInfo");
		CheckUtil.checkArgNull(connectionInfo, "req.connectionInfo");

		// External設定
		this.auth.setExternal(isExternal);
	}

	/**
	 * 内部処理で使用するコンストラクタ.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param isExternal Externalの場合true
	 */
	public TaggingContext(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo, boolean isExternal) {
		// 引数チェック
		try {
			CheckUtil.checkArgNull(auth, "auth");
			CheckUtil.checkArgNull(auth.getServiceName(), "auth.serviceName");
			CheckUtil.checkArgNull(requestInfo, "requestInfo");
			CheckUtil.checkArgNull(connectionInfo, "connectionInfo");

		} catch (IllegalParameterException e) {
			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[constructor] Null error occureed. ");
				sb.append(e.getMessage());
				logger.info(sb.toString(), e);
			}
			throw e;
		}

		// フィールド設定
		this.auth = copyAuth(auth);	// external指定をするため別途生成
		this.auth.setExternal(isExternal);
		this.requestInfo = (RequestInfoImpl)requestInfo;
		this.connectionInfo = connectionInfo;
	}

	/**
	 * 認証情報をコピーする.
	 * @param reflexAuth 認証情報
	 * @return コピーした認証情報
	 */
	private ReflexAuthentication copyAuth(ReflexAuthentication reflexAuth) {
		if (reflexAuth == null) {
			return null;
		}
		if (reflexAuth instanceof SystemAuthentication) {
			return new SystemAuthentication(reflexAuth);
		} else {
			AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
			return authManager.copyAuth(reflexAuth);
		}
	}

	/**
	 * 認証情報取得.
	 * @return Reflex内で使用する認証情報
	 */
	public ReflexAuthentication getAuth() {
		return auth;
	}

	/**
	 * UID取得.
	 * @return UID
	 */
	public String getUid() {
		return auth.getUid();
	}

	/**
	 * アカウント取得.
	 * @return アカウント
	 */
	public String getAccount() {
		return auth.getAccount();
	}

	/**
	 * ログインユーザEntryを取得.
	 * @return ログインユーザEntry
	 */
	public FeedBase whoami()
	throws IOException, TaggingException {
		UserBlogic userBlogic = new UserBlogic();
		return userBlogic.whoami(this);
	}

	/**
	 * ログインユーザが参加中のグループリストを取得.
	 * Entryのlinkのhrefにグループをセット
	 * @return ログインユーザが参加中のグループリスト
	 */
	public FeedBase getGroups() {
		List<String> groups = auth.getGroups();
		String serviceName = getServiceName();
		FeedBase feed = null;
		if (groups != null && !groups.isEmpty()) {
			List<EntryBase> entries = new ArrayList<>();
			for (String group : groups) {
				EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
				entry.setMyUri(group);
				entries.add(entry);
			}
			feed = TaggingEntryUtil.createFeed(serviceName);
			feed.entry = entries;
		}
		return feed;
	}
	
	/**
	 * ログインユーザが指定されたグループのメンバーかどうか判定.
	 * @param group グループ
	 * @return ログインユーザが指定されたグループに参加している場合true
	 */
	public boolean isGroupMember(String group) {
		// 引数チェック
		CheckUtil.checkNotNull(group, "group");
		if (auth != null) {
			List<String> groups = auth.getGroups();
			if (groups != null) {
				if (groups.contains(group)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * サービス名取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return auth.getServiceName();
	}

	/**
	 * 対象サービス名取得.
	 * 引数の対象サービス名に指定がない場合、自サービス名を返す。
	 * @param targetService 対象サービス名
	 * @return 対象サービス名
	 */
	public String getTargetService(String targetService) {
		if (StringUtils.isBlank(targetService)) {
			return auth.getServiceName();
		}
		return targetService;
	}

	/**
	 * ResourceMapper取得.
	 * <p>
	 * 起動時に生成したResourceMapperを返却します。
	 * </p>
	 * @return ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper() {
		return TaggingEnvUtil.getResourceMapper(getServiceName());
	}

	/**
	 * ResourceMapper取得.
	 * <p>
	 * 指定されたサービスのResourceMapperを返却します。
	 * </p>
	 * @param targetServiceName 対象サービス
	 * @return ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String targetServiceName)
	throws IOException, TaggingException {
		// 対象サービスの初期設定
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		serviceBlogic.initTargetService(targetServiceName, null, requestInfo,
				connectionInfo);
		// ResourceMapper返却
		return TaggingEnvUtil.getResourceMapper(targetServiceName);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri キー
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri)
	throws IOException, TaggingException {
		return getEntry(requestUri, USECACHE_DEFAULT);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param requestUri キー
	 * @param useCache メモリキャッシュを参照する場合true
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri, boolean useCache)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		param.setOption(RequestParam.PARAM_ENTRY, "");
		return getEntry(param, useCache);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param)
	throws IOException, TaggingException {
		boolean useCache = USECACHE_DEFAULT;
		if (param.getOption(RequestParam.PARAM_NOCACHE) != null) {
			useCache = false;	// データストアキャッシュを使用しない
		} else if (param.getOption(RequestParam.PARAM_USECACHE) != null) {
			useCache = true;	// データストアキャッシュを使用する
		}
		return getEntry(param, useCache);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache データストアキャッシュを参照する場合true
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param, boolean useCache)
	throws IOException, TaggingException {
		return getEntry(param, useCache, null, null);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri キー
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return getEntry(requestUri, USECACHE_DEFAULT, targetServiceName, targetServiceKey);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param requestUri キー
	 * @param useCache データストアキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri, boolean useCache, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getTargetService(targetServiceName));
		param.setOption(RequestParam.PARAM_ENTRY, "");
		return getEntry(param, useCache, targetServiceName, targetServiceKey);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		boolean useCache = USECACHE_DEFAULT;
		if (param.getOption(RequestParam.PARAM_NOCACHE) != null) {
			useCache = false;	// データストアキャッシュを使用しない
		} else if (param.getOption(RequestParam.PARAM_USECACHE) != null) {
			useCache = true;	// データストアキャッシュを使用する
		}
		return getEntry(param, useCache, targetServiceName, targetServiceKey);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache データストアキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param, boolean useCache, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.getEntry(param, useCache, targetServiceName, targetServiceKey, 
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getEntry", param, useCache, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri)
	throws IOException, TaggingException {
		return getFeed(requestUri, USECACHE_DEFAULT);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri, boolean useCache)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		param.setOption(RequestParam.PARAM_FEED, "");
		return getFeed(param, useCache);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param)
	throws IOException, TaggingException {
		boolean useCache = USECACHE_DEFAULT;
		if (param.getOption(RequestParam.PARAM_NOCACHE) != null) {
			useCache = false;	// データストアキャッシュを使用しない
		} else if (param.getOption(RequestParam.PARAM_USECACHE) != null) {
			useCache = true;	// データストアキャッシュを使用する
		}
		return getFeed(param, useCache);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache データストアキャッシュを参照する場合true
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param, boolean useCache)
	throws IOException, TaggingException {
		return getFeed(param, useCache, null, null);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return getFeed(requestUri, USECACHE_DEFAULT, targetServiceName, targetServiceKey);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri, boolean useCache, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getTargetService(targetServiceName));
		param.setOption(RequestParam.PARAM_FEED, "");
		return getFeed(param, useCache, targetServiceName, targetServiceKey);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		boolean useCache = USECACHE_DEFAULT;
		if (param.getOption(RequestParam.PARAM_NOCACHE) != null) {
			useCache = false;	// データストアキャッシュを使用しない
		} else if (param.getOption(RequestParam.PARAM_USECACHE) != null) {
			useCache = true;	// データストアキャッシュを使用する
		}
		return getFeed(param, useCache, targetServiceName, targetServiceKey);
	}

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache データストアキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param, boolean useCache,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.getFeed(param, useCache, targetServiceName, targetServiceKey,
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getFeed", param, useCache, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri)
	throws IOException, TaggingException {
		return getCount(requestUri, USECACHE_DEFAULT);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @param useCache データストアキャッシュを参照する場合true
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri, boolean useCache)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		param.setOption(RequestParam.PARAM_COUNT, "");
		return getCount(param, useCache);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param)
	throws IOException, TaggingException {
		return getCount(param, USECACHE_DEFAULT);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @param useCache データストアキャッシュを参照する場合true
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param, boolean useCache)
	throws IOException, TaggingException {
		return getCount(param, useCache, null, null);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return getCount(requestUri, USECACHE_DEFAULT, targetServiceName, targetServiceKey);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @param useCache データストアキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri, boolean useCache, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getTargetService(targetServiceName));
		param.setOption(RequestParam.PARAM_COUNT, "");
		return getCount(param, useCache, targetServiceName, targetServiceKey);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return getCount(param, USECACHE_DEFAULT, targetServiceName, targetServiceKey);
	}

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param, boolean useCache, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.getCount(param, useCache, targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getCount", param, useCache, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 採番処理.
	 * <p>
	 * キーに対して、指定された採番数だけ番号を採番します。<br>
	 * 採番数は正の数のみ指定可です。<br>
	 * 戻り値はFeed形式で、titleに採番された値が設定されます。複数の場合はカンマで区切られます。<br>
	 * </p>
	 * @param uri キー
	 * @param num 採番数。
	 * @return 採番された値(titleにセット)。複数の場合はカンマでつながれます。
	 */
	public FeedBase allocids(String uri, int num)
	throws IOException, TaggingException {
		return allocids(uri, num, null, null);
	}

	/**
	 * 採番処理.
	 * <p>
	 * キーに対して、指定された採番数だけ番号を採番します。<br>
	 * 採番数は正の数のみ指定可です。<br>
	 * 戻り値はFeed形式で、titleに採番された値が設定されます。複数の場合はカンマで区切られます。<br>
	 * </p>
	 * @param uri キー
	 * @param num 採番数。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 採番された値(titleにセット)。複数の場合はカンマでつながれます。
	 */
	public FeedBase allocids(String uri, int num, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			AllocateIdsBlogic blogic = new AllocateIdsBlogic();
			return blogic.allocids(uri, num, targetServiceName, targetServiceKey, 
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("allocids", uri, num, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 番号の加算処理.
	 * <p>
	 * パラメータで指定した数だけ値をプラスし、現在値を返します。<br>
	 * 加算する数にはマイナスの数値を指定することも可能です。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @param num 加算する数
	 * @return 加算後の現在値
	 */
	public FeedBase addids(String uri, long num)
	throws IOException, TaggingException {
		return addids(uri, num, null, null);
	}

	/**
	 * 番号の加算処理.
	 * <p>
	 * パラメータで指定した数だけ値をプラスし、現在値を返します。<br>
	 * 加算する数にはマイナスの数値を指定することも可能です。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @param num 加算する数
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 加算後の現在値
	 */
	public FeedBase addids(String uri, long num, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			IncrementBlogic blogic = new IncrementBlogic();
			return blogic.addids(uri, num, targetServiceName, targetServiceKey, 
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("addids", uri, num, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 加算処理の現在値取得.
	 * <p>
	 * addidsで加算する番号の現在値を返します。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @return addidsで加算する番号の現在値
	 */
	public FeedBase getids(String uri)
	throws IOException, TaggingException {
		return getids(uri, null, null);
	}

	/**
	 * 加算処理の現在値取得.
	 * <p>
	 * addidsで加算する番号の現在値を返します。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return addidsで加算する番号の現在値
	 */
	public FeedBase getids(String uri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return addids(uri, 0, targetServiceName, targetServiceKey);
	}

	/**
	 * インクリメント値を設定.
	 * @param uri インクリメント項目の値設定をしたいEntryのURI
	 * @param value 設定値
	 * @return 設定値 (Feedのtitleに設定)
	 */
	public FeedBase setids(String uri, long value)
	throws IOException, TaggingException {
		return setids(uri, value, null, null);
	}

	/**
	 * インクリメント値を設定.
	 * @param uri インクリメント項目の値設定をしたいEntryのURI
	 * @param value 設定値
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 設定値 (Feedのtitleに設定)
	 */
	public FeedBase setids(String uri, long value, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			IncrementBlogic blogic = new IncrementBlogic();
			return blogic.setids(uri, value, targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setids", uri, value, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 加算枠設定.
	 * <p>
	 * 加算範囲を「{最小値}-{最大値}!」の形式で指定します。<br>
	 * 「-{最大値}!」は任意です。<br>
	 * 加算枠をサイクルしない場合、加算範囲の後ろに!を指定します。<br>
	 * デフォルトは最大値まで加算した場合、最小値に戻って加算を続けます。<br>
	 * 加算枠が設定された場合、現在値を最小値に設定します。<br>
	 * 加算枠がnull・空文字の場合、加算枠を削除します。現在値は変更しません。<br>
	 * </p>
	 * @param uri 採番の初期設定をしたいEntryのURI
	 * @param value 採番初期値。null・空文字の場合は加算枠削除。
	 * @return 設定内容
	 */
	public FeedBase rangeids(String uri, String value)
	throws IOException, TaggingException {
		try {
			IncrementBlogic blogic = new IncrementBlogic();
			return blogic.rangeids(uri, value, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("rangeids", uri, value, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 加算枠取得.
	 * 戻り値はFeed形式で、titleに加算枠が設定されます。<br>
	 * @param uri 加算枠のURI
	 * @return 加算枠
	 */
	public FeedBase getRangeids(String uri)
	throws IOException, TaggingException {
		try {
			IncrementBlogic blogic = new IncrementBlogic();
			return blogic.getRangeids(uri, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getRangeids", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry)
	throws IOException, TaggingException {
		return post(entry, (RequestParam)null);
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param param リクエストパラメータオブジェクト
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, RequestParam param)
	throws IOException, TaggingException {
		FeedBase feed = TaggingEntryUtil.createFeed(getServiceName(), entry);
		FeedBase retFeed = post(feed, param);
		return TaggingEntryUtil.getFirstEntry(retFeed);
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param uri 自動採番の場合、上位階層を指定。
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, String uri)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(uri, getServiceName());
		return post(entry, param);
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param uri 自動採番の場合、上位階層を指定。
	 * @param ext 自動採番の場合、末尾につける拡張子を指定。
	 * @return 登録したEntry
	 */
	public EntryBase postWithExtension(EntryBase entry, String uri, String ext)
	throws IOException, TaggingException {
		String tmpUri = null;
		if (!StringUtils.isBlank(ext)) {
			tmpUri = UrlUtil.addParam(uri, RequestParam.PARAM_EXT, ext);
		} else {
			tmpUri = uri;
		}
		RequestParam param = new RequestParamInfo(tmpUri, getServiceName());
		return post(entry, param);
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed)
	throws IOException, TaggingException {
		return post(feed, (RequestParam)null);
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param param リクエストパラメータオブジェクト
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, RequestParam param)
	throws IOException, TaggingException {
		return post(feed, param, null, null);
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param uri 自動採番の場合、親階層を指定。
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String uri)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(uri, getServiceName());
		return post(feed, param);
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param uri 自動採番の場合、親階層を指定。
	 * @param ext 自動採番の場合、末尾につける拡張子を指定。
	 * @return 登録したFeed
	 */
	public FeedBase postWithExtension(FeedBase feed, String uri, String ext)
	throws IOException, TaggingException {
		String tmpUri = null;
		if (!StringUtils.isBlank(ext)) {
			tmpUri = UrlUtil.addParam(uri, RequestParam.PARAM_EXT, ext);
		} else {
			tmpUri = uri;
		}
		RequestParam param = new RequestParamInfo(tmpUri, getServiceName());
		return post(feed, param);
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return post(entry, (RequestParam)null, targetServiceName, targetServiceKey);
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param param リクエストパラメータオブジェクト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		FeedBase feed = TaggingEntryUtil.createFeed(getServiceName(), entry);
		FeedBase retFeed = post(feed, param, targetServiceName, targetServiceKey);
		return TaggingEntryUtil.getFirstEntry(retFeed);
	}

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param uri 自動採番の場合、上位階層を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, String uri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(uri, getTargetService(targetServiceName));
		return post(entry, param, targetServiceName, targetServiceKey);
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return post(feed, (RequestParam)null, targetServiceName, targetServiceKey);
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param param リクエストパラメータオブジェクト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, RequestParam param, 
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		String uri = null;
		String ext = null;
		if (param != null) {
			uri = param.getUri();
			ext = param.getOption(RequestParam.PARAM_EXT);
		}
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.post(feed, uri, ext, targetServiceName, targetServiceKey,
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("post", feed, uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param uri 自動採番の場合、親階層を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String uri, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(uri, getTargetService(targetServiceName));
		return post(feed, param, targetServiceName, targetServiceKey);
	}

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry)
	throws IOException, TaggingException {
		return put(entry, (RequestParam)null);
	}

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param requestUri パラメータ
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, String requestUri)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		return put(entry, param);
	}

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param param パラメータ
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, RequestParam param)
	throws IOException, TaggingException {
		FeedBase feed = TaggingEntryUtil.createFeed(getServiceName(), entry);
		FeedBase retFeed = put(feed, param);
		return TaggingEntryUtil.getFirstEntry(retFeed);
	}

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed)
	throws IOException, TaggingException {
		return put(feed, (RequestParam)null);
	}

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String requestUri)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		return put(feed, param);
	}

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, RequestParam param)
	throws IOException, TaggingException {
		return put(feed, param, null, null);
	}

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return put(entry, (RequestParam)null, targetServiceName, targetServiceKey);
	}

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param requestUri パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, String requestUri, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getTargetService(targetServiceName));
		return put(entry, param, targetServiceName, targetServiceKey);
	}

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param param パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		FeedBase feed = TaggingEntryUtil.createFeed(getServiceName(), entry);
		FeedBase retFeed = put(feed, param, targetServiceName, targetServiceKey);
		return TaggingEntryUtil.getFirstEntry(retFeed);
	}

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return put(feed, (RequestParam)null, targetServiceName, targetServiceKey);
	}

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String requestUri, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getTargetService(targetServiceName));
		return put(feed, param, targetServiceName, targetServiceKey);
	}

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		String uri = null;
		boolean updateAllIndex = false;
		if (param != null) {
			uri = param.getUri();
			updateAllIndex = param.getOption(RequestParam.PARAM_UPDATEALLINDEX) != null;
		}
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.put(feed, uri, updateAllIndex, targetServiceName, targetServiceKey,
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("put", feed, uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return bulkPut(feed, (RequestParam)null, async);
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, String requestUri, boolean async)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		return bulkPut(feed, param, async);
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, RequestParam param, boolean async)
	throws IOException, TaggingException {
		return bulkPut(feed, param, async, null, null);
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, RequestParam param, boolean async, 
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		String uri = null;
		boolean updateAllIndex = false;
		if (param != null) {
			uri = param.getUri();
			updateAllIndex = param.getOption(RequestParam.PARAM_UPDATEALLINDEX) != null;
		}
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.bulkPut(feed, uri, async, updateAllIndex, 
					targetServiceName, targetServiceKey,
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("bulkPut", feed, uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return bulkSerialPut(feed, (RequestParam)null, async);
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, String requestUri, boolean async)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(requestUri, getServiceName());
		return bulkSerialPut(feed, param, async);
	}

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, RequestParam param, boolean async)
	throws IOException, TaggingException {
		return bulkSerialPut(feed, param, async, null, null);
	}
	
	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, RequestParam param, boolean async, 
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		String uri = null;
		boolean updateAllIndex = false;
		if (param != null) {
			uri = param.getUri();
			updateAllIndex = param.getOption(RequestParam.PARAM_UPDATEALLINDEX) != null;
		}
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.bulkSerialPut(feed, uri, async, updateAllIndex, 
					targetServiceName, targetServiceKey, 
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("bulkSerialPut", feed, uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Entry削除.
	 * @param uri キー
	 * @param revision リビジョン(楽観的排他チェックに使用)
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String uri, int revision)
	throws IOException, TaggingException {
		return delete(uri, revision, null, null);
	}

	/**
	 * Entry削除.
	 * @param id IDまたはURI
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String id)
	throws IOException, TaggingException {
		return delete(id, null, null);
	}

	/**
	 * Entry削除.
	 * @param param リクエストパラメータオブジェクト
	 * @return 削除されたEntry
	 */
	public EntryBase delete(RequestParam param)
	throws IOException, TaggingException {
		return delete(param, null, null);
	}

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param ids IDリスト
	 */
	public FeedBase delete(List<String> ids)
	throws IOException, TaggingException {
		return delete(ids, null, null);
	}

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param feed Feed
	 */
	public FeedBase delete(FeedBase feed)
	throws IOException, TaggingException {
		return delete(feed, null, null);
	}

	/**
	 * Entry削除.
	 * @param uri キー
	 * @param revision リビジョン(楽観的排他チェックに使用)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String uri, int revision, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		String id = TaggingEntryUtil.createId(uri, revision);
		return delete(id, targetServiceName, targetServiceKey);
	}

	/**
	 * Entry削除.
	 * @param id IDまたはURI
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String id, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		// パラメータ指定かどうかチェック
		if (id != null && id.indexOf("?") > -1) {
			RequestParam param = new RequestParamInfo(id, getTargetService(targetServiceName));
			return delete(param);
		}

		List<String> ids = null;
		if (!StringUtils.isBlank(id)) {
			ids = new ArrayList<String>();
			ids.add(id);
		}
		FeedBase retFeed = delete(ids, targetServiceName, targetServiceKey);
		return TaggingEntryUtil.getFirstEntry(retFeed);
	}

	/**
	 * Entry削除.
	 * @param param リクエストパラメータオブジェクト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたEntry
	 */
	public EntryBase delete(RequestParam param, 
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		String uri = null;
		String revision = null;
		if (param != null) {
			uri = param.getUri();
			// folder削除かどうかチェック
			if (param.getOption(RequestParam.PARAM_RF) != null) {
				boolean async = param.getOption(RequestParam.PARAM_ASYNC) != null;
				boolean isParallel = true;
				deleteFolder(param, async, isParallel);
				return null;
			}
			revision = param.getOption(RequestParam.PARAM_REVISION);
		}
		String id = TaggingEntryUtil.getId(uri, revision);
		List<String> ids = new ArrayList<String>();
		if (!StringUtils.isBlank(id)) {
			ids.add(id);
		}
		FeedBase retFeed = delete(ids, targetServiceName, targetServiceKey);
		return TaggingEntryUtil.getFirstEntry(retFeed);
	}

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param ids IDリスト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたFeed
	 */
	public FeedBase delete(List<String> ids, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.delete(ids, targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("delete", ids, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param feed Feed
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたFeed
	 */
	public FeedBase delete(FeedBase feed, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		// 引数のidに?_deleteを付加する。
		String tmpServiceName = auth.getServiceName();
		FeedBase paramFeed = editIdToDelete(feed, tmpServiceName);
		return put(paramFeed, targetServiceName, targetServiceKey);
	}
	
	/**
	 * deleteメソッド実行時、Feedの各Entryのidに"?_delete"を付加する。
	 * @param feed Feed
	 * @param serviceName サービス名
	 * @return 編集したFeed
	 */
	private FeedBase editIdToDelete(FeedBase feed, String serviceName) {
		FeedBase paramFeed = TaggingEntryUtil.createFeed(serviceName);
		if (feed != null) {
			for (EntryBase entry : feed.entry) {
				StringBuilder sb = new StringBuilder();
				boolean isFirst = true;
				if (entry.id != null) {
					sb.append(entry.id);
					if (entry.id.indexOf("?") > -1) {
						isFirst = false;
					}
				}
				if (isFirst) {
					sb.append("?");
				} else {
					sb.append("&");
				}
				sb.append(RequestParam.PARAM_DELETE);

				FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
				EntryBase paramEntry = TaggingEntryUtil.copyEntry(entry, mapper);
				paramEntry.id = sb.toString();
				paramFeed.addEntry(paramEntry);
			}
		}
		return paramFeed;
	}

	/**
	 * Feed一括削除.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public List<Future<List<UpdatedInfo>>> bulkDelete(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		FeedBase paramFeed = editIdToDelete(feed, getServiceName());
		return bulkPut(paramFeed, async);
	}

	/**
	 * フォルダ削除.
	 * <p>
	 * 指定された親階層とその配下のデータをまとめて削除します。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param uri 上位階層
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(String uri, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(uri, getServiceName());
		return deleteFolder(param, async, isParallel);
	}

	/**
	 * フォルダ削除.
	 * <p>
	 * 指定された親階層とその配下のデータをまとめて削除します。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(RequestParam param, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		return deleteFolder(param, async, isParallel, null, null);
	}

	/**
	 * フォルダ削除.
	 * <p>
	 * 指定された親階層とその配下のデータをまとめて削除します。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(RequestParam param, boolean async, 
			boolean isParallel, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.deleteFolder(param.getUri(), false, async, isParallel,
					targetServiceName, targetServiceKey,
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteFolder", param, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * フォルダクリア.
	 * <p>
	 * 指定された親階層配下のデータをまとめて削除します。親階層自体は削除しません。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param uri 上位階層
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future clearFolder(String uri, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		RequestParam param = new RequestParamInfo(uri, getServiceName());
		return clearFolder(param, async, isParallel);
	}

	/**
	 * フォルダクリア.
	 * <p>
	 * 指定された親階層配下のデータをまとめて削除します。親階層自体は削除しません。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future clearFolder(RequestParam param, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		return clearFolder(param, async, isParallel, null, null);
	}

	/**
	 * フォルダクリア.
	 * <p>
	 * 指定された親階層配下のデータをまとめて削除します。親階層自体は削除しません。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Future 同期の場合はnull
	 */
	public Future clearFolder(RequestParam param, boolean async, 
			boolean isParallel, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.deleteFolder(param.getUri(), true, async, isParallel,
					targetServiceName, targetServiceKey,
					auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("clearFolder", param, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテント登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * キーは PathInfo + ファイル名 が設定されます。(multipart/formdata の場合) <br>
	 * multipartでない場合、PathInfoの値がそのままファイル名になります。
	 * </p>
	 * @return ファイル名のリスト
	 */
	public FeedBase putContent()
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.putContent(this, false, false);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putContent", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテントを設定サイズにリサイズして登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * キーは PathInfo + ファイル名 が設定されます。(multipart/formdata の場合) <br>
	 * multipartでない場合、PathInfoの値がそのままファイル名になります。
	 * </p>
	 * @return ファイル名のリスト
	 */
	public FeedBase putContentBySize()
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.putContent(this, true, false);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putContentBySize", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * multipart/formdata のファイルをコンテント登録します.
	 * <p>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * </p>
	 * @param namesAndKeys キー: uri、値: key
	 * @return ファイル名のリスト
	 *         feedのlinkリストにキーとContent-Typeを設定して返却します.
	 */
	public FeedBase putContent(Map<String, String> namesAndKeys)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.putMultipartContent(this, namesAndKeys, false);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putContent", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテント登録のための署名付きURL取得.
	 * @return feed.titleに署名付きURL
	 */
	public FeedBase putContentSignedUrl()
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.putContent(this, false, true);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putContentSignedUrl", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテント自動採番登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * selfidは自動採番され、引数の親キーと合わせてキーが生成されます。<br>
	 * </p>
	 * @param parentUri 親キー
	 * @return コンテントエントリー
	 */
	public FeedBase postContent(String parentUri)
	throws IOException, TaggingException {
		return postContent(parentUri, null);
	}

	/**
	 * コンテント自動採番登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * selfidは自動採番され、引数の親キーと合わせてキーが生成されます。<br>
	 * </p>
	 * @param parentUri 親キー
	 * @param ext 拡張子
	 * @return コンテントエントリー
	 */
	public FeedBase postContent(String parentUri, String ext)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.postContent(this, parentUri, ext, false);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("postContent", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテント自動採番登録のための署名付きURL取得.
	 * @return feed.titleに署名付きURL
	 */
	public FeedBase postContentSignedUrl(String parentUri, String ext)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.postContent(this, parentUri, ext, true);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("postContentSignedUrl", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * multipart/formdata のファイルを、設定サイズにリサイズしてコンテント登録します.
	 * <p>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * </p>
	 * @param namesAndKeys キー: uri、値: key
	 * @return ファイル名のリスト
	 *         feedのlinkリストにキーとContent-Typeを設定して返却します.
	 */
	public FeedBase putContentBySize(Map<String, String> namesAndKeys)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.putMultipartContent(this, namesAndKeys, true);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putContentBySize", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテンツを取得.
	 * コンテンツ登録先にアクセスし、データ本体、ヘッダ情報を取得します。
	 * このメソッドではEtagチェックを行わず、コンテンツ本体を取得します。
	 * @param uri URI
	 * @return コンテンツ情報 (データ本体とヘッダ情報)
	 *         存在しない場合はnullを返します。
	 */
	public ReflexContentInfo getContent(String uri)
	throws IOException, TaggingException {
		return getContent(uri, false);
	}

	/**
	 * コンテンツを取得.
	 * コンテンツ登録先にアクセスし、データ本体、ヘッダ情報を取得します。
	 * @param uri URI
	 * @param checkEtag Etagチェックを行う場合true
	 *                  リクエストのEtagが等しい場合、コンテンツ本体を返さずEtagのみ返します。
	 * @return コンテンツ情報 (データ本体とヘッダ情報)
	 *         存在しない場合はnullを返します。
	 */
	public ReflexContentInfo getContent(String uri, boolean checkEtag)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.getContent(uri, checkEtag, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getContent", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテンツを取得.
	 * コンテンツ登録先にアクセスし、データ本体、ヘッダ情報を取得します。
	 * このメソッドではEtagチェックを行わず、コンテンツ本体を取得します。
	 * @param uri URI
	 * @return コンテンツ情報 (データ本体とヘッダ情報)
	 *         存在しない場合はnullを返します。
	 */
	public FeedBase getContentSignedUrl(String uri)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.getContentSignedUrl(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getContentSignedUrl", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * コンテンツを削除.
	 * @return コンテンツ削除後のEntry
	 */
	public EntryBase deleteContent(String uri)
	throws IOException, TaggingException {
		try {
			ContentBlogic blogic = new ContentBlogic();
			return blogic.deleteContent(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteContent", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Contentを返す仮メソッド
	 * キーの先頭に"/_html"を付加する。
	 * @param requestUri URI
	 * @return コンテンツデータのバイト配列
	 */
	public byte[] getHtmlContent(String requestUri)
	throws IOException, TaggingException {
		String uri = requestUri;

		if (uri.startsWith("/")) {
			currentpath = getCurrentPath(uri);
		} else {
			uri = currentpath + uri;
		}

		CheckUtil.checkUri(uri);
		if (!uri.startsWith(Constants.URI_SYSTEM_MANAGER) &&
				!uri.startsWith(TaggingEntryUtil.URI_HTML_PREFIX)) {
			uri = AtomConst.URI_HTML + uri;
		}
		ReflexContentInfo contentInfo = getContent(uri);
		if (contentInfo != null) {
			return contentInfo.getData();
		}
		return null;
	}

	/**
	 * currentpathを取得.
	 * @param filepath filepath
	 * @return currentpath
	 */
	private String getCurrentPath(String filepath) {
		return filepath.substring(0, filepath.lastIndexOf("/") + 1);
	}

	/**
	 * リクエストを取得.
	 * @return リクエスト
	 */
	public ReflexRequest getRequest() {
		return req;
	}

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public ConnectionInfo getConnectionInfo() {
		return connectionInfo;
	}

	/**
	 * リクエスト情報を取得.
	 * @return リクエスト情報
	 */
	public RequestInfo getRequestInfo() {
		return requestInfo;
	}

	/**
	 * RXIDを取得.
	 * @return RXID
	 */
	public String getRXID() throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.createRXID(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getRXID", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * アクセストークンを取得
	 * @return アクセストークン
	 */
	public String getAccessToken() throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.getAccessToken(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getAccessToken", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * リンクトークンを取得
	 * @param uri URI
	 * @return リンクトークン
	 */
	public String getLinkToken(String uri)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.getLinkToken(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getLinkToken", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * アクセストークンを取得
	 * @return アクセストークン
	 */
	public void changeAccessKey() throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			userBlogic.changeAccessKey(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("changeAccessKey", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * サービス管理者権限のReflexContextを取得
	 * @return サービス管理者権限のReflexContext
	 */
	public ReflexContext getServiceAdminContext() {
		// 実装なし
		return null;
	}

	/**
	 * キャッシュにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param feed Feed
	 * @return 登録したFeed
	 */
	public FeedBase setCacheFeed(String uri, FeedBase feed)
	throws IOException, TaggingException {
		return setCacheFeed(uri, feed, null);
	}

	/**
	 * キャッシュにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param feed Feed
	 * @param sec 有効時間(秒)
	 * @return 登録したFeed
	 */
	public FeedBase setCacheFeed(String uri, FeedBase feed, Integer sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setFeed(uri, feed, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheFeed", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * データが存在しない場合のみキャッシュにFeedを登録.
	 * @param uri 登録キー
	 * @param feed Feed
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheFeedIfAbsent(String uri, FeedBase feed)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setFeedIfAbsent(uri, feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheFeedIfAbsent", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param entry Entry
	 * @return 登録したFeed
	 */
	public EntryBase setCacheEntry(String uri, EntryBase entry)
	throws IOException, TaggingException {
		return setCacheEntry(uri, entry, null);
	}

	/**
	 * キャッシュにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param entry Entry
	 * @param sec 有効時間(秒)
	 * @return 登録したFeed
	 */
	public EntryBase setCacheEntry(String uri, EntryBase entry, Integer sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setEntry(uri, entry, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheEntry", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * データが存在しない場合のみキャッシュにEntryを登録.
	 * @param uri 登録キー
	 * @param entry Entry
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheEntryIfAbsent(String uri, EntryBase entry)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setEntryIfAbsent(uri, entry, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheEntryIfAbsent", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param text 文字列
	 * @return 登録した文字列
	 */
	public String setCacheString(String uri, String text)
	throws IOException, TaggingException {
		return setCacheString(uri, text, null);
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param text 文字列
	 * @param sec 有効時間(秒)
	 * @return 登録した文字列
	 */
	public String setCacheString(String uri, String text, Integer sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setString(uri, text, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheString", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param uri 登録キー
	 * @param text 文字列
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheStringIfAbsent(String uri, String text)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setStringIfAbsent(uri, text, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheStringIfAbsent", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param num 数値
	 * @return 登録した文字列
	 */
	public long setCacheLong(String uri, long num)
	throws IOException, TaggingException {
		return setCacheLong(uri, num, null);
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param num 数値
	 * @param sec 有効時間(秒)
	 * @return 登録した文字列
	 */
	public long setCacheLong(String uri, long num, Integer sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setLong(uri, num, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheLong", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param uri 登録キー
	 * @param num 数値
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheLongIfAbsent(String uri, long num)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setLongIfAbsent(uri, num, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setCacheLongIfAbsent", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュからFeedを削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheFeed(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.deleteFeed(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteCacheFeed", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュからEntryを削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheEntry(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.deleteEntry(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteCacheEntry", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュから文字列を削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheString(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.deleteString(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteCacheString", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュから整数値を削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheLong(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.deleteLong(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteCacheLong", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュからFeedを取得.
	 * @param uri 登録キー
	 * @return Feed
	 */
	public FeedBase getCacheFeed(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.getFeed(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getCacheFeed", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュからEntryを取得.
	 * @param uri 登録キー
	 * @return Entry
	 */
	public EntryBase getCacheEntry(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.getEntry(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getCacheEntry", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュから文字列を取得.
	 * @param uri 登録キー
	 * @return 文字列
	 */
	public String getCacheString(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.getString(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getCacheString", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュから整数値を取得.
	 * @param uri 登録キー
	 * @return 整数値
	 */
	public Long getCacheLong(String uri)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.getLong(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getCacheLong", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Feedキャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheFeed(String uri, int sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setExpireFeed(uri, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setExpireCacheFeed", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Entryキャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheEntry(String uri, int sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setExpireEntry(uri, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setExpireCacheEntry", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 文字列キャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheString(String uri, int sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setExpireString(uri, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setExpireCacheString", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 文字列キャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheLong(String uri, int sec)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.setExpireLong(uri, sec, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setExpireCacheLong", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * キャッシュに指定された値を加算.
	 * @param uri キー
	 * @param num 加算値
	 * @return 加算後の値
	 */
	public long incrementCache(String uri, long num)
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.increment(uri, num, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("incrementCache", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * Redisの全てのデータを削除.
	 * メンテナンス用
	 * @return 削除できた場合true
	 */
	public boolean cacheFlushAll()
	throws IOException, TaggingException {
		try {
			CacheBlogic blogic = new CacheBlogic();
			return blogic.flushAll(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("cacheFlushAll", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param feed Feed
	 * @return 登録したFeed
	 */
	public FeedBase setSessionFeed(String name, FeedBase feed)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.setFeed(name, feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setSessionFeed", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param entry Entry
	 * @return 登録したEntry
	 */
	public EntryBase setSessionEntry(String name, EntryBase entry)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.setEntry(name, entry, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setSessionEntry", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param text 文字列
	 * @return 登録した文字列
	 */
	public String setSessionString(String name, String text)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.setString(name, text, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setSessionString", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションに文字列を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name 登録キー
	 * @param text 文字列
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public String setSessionStringIfAbsent(String name, String text)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.setStringIfAbsent(name, text, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setSessionString", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションに数値を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param num 数値
	 * @return 登録した数値
	 */
	public long setSessionLong(String name, long num)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.setLong(name, num, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setSessionLong", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションに数値を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name 登録キー
	 * @param num 数値
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public Long setSessionLongIfAbsent(String name, long num)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.setLongIfAbsent(name, num, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("setLongIfAbsent", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションの指定されたキーの数値に値を加算.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param num 数値
	 * @return 加算後の数値
	 */
	public long incrementSession(String name, long num)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.increment(name, num, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("incrementSession", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションからFeedを削除.
	 * @param name 登録キー
	 */
	public void deleteSessionFeed(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			blogic.deleteFeed(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteSessionFeed", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションからEntryを削除.
	 * @param name 登録キー
	 */
	public void deleteSessionEntry(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			blogic.deleteEntry(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteSessionEntry", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションから文字列を削除.
	 * @param name 登録キー
	 */
	public void deleteSessionString(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			blogic.deleteString(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteSessionString", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションから数値を削除.
	 * @param name 登録キー
	 */
	public void deleteSessionLong(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			blogic.deleteLong(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteSessionLong", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションからFeedを取得.
	 * @param name 登録キー
	 * @return Feed
	 */
	public FeedBase getSessionFeed(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getFeed(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionFeed", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションからEntryを取得.
	 * @param name 登録キー
	 * @return Entry
	 */
	public EntryBase getSessionEntry(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getEntry(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionEntry", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションから文字列を取得.
	 * @param name 登録キー
	 * @return 文字列
	 */
	public String getSessionString(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getString(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionString", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションから数値を取得.
	 * @param name 登録キー
	 * @return 数値
	 */
	public Long getSessionLong(String name)
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getLong(name, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionLong", name, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションへのFeed格納キー一覧を取得.
	 * @return セッションへのFeed格納キーリスト
	 */
	public List<String> getSessionFeedKeys()
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getFeedKeys(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionFeedKeys", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションへのEntry格納キー一覧を取得.
	 * @return セッションへのEntry格納キーリスト
	 */
	public List<String> getSessionEntryKeys()
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getEntryKeys(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionEntryKeys", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションへの文字列格納キー一覧を取得.
	 * @return セッションへの文字列格納キーリスト
	 */
	public List<String> getSessionStringKeys()
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getStringKeys(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionStringKeys", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションへの数値格納キー一覧を取得.
	 * @return セッションへの数値格納キーリスト
	 */
	public List<String> getSessionLongKeys()
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getLongKeys(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionLongKeys", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションへの格納キー一覧を取得.
	 * @return セッションへの値格納キーリスト。
	 *         キー: Feed, Entry, String, Longのいずれか
	 *         値: キーリスト
	 */
	public Map<String, List<String>> getSessionKeys()
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			return blogic.getKeys(this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getSessionKeys", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * セッションの有効時間を延ばす
	 */
	public void resetExpire()
	throws IOException, TaggingException {
		try {
			SessionBlogic blogic = new SessionBlogic();
			blogic.resetExpire(auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("resetExpire", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 署名検証.
	 * @param uri キー
	 * @return 署名が正しい場合true
	 */
	public boolean checkSignature(String uri)
	throws IOException, TaggingException {
		try {
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			return signatureBlogic.check(uri, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("checkSignature", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 署名設定.
	 * すでに署名が設定されている場合は更新します。
	 * @param uri
	 * @param revision リビジョン
	 * @return 署名したEntry
	 */
	public EntryBase putSignature(String uri, Integer revision)
	throws IOException, TaggingException {
		try {
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			return signatureBlogic.put(uri, revision, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putSignature", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 署名設定.
	 * link rel="self"に署名をします。
	 * Entryが存在しない場合は登録します。
	 * @param feed 署名対象Entryリスト
	 * @return 署名したEntryリスト
	 */
	public FeedBase putSignatures(FeedBase feed)
	throws IOException, TaggingException {
		try {
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			return signatureBlogic.put(feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msgUri = null;
			if (feed != null && feed.entry != null) {
				StringBuilder sb = new StringBuilder();
				boolean isFirst = true;
				for (EntryBase entry : feed.entry) {
					if (entry != null) {
						if (isFirst) {
							isFirst = false;
						} else {
							sb.append(",");
						}
						sb.append(entry.getMyUri());
					}
				}
				msgUri = sb.toString();
			}
			String msg = getErrorMessage("putSignatures", msgUri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 署名削除.
	 * @param uri
	 * @param revision リビジョン
	 * @return メッセージ
	 */
	public void deleteSignature(String uri, Integer revision)
	throws IOException, TaggingException {
		try {
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			signatureBlogic.delete(uri, revision, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteSignature", uri, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 管理者によるユーザ登録.
	 * @param feed 登録ユーザ情報.
	 */
	public FeedBase adduserByAdmin(FeedBase feed)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.adduserByAdmin(feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("adduserByAdmin", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * グループ管理者によるユーザ登録.
	 * @param feed 登録ユーザ情報
	 * @param groupName グループ名
	 */
	public FeedBase adduserByGroupadmin(FeedBase feed, String groupName)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.adduserByGroupadmin(feed, groupName, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("adduserByGroupadmin", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 管理者によるパスワード更新.
	 * @param feed パスワード更新情報
	 * @return 更新情報
	 */
	public FeedBase changepassByAdmin(FeedBase feed)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.changepassByAdmin(feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("adduserByAdmin", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * グループ管理者登録.
	 * @param feed 登録ユーザ情報.
	 * @return グループエントリーリスト
	 */
	public FeedBase createGroupadmin(FeedBase feed)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.createGroupadmin(feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("createGroupadmin", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * グループ管理用グループを削除する.
	 * @param groupName グループ管理用グループ
	 * @param async 削除を非同期に行う場合true
	 */
	@Override
	public void deleteGroupadmin(String groupName, boolean async)
	throws IOException, TaggingException {
		String serviceName = getServiceName();
		try {
			CheckUtil.checkNotNull(groupName, "group name");
			FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
			EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
			String groupUri = GroupUtil.getGroupUri(groupName);
			entry.setMyUri(groupUri);
			feed.addEntry(entry);
			deleteGroupadmin(feed, async);

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteGroupadmin", groupName, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * グループ管理用グループを削除する.
	 * @param feed グループ情報
	 * @param async 削除を非同期に行う場合true
	 */
	@Override
	public void deleteGroupadmin(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			userBlogic.deleteGroupadmin(feed, async, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteGroupadmin", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ログを出力します.
	 * <p>
	 * ログエントリー ( /_log/xxxx ) に指定したメッセージを登録します。<br>
	 * タグに指定した値はtitle、サブタイトルに指定した値はsubtitle、
	 * メッセージに指定した値はsummaryに登録されます。<br>
	 * </p>
	 * @param title タグ
	 * @param subtitle サブタイトル
	 * @param message メッセージ
	 */
	public void log(String title, String subtitle, String message) {
		try {
			String serviceName = null;
			if (auth != null) {
				serviceName = auth.getServiceName();
			}
			if (StringUtils.isBlank(serviceName)) {
				serviceName = requestInfo.getServiceName();
			}
			LogBlogic logBlogic = new LogBlogic();
			logBlogic.writeLogEntry(title, subtitle, message, serviceName,
					requestInfo, connectionInfo);
		} catch (RuntimeException | Error e) {
			String msg = getErrorMessage("log", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ログを出力します.
	 * <p>
	 * ログエントリー ( /_log/xxxx ) に指定したメッセージを登録します。<br>
	 * タグに指定した値はtitle、サブタイトルに指定した値はsubtitle、
	 * メッセージに指定した値はsummaryに登録されます。<br>
	 * </p>
	 * @param title タグ
	 * @param subtitle サブタイトル
	 * @param message メッセージ
	 */
	public void log(FeedBase feed)
	throws IOException, TaggingException {
		try {
			LogBlogic logBlogic = new LogBlogic();
			logBlogic.writeLogEntry(feed, auth, requestInfo, connectionInfo);
		} catch (RuntimeException | Error e) {
			String msg = getErrorMessage("log", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * サービス固有の設定値を返却.
	 *   /_settings/properties に設定された値のみ参照し返却.
	 * @param key キー
	 * @return 設定値
	 */
	public String getSettingValue(String key) {
		try {
			PropertyBlogic propertyBlogic = new PropertyBlogic();
			return propertyBlogic.getSettingValue(getServiceName(), key);
		} catch (RuntimeException | Error e) {
			String msg = getErrorMessage("getSettingValue", key, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * テキストメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param to  送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendMail(String title, String textMessage, String to,
			List<DataSource> attachments)
	throws IOException, TaggingException {
		try {
			EMailBlogic emailBlogic = new EMailBlogic();
			emailBlogic.sendMail(title, textMessage, null, attachments, to, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("sendMail", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * テキストメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param to  送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendMail(String title, String textMessage,
			String[] to, String[] cc, String[] bcc, List<DataSource> attachments)
	throws IOException, TaggingException {
		try {
			EMailBlogic emailBlogic = new EMailBlogic();
			emailBlogic.sendMail(title, textMessage, null, attachments, to, cc, bcc,
					this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("sendMail", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * HTMLメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 *                    (インライン画像は<IMG src="cid:{コンテンツのキー}">を指定する。)
	 * @param to  送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendHtmlMail(String title, String textMessage, String htmlMessage,
			String to, List<DataSource> attachments)
	throws IOException, TaggingException {
		try {
			EMailBlogic emailBlogic = new EMailBlogic();
			emailBlogic.sendMail(title, textMessage, htmlMessage, attachments, to, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("sendHtmlMail", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * HTMLメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 *                    (インライン画像は<IMG src="cid:{コンテンツのキー}">を指定する。)
	 * @param to  送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendHtmlMail(String title, String textMessage, String htmlMessage,
			String[] to, String[] cc, String[] bcc, List<DataSource> attachments)
	throws IOException, TaggingException {
		try {
			EMailBlogic emailBlogic = new EMailBlogic();
			emailBlogic.sendMail(title, textMessage, htmlMessage, attachments,
					to, cc, bcc, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("sendHtmlMail", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * メール送信 (テキスト・HTML).
	 * @param entry メールの送信内容
	 *              title : メールのタイトル
	 *              summary : テキストメッセージ
	 *              content : HTMLメッセージ
	 * @param to  送信先アドレス
	 * @param attachments 添付ファイルのコンテンツキーリスト
	 */
	public void sendMail(EntryBase entry, String to, String[] attachments)
	throws IOException, TaggingException {
		try {
			EMailBlogic emailBlogic = new EMailBlogic();
			emailBlogic.sendMail(entry, attachments, to, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("sendMail", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * メール送信 (テキスト・HTML).
	 * @param entry メールの送信内容
	 *              title : メールのタイトル
	 *              summary : テキストメッセージ
	 *              content : HTMLメッセージ
	 * @param to  送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param attachments 添付ファイルのコンテンツキーリスト
	 */
	public void sendMail(EntryBase entry, String[] to,
			String[] cc, String[] bcc, String[] attachments)
	throws IOException, TaggingException {
		try {
			EMailBlogic emailBlogic = new EMailBlogic();
			emailBlogic.sendMail(entry, attachments, to, cc, bcc, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("sendMail", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param requestUri キー+検索条件
	 * @param pageNum カーソルリスト作成ページ
	 *                "最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(String requestUri, String pageNum)
	throws IOException, TaggingException {
		return pagination(requestUri, pageNum, null, null);
	}

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param param 検索条件、カーソルリスト作成ページ
	 *              カーソルリスト作成ページは"最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(RequestParam param)
	throws IOException, TaggingException {
		return pagination(param, null, null);
	}

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param requestUri キー+検索条件
	 * @param pageNum カーソルリスト作成ページ
	 *                "最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(String requestUri, String pageNum, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		String editUri = null;
		if (requestUri != null && pageNum != null) {
			editUri = UrlUtil.addParam(requestUri, RequestParam.PARAM_PAGINATION, pageNum);
		} else {
			editUri = requestUri;
		}
		RequestParam param = new RequestParamInfo(editUri, getTargetService(targetServiceName));
		return pagination(param, targetServiceName, targetServiceKey);
	}

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param param 検索条件、カーソルリスト作成ページ
	 *              カーソルリスト作成ページは"最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return title:最終ページ番号、subtitle:範囲内のエントリー件数、link rel="next" があれば続きのデータあり。
	 */
	public FeedBase pagination(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			PaginationBlogic paginationBlogic = new PaginationBlogic();
			return paginationBlogic.paging(param, targetServiceName, targetServiceKey, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("pagination", param, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ページ指定検索
	 * @param requestUri キー+検索条件
	 * @param pageNum ページ番号
	 * @return 検索結果
	 */
	public FeedBase getPage(String requestUri, String pageNum)
	throws IOException, TaggingException {
		return getPage(requestUri, pageNum, null, null);
	}

	/**
	 * ページ指定検索
	 * @param param キー、検索条件、ページ番号
	 * @return 検索結果
	 */
	public FeedBase getPage(RequestParam param)
	throws IOException, TaggingException {
		return getPage(param, null, null);
	}

	/**
	 * ページ指定検索
	 * @param requestUri キー+検索条件
	 * @param pageNum ページ番号
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 検索結果
	 */
	public FeedBase getPage(String requestUri, String pageNum, String targetServiceName, 
			String targetServiceKey)
	throws IOException, TaggingException {
		String editUri = null;
		if (requestUri != null && pageNum != null) {
			editUri = UrlUtil.addParam(requestUri, RequestParam.PARAM_NUMBER, pageNum);
		} else {
			editUri = requestUri;
		}
		RequestParam param = new RequestParamInfo(editUri, getTargetService(targetServiceName));
		return getPage(param, targetServiceName, targetServiceKey);
	}

	/**
	 * ページ指定検索
	 * @param param キー、検索条件、ページ番号
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 検索結果
	 */
	public FeedBase getPage(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		try {
			PaginationBlogic paginationBlogic = new PaginationBlogic();
			return paginationBlogic.getPage(param, targetServiceName, targetServiceKey, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getPage", param, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザステータスを取得.
	 * @param email ユーザ名。nullの場合はステータス一覧を取得.
	 * @return ユーザステータス
	 */
	public EntryBase getUserstatus(String email)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.getUserstatusByEmail(email, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getUserstatus", email, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザステータス一覧を取得.
	 * @param param リクエストパラメータ
	 * @return ユーザステータス一覧
	 */
	public FeedBase getUserstatusList(RequestParam param)
	throws IOException, TaggingException {
		String limitStr = param.getOption(RequestParam.PARAM_LIMIT);
		String cursorStr = param.getOption(RequestParam.PARAM_NEXT);
		return getUserstatusList(limitStr, cursorStr);
	}

	/**
	 * ユーザステータス一覧を取得.
	 * @param limitStr 一覧最大件数
	 * @param cursorStr カーソル
	 * @return ユーザステータス一覧
	 */
	public FeedBase getUserstatusList(String limitStr, String cursorStr)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.getUserstatusList(limitStr, cursorStr, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getUserstatusList", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザを無効にする.
	 * @param email ユーザ名
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 */
	@Override
	public EntryBase revokeUser(String email, boolean isDeleteGroups)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.revokeUser(email, isDeleteGroups, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("revokeUser", email, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザを無効にする.
	 * @param feed ユーザ情報
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 */
	@Override
	public FeedBase revokeUser(FeedBase feed, boolean isDeleteGroups)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.revokeUser(feed, isDeleteGroups, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("revokeUser", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザを有効にする.
	 * @param email ユーザ名
	 */
	@Override
	public EntryBase activateUser(String email)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.activateUser(email, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("activateUser", email, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザを有効にする.
	 * @param feed ユーザ情報
	 */
	@Override
	public FeedBase activateUser(FeedBase feed)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.activateUser(feed, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("activateUser", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザを削除する.
	 * @param account アカウント
	 * @param async 削除を非同期に行う場合true
	 * @return 処理対象ユーザのトップエントリー
	 */
	public EntryBase deleteUser(String account, boolean async)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.deleteUser(account, async, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteUser", account, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ユーザを削除する.
	 * @param feed ユーザ情報
	 * @param async 削除を非同期に行う場合true
	 * @return 処理対象ユーザのトップエントリーリスト
	 */
	@Override
	public FeedBase deleteUser(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		try {
			UserBlogic userBlogic = new UserBlogic();
			return userBlogic.deleteUser(feed, async, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("deleteUser", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * エイリアスを追加する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	@Override
	public FeedBase addAlias(FeedBase feed)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.addAlias(feed, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("addAlias", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * エイリアスを削除する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	@Override
	public FeedBase removeAlias(FeedBase feed)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.removeAlias(feed, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("removeAlias", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ACLを追加する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	@Override
	public FeedBase addAcl(FeedBase feed)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.addAcl(feed, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("addAcl", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * ACLを削除する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	@Override
	public FeedBase removeAcl(FeedBase feed)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.removeAcl(feed, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("removeAcl", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return postBq(feed, null, async);
	}

	/**
	 * BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(FeedBase feed, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.postBq(feed, tableNames, async, auth, requestInfo, connectionInfo);
	}

	/**
	 * BigQueryにデータを登録する.
	 * ログデータ用。Feedでなく、テーブル名と、項目名と値のMapを詰めたリストを指定する。
	 * @param tableName テーブル名
	 * @param list 項目名と値のリスト
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(String tableName, List<Map<String, Object>> list, boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.postBq(tableName, list, async, auth, requestInfo, connectionInfo);
	}

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uri 削除キー。末尾に*(ワイルドカード)指定可能。
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String uri, boolean async)
	throws IOException, TaggingException {
		return deleteBq(uri, null, async);
	}

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uri 削除キー。末尾に*(ワイルドカード)指定可能。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String uri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.deleteBq(new String[]{uri}, tableNames, async, auth, requestInfo, connectionInfo);
	}

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uris 削除キーリスト。末尾に*(ワイルドカード)指定可能。
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String[] uris, boolean async)
	throws IOException, TaggingException {
		return deleteBq(uris, null, async);
	}

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uris 削除キーリスト。末尾に*(ワイルドカード)指定可能。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String[] uris, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.deleteBq(uris, tableNames, async, auth, requestInfo, connectionInfo);
	}

	/**
	 * BigQueryに対し検索SQLを実行し、結果を取得する.
	 * @param sql SQL
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryBq(String sql)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.queryBq(sql, auth, requestInfo, connectionInfo);
	}

	/**
	 * SDK呼び出し.
	 * @param name プロパティファイルに設定した、SDK実行クラス名に対応するname
	 * @param args SDK実行クラス実行時の引数
	 */
	public FeedBase callSDK(String name, String[] args)
	throws IOException, TaggingException {
		SDKBlogic sdkBlogic = new SDKBlogic();
		return sdkBlogic.call(name, args, auth, requestInfo, connectionInfo);
	}

	/**
	 * 名前空間を取得.
	 * @return 名前空間
	 */
	public String getNamespace()
	throws IOException, TaggingException {
		if (namespace == null) {
			namespace = NamespaceUtil.getNamespace(getServiceName(), requestInfo, connectionInfo);
		}
		return namespace;
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param param RequestParam
	 * @param useCache キャッシュを使用する場合true
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, Throwable e) {
		return getErrorMessageHeader(method, e);
	}

	/**
	 * インデックス部分更新.
	 * @param feed インデックス更新情報
	 * @param isDelete 削除の場合true
	 */
	public void putIndex(FeedBase feed, boolean isDelete)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			blogic.putIndex(feed, isDelete, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("putIndex", feed, null, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * 条件検索のインデックス使用チェック.
	 * @param param 検索条件
	 * @return メッセージ (Feedのtitleに設定)
	 */
	public FeedBase checkIndex(RequestParam param)
	throws IOException, TaggingException {
		try {
			DatastoreBlogic blogic = new DatastoreBlogic();
			return blogic.checkIndex(param, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("getFeed", param, e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * メッセージ通知
	 * @param body メッセージ
	 * @param to 送信先 (UID, account or group)
	 */
	public void pushNotification(String body, String[] to)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		FeedBase feed = null;
		EntryBase entry = null;
		if (auth != null) {
			entry = TaggingEntryUtil.createEntry(serviceName);
			feed = TaggingEntryUtil.createFeed(serviceName);
		} else {
			entry = TaggingEntryUtil.createAtomEntry();
			feed = TaggingEntryUtil.createAtomFeed();
		}
		entry.setContentText(body);
		feed.addEntry(entry);
		pushNotification(feed, to);
	}

	/**
	 * メッセージ通知
	 * @param entry 通知メッセージ
	 *          title: Push通知タイトル
	 *          subtitle: Push通知サブタイトル
	 *          content: Push通知メッセージ本文(body)
	 *          summary: dataに設定するメッセージ(Expo用)
	 *          category _$scheme="imageurl" _$label={通知イメージURL}(FCM用)
	 *          category _$scheme={dataのキー} _$label={dataの値}(Expo用)
	 * @param to 送信先 (UID, account or group)
	 */
	public void pushNotification(FeedBase feed, String[] to)
	throws IOException, TaggingException {
		PushNotificationBlogic blogic = new PushNotificationBlogic();
		blogic.pushNotification(feed, to, this);
	}

	/**
	 * WebSocketメッセージ送信.
	 * @param messageFeed メッセージ情報格納Feed。entryの内容は以下の通り。
	 *          summary : メッセージ
	 *          link rel="to"のhref属性 : 送信先。以下のいずれか。複数指定可。
	 *              UID
	 *              アカウント
	 *              グループ(*)
	 *              ポーリング(#)
	 *          title : WebSocket送信ができなかった場合のPush通知のtitle
	 *          subtitle : WebSocket送信ができなかった場合のPush通知のsubtitle(Expo用)
	 *          content : WebSocket送信ができなかった場合のPush通知のbody
	 *          category : WebSocket送信ができなかった場合のPush通知のdata(Expo用、key-value形式)
	 *              dataのキーに_$schemeの値、dataの値に_$labelの値をセットする。
	 *          rights : trueが指定されている場合、WebSocket送信ができなかった場合にPush通知しない。
	 * @param channel チャネル
	 */
	public void sendWebSocket(FeedBase messageFeed, String channel)
	throws IOException, TaggingException {
		WebSocketBlogic blogic = new WebSocketBlogic();
		blogic.onMessage(messageFeed, channel, auth, requestInfo, connectionInfo);
	}

	/**
	 * WebSocket接続をクローズ.
	 * 認証ユーザのWebSocketについて、指定されたチャネルの接続をクローズする。
	 * @param channel チャネル
	 */
	public void closeWebSocket(String channel)
	throws IOException, TaggingException {
		WebSocketBlogic blogic = new WebSocketBlogic();
		blogic.close(channel, auth, requestInfo, connectionInfo);
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param param RequestParam
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, RequestParam param,
			Throwable e) {
		return getErrorMessage(method, param, null, e);
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param param RequestParam
	 * @param useCache キャッシュを使用する場合true
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, RequestParam param,
			Boolean useCache, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		if (param != null) {
			sb.append(param.toString());
		} else {
			sb.append("null");
		}
		if (useCache != null) {
			sb.append(" useCache=");
			sb.append(useCache);
		}
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param uri URI
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, String uri, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		if (uri != null) {
			sb.append(uri);
		}
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param uri URI
	 * @param num 番号
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, String uri, long num, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		if (uri != null) {
			sb.append(uri);
		}
		sb.append(" num=");
		sb.append(num);
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param uri URI
	 * @param val 値
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, String uri, String val, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		if (uri != null) {
			sb.append(uri);
		}
		sb.append(" val=");
		sb.append(val);
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param feed Feed
	 * @param uri URI
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, FeedBase feed, String uri, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		sb.append("feed size=");
		if (feed != null && feed.entry != null) {
			sb.append(feed.entry.size());
		} else {
			sb.append("0");
		}
		if (uri != null) {
			sb.append(" parent uri=");
			sb.append(uri);
		}
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param uris キーリスト
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	private String getErrorMessage(String method, List<String> uris, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		int size = 0;
		if (uris != null) {
			size = uris.size();
		} else {
			size = 0;
		}
		sb.append("key(AndRev)");
		if (size == 0) {
			sb.append(" size=0");
		} else  {
			sb.append("=[");
			boolean isFirst = true;
			for (String uriAndRev : uris) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(uriAndRev);
			}
			sb.append("]");
		}
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージの先頭部分を取得.
	 * @param method メソッド名
	 * @param e 例外
	 * @return ログ用エラーメッセージの先頭部分
	 */
	private String getErrorMessageHeader(String method, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getSimpleName());
		sb.append(" : ");
		sb.append(e.getMessage());
		sb.append(" [ReflexContext.");
		sb.append(method);
		sb.append("] ");
		return sb.toString();
	}
	
	/**
	 * ログアウト.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return ログアウトメッセージ
	 */
	public FeedBase logout(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
		return loginLogoutBlogic.logout(req, resp);
	}

	/**
	 * 指定したURI配下のキーのエントリーで自分が署名していないものを取得.
	 * ただしすでにグループ参加状態のものは除く。
	 * @param uri 親キー
	 * @return 親キー配下のエントリーで署名していないEntryリスト
	 */
	public FeedBase getNoGroupMember(String uri) 
	throws IOException, TaggingException {
		UserBlogic userBlogic = new UserBlogic();
		return userBlogic.getNoGroupMember(uri, this);
	}
	
	/**
	 * PDF生成.
	 * @param htmlTemplate HTML形式テンプレート
	 * @return PDFデータ
	 */
	public byte[] toPdf(String htmlTemplate) 
	throws IOException, TaggingException {
		try {
			PdfBlogic pdfBlogic = new PdfBlogic();
			return pdfBlogic.toPdf(htmlTemplate, this);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			String msg = getErrorMessage("generatePdf", e);
			requestInfo.setReflexContextMessage(msg);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);
			throw e;
		}
	}

	/**
	 * メッセージキュー使用ON/OFF設定
	 * @param flag メッセージキューを使用する場合true
	 * @param channel チャネル
	 */
	public void setMessageQueueStatus(boolean flag, String channel)
	throws IOException, TaggingException {
		MessageQueueBlogic blogic = new MessageQueueBlogic();
		blogic.setMessageQueueStatus(flag, channel, this);
	}

	/**
	 * メッセージキュー使用ON/OFF設定を取得
	 * @param channel チャネル
	 */
	public boolean getMessageQueueStatus(String channel)
	throws IOException, TaggingException {
		MessageQueueBlogic blogic = new MessageQueueBlogic();
		return blogic.getMessageQueueStatus(channel, this);
	}

	/**
	 * メッセージキューへメッセージ送信
	 * @param feed メッセージ
	 * @param channel チャネル
	 */
	public void setMessageQueue(FeedBase feed, String channel)
	throws IOException, TaggingException {
		MessageQueueBlogic blogic = new MessageQueueBlogic();
		blogic.setMessageQueue(feed, channel, this);
	}
	
	/**
	 * メッセージキューからメッセージ受信
	 * @param channel チャネル
	 * @return メッセージ
	 */
	public FeedBase getMessageQueue(String channel)
	throws IOException, TaggingException {
		MessageQueueBlogic blogic = new MessageQueueBlogic();
		return blogic.getMessageQueue(channel, this);
	}
	
	/**
	 * グループに参加登録する.
	 * 署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @return グループエントリー
	 */
	public EntryBase addGroup(String group, String selfid)
	throws IOException, TaggingException {
		UserBlogic blogic = new UserBlogic();
		return blogic.addGroup(group, selfid, this);
	}
	
	/**
	 * 管理者によるグループの参加登録.
	 * 署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @return グループエントリー
	 */
	public FeedBase addGroupByAdmin(String group, String selfid, FeedBase feed)
	throws IOException, TaggingException {
		UserBlogic blogic = new UserBlogic();
		return blogic.addGroupByAdmin(group, selfid, feed, this);
	}

	/**
	 * グループに参加署名する.
	 * グループ作成者がユーザへの参加承認をしている状態の時、ユーザがグループに参加する。
	 * 自身のグループエイリアス作成(登録がない場合→再加入を想定)と、自身のグループエイリアスへの署名を行う。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @return グループエントリー
	 */
	public EntryBase joinGroup(String group, String selfid)
	throws IOException, TaggingException {
		UserBlogic blogic = new UserBlogic();
		return blogic.joinGroup(group, selfid, this);
	}
	
	/**
	 * グループから退会する.
	 * グループエントリーの、自身のグループエイリアスを削除する。
	 * @param group グループ名
	 * @return 退会したグループエントリー
	 */
	public EntryBase leaveGroup(String group)
	throws IOException, TaggingException {
		UserBlogic blogic = new UserBlogic();
		return blogic.leaveGroup(group, this);
	}
	
	/**
	 * 管理者によるグループからの退会処理.
	 * 署名はなし。
	 * @param group グループ名
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @return 退会したグループエントリー
	 */
	public FeedBase leaveGroupByAdmin(String group, FeedBase feed)
	throws IOException, TaggingException {
		UserBlogic blogic = new UserBlogic();
		return blogic.leaveGroupByAdmin(group, feed, this);
	}

	/**
	 * RDBへ更新系SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト。トランザクションで括られる。
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	public int[] execRdb(String[] sqls)
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		return blogic.execRdb(sqls, false, auth, requestInfo, connectionInfo);
	}

	/**
	 * RDBへ更新系SQLを実行する.
	 * 大量データ登録を想定。AutoCommitがデフォルト(true)の場合、SQLリストはトランザクションで括られない。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト。
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	public int[] bulkExecRdb(String[] sqls)
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		return blogic.execRdb(sqls, true, auth, requestInfo, connectionInfo);
	}

	/**
	 * 非同期でRDBへ更新系SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト。トランザクションで括られる。
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFuture
	 */
	public Future<int[]> execRdbAsync(String[] sqls)
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		return blogic.execRdbAsync(sqls, false, auth, requestInfo, connectionInfo);
	}

	/**
	 * 非同期でRDBへ更新系SQLを実行する.
	 * 大量データ登録を想定。AutoCommitがデフォルト(true)の場合、SQLリストはトランザクションで括られない。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト。トランザクションで括られる。
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFuture
	 */
	public Future<int[]> bulkExecRdbAsync(String[] sqls)
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		return blogic.execRdbAsync(sqls, true, auth, requestInfo, connectionInfo);
	}

	/**
	 * AutoCommit設定を取得する.
	 * @return autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public boolean getAutoCommitRdb()
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		return blogic.getAutoCommit(auth, requestInfo, connectionInfo);
	}
	
	/**
	 * AutoCommitを設定する.
	 * デフォルトはtrue。
	 * execSqlにおいて非同期処理(async=true)の場合、この設定は無効になる。(非同期処理の場合AutoCommit=true)
	 * @param autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public void setAutoCommitRdb(boolean autoCommit)
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		blogic.setAutoCommit(autoCommit, auth, requestInfo, connectionInfo);
	}

	/**
	 * コミットを実行する.
	 * AutoCommit=falseの場合に有効。
	 */
	public void commitRdb()
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		blogic.commit(auth, requestInfo, connectionInfo);
	}
	
	/**
	 * ロールバックを実行する.
	 * AutoCommit=falseの場合に有効。
	 */
	public void rollbackRdb()
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		blogic.rollback(auth, requestInfo, connectionInfo);
	}

	/**
	 * RDBに対しSQLを実行し、結果を取得する.
	 * @param sql SQL
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryRdb(String sql)
	throws IOException, TaggingException {
		RDBBlogic blogic = new RDBBlogic();
		return blogic.queryRdb(sql, auth, requestInfo, connectionInfo);
	}

	/**
	 * BDBにEntryを登録し、非同期でBigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param parentUri Entry登録でキーを自動採番する場合の親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの登録について非同期の場合true、同期の場合false
	 * @return 登録したEntryリスト。
	 */
	public FeedBase postBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames, 
			boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.postBdbq(feed, parentUri, tableNames, async, this);
	}

	/**
	 * BDBのEntryを更新し、非同期でBigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param parentUri Entry登録でキーを自動採番する場合の親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの更新について非同期の場合true、同期の場合false
	 * @return 更新したEntryリスト
	 */
	public FeedBase putBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames,
			boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.putBdbq(feed, parentUri, tableNames, async, this);
	}

	/**
	 * BDBのEntryを削除し、非同期でBigQueryに削除データを登録する.
	 * @param uris 削除キーリスト。ワイルドカードは指定不可。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの削除について非同期の場合true、同期の場合false
	 * @return 更新したEntryリスト
	 */
	public FeedBase deleteBdbq(String[] uris, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.deleteBdbq(uris, tableNames, async, this);
	}

	/**
	 * BDBのEntryを更新し、BigQueryにEntryを登録する.
	 * Feedの一貫性は保証しない。
	 * @param feed Feed
	 * @param parentUri Entry登録でキーを自動採番する場合の親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの登録について非同期の場合true、同期の場合false
	 * @return 更新したEntryリスト
	 */
	public List<Future<List<UpdatedInfo>>> bulkPutBdbq(
			FeedBase feed, String parentUri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		BigQueryBlogic blogic = new BigQueryBlogic();
		return blogic.bulkPutBdbq(feed, parentUri, tableNames, this, async);
	}

}
