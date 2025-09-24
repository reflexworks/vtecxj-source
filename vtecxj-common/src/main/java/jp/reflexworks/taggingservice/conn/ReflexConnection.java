package jp.reflexworks.taggingservice.conn;

/**
 * TaggingServiceが管理するコネクションのインターフェース.
 * コネクションを、このインターフェースをimplementsしたクラスにラップして発行してください。
 * クローズ処理は処理終了前(レスポンス前、バッチ処理終了前)にTaggingServiceが行います。
 */
public interface ReflexConnection<T> {
	
	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public T getConnection();
	
	/**
	 * クローズ処理.
	 * この処理はTaggingServiceが行いますので、サービスでは実行しないでください。
	 */
	public void close();

}
