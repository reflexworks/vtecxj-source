package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.Author;
import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.SignatureBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.HierarchyFormatException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.Template;
import jp.reflexworks.taggingservice.plugin.ResourceMapperManager;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * チェックユーティリティ
 */
public final class CheckUtil extends ReflexCheckUtil {

	/** 自動採番指定階層 */
	private static final String URI_NUMBERING = Constants.URI_NUMBERING;
	/** 自動採番指定階層の長さ */
	private static final int URI_NUMBERING_LEN = Constants.URI_NUMBERING.length();
	/** URN length : acl */
	private static final int URN_PREFIX_ACL_LEN = Constants.URN_PREFIX_ACL.length();

	/** OR演算子で分割する際のエスケープ条件 */
	private static final String OR_REGEX = "\\" + Condition.OR;

	/**
	 * 生成不可コンストラクタ.
	 */
	private CheckUtil() {}

	/**
	 * 引数のオブジェクトがnullの場合エラーを返します。
	 * このメソッドは、メソッド呼び出し時の引数に正しく値が設定されているかの確認に使用します。
	 * 返却するエラーはIllegalArgumentExceptionであり、サービス側の原因でエラーが発生したことを意味します。
	 * @param obj オブジェクト
	 * @param name エラーの場合メッセージに表示する名前
	 * @throws IllegalArgumentException 引数のオブジェクトがnullの場合
	 */
	public static void checkArgNull(Object obj, String name) {
		if (obj == null) {
			throw new IllegalParameterException("Argument is required : " + name);
		}
		if (obj instanceof Collection) {
			// コレクションが空の場合もエラー
			Collection<?> collection = (Collection<?>)obj;
			if (collection.isEmpty()) {
				throw new IllegalParameterException("Argument's element is required : " + name);
			}
		}
	}

	/**
	 * キーチェック（削除処理・サービス作成処理で使用）
	 * エラーの場合、IllegalParameterExceptionを返します。
	 * @param entry Entry
	 */
	public static void checkEntryKey(EntryBase entry) {
		// キーおよび必須項目チェック
		if (entry == null) {
			throw new IllegalParameterException("Entry is required.");
		}
		if (entry.getLink() == null) {
			throw new IllegalParameterException("Link is required.");
		}
		boolean hasSelf = false;
		for (Link link : entry.getLink()) {
			if (link == null) {
				continue;
			}
			if ("self".equals(link.get$rel())) {
				// selfは1エントリー1件のみ
				if (hasSelf) {
					throw new IllegalParameterException("Please set only one key.");
				}
				if (link.get$href() != null && link.get$href().length() > 0) {
					hasSelf = true;
				} else {
					throw new IllegalParameterException("Key is required.");
				}
			}
		}
		if (!hasSelf) {
			throw new IllegalParameterException("Key is required.");
		}
	}

