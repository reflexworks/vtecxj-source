package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.MonitorManager;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * モニター管理クラス.
 */
public class MonitorManagerDefault implements MonitorManager {

	/** monitorパラメータ : データストア */
	public static final String MONITOR_DATASTORE = "datastore";
	/** monitorパラメータ : 全文検索インデックス */
	public static final String MONITOR_FULLTEXTSEARCH = "fulltextsearch";
	/** monitorパラメータ : インデックス */
	public static final String MONITOR_INDEX = "index";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * サーバ起動時に一度だけ呼び出されます。
	 */
	@Override
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
	 * モニター.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return Feed
	 */
	@Override
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		// システム管理サービスかつ管理ユーザでなければエラー
		String serviceName = req.getServiceName();
		if (!serviceName.equals(TaggingEnvUtil.getSystemService())) {
			throw new PermissionException();
		}
		ReflexAuthentication auth = req.getAuth();
		AclBlogic aclBlogic = new AclBlogic();
		if (!aclBlogic.isInTheGroup(auth, Constants.URI_GROUP_ADMIN)) {
			throw new PermissionException();
		}

		// モニター処理
		// パラメータにより各Managerに処理移譲。
		RequestParam param = (RequestParam)req.getRequestType();
		String target = param.getOption(RequestParam.PARAM_MONITOR);
		if (MONITOR_DATASTORE.equals(target)) {
			DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
			return datastoreManager.monitor(req, resp);
		} else if (MONITOR_FULLTEXTSEARCH.equals(target)) {
			DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
			return datastoreManager.monitor(req, resp);
		} else if (MONITOR_INDEX.equals(target)) {
			DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
			return datastoreManager.monitor(req, resp);
		} else {
			throw new IllegalParameterException("Invalid parameter: " + target);
		}

	}

}
