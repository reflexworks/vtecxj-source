package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;

/**
 * サービス登録時に呼び出しが必要なインスタンスが継承するインターフェース.
 */
public interface ExecuteAtCreateService {
	
	/**
	 * サービス登録時の処理
	 * @param newServiceName サービス名
	 * @param auth 実行ユーザ認証情報
	 * @param systemContext システム管理サービスのSystemContext
	 */
	public void doCreateService(String newServiceName, ReflexAuthentication auth, 
			SystemContext systemContext)
	throws IOException, TaggingException;
	
}
