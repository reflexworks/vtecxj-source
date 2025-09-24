package jp.reflexworks.taggingservice.rdb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.ConnectionPoolDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.BaseReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;

/**
 * RDBに使用するstatic情報保持クラス
 */
public class ReflexRDBEnv {
	
	/** データソース格納Map */
	private final ConcurrentMap<String, ConnectionPoolDataSource> dataSources = 
			new ConcurrentHashMap<>();

	/** ロガー. */
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理
	 */
	void init() {
		if (logger.isDebugEnabled()) {
			logger.debug("[init] start.");
		}
		BaseReflexEnv env = ReflexStatic.getEnv();
		Context context = null;
		try {
			context = new InitialContext();
			Map<String, String> propMap = env.getSystemPropMap(ReflexRDBConst.RDB_JNDI_PREFIX);
			if (propMap != null) {
				for (Map.Entry<String, String> mapEntry : propMap.entrySet()) {
					String key = mapEntry.getKey();
					String serviceName = key.substring(ReflexRDBConst.RDB_JNDI_PREFIX_LEN);
					String jndi = mapEntry.getValue();
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[init] serviceName=");
						sb.append(serviceName);
						sb.append(", jndi=");
						sb.append(jndi);
						logger.debug(sb.toString());
					}
	
					try {
						// DataSourceを取得
						Object dataSourceObj = context.lookup(
								ReflexRDBConst.JNDI_PREFIX + jndi);
						ConnectionPoolDataSource dataSource = (ConnectionPoolDataSource)dataSourceObj;
						if (dataSource != null) {
							ConnectionPoolDataSource tmp = dataSources.putIfAbsent(serviceName, dataSource);
							if (tmp != null) {
								StringBuilder sb = new StringBuilder();
								sb.append("[init] serviceName=");
								sb.append(serviceName);
								sb.append(", jndi=");
								sb.append(jndi);
								sb.append(" DataSource already exist.");
								logger.warn(sb.toString());
							}
						}
	
					} catch (NamingException e) {
						StringBuilder sb = new StringBuilder();
						sb.append("[init] serviceName=");
						sb.append(serviceName);
						sb.append(", jndi=");
						sb.append(jndi);
						sb.append(", NamingException: ");
						sb.append(e.getMessage());
						logger.warn(sb.toString());
					}
				}
			}
			
		} catch (NamingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("[init] Initial context create error. NamingException: ");
			sb.append(e.getMessage());
			logger.warn(sb.toString());
			
		} finally {
			if (context != null) {
				try {
					context.close();
				} catch (NamingException e) {
					StringBuilder sb = new StringBuilder();
					sb.append("[init] Initial context close error. NamingException: ");
					sb.append(e.getMessage());
					logger.warn(sb.toString());
				}
			}
		}
	}

	/**
	 * クローズ処理
	 */
	public void close() {
		// Do nothing.
	}
	
	/**
	 * データソースを取得.
	 * @param serviceName サービス名
	 * @return データソース
	 */
	public ConnectionPoolDataSource getDataSource(String serviceName) {
		return dataSources.get(serviceName);
	}

}