	/**
	 * キーチェック（登録・更新処理で使用）
	 * エラーの場合、IllegalParameterExceptionを返します。
	 * @param entry Entry
	 * @param uri 自動採番の場合、親キーを設定。
	 */
	public static void checkEntryKey(EntryBase entry, String uri) {
		// キーおよび必須項目チェック
		if (entry == null) {
			throw new IllegalParameterException("Entry is required.");
		}
		boolean hasSelf = false;
		// Entryに指定されたURIのチェック
		if (entry.getLink() != null) {
			for (Link link : entry.getLink()) {
				if (link == null) {
					continue;
				}
				if (Link.REL_SELF.equals(link.get$rel())) {
					// selfは1エントリー1件のみ
					if (hasSelf) {
						throw new IllegalParameterException("Please set only one key.");
					}
					if (link.get$href() != null && link.get$href().length() > 0) {
						hasSelf = true;
						String selfUri = link.get$href();
						checkUri(selfUri);
						checkLastSlash(selfUri, "Key");
					} else {
						throw new IllegalParameterException("Key is required.");
					}
				}
			}
		}

		// 自動採番の場合のチェック
		boolean isNumbering = false;
		String numberingUri = null;
		if (!hasSelf) {
			if (uri == null || uri.trim().length() == 0 || "/".equals(uri)) {
				throw new IllegalParameterException("Key is required.");
			}
			checkUri(uri);
			isNumbering = true;
			numberingUri = TaggingEntryUtil.removeLastSlash(uri);
		}

		// エイリアスのURIチェック
		Set<String> aliasUris = new HashSet<String>();
		if (entry.getLink() != null) {
			boolean existAlias = false;
			int cntBlankAlias = 0;
			for (Link link : entry.getLink()) {
				if (link == null) {
					continue;
				}
				if (Link.REL_ALTERNATE.equals(link.get$rel())) {
					if (link.get$href() != null && link.get$href().length() > 0) {
						String aliasUri = link.get$href();
						if (isNumbering && aliasUri.endsWith(URI_NUMBERING)) {
							aliasUri = aliasUri.substring(0, aliasUri.length() - URI_NUMBERING_LEN);
						}
						checkUri(aliasUri);
						checkLastSlash(aliasUri, "Alias Key");
						// エイリアス重複はエラー
						if (aliasUris.contains(aliasUri)) {
							throw new IllegalParameterException("Alias keys are duplicated. " + aliasUri);
						}
						// 自動採番指定URIとエイリアス重複はエラー
						if (isNumbering && numberingUri.equals(aliasUri)) {
							throw new IllegalParameterException("Numbering key and Alias key are duplicated. " + aliasUri);
						}
						aliasUris.add(aliasUri);
						existAlias = true;
					} else {
						//throw new IllegalParameterException("Alias key is required.");
						cntBlankAlias++;
					}
				}
			}
			if (cntBlankAlias > 0) {
				if (existAlias || cntBlankAlias > 1) {
					throw new IllegalParameterException("Alias key is required.");
				}
			}

			// エイリアスの件数制限チェック
			int aliasCnt = aliasUris.size();
			if (aliasCnt > TaggingEnvUtil.getAliasNumberLimit()) {
				throw new IllegalParameterException("The number of aliases per entry exceeds the limit.");
			}
		}
	}

	/**
	 * IDチェック
	 * @param id ID
	 */
	public static void checkIdAtPost(String id) {
		checkId(id, OperationType.INSERT);
	}

	/**
	 * IDチェック
	 * @param id ID
	 */
	public static void checkIdAtPut(String id) {
		checkId(id, OperationType.UPDATE);
	}

	/**
	 * IDチェック
	 * @param id ID
	 */
	public static void checkIdAtDelete(String id) {
		checkId(id, OperationType.DELETE);
	}


	/**
	 * IDチェック
	 * @param id ID
	 */
	public static void checkIdExist(String id) {
		if (id == null || id.length() == 0) {
			throw new IllegalParameterException("ID is required.");
		}
	}

	/**
	 * IDチェック
	 * @param id ID
	 * @param flg INSERT、UPDATE、DELETE定数値のいずれか
	 */
	//private static void checkId(String id, int flg) {
	private static void checkId(String id, OperationType flg) {
		if (StringUtils.isBlank(id)) {
			return;
		}
		int len = id.length();
		int idx = id.indexOf(",");
		int idx2 = idx + 1;
		// UPDATEの場合?パラメータが付加されている場合があるため除去する。
		String chkId = id;
		if (flg == OperationType.UPDATE) {
			int idxQ = id.indexOf("?", idx2);
			if (idxQ >= idx2) {
				chkId = id.substring(0, idxQ);
				if (StringUtils.isBlank(chkId)) {
					return;
				}
			}
		}

		if (!chkId.startsWith("/") || idx == -1 || idx2 == len) {
			if (flg == OperationType.INSERT) {
				return;
			}
			throw new IllegalParameterException("ID format is invalid. " + id);
		}
		String uri = chkId.substring(0, idx);
		checkUri(uri, "ID");
		checkLastSlash(uri, "ID");

		String rev = chkId.substring(idx2);
		try {
			Integer.parseInt(rev);
		} catch (NumberFormatException e) {
			throw new IllegalParameterException("ID format is invalid. " + id);
		}
	}

