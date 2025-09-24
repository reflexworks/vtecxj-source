package jp.reflexworks.taggingservice.bdb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BDB 定数クラス.
 */
public class ManifestConst {

	/** `DBManifest` : マニフェスト */
	static final String DB_MANIFEST = "DBManifest";
	/** `DBManifestAncestor` : マニフェスト更新用Ancestor */
	static final String DB_MANIFEST_ANCESTOR = "DBManifestAncestor";

	/** テーブル名リスト */
	public static final List<String> DB_NAMES = new CopyOnWriteArrayList<String>();
	static {
		DB_NAMES.add(DB_MANIFEST);
		DB_NAMES.add(DB_MANIFEST_ANCESTOR);
	}

}
