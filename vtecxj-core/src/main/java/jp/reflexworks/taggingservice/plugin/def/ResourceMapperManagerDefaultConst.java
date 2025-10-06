package jp.reflexworks.taggingservice.plugin.def;

/**
 * ResourceMapper管理 定数クラス.
 */
public interface ResourceMapperManagerDefaultConst {
	
	/**
	 * Atom項目のインデックス・暗号化・項目ACLデフォルト値
	 */
	public static final String[] DEFAULT_RIGHTS = {
		"contributor=@+RW,/_group/$admin+RW,/_group/$useradmin+RW,/_group/$groupadmin_.*+RW",
		"contributor.uri#",
		"rights#=@+RW,/_group/$admin+RW",
		"title:^/_user$"
	};

}