	/**
	 * Feed内Entryチェック
	 * @param feed Feed
	 * @param isEntryUpdate Entry更新の場合true
	 */
	public static void checkFeed(FeedBase feed, boolean isEntryUpdate) {
		checkFeed(feed, isEntryUpdate, false);
	}

	/**
	 * Feed内Entryチェック
	 * @param feed Feed
	 * @param isEntryUpdate Entry更新の場合true
	 * @param isBulk 一括更新の場合true
	 */
	public static void checkFeed(FeedBase feed, boolean isEntryUpdate,
			boolean isBulk) {
		if (feed == null || feed.getEntry() == null || feed.getEntry().isEmpty()) {
			throw new IllegalParameterException("Entry is required.");
		}
		int idx = 1;
		for (EntryBase entry : feed.entry) {
			if (entry == null) {
				throw new IllegalParameterException("Entry is required. num=" + idx);
			}
			idx++;
		}
		if (!isBulk) {
			int limitMax = TaggingEnvUtil.getUpdateEntryNumberLimit();
			if (limitMax > 0 && feed.getEntry().size() > limitMax) {
				StringBuilder sb = new StringBuilder();
				sb.append("Too many entries. Please to keep less than ");
				sb.append(limitMax);
				sb.append(" entries");
				if (isEntryUpdate) {
					sb.append(" or retry with '_bulk' option.");	// bulk putの案内
				} else {
					sb.append(".");
				}
				throw new IllegalParameterException(sb.toString());
			}
		}

		// キー重複チェック
		checkDuplicateUrl(feed);
	}

	/**
	 * Revisionチェック
	 * @param revision
	 */
	public static void checkRevision(String revision) {
		// Revision指定なしでもOK
		if (revision != null) {
			try {
				Integer.parseInt(revision);
			} catch (NumberFormatException e) {
				throw new IllegalParameterException("Revision number must be a numeric value.");
			}
		}
	}

	/**
	 * URL重複チェック
	 * @param feed Feed
	 */
	public static void checkDuplicateUrl(FeedBase feed) {
		if (feed == null || feed.getEntry() == null) {
			return;
		}

		// キー重複チェック
		List<EntryBase> entries = feed.getEntry();
		int size = entries.size();
		for (int i = 0; i < size - 1; i++) {
			String uri = entries.get(i).getMyUri();
			if (uri == null) {
				continue;
			}
			uri = TaggingEntryUtil.editSlash(uri);
			for (int j = i + 1; j < size; j++) {
				String tmpUri = entries.get(j).getMyUri();
				if (tmpUri != null) {
					if (uri.equals(TaggingEntryUtil.editSlash(tmpUri))) {
						throw new IllegalParameterException("Keys are duplicated. " + uri + ".");
					}
				}
			}
		}
	}

	/**
	 * Alias重複チェック.
	 * また、Aliasのselfidがselfのselfidと異なる場合はエラーとする。
	 * @param entry Entry
	 */
	public static void checkDuplicatedAlias(EntryBase entry) {
		if (entry == null || entry.getLink() == null) {
			return;
		}
		List<Link> links = entry.getLink();
		Set<String> parents = new HashSet<String>();
		Set<String> uris = new HashSet<String>();
		for (Link link : links) {
			if (link == null) {
				continue;
			}
			if (!Link.REL_SELF.equals(link._$rel) &&
					!Link.REL_ALTERNATE.equals(link._$rel)) {
				continue;
			}
			if (StringUtils.isBlank(link._$href)) {
				continue;
			}

			String uri = link._$href;
			// 重複チェック
			if (uris.contains(uri)) {
				throw new IllegalParameterException("Keys are duplicated. " + uri);
			}
			// 親階層が同じURIが複数あるとエラー
			String parentUri = TaggingEntryUtil.getParentUri(uri);
			if (parents.contains(parentUri)) {
				throw new IllegalParameterException("Parent keys are duplicated. " + parentUri);
			}

			uris.add(uri);
			parents.add(parentUri);
		}
	}

