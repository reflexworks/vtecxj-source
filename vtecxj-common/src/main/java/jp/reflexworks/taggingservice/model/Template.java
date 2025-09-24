package jp.reflexworks.taggingservice.model;

/**
 * テンプレート.
 */
public class Template {
	
	/** エンティティ定義 */
	public String[] template;
	/** Index・暗号化・項目ACL定義 */
	public String[] rights;
	
	/**
	 * コンストラクタ
	 * @param template エンティティ定義
	 * @param rights Index・暗号化・項目ACL定義
	 */
	public Template(String[] template, String[] rights) {
		this.template = template;
		this.rights = rights;
	}

}
