package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * ログイン履歴エントリー登録.
 */
public class WriteLoginHistoryCallable extends ReflexCallable<Boolean> {

	/**
	 * ログエントリーのtitle.
	 * login:WSSE、login:RXID、login:OAuth-{provider}、AuthenticationException のいずれか
	 */
	private String title;
	/** UID */
	private String uid;
	/** アカウント */
	private String account;
	/** ログイン情報文字列 */
	private String loginHistoryInfoStr;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param title login:WSSE、login:RXID、login:OAuth-{provider}、AuthenticationException のいずれか
	 * @param uid UID
	 * @param account アカウント
	 * @param loginHistoryInfoStr ログイン情報文字列
	 */
	public WriteLoginHistoryCallable(String title, String uid, String account,
			String loginHistoryInfoStr) {
		this.title = title;
		this.uid = uid;
		this.account = account;
		this.loginHistoryInfoStr = loginHistoryInfoStr;
	}

	/**
	 * ログイン履歴エントリー登録.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[writeLoginHistory call] start.");
		}

		SystemContext systemContext = new SystemContext(getServiceName(),
				getRequestInfo(), getConnectionInfo());

		LoginLogoutManagerDefault loginLogoutManager = new LoginLogoutManagerDefault();
		loginLogoutManager.writeLoginHistoryProc(title, uid, account, loginHistoryInfoStr,
				systemContext);

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[writeLoginHistory call] end.");
		}
		return true;
	}

}