	/**
	 * URL重複チェック
	 * @param ids IDまたはURI
	 */
	public static void checkDuplicateUrl(List<String> ids) {
		// 重複チェック
		Set<String> tmpUris = new HashSet<String>();
		for (String idOrUri : ids) {
			String uri = null;
			if (idOrUri.indexOf(",") > -1) {
				uri = TaggingEntryUtil.getUriById(idOrUri);
			} else {
				uri = idOrUri;
			}
			if (tmpUris.contains(uri)) {
				throw new IllegalParameterException("Keys are duplicated. " + uri);
			}
			tmpUris.add(uri);
		}
	}

	/**
	 * 上位階層が存在するかどうかのチェック
	 * @param parentPathEntries 上位階層Entryのリスト
	 * @param uri URI
	 * @throws HierarchyFormatException 上位階層が存在しない場合
	 * @throws IOException データ取得時のエラー
	 */
	public static void checkUpperHierarchy(List<EntryBase> parentPathEntries, String uri)
	throws HierarchyFormatException, IOException {
		if (parentPathEntries == null || parentPathEntries.isEmpty()) {
			throw new HierarchyFormatException("Parent path is required.");
		}
		TaggingEntryUtil.UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
		if (!TaggingEntryUtil.isTop(uriPair.parent)) {
			if (parentPathEntries.size() > 1) {
				EntryBase parentEntry = parentPathEntries.get(1);	// 直近の上位階層Entry
				if (!uriPair.parent.equals(TaggingEntryUtil.getMyUriSlash(parentEntry))) {
					throw new HierarchyFormatException("Parent path is required.");
				}
			} else {
				throw new HierarchyFormatException("Parent path is required.");
			}
		}
	}

	/**
	 * URIチェック.
	 * 共通のURIチェック + 階層数チェックを行う。
	 * @param uri URI
	 * @param name "Key"または"Parent key"
	 */
	public static void checkUri(String uri, String name) {
		// 親クラスのメソッド実行
		ReflexCheckUtil.checkUri(uri, name);

		// 階層は10階層まで
		int maxNumKeyHierarchies = TaggingEnvUtil.getSystemPropInt(
				TaggingEnvConst.MAXNUM_KEY_HIERARCHIES,
				TaggingEnvConst.MAXNUM_KEY_HIERARCHIES_DEFAULT);
		int cnt = 0;
		int idx = 0;
		int start = 0;
		while ((idx = uri.indexOf("/", start)) > -1) {
			cnt++;
			if (cnt > maxNumKeyHierarchies) {
				throw new IllegalParameterException(
						"The number of " + name + "'s hierarchy is exceeded.");
			}
			start = idx + 1;
		}
	}

