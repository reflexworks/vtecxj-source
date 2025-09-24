package jp.reflexworks.taggingservice.util;

import java.util.Date;
import java.util.TimeZone;

import jp.reflexworks.atom.entry.Author;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.sourceforge.reflex.util.DateUtil;

public class MetadataUtil {

	/**
	 * 作成者、更新者、削除者を取得
	 * @param auth Authentication information
	 * @param flg 1:INSERT, 2:UPDATE, 3:DELETE, 9:Service呼び出し
	 */
	public static Author getAuthor(ReflexAuthentication auth, OperationType flg) {
		if (auth == null) {
			return null;
		}
		Author author = new Author();

		String urn = null;
		if (flg == OperationType.INSERT) {
			urn = getCreatedUrn(auth);
		} else if (flg == OperationType.DELETE) {
			urn = getDeletedUrn(auth);
		} else {
			urn = getUpdatedUrn(auth);
		}
		author.setUri(urn);

		return author;
	}

	/**
	 * 作成者のurnを取得
	 * @param auth 認証情報
	 */
	public static String getCreatedUrn(ReflexAuthentication auth) {
		StringBuilder urn = new StringBuilder();
		urn.append(Constants.URN_PREFIX_CREATED);
		urn.append(getOperater(auth));
		return urn.toString();
	}

	/**
	 * 更新者のurnを取得
	 * @param auth 認証情報
	 */
	public static String getUpdatedUrn(ReflexAuthentication auth) {
		StringBuilder urn = new StringBuilder();
		urn.append(Constants.URN_PREFIX_UPDATED);
		urn.append(getOperater(auth));
		return urn.toString();
	}

	/**
	 * 削除者のurnを取得
	 * @param auth 認証情報
	 */
	public static String getDeletedUrn(ReflexAuthentication auth) {
		StringBuilder urn = new StringBuilder();
		urn.append(Constants.URN_PREFIX_DELETED);
		urn.append(getOperater(auth));
		return urn.toString();
	}

	/**
	 * 操作者を取得.
	 * @param auth 認証情報
	 * @return 操作者のUID
	 */
	private static String getOperater(ReflexAuthentication auth) {
		return auth.getUid();
	}

	/**
	 * 現在時刻を取得
	 * @return 現在時刻
	 */
	public static String getCurrentTime() {
		return getCurrentTime(TimeZone.getDefault().getID());
	}

	/**
	 * 現在時刻を取得
	 * @param timezoneId タイムゾーンID
	 * @return 現在時刻
	 */
	public static String getCurrentTime(String timezoneId) {
		Date date = new Date();
		return DateUtil.getDateTimeMillisec(date, timezoneId);
	}

}
