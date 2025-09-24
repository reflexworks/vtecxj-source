package jp.reflexworks.taggingservice.bdb;

import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.LockMode;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * BDB 定数クラス.
 */
public interface BDBConst {

	/** メモリ上のstaticオブジェクト格納キー : BDB環境オブジェクトMap */
	public static final String STATIC_NAME_BDBENV_MAP ="_bdbenv_map";
	/** メモリ上のstaticオブジェクト格納キー : BDB環境ロックMap */
	public static final String STATIC_NAME_BDBENV_LOCK_MAP ="_bdbenv_lock_map";
	/** メモリ上のstaticオブジェクト格納キー : BDB環境へのアクセス時間Map */
	public static final String STATIC_NAME_BDBENV_ACCESSTIME_MAP ="_bdbenv_accesstime_map";

	/** Encoding */
	public static final String ENCODING = AtomConst.ENCODING;

	/** Manifestのuriに登録する親階層の終端 */
	public static final String END_PARENT_URI_STRING = Constants.END_PARENT_URI_STRING;

	/** 文字コードの終端 */
	public static final String END_STRING = "\uffff";
	/** 文字コードの先端 */
	public static final String START_STRING = "\u0001";
	/** 前方一致検索の終端 (BDBのFeed検索は全て前方一致検索になる。) */
	public static final String FOWARD_MATCHING_END = "\ufffd";
	/** 小なり指定の終端 */
	public static final String LESS_THAN_END = "\u0002";
	/** Index項目値とselfidの区切り文字 */
	public static final String INDEX_SELF = Constants.START_STRING;

	/** ロックモード */
	static final LockMode LOCK_MODE = LockMode.DEFAULT;
	/** カーソル設定 */
	static final CursorConfig CURSOR_CONFIG = CursorConfig.DEFAULT;

	/** BDBに格納するEntryデータをDeflate圧縮しない場合true。圧縮する場合false。 */
	public static final boolean IS_DISABLE_DEFLATE = false;

}