	/**
	 * バリデーションチェック
	 * @param entry Entry
	 * @param currentEntry 更新前Entry
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public static void validate(EntryBase entry, EntryBase currentEntry,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		try {
			String uid = null;
			List<String> groups = null;
			if (auth != null) {
				uid = auth.getUid();
				groups = auth.getGroups();
			}
			entry.validate(uid, groups);

			// link titleの更新チェック

			List<Link> links = entry.getLink();
			List<Link> currentLinks = null;
			List<String> checkUris = new ArrayList<>();	// チェック対象URI
			List<String> noChangeUris = new ArrayList<>();	// 変更なしURI
			if (currentEntry != null) {
				currentLinks = currentEntry.getLink();
			}
			if (links != null) {
				for (Link link : links) {
					if (link == null) {
						throw new IllegalParameterException("Property 'link' cannot be null.");
					}
					// 更新時、link titleは設定がなければ(=nullであれば)更新しない。(see TaggingEntryUtil#complementSetValue)
					if (link._$title != null) {
						boolean isUpdated = false;
						if ((Link.REL_SELF.equals(link._$rel) || Link.REL_ALTERNATE.equals(link._$rel)) &&
								!StringUtils.isBlank(link._$href)) {
							// 変更があるかどうか
							if (currentLinks != null) {
								for (Link currentLink : currentLinks) {
									if (link._$rel.equals(currentLink._$rel) &&
											link._$href.equals(currentLink._$href)) {
										// 同一キーあり
										if (link._$title.equals(currentLink._$title)) {
											// 値変更なし
											noChangeUris.add(link._$href);
										} else {
											// 値変更あり
											checkUris.add(link._$href);
										}
										isUpdated = true;
										break;
									}
								}
							}

						} else {
							throw new IllegalParameterException("Property 'link#title' is not writeable.");
						}

						if (!isUpdated) {
							// link追加
							checkUris.add(link._$href);
						}
					}
				}
			}

			// 更新チェック
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			for (String checkUri : checkUris) {
				if (!signatureBlogic.hasSignatureAcl(checkUri, auth, requestInfo, connectionInfo)) {
					throw new IllegalParameterException("Property 'link#title' is not writeable. " + checkUri);
				}
			}

			// サイズチェックはBDBサーバで行う。
			// バイト配列に変換するコストがもったいないため。
			
			// contributorのnullチェック
			List<Contributor> contributors = entry.getContributor();
			if (contributors != null) {
				for (Contributor contributor : contributors) {
					if (contributor == null) {
						throw new IllegalParameterException("Property 'contributor' cannot be null.");
					}
				}
			}
			
			// Authorのnullチェック
			List<Author> authors = entry.getAuthor();
			if (authors != null) {
				for (Author author : authors) {
					if (author == null) {
						throw new IllegalParameterException("Property 'author' cannot be null.");
					}
				}
			}
			
			// Categoryのnullチェック
			List<Category> categories = entry.getCategory();
			if (categories != null) {
				for (Category category : categories) {
					if (category == null) {
						throw new IllegalParameterException("Property 'category' cannot be null.");
					}
				}
			}

		} catch (ParseException e) {
			throw new IllegalParameterException(e.getMessage());
		}
	}

	/**
	 * 検索時の条件チェック.
	 * @param param 検索時の条件チェック
	 */
	public static void checkRequestParam(RequestParam param) {
		if (param == null) {
			throw new IllegalParameterException("Key is required.");
		}
		checkUri(param.getUri());
	}

	/**
	 * テンプレートのチェックを行う
	 * @param entry 更新予定エントリー
	 * @param currentEntry 現在のエントリー
	 * @param serviceName サービス名
	 * @param mapper FeedTemplateMapper
	 */
	public static void checkTemplate(EntryBase entry, EntryBase currentEntry,
			String serviceName, FeedTemplateMapper mapper) {
		if (entry == null) {
			return;
		}
		// /_settings/template の場合はテンプレートのフォーマットチェックを行う。
		String uri = entry.getMyUri();
		ResourceMapperManager resourceMapperManager =
				TaggingEnvUtil.getResourceMapperManager();
		if (!resourceMapperManager.isTemplateUri(uri)) {
			return;
		}
		if (StringUtils.isBlank(entry.getContentText()) &&
				StringUtils.isBlank(entry.rights)) {
			return;
		}

		// entryを複製
		EntryBase tmpEntry = TaggingEntryUtil.mergeUpdateEntry(currentEntry, entry,
				mapper);

		Template template = resourceMapperManager.getTemplate(tmpEntry, serviceName);

		// FeedTemplateMapperは標準ATOMを使用する。
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();

		// 前リビジョンの設定と比較
		// 前リビジョンでテンプレートが設定されていない場合も、新テンプレートの内容チェックのために
		// FeedTemplateMapper.precheckTemplateを呼び出す。
		Template currentTemplate = resourceMapperManager.getTemplate(currentEntry, serviceName);
		String[] currentTemplateArray = null;
		if (currentTemplate != null && currentTemplate.template != null &&
				currentTemplate.template.length > 1) {
			currentTemplateArray = currentTemplate.template;
		}

		try {
			// precheck
			boolean precheck = atomMapper.precheckTemplate(currentTemplateArray,
					template.template);
			if (!precheck) {
				throw new IllegalParameterException(
						"Optimistic locking failed for the specified template.");
			}

			// 新しいテンプレート情報で正しくMapper生成できるかどうかチェックする。
			new FeedTemplateMapper(template.template, template.rights,
								TaggingEnvUtil.getIndexLimit(), null);

		} catch (ParseException e) {
			throw new IllegalParameterException(e.getMessage());
		}
	}

