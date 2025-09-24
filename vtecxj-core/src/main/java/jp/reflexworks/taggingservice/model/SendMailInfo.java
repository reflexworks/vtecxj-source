package jp.reflexworks.taggingservice.model;

import jp.reflexworks.atom.entry.EntryBase;

/**
 * メール送信 情報クラス.
 */
public class SendMailInfo {
	
	/** メール内容 */
	public EntryBase entry;
	/** TO */
	public String[] to;
	/** CC */
	public String[] cc;
	/** BCC */
	public String[] bcc;
	/** 添付ファイル */
	public String[] attachments;
	
	/**
	 * コンストラクタ.
	 * @param entry メール内容
	 * @param to TO
	 * @param cc CC
	 * @param bcc BCC
	 * @param attachments 添付ファイル
	 */
	public SendMailInfo(EntryBase entry, String[] to, String[] cc, String[] bcc, String[] attachments) {
		this.entry = entry;
		this.to = to;
		this.cc = cc;
		this.bcc = bcc;
		this.attachments = attachments;
	}

	/**
	 * メール内容を取得
	 * @return メール内容
	 */
	public EntryBase getEntry() {
		return entry;
	}

	/**
	 * TOを取得
	 * @return TO
	 */
	public String[] getTo() {
		return to;
	}

	/**
	 * CCを取得
	 * @return CC
	 */
	public String[] getCc() {
		return cc;
	}

	/**
	 * BCCを取得
	 * @return BCC
	 */
	public String[] getBcc() {
		return bcc;
	}

	/**
	 * 添付ファイルを取得
	 * @return 添付ファイル
	 */
	public String[] getAttachments() {
		return attachments;
	}

}
