package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.InUseSecretManager;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * SecretManagerの再読み込みビジネスロジック
 */
public class SecretBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 検索結果のキャッシュをリフレッシュ.
	 * @param req リクエスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void reloadSecret(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// システム管理サービスかどうか
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}
		// サービス管理者かどうか
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_ADMIN);

		// reloadSecret インターフェースを実行
		List<InUseSecretManager> inUseSecretManagerList = 
				TaggingEnvUtil.getInUseSecretManagerList();
		if (inUseSecretManagerList != null) {
			for (InUseSecretManager inUseSecretManager : inUseSecretManagerList) {
				if (logger.isTraceEnabled()) {
					logger.info("[reloadSecret] className: " + inUseSecretManager.getClass().getName());
				}
				inUseSecretManager.reloadSecret();
			}
		}
	}

}
