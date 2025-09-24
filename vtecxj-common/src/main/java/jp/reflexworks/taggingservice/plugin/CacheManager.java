package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * キャッシュ管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 * @param <T> コネクション
 */
public interface CacheManager extends ReflexPlugin {

	/**
	 * キャッシュにFeedを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param name キー
	 * @param feed Feed
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String name, FeedBase feed, Integer sec,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュにEntryを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param name キー
	 * @param entry Entry
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録したEntry
	 */
	public EntryBase setEntry(String name, EntryBase entry, Integer sec,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param name キー
	 * @param text 文字列
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String setString(String name, String text, Integer sec,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュに整数値を設定.
	 * @param name キー
	 * @param val 設定値
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定した整数値
	 */
	public long setLong(String name, long val, Integer sec, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュにFeedを登録.
	 * @param name キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setFeedIfAbsent(String name, FeedBase feed,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュにEntryを登録.
	 * @param name キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setEntryIfAbsent(String name, EntryBase entry,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param name キー
	 * @param text 文字列
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setStringIfAbsent(String name, String text,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * データが存在しない場合のみキャッシュに数値を登録.
	 * @param name キー
	 * @param num 数値
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setLongIfAbsent(String name, long num,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュに加算.
	 * @param name キー
	 * @param val 加算値
	 * @param reflexContext ReflexContext
	 */
	public long increment(String name, long val, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュからFeedを削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 削除完了の場合true、データ存在なしの場合false
	 */
	public boolean deleteFeed(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュからEntryを削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 削除完了の場合true、データ存在なしの場合false
	 */
	public boolean deleteEntry(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュから文字列を削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 削除完了の場合true、データ存在なしの場合false
	 */
	public boolean deleteString(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュから整数値を削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 削除完了の場合true、データ存在なしの場合false
	 */
	public boolean deleteLong(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュからFeedを取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Feed
	 */
	public FeedBase getFeed(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュからEntryを取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Entry
	 */
	public EntryBase getEntry(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュから文字列を取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public String getString(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * キャッシュから整数値を取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 整数値
	 */
	public Long getLong(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * Feedキャッシュの有効期限を設定
	 * @param name キー
	 * @param sec 有効期限(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定完了の場合true、データ存在なしの場合false
	 */
	public boolean setExpireFeed(String name, int sec, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * Entryキャッシュの有効期限を設定
	 * @param name キー
	 * @param sec 有効期限(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定完了の場合true、データ存在なしの場合false
	 */
	public boolean setExpireEntry(String name, int sec, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 文字列キャッシュの有効期限を設定
	 * @param name キー
	 * @param sec 有効期限(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定完了の場合true、データ存在なしの場合false
	 */
	public boolean setExpireString(String name, int sec, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 整数値キャッシュの有効期限を設定
	 * @param name キー
	 * @param sec 有効期限(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定完了の場合true、データ存在なしの場合false
	 */
	public boolean setExpireLong(String name, int sec, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * Redisの全てのデータを削除.
	 * メンテナンス用
	 * @param reflexContext ReflexContext
	 * @return 実行結果
	 */
	public String flushAll(BaseReflexContext reflexContext)
	throws IOException, TaggingException;

}
