package jp.reflexworks.taggingservice.api;

import jp.reflexworks.taggingservice.sys.SystemAuthentication;

/**
 * 初期化バッチ用起動プログラム.
 * プロパティファイルを指定し起動します。
 */
public class ReflexInitialization<O> extends ReflexApplication<O> {

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 */
	public static void main(String[] args) {
		ReflexApplication me = new ReflexApplication();
		me.exec(args);
	}

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 * @return 処理結果
	 */
	public O exec(String[] args) {
		// 引数チェック
		checkArgs(args);
		// 環境初期設定
		String propFile = args[0];
		contextInitialized(propFile, true);		// 初期データ登録のみ
		// 認証情報
		// 初期データ登録処理はシステムユーザで行う。
		String serviceName = args[1];
		SystemAuthentication auth = new SystemAuthentication(null, null, serviceName);
		return exec(args, auth);	// 第一引数は初期データ登録処理フラグ
	}

}
