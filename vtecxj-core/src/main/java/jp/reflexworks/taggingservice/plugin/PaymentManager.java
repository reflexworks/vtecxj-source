package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * オンライン決済管理プラグインクラス.
 */
public interface PaymentManager extends ReflexPlugin {
	
	/**
	 * 課金処理.
	 * 支払い手続きのための処理を行い。カード決済のリダイレクトURLを返却する。
	 * @param targetServiceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param reflexContext ReflexContext
	 * @param リダイレクトURL
	 */
	public String registerPayment(String targetServiceName, EntryBase serviceEntry, 
			ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * 課金処理の期間終了.
	 * サービスステータスをproductionからstagingに変更したときに呼び出される処理
	 * @param targetServiceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param reflexContext ReflexContext
	 */
	public void cancelPayment(String targetServiceName, EntryBase serviceEntry, 
			ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * 課金処理の削除.
	 * productionサービスが削除されたときに呼び出される処理
	 * @param targetServiceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param reflexContext ReflexContext
	 */
	public void deletePayment(String targetServiceName, EntryBase serviceEntry, 
			ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * カスタマーポータル画面のリンク発行処理.
	 * @param reflexContext ReflexContext
	 */
	public String billingPortal(ReflexContext reflexContext)
	throws IOException, TaggingException;

}
