package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リダイレクト
 */
public class RedirectBlogic {

	/**
	 * 指定されたアプリにリダイレクトする.
	 * @param appName アプリ名
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void redirectApp(String appName, ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		// アプリリダイレクトの設定がない場合エラー
		Map<String, String> redirectApps = TaggingEnvUtil.getPropMap(serviceName,
				SettingConst.REDIRECT_APP_PREFIX);
		String appNameKey = editAppNameKey(appName);
		if (redirectApps == null || !redirectApps.containsKey(appNameKey)) {
			throw new IllegalParameterException("Invalid app name. " + appName);
		}
		String appUrl = redirectApps.get(appNameKey);
		// アプリURLを組み立てる。
		StringBuilder sb = new StringBuilder();
		sb.append(appUrl);
		// QueryStringは、_redicrect_app パラメータのみ除去する。
		Set<String> ignoreParams = new HashSet<>();
		ignoreParams.add(RequestParam.PARAM_REDIRECT_APP);
		String queryString = UrlUtil.editQueryString(req, ignoreParams, null, true);
		if (!StringUtils.isBlank(queryString)) {
			if (appUrl.indexOf("?") < 0) {
				sb.append(queryString);
			} else {
				sb.append("&");
				sb.append(queryString.substring(1));
			}
		}

		String location = sb.toString();
		resp.sendRedirect(location);
		resp.setStatus(HttpStatus.SC_MOVED_PERMANENTLY);
	}

	/**
	 * アプリリダイレクトURL抽出キーを編集
	 * @param appName アプリ名
	 * @return アプリリダイレクトURL抽出キー
	 */
	private String editAppNameKey(String appName) {
		return SettingConst.REDIRECT_APP_PREFIX + appName;
	}

}
