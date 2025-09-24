package jp.reflexworks.taggingservice.util;

/**
 * Logger Task定数
 */
public interface LoggerConst {

	/** Logger のメソッド */
	public static final String METHOD = Constants.POST;
	/** Param : ServiceName */
	public static final String PARAM_TITLE = "title";
	/** Param : ServiceName */
	public static final String PARAM_SUBTITLE = "subtitle";
	/** Param : ServiceName */
	public static final String PARAM_SUMMARY = "summary";

	/** Logger QueueName */
	public static final String LOGGER_QUEUE_NAME = "Logger";
	/** Logger URL */
	public static final String LOGGER_URL = "/sys/logger";
	
	/** Logger Level : INFO */
	public static final String LEVEL_INFO = "INFO";
	/** Logger Level : WARN */
	public static final String LEVEL_WARN = "WARN";
	/** Logger Level : ERROR */
	public static final String LEVEL_ERROR = "ERROR";
	/** Logger Level : FATAL */
	public static final String LEVEL_FATAL = "FATAL";
	

}