	/**
	 * 自動採番数の指定チェック
	 * @param num 自動採番数
	 */
	public static void checkAllocNumber(int num) {
		if (num <= 0) {
			throw new IllegalParameterException("Please specify 1 or more for the allocate number.");
		}
		// 最大数チェック
		int limit = TaggingEnvUtil.getAllocidsLimit();
		if (num > limit) {
			throw new IllegalParameterException("Please specify " + limit + " or less for the allocate number.");
		}
	}

	/**
	 * IDまたはURIのリストチェック
	 * @param ids IDまたはURIのリスト
	 */
	public static void checkIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			throw new IllegalParameterException("ID or Key is required.");
		}
		int limit = TaggingEnvUtil.getUpdateEntryNumberLimit();
		if (ids.size() > limit) {
			throw new IllegalParameterException("Too many entries. Please to keep less than " + limit + " IDs.");
		}
	}

	/**
	 * リクエストのチェック
	 * @param req リクエスト
	 */
	public static void checkRequest(ReflexRequest req) {
		if (req == null) {
			throw new IllegalParameterException("Request is required.");
		}
	}

	/**
	 * 入力ストリームのチェック
	 * @param in InputStream
	 */
	public static void checkRequestPayload(InputStream in) {
		if (in == null) {
			throw new IllegalParameterException("Request payload is required.");
		}
	}

	/**
	 * 値に指定がないかどうかチェック.
	 * 指定が無い場合を正とし、あればエラーとする。
	 * @param val 値
	 * @param name 名前(エラーメッセージに使用)
	 * @param ignores 指定可能値
	 */
	public static void checkNotSpecified(String val, String name, Set<String> ignores) {
		if (!StringUtils.isBlank(val)) {
			if (ignores == null || !ignores.contains(val)) {
				throw new IllegalParameterException(name + " can not be specified.");
			}
		}
	}

	/**
	 * アクセストークン生成のURIチェック
	 * @param uri URI
	 */
	public static void checkLinkToken(String uri) {
		// 未入力は不可
		if (StringUtils.isBlank(uri)) {
			throw new IllegalParameterException("LinkToken key is required.");
		}
	}

	/**
	 * アクセストークン生成のURIチェック
	 * @param uris URIリスト
	 */
	public static void checkLinkToken(String[] uris) {
		if (uris == null || uris.length == 0) {
			throw new IllegalParameterException("LinkToken key is required.");
		}
		for (String uri : uris) {
			checkUri(uri);
			// ルートエントリーは不可
			if (Constants.URI_ROOT.equals(uri)) {
				throw new IllegalParameterException("Top entry can not be specified.");
			}
		}
	}

	/**
	 * エイリアス更新処理でエイリアスが指定されているかどうか
	 * @param entry Entry
	 * @return エイリアスリスト
	 */
	public static List<String> checkUpdateAlias(EntryBase entry) {
		List<String> aliases = entry.getAlternate();
		checkNotNull(aliases, "Alias");
		if (aliases != null) {
			for (String alias : aliases) {
				CheckUtil.checkUri(alias, "Alias");
			}
		}
		return aliases;
	}

	/**
	 * ACL更新処理でACLが指定されているかどうか
	 * @param entry Entry
	 * @return ACLリスト
	 */
	public static Set<String> checkUpdateACL(EntryBase entry) {
		List<Contributor> acls = TaggingEntryUtil.getAcls(entry);
		checkNotNull(acls, "ACL");
		Set<String> aclUrns = new HashSet<String>();
		for (Contributor aclCont : acls) {
			// "urn:vte.cx:acl:"の後に指定があるかどうかチェック
			checkNotNull(aclCont.uri.substring(URN_PREFIX_ACL_LEN), "ACL");
			aclUrns.add(aclCont.uri);
		}
		return aclUrns;
	}

	/**
	 * 一般サービス参照可能キーチェック.
	 * 一般サービスの場合、/@から始まるキーは登録不可.
	 * @param uri URI
	 * @param serviceName サービス名
	 */
	public static void checkCommonUri(String uri, String serviceName) {
		if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
			return;
		}
		if (uri == null) {
			return;
		}
		if (uri.startsWith(Constants.URI_SYSTEM_MANAGER)) {
			throw new IllegalParameterException("You can not specify a key that starts with '/@'.");
		}
	}

	/**
	 * 検索条件インメモリチェック.
	 * 項目OR指定の場合を考慮
	 * @param entry Entry
	 * @param innerConditions インメモリ条件
	 * @return 条件を満たす場合true
	 */
	public static boolean isMatchInnerCondition(EntryBase entry, Condition[] innerConditions) {
		if (innerConditions == null || innerConditions.length == 0) {
			return true;
		}
		for (Condition condition : innerConditions) {
			if (condition.getProp().indexOf(Condition.OR) > 0) {
				// 項目OR指定
				boolean isMatchOr = false;
				String[] propParts = condition.getProp().split(OR_REGEX);
				for (String propPart : propParts) {
					Condition conditionPart = new Condition(propPart,
							condition.getEquations(), condition.getValue());
					if (entry.isMatch(new Condition[] {conditionPart})) {
						isMatchOr = true;
						break;
					}
				}
				if (!isMatchOr) {
					return false;
				}

			} else {
				// 通常
				if (!entry.isMatch(new Condition[] {condition})) {
					return false;
				}
			}
		}
		// 全ての条件がtrue
		return true;
	}
	
	/**
	 * 文字列がBoolean形式かどうかチェック.
	 * @param val 文字列
	 * @param name 項目名
	 */
	public static void checkBoolean(String val, String name) {
		if (!StringUtils.isBoolean(val)) {
			throw new IllegalParameterException("Not boolean : " + name);
		}
	}
	
	/**
	 * 値にスラッシュが含まれている場合エラー.
	 * @param val 文字列
	 * @param name 項目名
	 */
	public static void checkSlash(String val, String name) {
		if (!StringUtils.isBlank(val) && val.indexOf("/") > -1) {
			throw new IllegalParameterException(name + " cannot contain a slash : " + val);
		}
	}
	
	/**
	 * キーが指定された親キーかどうかチェック.
	 * 重複チェックも行う。
	 * @param entry エントリー
	 * @param parentUri 親キー
	 */
	public static void checkParentUri(FeedBase feed, String parentUri) {
		if (!TaggingEntryUtil.isExistData(feed)) {
			return;
		}
		String parentUriSlash = TaggingEntryUtil.editSlash(parentUri);
		Set<String> selfids = new HashSet<>();
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();
			TaggingEntryUtil.UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
			if (!parentUriSlash.equals(uriPair.parent)) {
				throw new IllegalParameterException("Key is not the default parent key : " + uri);
			}
			if (selfids.contains(uriPair.selfid)) {
				throw new IllegalParameterException("The key is duplicated : " + uri);
			}
			selfids.add(uriPair.selfid);
		}
	}

}
