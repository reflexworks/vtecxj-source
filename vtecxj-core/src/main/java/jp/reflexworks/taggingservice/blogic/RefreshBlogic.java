package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * キャッシュを最新状態にリフレッシュするビジネスロジック.
 * データパッチ用
 */
public class RefreshBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 検索結果のキャッシュをリフレッシュ.
	 * @param req リクエスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void refreshCache(ReflexRequest req, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービス管理者かどうか
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_ADMIN);

		// ACLは不要
		// システム管理サービスの場合、他サービスのキャッシュリフレッシュも可能
		RequestParam param = (RequestParam)req.getRequestType();
		String serviceName = auth.getServiceName();
		String targetServiceName = null;
		if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
			targetServiceName = param.getOption(RequestParam.PARAM_REFRESHCACHE);
			if (!StringUtils.isBlank(targetServiceName)) {
				// 対象サービスチェック、初期化
				initTargetService(targetServiceName, req, requestInfo, connectionInfo);
			} else {
				// 自サービス
				targetServiceName = serviceName;
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[refreshCache] targetService = " + targetServiceName + " [param] " + param.toString());
		}

		// システム権限でアクセス
		SystemContext systemContext = new SystemContext(targetServiceName, requestInfo, connectionInfo);
		if (param.getOption(RequestParam.PARAM_ENTRY) != null) {
			systemContext.getEntry(param, false);
		} else if (param.getOption(RequestParam.PARAM_FEED) != null) {
			systemContext.getFeed(param, false);
		} else {
			throw new IllegalParameterException("Entry(e) or Feed(f) parameter is required.");
		}
	}

	/**
	 * 対象サービスの初期設定を行う.
	 * @param targetServiceName 対象サービス
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void initTargetService(String targetServiceName, ReflexRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 対象サービスの初期設定
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		serviceBlogic.initTargetService(targetServiceName, null, requestInfo,
				connectionInfo);
	}

}
