package jp.reflexworks.taggingservice.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ReflexContext生成クラス.
 */
public final class ReflexContextUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexContextUtil.class);

	/**
	 * ReflexContext取得.
	 * サービスからの呼び出し時に使用します。
	 * Externalではありません。
	 * @param req リクエスト
	 */
	public static ReflexContext getReflexContext(ReflexRequest req) {
		return getReflexContext(req, false);
	}

	/**
	 * ReflexContext取得.
	 * サービスからの呼び出し時に使用します。
	 * @param req リクエスト
	 * @param isExternal Externalの場合true
	 */
	public static ReflexContext getReflexContext(ReflexRequest req, boolean isExternal) {
		String serviceName = req.getServiceName();
		// ReflexContextの生成
		ReflexContext reflexContext = null;
		String clsNameReflexContext = TaggingEnvUtil.getProp(serviceName,
				TaggingEnvConst.PLUGIN_REFLEXCONTEXT, null);
		if (!StringUtils.isBlank(clsNameReflexContext)) {
			// ReflexContextを継承したクラス
			try {
				Class<? extends ReflexContext> clsRc =
						(Class<? extends ReflexContext>) Class.forName(clsNameReflexContext);
				Class<?>[] types = {ReflexRequest.class};
				Constructor<? extends ReflexContext> constructorRc = clsRc.getConstructor(types);

				Object[] objs = new Object[]{req};

				reflexContext = constructorRc.newInstance(objs);

			} catch (ClassNotFoundException | NoSuchMethodException |
					InvocationTargetException | IllegalAccessException |
					InstantiationException e) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(req.getRequestInfo()));
				sb.append(e.getClass().getName());
				sb.append(": ");
				sb.append(e.getMessage());
				String msg = sb.toString();
				logger.warn("[getReflexContext] " + msg);

				SystemContext systemContext = new SystemContext(req.getAuth(),
						req.getRequestInfo(), req.getConnectionInfo());
				systemContext.errorLog(e);
			}
		}

		if (reflexContext == null) {
			// 純正ReflexContext
			reflexContext = new TaggingContext(req, isExternal);
		}
		return reflexContext;
	}

	/**
	 * ReflexContext取得.
	 * 内部処理で使用します。
	 * Externalはauthの情報を引き継ぎます。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ReflexContext
	 */
	public static ReflexContext getReflexContext(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		return getReflexContext(auth, requestInfo, connectionInfo, auth.isExternal());
	}

	/**
	 * ReflexContext取得.
	 * 内部処理で使用します。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param isExternal Externalの場合true
	 * @return ReflexContext
	 */
	public static ReflexContext getReflexContext(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo, boolean isExternal) {
		String serviceName = auth.getServiceName();
		// ReflexContextの生成
		ReflexContext reflexContext = null;
		String clsNameReflexContext = TaggingEnvUtil.getProp(serviceName,
				TaggingEnvConst.PLUGIN_REFLEXCONTEXT, null);
		if (!StringUtils.isBlank(clsNameReflexContext)) {
			// ReflexContextを継承したクラス
			try {
				Class<? extends ReflexContext> clsRc =
						(Class<? extends ReflexContext>) Class.forName(clsNameReflexContext);
				Class<?>[] types = {ReflexAuthentication.class,
						RequestInfo.class, ConnectionInfo.class};
				Constructor<? extends ReflexContext> constructorRc = clsRc.getConstructor(types);

				Object[] objs = new Object[]{auth, requestInfo,
						connectionInfo};

				reflexContext = constructorRc.newInstance(objs);

			} catch (ClassNotFoundException | NoSuchMethodException |
					InvocationTargetException | IllegalAccessException |
					InstantiationException e) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append(e.getClass().getName());
				sb.append(": ");
				sb.append(e.getMessage());
				String msg = sb.toString();
				logger.warn("[getReflexContext] " + msg);

				SystemContext systemContext = new SystemContext(auth,
						requestInfo, connectionInfo);
				systemContext.errorLog(e);
			}
		}

		if (reflexContext == null) {
			// 純正ReflexContext
			reflexContext = new TaggingContext(auth, requestInfo, connectionInfo, isExternal);
		}
		return reflexContext;
	}

}
