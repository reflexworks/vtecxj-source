package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexExternalSdkCaller;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.PluginUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * SDK呼び出し機能
 */
public class SDKBlogic {

	/** プロパティ設定名接頭辞 : SDK実行クラス */
	private static final String PROP_SDK_PREFIX = "_sdk.";

	/**
	 * SDK呼び出し
	 * @param name プロパティファイルに設定した、SDK実行クラス名に対応するname
	 * @param args SDK実行クラス実行時の引数
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 実行結果
	 */
	public FeedBase call(String name, String[] args,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// name入力チェック
		CheckUtil.checkNotNull(name, "The name of SDK");
		String sdkExecClassName = TaggingEnvUtil.getSystemProp(getPropNameSdk(name),
				null);
		if (StringUtils.isBlank(sdkExecClassName)) {
			throw new IllegalParameterException("The name of SDK does not exist. " + name);
		}
		Class<ReflexExternalSdkCaller> cls = PluginUtil.forName(sdkExecClassName);
		ReflexExternalSdkCaller sdkCaller =
				(ReflexExternalSdkCaller)PluginUtil.newInstance(cls);

		// SDK実行クラスのcallメソッドを実行。戻り値をそのまま返す。
		return sdkCaller.call(args, auth, requestInfo, connectionInfo);
	}

	/**
	 * SDK実行クラスの設定名を取得
	 *   "_sdk.{name}"
	 * @param name SDK実行クラス名
	 * @return SDK実行クラスの設定名
	 */
	private String getPropNameSdk(String name) {
		return PROP_SDK_PREFIX + name;
	}

}
