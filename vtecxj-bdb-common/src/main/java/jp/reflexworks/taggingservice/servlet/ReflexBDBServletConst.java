package jp.reflexworks.taggingservice.servlet;

import jp.reflexworks.servlet.ReflexServletConst;

/**
 * Reflex BDB Servletで使用する定数
 */
public interface ReflexBDBServletConst extends ReflexServletConst {

	/** text/xml + ";" */
	public static final String CONTENT_TYPE_XML_SEMICOLON = CONTENT_TYPE_XML + ";";
	/** text/xml + " " */
	public static final String CONTENT_TYPE_XML_SPACE = CONTENT_TYPE_XML + " ";
	/** application/json + ";" */
	public static final String CONTENT_TYPE_JSON_SEMICOLON = CONTENT_TYPE_JSON + ";";
	/** application/json + " " */
	public static final String CONTENT_TYPE_JSON_SPACE = CONTENT_TYPE_JSON + " ";
	/** application/x-msgpack + ";" */
	public static final String CONTENT_TYPE_MESSAGEPACK_SEMICOLON = CONTENT_TYPE_MESSAGEPACK + ";";
	/** application/x-msgpack + " " */
	public static final String CONTENT_TYPE_MESSAGEPACK_SPACE = CONTENT_TYPE_MESSAGEPACK + " ";


}
