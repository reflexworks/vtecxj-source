package jp.reflexworks.taggingservice.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jakarta.activation.DataSource;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;

/**
 * Reflex データ操作インターフェース.
 */
public interface ReflexContext extends BaseReflexContext {

	/**
	 * 認証情報取得.
	 * @return Reflex内で使用する認証情報
	 */
	public ReflexAuthentication getAuth();

	/**
	 * UID取得.
	 * @return UID
	 */
	public String getUid();

	/**
	 * アカウント取得.
	 * @return アカウント
	 */
	public String getAccount();

	/**
	 * ログインユーザEntryを取得.
	 * @return ログインユーザEntry
	 */
	public FeedBase whoami()
	throws IOException, TaggingException;

	/**
	 * ログインユーザが参加中のグループリストを取得.
	 * Entryのlinkのhrefにグループをセット
	 * @return ログインユーザが参加中のグループリスト
	 */
	public FeedBase getGroups()
	throws IOException, TaggingException;

	/**
	 * ログインユーザが指定されたグループのメンバーかどうか判定.
	 * @param group グループ
	 * @return ログインユーザが指定されたグループに参加している場合true
	 */
	public boolean isGroupMember(String group)
	throws IOException, TaggingException;

	/**
	 * ResourceMapper取得.
	 * <p>
	 * 指定されたサービスのResourceMapperを返却します。
	 * </p>
	 * @param targetServiceName 対象サービス
	 * @return ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String targetServiceName)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri キー
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param requestUri キー
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri, boolean useCache)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param, boolean useCache)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri キー
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri, String targetServiceName,
			String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param requestUri キー
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(String requestUri, boolean useCache, String targetServiceName,
			String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param, String targetServiceName,
			String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキーのEntryを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Entry
	 */
	public EntryBase getEntry(RequestParam param, boolean useCache, String targetServiceName,
			String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri, boolean useCache)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param, boolean useCache)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 引数に親階層を指定することで、配下の一覧が取得できます。<br>
	 * また、URLパラメータのように、上位階層の後ろに"?"を指定し、
	 * その後ろに絞り込み条件を指定することができます。<br>
	 * </p>
	 * @param requestUri 上位階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(String requestUri, boolean useCache, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * メモリ上のキャッシュは参照しません。<br>
	 * </p>
	 * @param param 検索条件
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 条件検索.
	 * <p>
	 * 指定されたキー配下のFeedを返却します。<br>
	 * データが存在しない場合、nullを返却します。<br>
	 * </p>
	 * @param param 検索条件
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Feed
	 */
	public FeedBase getFeed(RequestParam param, boolean useCache, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri, boolean useCache)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param, boolean useCache)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param requestUri 親階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)。
	 */
	public FeedBase getCount(String requestUri, boolean useCache, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * <p>
	 * 引数に親階層を指定することで、配下のデータの件数が取得できます。<br>
	 * 条件指定ができます。<br>
	 * 戻り値のentryのtitleに件数が設定されます。<br>
	 * フェッチ数を超えた場合、Feedのlinkタグの rel="next"のhref項目にカーソルが設定されます。
	 * </p>
	 * @param param 親階層 + 絞り込み条件(任意)
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 件数(titleにセット)
	 */
	public FeedBase getCount(RequestParam param, boolean useCache, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 採番処理.
	 * <p>
	 * キーに対して、指定された採番数だけ番号を採番します。<br>
	 * 0、マイナス値の指定は不可です。<br>
	 * 戻り値はFeed形式で、titleに採番された値が設定されます。複数の場合はカンマで区切られます。<br>
	 * </p>
	 * @param uri キー
	 * @param num 採番数。
	 * @return 採番された値(titleにセット)。複数の場合はカンマでつながれます。
	 */
	public FeedBase allocids(String uri, int num)
	throws IOException, TaggingException;

	/**
	 * 採番処理.
	 * <p>
	 * キーに対して、指定された採番数だけ番号を採番します。<br>
	 * 0、マイナス値の指定は不可です。<br>
	 * 戻り値はFeed形式で、titleに採番された値が設定されます。複数の場合はカンマで区切られます。<br>
	 * </p>
	 * @param uri キー
	 * @param num 採番数。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 採番された値(titleにセット)。複数の場合はカンマでつながれます。
	 */
	public FeedBase allocids(String uri, int num, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 番号の加算処理.
	 * <p>
	 * パラメータで指定した数だけ値をプラスし、現在値を返します。<br>
	 * 加算する数にはマイナスの数値を指定することも可能です。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @param num 加算する数
	 * @return 加算後の現在値
	 */
	public FeedBase addids(String uri, long num)
	throws IOException, TaggingException;

	/**
	 * 番号の加算処理.
	 * <p>
	 * パラメータで指定した数だけ値をプラスし、現在値を返します。<br>
	 * 加算する数にはマイナスの数値を指定することも可能です。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @param num 加算する数
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 加算後の現在値
	 */
	public FeedBase addids(String uri, long num, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 加算処理の現在値取得.
	 * <p>
	 * addidsで加算する番号の現在値を返します。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @return addidsで加算する番号の現在値
	 */
	public FeedBase getids(String uri)
	throws IOException, TaggingException;

	/**
	 * 加算処理の現在値取得.
	 * <p>
	 * addidsで加算する番号の現在値を返します。<br>
	 * 戻り値はFeed形式で、titleに加算後の現在値が設定されます。<br>
	 * 加算枠が設定されている場合でサイクルしない場合、
	 * 最大値より大きい・最小値より小さい値となった場合エラーを返します。<br>
	 * </p>
	 * @param uri キー
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return addidsで加算する番号の現在値
	 */
	public FeedBase getids(String uri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * インクリメント値を設定.
	 * @param uri インクリメント項目の値設定をしたいEntryのURI
	 * @param value 設定値
	 * @return 設定値 (Feedのtitleに設定)
	 */
	public FeedBase setids(String uri, long value)
	throws IOException, TaggingException;

	/**
	 * インクリメント値を設定.
	 * @param uri インクリメント項目の値設定をしたいEntryのURI
	 * @param value 設定値
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 設定値 (Feedのtitleに設定)
	 */
	public FeedBase setids(String uri, long value, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * 加算枠設定.
	 * <p>
	 * 加算範囲を「{最小値}-{最大値}!」の形式で指定します。<br>
	 * 「-{最大値}!」は任意です。<br>
	 * 加算枠をサイクルしない場合、加算範囲の後ろに!を指定します。<br>
	 * デフォルトは最大値まで加算した場合、最小値に戻って加算を続けます。<br>
	 * 加算枠が設定された場合、現在値を最小値に設定します。<br>
	 * 加算枠がnull・空文字の場合、加算枠を削除します。現在値は変更しません。<br>
	 * </p>
	 * @param uri 採番の初期設定をしたいEntryのURI
	 * @param value 採番初期値。null・空文字の場合は加算枠削除。
	 * @return 設定内容
	 */
	public FeedBase rangeids(String uri, String value)
	throws IOException, TaggingException;

	/**
	 * 加算枠取得.
	 * 戻り値はFeed形式で、titleに加算枠が設定されます。<br>
	 * @param uri 加算枠のURI
	 * @return 加算枠
	 */
	public FeedBase getRangeids(String uri)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param param リクエストパラメータオブジェクト
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, RequestParam param)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param uri 自動採番の場合、上位階層を指定。
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, String uri)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param uri 自動採番の場合、上位階層を指定。
	 * @param ext 自動採番の場合、末尾につける拡張子を指定。
	 * @return 登録したEntry
	 */
	public EntryBase postWithExtension(EntryBase entry, String uri, String ext)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param param リクエストパラメータオブジェクト
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, RequestParam param)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param uri 自動採番の場合、親階層を指定。
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String uri)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param uri 自動採番の場合、親階層を指定。
	 * @param ext 自動採番の場合、末尾につける拡張子を指定。
	 * @return 登録したFeed
	 */
	public FeedBase postWithExtension(FeedBase feed, String uri, String ext)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param param リクエストパラメータオブジェクト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry登録.
	 * <p>
	 * Entryを1件登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * </p>
	 * @param entry 登録するEntry
	 * @param uri 自動採番の場合、上位階層を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したEntry
	 */
	public EntryBase post(EntryBase entry, String uri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param param リクエストパラメータオブジェクト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed登録.
	 * <p>
	 * Feed内のEntryをまとめて登録します。<br>
	 * uriに値を指定し、entryのlink rel=selfタグが設定されていない場合、キーが自動採番されます。
	 * (uri + "/" + 自動採番番号)<br>
	 * キーをlink rel=selfタグのhref属性に指定してください。<br>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 登録するEntryを設定したFeed
	 * @param uri 自動採番の場合、親階層を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String uri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry)
	throws IOException, TaggingException;

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param requestUri パラメータ
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, String requestUri)
	throws IOException, TaggingException;

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param param パラメータ
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, RequestParam param)
	throws IOException, TaggingException;

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String requestUri)
	throws IOException, TaggingException;

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, RequestParam param)
	throws IOException, TaggingException;

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param requestUri パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * </p>
	 * @param entry 更新内容を設定したEntry
	 * @param param パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したEntry
	 */
	public EntryBase put(EntryBase entry, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String requestUri, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed更新.
	 * <p>
	 * Entryを更新、または登録、削除を行います。<br>
	 * 判定方法:<br>
	 * <ul>
	 *   <li>idに「?_delete」が設定されていれば削除</li>
	 *   <li>idにリビジョンが指定されていれば更新</li>
	 *   <li>idにリビジョンが指定されていない場合、データを検索し、あれば更新、なければ登録。</li>
	 * </ul>
	 * この処理は一貫性が保証されます。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, boolean async)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, String requestUri, boolean async)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, RequestParam param, boolean async)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, RequestParam param, boolean async,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, boolean async)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param requestUri パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, String requestUri, boolean async)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, RequestParam param, boolean async)
	throws IOException, TaggingException;

	/**
	 * Feed一括更新.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は直列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param param パラメータ
	 * @param async 非同期の場合true、同期の場合false
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 更新したFeed
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, RequestParam param, boolean async,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * @param uri キー
	 * @param revision リビジョン(楽観的排他チェックに使用)
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String uri, int revision)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * @param id IDまたはURI
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String id)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * @param param リクエストパラメータオブジェクト
	 * @return 削除されたEntry
	 */
	public EntryBase delete(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param ids IDリスト
	 */
	public FeedBase delete(List<String> ids)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param feed Feed
	 */
	public FeedBase delete(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * @param uri キー
	 * @param revision リビジョン(楽観的排他チェックに使用)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String uri, int revision, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * @param id IDまたはURI
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたEntry
	 */
	public EntryBase delete(String id, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * @param param リクエストパラメータオブジェクト
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 削除されたEntry
	 */
	public EntryBase delete(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param ids IDリスト
	 */
	public FeedBase delete(List<String> ids, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Entry削除.
	 * この処理は一貫性が保証されます。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param feed Feed
	 */
	public FeedBase delete(FeedBase feed, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * Feed一括削除.
	 * <p>
	 * Entryの最大更新数ごとにFeed更新処理を呼び出します。<br>
	 * 更新処理は並列で行われます。<br>
	 * この処理は一貫性が保証されません。
	 * </p>
	 * @param feed 更新内容を設定したEntryを格納したFeed
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public List<Future<List<UpdatedInfo>>> bulkDelete(FeedBase feed, boolean async)
	throws IOException, TaggingException;

	/**
	 * フォルダ削除.
	 * <p>
	 * 指定された親階層とその配下のデータをまとめて削除します。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param uri 上位階層
	 * @param async 非同期処理の場合true、同期処理の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(String uri, boolean async, boolean isParallel)
	throws IOException, TaggingException;

	/**
	 * フォルダ削除.
	 * <p>
	 * 指定された親階層とその配下のデータをまとめて削除します。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期処理の場合true、同期処理の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(RequestParam param, boolean async, 
			boolean isParallel)
	throws IOException, TaggingException;

	/**
	 * フォルダ削除.
	 * <p>
	 * 指定された親階層とその配下のデータをまとめて削除します。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期処理の場合true、同期処理の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(RequestParam param, boolean async,
			boolean isParallel, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * フォルダクリア.
	 * <p>
	 * 指定された親階層配下のデータをまとめて削除します。親階層自体は削除しません。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param uri 上位階層
	 * @param async 非同期処理の場合true、同期処理の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future clearFolder(String uri, boolean async, boolean isParallel)
	throws IOException, TaggingException;

	/**
	 * フォルダクリア.
	 * <p>
	 * 指定された親階層配下のデータをまとめて削除します。親階層自体は削除しません。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期処理の場合true、同期処理の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @return Future 同期の場合はnull
	 */
	public Future clearFolder(RequestParam param, boolean async, 
			boolean isParallel)
	throws IOException, TaggingException;

	/**
	 * フォルダクリア.
	 * <p>
	 * 指定された親階層配下のデータをまとめて削除します。親階層自体は削除しません。<br>
	 * この処理はトランザクションで括らないため、一貫性が保証されません。
	 * </p>
	 * @param param リクエストパラメータオブジェクト
	 * @param async 非同期処理の場合true、同期処理の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return Future 同期の場合はnull
	 */
	public Future clearFolder(RequestParam param, boolean async,
			boolean isParallel, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * コンテント登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * キーは PathInfo + ファイル名 が設定されます。(multipart/formdata の場合) <br>
	 * multipartでない場合、PathInfoの値がそのままファイル名になります。
	 * </p>
	 * @return ファイル名のリスト
	 */
	public FeedBase putContent()
	throws IOException, TaggingException;

	/**
	 * コンテントを設定サイズにリサイズして登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * キーは PathInfo + ファイル名 が設定されます。(multipart/formdata の場合) <br>
	 * multipartでない場合、PathInfoの値がそのままファイル名になります。
	 * </p>
	 * @return ファイル名のリスト
	 */
	public FeedBase putContentBySize()
	throws IOException, TaggingException;

	/**
	 * multipart/formdata のファイルをコンテント登録します.
	 * <p>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * </p>
	 * @param namesAndKeys キー: uri、値: key
	 * @return ファイル名のリスト
	 *         feedのlinkリストにキーとContent-Typeを設定して返却します.
	 */
	public FeedBase putContent(Map<String, String> namesAndKeys)
	throws IOException, TaggingException;

	/**
	 * multipart/formdata のファイルを、設定サイズにリサイズしてコンテント登録します.
	 * <p>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * </p>
	 * @param namesAndKeys キー: uri、値: key
	 * @return ファイル名のリスト
	 *         feedのlinkリストにキーとContent-Typeを設定して返却します.
	 */
	public FeedBase putContentBySize(Map<String, String> namesAndKeys)
	throws IOException, TaggingException;

	/**
	 * コンテント登録のための署名付きURL取得.
	 * @return feed.titleに署名付きURL
	 */
	public FeedBase putContentSignedUrl()
	throws IOException, TaggingException;

	/**
	 * コンテント自動採番登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * selfidは自動採番され、引数の親キーと合わせてキーが生成されます。<br>
	 * </p>
	 * @param parentUri 親キー
	 * @return コンテントエントリー
	 */
	public FeedBase postContent(String parentUri)
	throws IOException, TaggingException;

	/**
	 * コンテント自動採番登録.
	 * <p>
	 * リクエストデータをコンテント登録します。<br>
	 * 本クラスのコンストラクタにリクエスト・レスポンスを引数に設定して生成した場合のみ有効。<br>
	 * selfidは自動採番され、引数の親キーと合わせてキーが生成されます。<br>
	 * </p>
	 * @param parentUri 親キー
	 * @param ext 拡張子
	 * @return コンテントエントリー
	 */
	public FeedBase postContent(String parentUri, String ext)
	throws IOException, TaggingException;

	/**
	 * コンテント自動採番登録のための署名付きURL取得.
	 * @return feed.titleに署名付きURL
	 */
	public FeedBase postContentSignedUrl(String parentUri, String ext)
	throws IOException, TaggingException;

	/**
	 * コンテンツを取得.
	 * コンテンツ登録先にアクセスし、データ本体、ヘッダ情報を取得します。
	 * @param uri URI
	 * @return コンテンツ情報 (データ本体とヘッダ情報)
	 *         存在しない場合はnullを返します。
	 */
	public ReflexContentInfo getContent(String uri)
	throws IOException, TaggingException;

	/**
	 * コンテンツを取得.
	 * コンテンツ登録先にアクセスし、データ本体、ヘッダ情報を取得します。
	 * @param uri URI
	 * @param checkEtag Etagチェックを行う場合true
	 *                  リクエストのEtagが等しい場合、コンテンツ本体を返さずEtagのみ返します。
	 * @return コンテンツ情報 (データ本体とヘッダ情報)
	 *         存在しない場合はnullを返します。
	 */
	public ReflexContentInfo getContent(String uri, boolean checkEtag)
	throws IOException, TaggingException;

	/**
	 * コンテント取得のための署名付きURL取得.
	 * @param キー
	 * @return feed.titleに署名付きURL
	 */
	public FeedBase getContentSignedUrl(String uri)
	throws IOException, TaggingException;

	/**
	 * コンテンツを削除.
	 * @return コンテンツ削除後のEntry
	 */
	public EntryBase deleteContent(String uri)
	throws IOException, TaggingException;

	/**
	 * Contentを返す仮メソッド
	 * キーの先頭に"/_html"を付加する。
	 */
	public byte[] getHtmlContent(String requestUri)
	throws IOException, TaggingException;

	/**
	 * リクエストを取得.
	 * @return リクエスト
	 */
	public ReflexRequest getRequest();

	/**
	 * RXIDを取得.
	 * @return RXID
	 */
	public String getRXID()
	throws IOException, TaggingException;

	/**
	 * アクセストークンを取得
	 * @return アクセストークン
	 */
	public String getAccessToken()
	throws IOException, TaggingException;

	/**
	 * リンクトークンを取得
	 * @return リンクトークン
	 */
	public String getLinkToken(String uri)
	throws IOException, TaggingException;

	/**
	 * アクセスキーを更新
	 */
	public void changeAccessKey()
	throws IOException, TaggingException;

	/**
	 * サービス管理者権限のReflexContextを取得
	 * @return サービス管理者権限のReflexContext
	 */
	public ReflexContext getServiceAdminContext();

	/**
	 * キャッシュにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param feed Feed
	 * @return 登録したFeed
	 */
	public FeedBase setCacheFeed(String uri, FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * キャッシュにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param feed Feed
	 * @param sec 有効時間(秒)
	 * @return 登録したFeed
	 */
	public FeedBase setCacheFeed(String uri, FeedBase feed, Integer sec)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュにFeedを登録.
	 * @param uri 登録キー
	 * @param feed Feed
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheFeedIfAbsent(String uri, FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * キャッシュにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param entry Entry
	 * @return 登録したFeed
	 */
	public EntryBase setCacheEntry(String uri, EntryBase entry)
	throws IOException, TaggingException;

	/**
	 * キャッシュにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param entry Entry
	 * @param sec 有効時間(秒)
	 * @return 登録したFeed
	 */
	public EntryBase setCacheEntry(String uri, EntryBase entry, Integer sec)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュにEntryを登録.
	 * @param uri 登録キー
	 * @param entry Entry
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheEntryIfAbsent(String uri, EntryBase entry)
	throws IOException, TaggingException;

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param text 文字列
	 * @return 登録した文字列
	 */
	public String setCacheString(String uri, String text)
	throws IOException, TaggingException;

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param text 文字列
	 * @param sec 有効時間(秒)
	 * @return 登録した文字列
	 */
	public String setCacheString(String uri, String text, Integer sec)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param uri 登録キー
	 * @param text 文字列
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheStringIfAbsent(String uri, String text)
	throws IOException, TaggingException;

	/**
	 * キャッシュに数値を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param num 数値
	 * @return 登録した文字列
	 */
	public long setCacheLong(String uri, long num)
	throws IOException, TaggingException;

	/**
	 * キャッシュに数値を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param num 数値
	 * @param sec 有効時間(秒)
	 * @return 登録した数値
	 */
	public long setCacheLong(String uri, long num, Integer sec)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュに数値を登録.
	 * @param uri 登録キー
	 * @param num 数値
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setCacheLongIfAbsent(String uri, long num)
	throws IOException, TaggingException;

	/**
	 * キャッシュからFeedを削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheFeed(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュからEntryを削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheEntry(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュから文字列を削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheString(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュから整数値を削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteCacheLong(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュからFeedを取得.
	 * @param uri 登録キー
	 * @return Feed
	 */
	public FeedBase getCacheFeed(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュからEntryを取得.
	 * @param uri 登録キー
	 * @return Entry
	 */
	public EntryBase getCacheEntry(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュから文字列を取得.
	 * @param uri 登録キー
	 * @return 文字列
	 */
	public String getCacheString(String uri)
	throws IOException, TaggingException;

	/**
	 * キャッシュから文字列を取得.
	 * @param uri 登録キー
	 * @return 文字列
	 */
	public Long getCacheLong(String uri)
	throws IOException, TaggingException;

	/**
	 * Feedキャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheFeed(String uri, int sec)
	throws IOException, TaggingException;

	/**
	 * Entryキャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheEntry(String uri, int sec)
	throws IOException, TaggingException;

	/**
	 * 文字列キャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheString(String uri, int sec)
	throws IOException, TaggingException;

	/**
	 * 文字列キャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireCacheLong(String uri, int sec)
	throws IOException, TaggingException;

	/**
	 * キャッシュに指定された値を加算.
	 * @param uri キー
	 * @param num 加算値
	 * @return 加算後の値
	 */
	public long incrementCache(String uri, long num)
	throws IOException, TaggingException;

	/**
	 * キャッシュから文字列を削除.
	 * @param uri 登録キー
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean cacheFlushAll()
	throws IOException, TaggingException;

	/**
	 * セッションにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param feed Feed
	 * @return 登録したFeed
	 */
	public FeedBase setSessionFeed(String name, FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * セッションにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param entry Entry
	 * @return 登録したEntry
	 */
	public EntryBase setSessionEntry(String name, EntryBase entry)
	throws IOException, TaggingException;

	/**
	 * セッションに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param text 文字列
	 * @return 登録した文字列
	 */
	public String setSessionString(String name, String text)
	throws IOException, TaggingException;

	/**
	 * セッションに文字列を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name 登録キー
	 * @param text 文字列
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public String setSessionStringIfAbsent(String name, String text)
	throws IOException, TaggingException;

	/**
	 * セッションに数値を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param num 数値
	 * @return 登録した数値
	 */
	public long setSessionLong(String name, long num)
	throws IOException, TaggingException;

	/**
	 * セッションに数値を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name 登録キー
	 * @param num 数値
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public Long setSessionLongIfAbsent(String name, long num)
	throws IOException, TaggingException;

	/**
	 * セッションの指定されたキーの数値を加算.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param num 数値
	 * @return 登録した数値
	 */
	public long incrementSession(String name, long num)
	throws IOException, TaggingException;

	/**
	 * セッションからFeedを削除.
	 * @param name 登録キー
	 */
	public void deleteSessionFeed(String name)
	throws IOException, TaggingException;

	/**
	 * セッションからEntryを削除.
	 * @param name 登録キー
	 */
	public void deleteSessionEntry(String name)
	throws IOException, TaggingException;

	/**
	 * セッションから文字列を削除.
	 * @param name 登録キー
	 */
	public void deleteSessionString(String name)
	throws IOException, TaggingException;

	/**
	 * セッションから数値を削除.
	 * @param name 登録キー
	 */
	public void deleteSessionLong(String name)
	throws IOException, TaggingException;

	/**
	 * セッションからFeedを取得.
	 * @param name 登録キー
	 * @return Feed
	 */
	public FeedBase getSessionFeed(String name)
	throws IOException, TaggingException;

	/**
	 * セッションからEntryを取得.
	 * @param name 登録キー
	 * @return Entry
	 */
	public EntryBase getSessionEntry(String name)
	throws IOException, TaggingException;

	/**
	 * セッションから文字列を取得.
	 * @param name 登録キー
	 * @return 文字列
	 */
	public String getSessionString(String name)
	throws IOException, TaggingException;

	/**
	 * セッションから数値を取得.
	 * @param name 登録キー
	 * @return 数値
	 */
	public Long getSessionLong(String name)
	throws IOException, TaggingException;

	/**
	 * セッションへのFeed格納キー一覧を取得.
	 * @return セッションへのFeed格納キーリスト
	 */
	public List<String> getSessionFeedKeys()
	throws IOException, TaggingException;

	/**
	 * セッションへのEntry格納キー一覧を取得.
	 * @return セッションへのEntry格納キーリスト
	 */
	public List<String> getSessionEntryKeys()
	throws IOException, TaggingException;

	/**
	 * セッションへの文字列格納キー一覧を取得.
	 * @return セッションへの文字列格納キーリスト
	 */
	public List<String> getSessionStringKeys()
	throws IOException, TaggingException;

	/**
	 * セッションへの数値格納キー一覧を取得.
	 * @return セッションへの数値格納キーリスト
	 */
	public List<String> getSessionLongKeys()
	throws IOException, TaggingException;

	/**
	 * セッションへの格納キー一覧を取得.
	 * @return セッションへの値格納キーリスト。
	 *         キー: Feed, Entry, String, Longのいずれか
	 *         値: キーリスト
	 */
	public Map<String, List<String>> getSessionKeys()
	throws IOException, TaggingException;

	/**
	 * セッションの有効時間を延ばす
	 */
	public void resetExpire()
	throws IOException, TaggingException;

	/**
	 * 署名検証.
	 * @param uri キー
	 * @return 署名が正しい場合true
	 */
	public boolean checkSignature(String uri)
	throws IOException, TaggingException;

	/**
	 * 署名設定.
	 * すでに署名が設定されている場合は更新します。
	 * @param uri URI
	 * @param revision リビジョン
	 * @return 署名したEntry
	 */
	public EntryBase putSignature(String uri, Integer revision)
	throws IOException, TaggingException;

	/**
	 * 署名設定.
	 * link rel="self"に署名をします。
	 * Entryが存在しない場合は登録します。
	 * @param feed 署名対象Entryリスト
	 * @return 署名したEntryリスト
	 */
	public FeedBase putSignatures(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * 署名削除.
	 * @param uri URI
	 * @param revision リビジョン
	 */
	public void deleteSignature(String uri, Integer revision)
	throws IOException, TaggingException;

	/**
	 * 管理者によるユーザ登録.
	 * @param feed 登録ユーザ情報.
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByAdmin(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * グループ管理者によるユーザ登録.
	 * @param feed 登録ユーザ情報.
	 * @param groupName グループ名
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByGroupadmin(FeedBase feed, String groupName)
	throws IOException, TaggingException;

	/**
	 * 管理者によるパスワード更新.
	 * @param feed パスワード更新情報
	 * @return 更新情報
	 */
	public FeedBase changepassByAdmin(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * グループ管理者登録.
	 * @param feed 登録ユーザ情報.
	 * @return グループエントリーリスト
	 */
	public FeedBase createGroupadmin(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * グループ管理用グループを削除する.
	 * @param groupName グループ管理用グループ
	 * @param async 削除を非同期に行う場合true
	 */
	public void deleteGroupadmin(String groupName, boolean async)
	throws IOException, TaggingException;

	/**
	 * グループ管理用グループを削除する.
	 * @param feed グループ情報
	 * @param async 削除を非同期に行う場合true
	 */
	public void deleteGroupadmin(FeedBase feed, boolean async)
	throws IOException, TaggingException;

	/**
	 * ログを出力します.
	 * <p>
	 * ログエントリー ( /_log/xxxx ) に指定したメッセージを登録します。<br>
	 * タグに指定した値はtitle、サブタイトルに指定した値はsubtitle、
	 * メッセージに指定した値はsummaryに登録されます。<br>
	 * </p>
	 * @param title タグ
	 * @param subtitle サブタイトル
	 * @param message メッセージ
	 */
	public void log(String title, String subtitle, String message);

	/**
	 * ログを出力します.
	 * <p>
	 * ログエントリー ( /_log/xxxx ) に指定したメッセージを登録します。<br>
	 * </p>
	 * @param feed feed
	 */
	public void log(FeedBase feed) throws IOException, TaggingException;

	/**
	 * サービス固有の設定値を返却.
	 *   /_settings/properties に設定された値のみ参照し返却.
	 * @param key キー
	 * @return 設定値
	 */
	public String getSettingValue(String key);

	/**
	 * テキストメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param to  送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendMail(String title, String textMessage, String to,
			List<DataSource> attachments)
	throws IOException, TaggingException;

	/**
	 * テキストメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param to  送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendMail(String title, String textMessage,
			String[] to, String[] cc, String[] bcc,
			List<DataSource> attachments)
	throws IOException, TaggingException;

	/**
	 * HTMLメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 *                    (インライン画像は<IMG src="cid:{コンテンツのキー}">を指定する。)
	 * @param to  送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendHtmlMail(String title, String textMessage, String htmlMessage,
			String to, List<DataSource> attachments)
	throws IOException, TaggingException;

	/**
	 * HTMLメール送信.
	 * @param title メールのタイトル
	 * @param textMessage テキストメッセージ
	 * @param htmlMessage HTMLメッセージ
	 *                    (インライン画像は<IMG src="cid:{コンテンツのキー}">を指定する。)
	 * @param to  送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param attachments 添付ファイル
	 */
	public void sendHtmlMail(String title, String textMessage, String htmlMessage,
			String[] to, String[] cc, String[] bcc, List<DataSource> attachments)
	throws IOException, TaggingException;

	/**
	 * メール送信 (テキスト・HTML).
	 * @param entry メールの送信内容
	 *              title : メールのタイトル
	 *              summary : テキストメッセージ
	 *              content : HTMLメッセージ
	 * @param to 送信先アドレス
	 * @param attachments 添付ファイルのコンテンツキーリスト
	 */
	public void sendMail(EntryBase entry, String to, String[] attachments)
	throws IOException, TaggingException;

	/**
	 * メール送信 (テキスト・HTML).
	 * @param entry メールの送信内容
	 *              title : メールのタイトル
	 *              summary : テキストメッセージ
	 *              content : HTMLメッセージ
	 * @param to 送信先アドレス
	 * @param cc CCでの送信先アドレス
	 * @param bcc BCCでの送信先アドレス
	 * @param attachments 添付ファイルのコンテンツキーリスト
	 */
	public void sendMail(EntryBase entry, String[] to,
			String[] cc, String[] bcc, String[] attachments)
	throws IOException, TaggingException;

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param requestUri キー+検索条件
	 * @param pageNum カーソルリスト作成ページ
	 *                "最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(String requestUri, String pageNum)
	throws IOException, TaggingException;

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param param 検索条件、カーソルリスト作成ページ
	 *              カーソルリスト作成ページは"最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param requestUri キー+検索条件
	 * @param pageNum カーソルリスト作成ページ
	 *                "最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(String requestUri, String pageNum, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * ページング機能のためのカーソルリスト作成.
	 * @param param 検索条件、カーソルリスト作成ページ
	 *              カーソルリスト作成ページは"最終ページ"か、"開始ページ-最終ページ"を指定。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 最終ページ番号
	 */
	public FeedBase pagination(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * ページ指定検索
	 * @param requestUri キー+検索条件
	 * @param pageNum ページ番号
	 * @return 検索結果
	 */
	public FeedBase getPage(String requestUri, String pageNum)
	throws IOException, TaggingException;

	/**
	 * ページ指定検索
	 * @param param キー、検索条件、ページ番号
	 * @return 検索結果
	 */
	public FeedBase getPage(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * ページ指定検索
	 * @param requestUri キー+検索条件
	 * @param pageNum ページ番号
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 検索結果
	 */
	public FeedBase getPage(String requestUri, String pageNum, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * ページ指定検索
	 * @param param キー、検索条件、ページ番号
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @return 検索結果
	 */
	public FeedBase getPage(RequestParam param, String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException;

	/**
	 * ユーザステータス取得.
	 * ユーザ管理者のみ実行可能
	 * @param email ユーザ名
	 * @return ユーザステータス (entryのsummaryにユーザステータス)
	 */
	public EntryBase getUserstatus(String email) throws IOException, TaggingException;

	/**
	 * ユーザステータス一覧を取得.
	 * @param param リクエストパラメータ
	 * @return ユーザステータス一覧
	 */
	public FeedBase getUserstatusList(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * ユーザステータス一覧を取得.
	 * @param limitStr 一覧最大件数。nullはデフォルト値。*は制限なし。
	 * @param cursorStr カーソル
	 * @return ユーザステータス一覧
	 */
	public FeedBase getUserstatusList(String limitStr, String cursorStr)
	throws IOException, TaggingException;

	/**
	 * ユーザを無効にする.
	 * @param email ユーザ名
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 */
	public EntryBase revokeUser(String email, boolean isDeleteGroups)
	throws IOException, TaggingException;

	/**
	 * ユーザを無効にする.
	 * @param feed ユーザ情報
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 */
	public FeedBase revokeUser(FeedBase feed, boolean isDeleteGroups)
	throws IOException, TaggingException;

	/**
	 * ユーザを有効にする.
	 * @param email ユーザ名
	 */
	public EntryBase activateUser(String email)
	throws IOException, TaggingException;

	/**
	 * ユーザを有効にする.
	 * @param feed ユーザ情報
	 */
	public FeedBase activateUser(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * ユーザを削除する.
	 * @param email ユーザ名
	 * @param async 削除を非同期に行う場合true
	 * @return 処理対象ユーザのトップエントリー
	 */
	public EntryBase deleteUser(String email, boolean async)
	throws IOException, TaggingException;

	/**
	 * ユーザを削除する.
	 * @param feed ユーザ情報
	 * @param async 削除を非同期に行う場合true
	 * @return 処理対象ユーザのトップエントリーリスト
	 */
	public FeedBase deleteUser(FeedBase feed, boolean async)
	throws IOException, TaggingException;

	/**
	 * エイリアスを追加する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	public FeedBase addAlias(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * エイリアスを削除する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	public FeedBase removeAlias(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * ACLを追加する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	public FeedBase addAcl(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * ACLを削除する.
	 * @param feed 更新したEntry
	 * @return 処理対象エントリーリスト
	 */
	public FeedBase removeAcl(FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(FeedBase feed, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(FeedBase feed, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryにデータを登録する.
	 * ログデータ用。Feedでなく、テーブル名と、項目名と値のMapを詰めたリストを指定する。
	 * @param tableName テーブル名
	 * @param list 項目名と値のリスト
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(String tableName, List<Map<String, Object>> list, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uri 削除キー。末尾に*(ワイルドカード)指定可能。
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String uri, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uri 削除キー。末尾に*(ワイルドカード)指定可能。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String uri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uris 削除キーリスト。末尾に*(ワイルドカード)指定可能。
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String[] uris, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryからデータを削除する.
	 * 削除データの登録を行う。
	 * @param uris 削除キーリスト。末尾に*(ワイルドカード)指定可能。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String[] uris, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException;

	/**
	 * BigQueryに対しSQLを実行し、結果を取得する.
	 * @param sql SQL
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryBq(String sql)
	throws IOException, TaggingException;

	/**
	 * SDK呼び出し.
	 * @param name プロパティファイルに設定した、SDK実行クラス名に対応するname
	 * @param args SDK実行クラス実行時の引数
	 */
	public FeedBase callSDK(String name, String args[])
	throws IOException, TaggingException;

	/**
	 * インデックス更新.
	 * @param feed インデックス更新情報
	 * @param isDelete 削除の場合true
	 */
	public void putIndex(FeedBase feed, boolean isDelete)
	throws IOException, TaggingException;

	/**
	 * 条件検索のインデックス使用チェック.
	 * @param param 検索条件
	 * @return メッセージ (Feedのtitleに設定)
	 */
	public FeedBase checkIndex(RequestParam param)
	throws IOException, TaggingException;

	/**
	 * メッセージ通知
	 * @param body メッセージ
	 * @param to 送信先 (UID, account or group)
	 */
	public void pushNotification(String body, String[] to)
	throws IOException, TaggingException;

	/**
	 * メッセージ通知
	 * @param feed 通知メッセージ。entryの内容は以下の通り。
	 *          title: Push通知タイトル
	 *          subtitle: Push通知サブタイトル
	 *          content: Push通知メッセージ本文(body)
	 *          summary: dataに設定するメッセージ(Expo用)
	 *          category _$scheme="imageurl" _$label={通知イメージURL}(FCM用)
	 *          category _$scheme={dataのキー} _$label={dataの値}(Expo用)
	 * @param to 送信先 (UID, account or group)
	 */
	public void pushNotification(FeedBase feed, String[] to)
	throws IOException, TaggingException;

	/**
	 * WebSocketメッセージ送信.
	 * @param messageFeed メッセージ情報格納Feed。entryの内容は以下の通り。
	 *          summary : メッセージ
	 *          link rel="to"のhref属性 : 送信先。以下のいずれか。複数指定可。
	 *              UID
	 *              アカウント
	 *              グループ(*)
	 *              ポーリング(#)
	 *          title : WebSocket送信ができなかった場合のPush通知のtitle
	 *          subtitle : WebSocket送信ができなかった場合のPush通知のsubtitle(Expo用)
	 *          content : WebSocket送信ができなかった場合のPush通知のbody
	 *          category : WebSocket送信ができなかった場合のPush通知のdata(Expo用、key-value形式)
	 *              dataのキーに_$schemeの値、dataの値に_$labelの値をセットする。
	 *          rights : trueが指定されている場合、WebSocket送信ができなかった場合にPush通知しない。
	 * @param channel チャネル
	 */
	public void sendWebSocket(FeedBase messageFeed, String channel)
	throws IOException, TaggingException;

	/**
	 * WebSocket接続をクローズ.
	 * 認証ユーザのWebSocketについて、指定されたチャネルの接続をクローズする。
	 * @param channel チャネル
	 */
	public void closeWebSocket(String channel)
	throws IOException, TaggingException;

	/**
	 * ログアウト.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return ログアウトメッセージ
	 */
	public FeedBase logout(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * 指定したURI配下のキーのエントリーで自分が署名していないものを取得.
	 * ただしすでにグループ参加状態のものは除く。
	 * @param uri 親キー
	 * @return 親キー配下のエントリーで署名していないEntryリスト
	 */
	public FeedBase getNoGroupMember(String uri) 
	throws IOException, TaggingException;
	
	/**
	 * PDF生成.
	 * @param htmlTemplate HTML形式テンプレート
	 * @return PDFデータ
	 */
	public byte[] toPdf(String htmlTemplate) 
	throws IOException, TaggingException;

	/**
	 * メッセージキュー使用ON/OFF設定
	 * @param flag メッセージキューを使用する場合true
	 * @param channel チャネル
	 */
	public void setMessageQueueStatus(boolean flag, String channel)
	throws IOException, TaggingException;

	/**
	 * メッセージキュー使用ON/OFF設定を取得
	 * @param channel チャネル
	 */
	public boolean getMessageQueueStatus(String channel)
	throws IOException, TaggingException;

	/**
	 * メッセージキューへメッセージ送信
	 * @param feed メッセージ
	 * @param channel チャネル
	 */
	public void setMessageQueue(FeedBase feed, String channel)
	throws IOException, TaggingException;
	
	/**
	 * メッセージキューからメッセージ受信
	 * @param channel チャネル
	 * @return メッセージ
	 */
	public FeedBase getMessageQueue(String channel)
	throws IOException, TaggingException;
	
	/**
	 * グループに参加登録する.
	 * 署名はなし
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @return グループエントリー
	 */
	public EntryBase addGroup(String group, String selfid)
	throws IOException, TaggingException;
	
	/**
	 * 管理者によるグループ参加登録する.
	 * ユーザ管理者・グループ管理者のみ実行可
	 * 署名はなし
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param feed UIDリスト
	 * @return グループエントリー
	 */
	public FeedBase addGroupByAdmin(String group, String selfid, FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * グループに参加署名する.
	 * グループ作成者がユーザへの参加承認をしている状態の時、ユーザがグループに参加する。
	 * 自身のグループエイリアス作成(登録がない場合→再加入を想定)と、自身のグループエイリアスへの署名を行う。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @return グループエントリー
	 */
	public EntryBase joinGroup(String group, String selfid)
	throws IOException, TaggingException;
	
	/**
	 * グループから退会する.
	 * グループエントリーの、自身のグループエイリアスを削除する。
	 * @param group グループ名
	 * @return 退会したグループエントリー
	 */
	public EntryBase leaveGroup(String group)
	throws IOException, TaggingException;
	
	/**
	 * 管理者によるグループからの退会処理.
	 * 署名はなし。
	 * @param group グループ名
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @return 退会したグループエントリー
	 */
	public FeedBase leaveGroupByAdmin(String group, FeedBase feed)
	throws IOException, TaggingException;

	/**
	 * RDBへ更新系SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	public int[] execRdb(String[] sqls)
	throws IOException, TaggingException;

	/**
	 * RDBへ更新系SQLを実行する.
	 * 大量データ登録を想定。AutoCommitがデフォルト(true)の場合、SQLリストはトランザクションで括られない。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	public int[] bulkExecRdb(String[] sqls)
	throws IOException, TaggingException;

	/**
	 * 非同期でRDBへ更新系SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFuture
	 */
	public Future<int[]> execRdbAsync(String[] sqls)
	throws IOException, TaggingException;

	/**
	 * 非同期でRDBへ更新系SQLを実行する.
	 * 大量データ登録を想定。AutoCommitがデフォルト(true)の場合、SQLリストはトランザクションで括られない。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFuture
	 */
	public Future<int[]> bulkExecRdbAsync(String[] sqls)
	throws IOException, TaggingException;

	/**
	 * AutoCommit設定を取得する.
	 * @return autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public boolean getAutoCommitRdb()
	throws IOException, TaggingException;
	
	/**
	 * AutoCommitを設定する.
	 * デフォルトはtrue。
	 * execSqlにおいて非同期処理(async=true)の場合、この設定は無効になる。(非同期処理の場合AutoCommit=true)
	 * @param autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public void setAutoCommitRdb(boolean autoCommit)
	throws IOException, TaggingException;

	/**
	 * コミットを実行する.
	 * AutoCommit=falseの場合に有効。
	 */
	public void commitRdb()
	throws IOException, TaggingException;
	
	/**
	 * ロールバックを実行する.
	 * AutoCommit=falseの場合に有効。
	 */
	public void rollbackRdb()
	throws IOException, TaggingException;

	/**
	 * RDBに対しSQLを実行し、結果を取得する.
	 * @param sql SQL
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryRdb(String sql)
	throws IOException, TaggingException;

	/**
	 * BDBにEntryを登録し、非同期でBigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param parentUri Entry登録でキーを自動採番する場合の親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの登録について非同期の場合true、同期の場合false
	 * @return 登録したEntryリスト
	 */
	public FeedBase postBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames,
			boolean async)
	throws IOException, TaggingException;

	/**
	 * BDBのEntryを更新し、非同期でBigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param parentUri Entry登録でキーを自動採番する場合の親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの更新について非同期の場合true、同期の場合false
	 * @return 更新したEntryリスト
	 */
	public FeedBase putBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames,
			boolean async)
	throws IOException, TaggingException;

	/**
	 * BDBのEntryを削除し、非同期でBigQueryに削除データを登録する.
	 * @param uris 削除キーリスト。ワイルドカードは指定不可。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの削除について非同期の場合true、同期の場合false
	 * @return 更新したEntryリスト
	 */
	public FeedBase deleteBdbq(String[] uris, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException;

	/**
	 * BDBのEntryを更新し、BigQueryにEntryを登録する.
	 * Feedの一貫性は保証しない。
	 * @param feed Feed
	 * @param parentUri Entry登録でキーを自動採番する場合の親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの登録について非同期の場合true、同期の場合false
	 * @return 更新したEntryリスト
	 */
	public List<Future<List<UpdatedInfo>>> bulkPutBdbq(
			FeedBase feed, String parentUri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException;

}
