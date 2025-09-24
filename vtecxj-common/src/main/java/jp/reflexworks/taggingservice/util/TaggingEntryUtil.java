package jp.reflexworks.taggingservice.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.api.EntryUtil;
import jp.reflexworks.atom.entry.Author;
import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.BaseReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.env.ReflexEnvConst;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.sourceforge.reflex.util.FieldMapper;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Entryオブジェクトの編集クラス
 */
public class TaggingEntryUtil extends EntryUtil {

	/** ルートuri */
	public static final String URI_ROOT = Constants.URI_ROOT;
	/** HTML uriの接頭辞 */
	public static final String URI_HTML_PREFIX = AtomConst.URI_HTML + "/";
	/** ResourceMapper格納キー : ATOM標準 */
	public static final String ATOM_STANDARD = ReflexEnvConst.ATOM_STANDARD;

	/** HTML uriの文字数 */
	private static final int URI_HTML_LEN = AtomConst.URI_HTML.length();
	/** intの最大桁数 */
	private static final int INT_MAXVAL_LEN = 10;
	/** created uid接頭辞の文字数 */
	private static final int URN_PREFIX_CREATED_LEN = Constants.URN_PREFIX_CREATED.length();

	/**
	 * Entryクラスのインスタンスを生成します。
	 * @param mapper FeedTemplateMapper;
	 * @return Entryオブジェクト
	 */
	public static EntryBase createEntry(FeedTemplateMapper mapper) {
		return EntryUtil.createEntry(mapper);
	}

	/**
	 * Entryクラスのインスタンスを生成します。
	 * @param serviceName サービス名
	 * @return Entryオブジェクト
	 */
	public static EntryBase createEntry(String serviceName) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return createEntry(env.getResourceMapper(serviceName));
	}

	/**
	 * ATOM標準Entryクラスのインスタンスを生成します.
	 * @return ATOM標準Entryオブジェクト
	 */
	public static EntryBase createAtomEntry() {
		return createEntry(ATOM_STANDARD);
	}

	/**
	 * Feedクラスのインスタンスを生成し、引数のエントリーを1件セットします.
	 * @param mapper FeedTemplateMapper
	 * @param entry エントリー
	 * @return フィード
	 */
	public static FeedBase createFeed(FeedTemplateMapper mapper, EntryBase entry) {
		FeedBase feed = createFeed(mapper);
		if (entry != null) {
			List<EntryBase> entries = new ArrayList<EntryBase>();
			entries.add(entry);
			feed.entry = entries;
		}
		return feed;
	}

	/**
	 * Feedクラスのインスタンスを生成し、引数のエントリーを1件セットします.
	 * @param serviceName サービス名
	 * @param entry エントリー
	 * @return フィード
	 */
	public static FeedBase createFeed(String serviceName, EntryBase entry) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return createFeed(env.getResourceMapper(serviceName), entry);
	}

	/**
	 * Feedクラスのインスタンスを生成します。
	 * @param mapper FeedTemplateMapper
	 * @return Feedオブジェクト
	 */
	public static FeedBase createFeed(FeedTemplateMapper mapper) {
		return EntryUtil.createFeed(mapper);
	}

	/**
	 * Feedクラスのインスタンスを生成します。
	 * @param serviceName サービス名
	 * @return Feedオブジェクト
	 */
	public static FeedBase createFeed(String serviceName) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return EntryUtil.createFeed(env.getResourceMapper(serviceName));
	}

	/**
	 * ATOM標準Feedクラスのインスタンスを生成します。
	 * @return ATOM標準Feedオブジェクト
	 */
	public static FeedBase createAtomFeed() {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return createFeed(env.getResourceMapper(ATOM_STANDARD));
	}

	/**
	 * Feedクラスのインスタンスを生成し、引数のエントリーを1件セットします.
	 * @param entry エントリー
	 * @return フィード
	 */
	public static FeedBase createAtomFeed(EntryBase entry) {
		FeedBase feed = createAtomFeed();
		if (entry != null) {
			List<EntryBase> entries = new ArrayList<EntryBase>();
			entries.add(entry);
			feed.entry = entries;
		}
		return feed;
	}

	/**
	 * URIとリビジョンからIDを取得.
	 * リビジョンが設定されていない場合、URIをそのまま返します。
	 * @param uri URI
	 * @param revision リビジョン
	 * @return ID
	 */
	public static String getId(String uri, String revision) {
		if (StringUtils.isBlank(uri)) {
			return uri;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (!StringUtils.isBlank(revision)) {
			sb.append(",");
			sb.append(revision);
		}
		return sb.toString();
	}
	/**
	 * エントリーからキーを取得し、末尾にスラッシュを付けて返却します
	 * @param entry エントリー
	 * @return 末尾にスラッシュを付けたキー
	 */
	public static String getMyUriSlash(EntryBase entry) {
		String myUri = getMyUri(entry);
		return editSlash(myUri);
	}

	/**
	 * エントリーからキーを取得します
	 * @param entry エントリー
	 * @return キー
	 */
	public static String getMyUri(EntryBase entry) {
		String myUri = null;
		if (entry != null && entry.link != null) {
			for (Link childLink : entry.link) {
				if (Link.REL_SELF.equals(childLink._$rel)) {
					myUri = childLink._$href;
				}
			}
		}
		return myUri;
	}

	/**
	 * 更新情報リストの1件目を返却
	 * @param updatedInfos 更新情報リスト
	 * @return 更新情報リストの1件目
	 */
	public static UpdatedInfo getFirstUpdatedInfo(List<UpdatedInfo> updatedInfos) {
		if (updatedInfos != null && !updatedInfos.isEmpty()) {
			return updatedInfos.get(0);
		}
		return null;
	}

	/**
	 * Feedを複製します.
	 * <p>
	 * Linkのみ複製します。その他のフィールドはプリミティブ型、String型を除き参照コピーです。
	 * </p>
	 * @param feed Feed
	 * @param mapper FeedTemplateMapper
	 * @return 複製したFeed
	 */
	public static FeedBase copyFeed(FeedBase feed, FeedTemplateMapper mapper) {
		if (feed == null || feed.entry == null || feed.entry.isEmpty()) {
			return null;
		}
		FeedBase retFeed = createFeed(mapper);
		retFeed.addEntries(copyEntries(feed.entry, mapper));
		return retFeed;
	}

	/**
	 * Entryリストを複製します.
	 * <p>
	 * Linkのみ複製します。その他のフィールドはプリミティブ型、String型を除き参照コピーです。
	 * </p>
	 * @param entries Entryリスト
	 * @param mapper FeedTemplateMapper
	 * @return 複製したEntryリスト
	 */
	public static List<EntryBase> copyEntries(List<EntryBase> entries, FeedTemplateMapper mapper) {
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		List<EntryBase> retEntries = new ArrayList<EntryBase>();
		for (EntryBase entry : entries) {
			retEntries.add(copyEntry(entry, mapper));
		}
		return retEntries;
	}

	/**
	 * エントリーを複製します.
	 * <p>
	 * Linkのみ複製します。その他のフィールドはプリミティブ型、String型を除き参照コピーです。
	 * </p>
	 * @param entry 複製元エントリー
	 * @param mapper FeedTemplateMapper
	 * @return 複製したエントリーオブジェクト
	 */
	public static EntryBase copyEntry(EntryBase entry, FeedTemplateMapper mapper) {
		if (entry == null) {
			return null;
		}
		EntryBase newEntry = createEntry(mapper);
		FieldMapper fieldMapper = new FieldMapper(true);
		fieldMapper.setValue(entry, newEntry, false, false, 2);	// コピー階層2(Entry, EntryBase)

		// Link、Contributor、Author、Categoryを複製
		List<Link> links = cloneLink(entry.getLink());
		newEntry.setLink(links);
		List<Contributor> contributors = cloneContributor(entry.getContributor());
		newEntry.setContributor(contributors);
		List<Author> authors = cloneAuthor(entry.getAuthor());
		newEntry.setAuthor(authors);
		List<Category> categories = cloneCategory(entry.getCategory());
		newEntry.setCategory(categories);

		return newEntry;
	}

	/**
	 * Linkリストを複製します.
	 * @param source Linkリストオブジェクト
	 * @return 複製したLinkリストオブジェクト
	 */
	public static List<Link> cloneLink(List<Link> source) {
		if (source == null) {
			return null;
		}
		List<Link> links = new ArrayList<Link>();
		for (Link sourcelink : source) {
			Link link = cloneLink(sourcelink);
			links.add(link);
		}
		return links;
	}

	/**
	 * Linkを複製します.
	 * @param source Linkの元オブジェクト
	 * @return 複製したLinkオブジェクト
	 */
	public static Link cloneLink(Link source) {
		if (source == null) {
			return null;
		}
		Link clone = new Link();
		clone._$href = source._$href;
		clone._$rel = source._$rel;
		clone._$type = source._$type;
		clone._$title = source._$title;
		clone._$length = source._$length;
		return clone;
	}

	/**
	 * Contributorリストを複製します.
	 * @param source Contributorリストの元オブジェクト
	 * @return 複製したContributorリストオブジェクト
	 */
	public static List<Contributor> cloneContributor(List<Contributor> source) {
		if (source == null) {
			return null;
		}
		List<Contributor> contributors = new ArrayList<Contributor>();
		for (Contributor sourceContributor : source) {
			Contributor contributor = cloneContributor(sourceContributor);
			contributors.add(contributor);
		}
		return contributors;
	}

	/**
	 * Contributorを複製します.
	 * @param source Contributorの元オブジェクト
	 * @return 複製したContributorオブジェクト
	 */
	public static Contributor cloneContributor(Contributor source) {
		if (source == null) {
			return null;
		}
		Contributor clone = new Contributor();
		clone.name = source.name;
		clone.uri = source.uri;
		clone.email = source.email;
		return clone;
	}

	/**
	 * Authorリストを複製します.
	 * @param source Authorリストの元オブジェクト
	 * @return 複製したAuthorリストオブジェクト
	 */
	public static List<Author> cloneAuthor(List<Author> source) {
		if (source == null) {
			return null;
		}
		List<Author> authors = new ArrayList<Author>();
		for (Author sourceAuthor : source) {
			Author author = cloneAuthor(sourceAuthor);
			authors.add(author);
		}
		return authors;
	}

	/**
	 * Authorを複製します.
	 * @param source Authorの元オブジェクト
	 * @return 複製したAuthorオブジェクト
	 */
	public static Author cloneAuthor(Author source) {
		if (source == null) {
			return null;
		}
		Author clone = new Author();
		clone.name = source.name;
		clone.uri = source.uri;
		clone.email = source.email;
		return clone;
	}

	/**
	 * Categoryリストを複製します.
	 * @param source Categoryリストの元オブジェクト
	 * @return 複製したCategoryリストオブジェクト
	 */
	public static List<Category> cloneCategory(List<Category> source) {
		if (source == null) {
			return null;
		}
		List<Category> categorys = new ArrayList<Category>();
		for (Category sourceCategory : source) {
			Category category = cloneCategory(sourceCategory);
			categorys.add(category);
		}
		return categorys;
	}

	/**
	 * Categoryを複製します.
	 * @param source Categoryの元オブジェクト
	 * @return 複製したCategoryオブジェクト
	 */
	public static Category cloneCategory(Category source) {
		if (source == null) {
			return null;
		}
		Category clone = new Category();
		clone._$term = source._$term;
		clone._$scheme = source._$scheme;
		clone._$label = source._$label;
		return clone;
	}

	/**
	 * キーの階層を取得します.
	 * <p>
	 * 階層を一段ずつ上がっていくリストを返します。<br>
	 * 例えば、URLが「/a1/b1/c1」であれば、以下の内容が返却されます。
	 * <ul>
	 * <li>list(0)="/a1/b1/c1"</li>
	 * <li>list(1)="/a1/b1"</li>
	 * <li>list(2)="/a1"</li>
	 * <li>list(3)="/"</li>
	 * </ul>
	 * </p>
	 * @param uri
	 * @return キー階層リスト
	 */
	public static List<String> getLayers(String uri) {
		List<String> layers = new ArrayList<String>();
		String tmp = uri;
		while (!EntryBase.TOP.equals(tmp)) {
			layers.add(removeLastSlash(tmp));
			tmp = getParentUri(tmp);
		}
		return layers;
	}

	/**
	 * 自階層 + 親階層一覧を取得します
	 * @param uri キー
	 * @return 親階層一覧
	 */
	public static List<String> getParentPathUris(String uri) {
		List<String> uris = new ArrayList<String>();
		if (TaggingEntryUtil.isTop(uri)) {
			return uris;
		}
		String tmpUri = uri;
		while (tmpUri != null && tmpUri.length() > 0) {
			uris.add(tmpUri);

			if ("/".equals(tmpUri)) {
				tmpUri = null;
			} else {
				int idx = tmpUri.lastIndexOf("/");
				if (idx > 1) {
					tmpUri = tmpUri.substring(0, idx);
				} else if (idx == 0) {
					tmpUri = "/";
				} else {
					tmpUri = null;
				}
			}
		}

		return uris;
	}

	/**
	 * IDからパラメータを取得します.
	 * @param id ID
	 * @return パラメータ
	 */
	public static String getParamById(String id) {
		if (id != null) {
			int idx0 = id.indexOf(",");
			if (idx0 == -1) {
				idx0 = 0;
			}
			int idx = id.indexOf("?", idx0);
			if (idx > -1) {
				return id.substring(idx + 1);
			}
		}
		return null;
	}

	/**
	 * IDからパラメータ部分を取り除きます.
	 * @param entry エントリー
	 */
	public static void removeIdParam(EntryBase entry) {
		if (entry != null && entry.id != null) {
			String id = removeParam(entry.id);
			entry.setId(id);
		}
	}

	/**
	 * ID文字列から、?で指定されたパラメータを除去する.
	 * @param id ID
	 * @return 編集した文字列
	 */
	public static String removeParam(String id) {
		if (!StringUtils.isBlank(id)) {
			int idx0 = id.indexOf(",");
			if (idx0 == -1) {
				idx0 = 0;
			}
			int idx = id.indexOf("?", idx0);
			if (idx > -1) {
				return id.substring(0, idx);
			}
		}
		return id;
	}

	/**
	 * Insert時のEntry編集を行います.
	 * @param entry リクエスタからの入力値
	 * @param currentTime 実行日時
	 * @param auth 認証情報
	 * @return 編集後のEntry
	 */
	public static EntryBase editInsertEntry(EntryBase entry,
			String currentTime, ReflexAuthentication auth) {
		String uri = entry.getMyUri();
		int revision = 1;
		entry.setId(createId(uri, revision));
		entry.setUpdated(currentTime);
		entry.setPublished(currentTime);
		setAuthor(entry, MetadataUtil.getAuthor(auth, OperationType.INSERT));
		editLog(entry);
		return entry;
	}

	/**
	 * Update時のEntry編集を行います.
	 * @param currentEntry データストアから取得した現在のEntry
	 * @param entry リクエスタからの入力値
	 * @param currentKey Revision0のキー
	 * @param currentTime 実行日時
	 * @param author 編集者
	 * @return 編集後のEntry
	 */
	public static EntryBase editUpdateEntry(EntryBase currentEntry, EntryBase entry,
			String currentTime, ReflexAuthentication auth, boolean isSilent) {
		// id
		String currentId = currentEntry.id;
		String idUri = getUriById(currentId);

		// currentデータに、引数のentryの値をマージしてput
		int currentRevision = getRevisionById(currentId);
		int updateRevison = currentRevision + 1;
		if (isSilent) {
			updateRevison = currentRevision;	// silentオプション
		}

		entry.setId(createId(idUri, updateRevison));
		entry.setUpdated(currentTime);
		entry.setPublished(currentEntry.published);
		entry.author = currentEntry.author;
		setAuthor(entry, MetadataUtil.getAuthor(auth, OperationType.UPDATE));
		editLog(entry);
		return entry;
	}

	/**
	 * Update時のEntry編集を行います.
	 * @param currentEntry データストアから取得した現在のEntry
	 * @param entry リクエスタからの入力値 (id、updated、author設定済み)
	 * @param mapper FeedTemplateMapper
	 * @return 編集後のEntry
	 */
	public static EntryBase mergeUpdateEntry(EntryBase currentEntry,
			EntryBase entry, FeedTemplateMapper mapper) {
		// currentEntryをコピー
		EntryBase updateEntry = TaggingEntryUtil.copyEntry(currentEntry, mapper);
		if (updateEntry == null) {
			updateEntry = TaggingEntryUtil.createEntry(mapper);
		}

		// そのまま移送できない項目の移送(戻り値はsource、currentEntryを一部編集)
		EntryBase[] editEntries = complementSetValue(entry, updateEntry, mapper);
		FieldMapper fieldMapper = new FieldMapper(true);
		fieldMapper.setValue(editEntries[0], updateEntry);
		restoreBackup(entry, editEntries[1]);

		return updateEntry;
	}

	/**
	 * 一部項目のバックアップ.
	 * @param source 元Entry
	 * @param backup バックアップ先Entry
	 */
	private static void restoreBackup(EntryBase source, EntryBase backup) {
		source.category = backup.category;
		source.contributor = backup.contributor;
		source.link = backup.link;
	}

	/**
	 * 編集が必要な項目の移送
	 * target(currentEntryのコピー)に移送する。
	 * @param source リクエスタからの入力値
	 * @param target データストアから取得した現在のEntry
	 * @param mapper FeedTemplateMapper
	 * @return 編集し終わった項目を除いたEntry, 除いた項目のバックアップ
	 */
	private static EntryBase[] complementSetValue(EntryBase source,
			EntryBase target, FeedTemplateMapper mapper) {
		if (source == null) {
			return null;
		}

		EntryBase retEntry = source;
		EntryBase backupEntry = createEntry(mapper);

		EntryBase[] retEntries = new EntryBase[2];
		retEntries[0] = retEntry;
		retEntries[1] = backupEntry;

		// Category
		if (source.getCategory() != null &&
				source.getCategory() != target.getCategory()) {
			// termごとに設定があれば全更新、設定がなければ更新対象外とする。
			List<Category> categories = new ArrayList<Category>();
			for (Category category : source.getCategory()) {
				categories.add(category);
			}
			if (target.getCategory() != null) {
				for (Category tcate : target.getCategory()) {
					String term = tcate.get$term();
					if (term != null) {
						boolean isAdd = true;
						for (Category scate : source.getCategory()) {
							if (term.equals(scate.get$term())) {
								isAdd = false;
								break;
							}
						}
						if (isAdd) {
							categories.add(tcate);
						}
					}
				}
			}
			target.setCategory(categories);
		}
		backupEntry.category = retEntry.category;
		retEntry.category = null;

		// Contributor
		if (source.getContributor() != null &&
				source.getContributor() != target.getContributor()) {
			// 管理者権限を持つ場合、ACLごと更新
			target.setContributor(source.getContributor());
		}
		backupEntry.contributor = retEntry.contributor;
		retEntry.contributor = null;

		// Link
		// relごとに設定があれば全更新、設定がなければ更新対象外とする。
		// title属性は署名のため、設定がなければ(=nullであれば)更新しない。
		if (source.getLink() != null && source.getLink() != target.getLink()) {
			List<Link> links = new ArrayList<Link>();
			for (Link sourceLink : source.getLink()) {
				// エイリアスの削除の場合移送しない
				if (Link.REL_ALTERNATE.equals(sourceLink._$rel) &&
						StringUtils.isBlank(sourceLink._$href)) {

				} else {
					links.add(sourceLink);
				}
			}

			if (target.getLink() != null) {
				for (Link tlink : target.getLink()) {
					String trel = tlink._$rel;
					String thref = tlink._$href;
					String ttitle = tlink._$title;
					if (trel != null) {
						boolean isAdd = true;
						for (Link slink : source.getLink()) {
							if (trel.equals(slink.get$rel())) {
								// 指定されたrelは全更新
								isAdd = false;
								if (StringUtils.isBlank(ttitle)) {
									break;
								} else {
									if ((trel.equals(Link.REL_SELF) ||
											trel.equals(Link.REL_ALTERNATE)) &&
											thref != null && thref.equals(slink._$href) &&
											slink._$title == null) {
										// 署名は残す
										slink._$title = ttitle;
									}
								}
							}
						}
						if (isAdd) {
							links.add(tlink);
						}
					}
				}
			}
			target.setLink(links);
		}
		backupEntry.link = retEntry.link;
		retEntry.link = null;

		return retEntries;
	}

	/**
	 * Entryにauthorをセットします
	 * @param entry エントリー
	 * @param author 更新者
	 */
	private static void setAuthor(EntryBase entry, Author author) {
		if (entry == null) {
			return;
		}
		List<Author> oldAuthors = entry.getAuthor();
		List<Author> newAuthors = new ArrayList<Author>();

		if (oldAuthors == null) {
			newAuthors.add(author);

		} else {
			String prefix = null;
			if (author.getUri().startsWith(Constants.URN_PREFIX_CREATED)) {
				prefix = Constants.URN_PREFIX_CREATED;
			} else if (author.getUri().startsWith(Constants.URN_PREFIX_DELETED)) {
				prefix = Constants.URN_PREFIX_DELETED;
			} else {
				prefix = Constants.URN_PREFIX_UPDATED;
			}

			boolean isSet = false;
			for (Author aut : oldAuthors) {
				if (aut.getUri() != null && aut.getUri().startsWith(prefix)) {
					newAuthors.add(author);
					isSet = true;
				} else {
					newAuthors.add(aut);
				}
			}
			if (!isSet) {
				newAuthors.add(author);
			}
		}

		entry.setAuthor(newAuthors);
	}

	/**
	 * ログエントリーの文字長編集.
	 * @param entry Entry
	 */
	private static void editLog(EntryBase entry) {
		for (Link link : entry.link) {
			if (link == null) {
				continue;
			}
			if (Link.REL_SELF.equals(link._$rel) || Link.REL_ALTERNATE.equals(link._$rel)) {
				String parentUri = removeLastSlash(getParentUri(link._$href));
				if (Constants.URI_LOG.equals(parentUri)) {
					// ログエントリーの場合、規定の文字数で切り取る。
					int logLimit = LogUtil.getLogMessageWordcountLimit();
					if (entry.summary != null && entry.summary.length() > logLimit) {
						entry.summary = entry.summary.substring(0, logLimit);
					}
					break;
				}
			}
		}
	}

	/**
	 * IDに設定する文字列を返却します.
	 * @param uri URI
	 * @param revision リビジョン
	 * @return ID
	 */
	public static String createId(String uri, int revision) {
		String tmpUri = removeLastSlash(uri);
		if (StringUtils.isBlank(tmpUri)) {
			return null;
		}
		StringBuilder buf = new StringBuilder();
		buf.append(tmpUri);
		buf.append(",");
		buf.append(revision);
		return buf.toString();
	}

	/**
	 * 条件指定されたURIから検索対象URIを取得します.
	 * <p>
	 * URIに前方一致が指定された場合、前方一致の階層より上の階層を返却します.<br>
	 * 例) URIが「/user/data/d01*」の場合、「/user/data/」を返却します.
	 * </p>
	 * @param uri URI
	 * @return 検索対象URI
	 */
	public static String getConditionUri(String uri) {
		if (uri != null) {
			int idx = uri.lastIndexOf(RequestType.WILDCARD);
			int len = uri.length();
			if (idx > -1 && idx >= len - 2) {
				int idx2 = uri.lastIndexOf("/");
				if (idx2 >= idx) {
					idx2 = uri.substring(0, idx2).lastIndexOf("/");
				}
				String tmp = uri.substring(0, idx2);
				return editSlash(tmp);
			}
		}
		return uri;
	}

	/**
	 * 次ページ検索のためのカーソル文字列をFeedにセットします。
	 * @param cursorStr カーソル文字列
	 * @param feed Feed
	 */
	public static void setCursorToFeed(String cursorStr, FeedBase feed) {
		if (feed != null && !StringUtils.isBlank(cursorStr)) {
			if (feed.link == null) {
				feed.link = new ArrayList<Link>();
			}
			// nextがあれば置き換える
			boolean hasNext = false;
			for (Link link : feed.link) {
				if (Link.REL_NEXT.equals(link._$rel)) {
					link._$href = cursorStr;
					hasNext = true;
					break;
				}
			}
			// nextがなければ追加
			if (!hasNext) {
				feed.link.add(createCursorLink(cursorStr));
			}
		}
	}

	/**
	 * カーソル文字列をセットするLinkクラスを生成します。
	 * @param cursorStr カーソル
	 * @return カーソル文字列をセットするLinkクラス
	 */
	public static Link createCursorLink(String cursorStr) {
		if (!StringUtils.isBlank(cursorStr)) {
			Link linkNext =
					new Link();
			linkNext._$rel = Link.REL_NEXT;
			linkNext._$href = cursorStr;
			return linkNext;
		}
		return null;
	}

	/**
	 * Feedから次ページ検索のためのカーソル文字列を取得します。
	 * @param feed Feed
	 * @return 次ページ検索のためのカーソル文字列
	 */
	public static String getCursorFromFeed(FeedBase feed) {
		if (feed != null && feed.link != null) {
			for (Link link : feed.link) {
				if (Link.REL_NEXT.equals(link._$rel)) {
					return link._$href;
				}
			}
		}
		return null;
	}

	/**
	 * キーの先頭に"/"が無い場合は付加する。
	 * @param uri キー
	 * @return 編集したキー
	 */
	public static String editHeadSlash(String uri) {
		if (!StringUtils.isBlank(uri)) {
			if (!uri.startsWith("/")) {
				return "/" + uri;
			}
		}
		return uri;
	}

	/**
	 * EntryのID URI、エイリアスから指定された親階層のURIを検索して取得する.
	 * @param entry Entry
	 * @param parentUri 親階層
	 * @return URI
	 */
	public static String getSpecifiedParentUri(EntryBase entry, String parentUri) {
		if (entry == null || StringUtils.isBlank(parentUri)) {
			return null;
		}
		// ID URI
		String idUri = getUriById(entry.id);
		if (!StringUtils.isBlank(idUri)) {
			String tmpParentUri = removeLastSlash(getParentUri(idUri));
			if (parentUri.equals(tmpParentUri)) {
				return idUri;
			}
		} else {
			// myUri
			String myUri = entry.getMyUri();
			String tmpParentUri = removeLastSlash(getParentUri(myUri));
			if (parentUri.equals(tmpParentUri)) {
				return myUri;
			}
		}
		// エイリアスのチェック
		List<String> aliases = entry.getAlternate();
		if (aliases != null) {
			for (String alias : aliases) {
				String tmpParentUri = removeLastSlash(getParentUri(alias));
				if (parentUri.equals(tmpParentUri)) {
					return alias;
				}
			}
		}

		return null;
	}

	/**
	 * 先頭の"/_html"を除去する。
	 * @param uri URI
	 * @return 先頭の"/_html"を除去したURI
	 */
	public static String cutHtmlUri(String uri) {
		if (uri.startsWith(URI_HTML_PREFIX)) {
			return uri.substring(URI_HTML_LEN);
		}
		return uri;
	}

	/**
	 * EntryのACL指定情報を取得.
	 * @param entry Entry
	 * @return ACL指定情報リスト
	 */
	public static List<Contributor> getAcls(EntryBase entry) {
		if (entry == null || entry.contributor == null || entry.contributor.isEmpty()) {
			return null;
		}
		List<Contributor> acls = new ArrayList<Contributor>();
		for (Contributor contributor : entry.contributor) {
			String urn = contributor.getUri();
			if (urn != null && urn.startsWith(Constants.URN_PREFIX_ACL)) {
				acls.add(contributor);
			}
		}
		if (!acls.isEmpty()) {
			return acls;
		}
		return null;
	}

	/**
	 * Meta情報(id, author, published, updated)を除去する.
	 * @param entry
	 */
	public static void editNometa(EntryBase entry) {
		if (entry != null) {
			entry.id = null;
			entry.author = null;
			entry.published = null;
			entry.updated = null;
		}
	}

	/**
	 * 指定されたエイリアスがEntryのエイリアスに存在するかどうかチェックする.
	 * @param entry Entry
	 * @param alias エイリアス
	 * @return 指定されたエイリアスがEntryのエイリアスに存在する場合true
	 */
	public static boolean hasAlias(EntryBase entry, String alias) {
		if (entry == null || StringUtils.isBlank(alias)) {
			return false;
		}
		List<String> currentAliases = entry.getAlternate();
		if (currentAliases != null && !currentAliases.isEmpty() &&
				currentAliases.contains(alias)) {
			return true;
		}
		return false;
	}

	/**
	 * 指定されたEntryのupdatedとrevisionをつなげて返す。
	 * updated + "." + revisionの10桁0埋め
	 * @param entry Entry
	 * @return updated + revision
	 */
	public static String getUpdatedAndRevision(EntryBase entry) {
		if (entry == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(getUpdated(entry));
		sb.append(".");
		int rev = getRevisionById(entry.id);
		sb.append(StringUtils.zeroPadding(rev, INT_MAXVAL_LEN));
		return sb.toString();
	}

	/**
	 * 指定されたEntryのupdatedを返却。
	 * updatedの設定がなければpublishedを返す。
	 * @param entry Entry
	 * @return updated
	 */
	public static String getUpdated(EntryBase entry) {
		if (entry != null) {
			if (!StringUtils.isBlank(entry.updated)) {
				return entry.updated;
			} else {
				return entry.published;
			}
		}
		return null;
	}

	/**
	 * 指定されたEntryのcreated uidを取得.
	 * author.uri の値 urn:vte.cx:created:{UID} から取得
	 * @param entry
	 * @return created uid
	 */
	public static String getCreatedUid(EntryBase entry) {
		if (entry == null || entry.author == null) {
			return null;
		}
		for (Author author : entry.getAuthor()) {
			String urn = author.getUri();
			if (!StringUtils.isBlank(urn) &&
					urn.startsWith(Constants.URN_PREFIX_CREATED)) {
				return urn.substring(URN_PREFIX_CREATED_LEN);
			}
		}
		return null;
	}

	/**
	 * IDキーをFeed.linkへ設定.
	 * @param feed Feed
	 * @param idKeys IDキーリスト
	 */
	public static void setIdKeysToFeedLink(FeedBase feed, Collection<String> idKeys) {
		if (feed == null || idKeys == null || idKeys.isEmpty()) {
			return;
		}
		if (feed.link == null) {
			feed.link = new ArrayList<>();
		}
		for (String idUri : idKeys) {
			Link link = new Link();
			link._$rel = Link.REL_SELF;
			link._$href = idUri;
			feed.link.add(link);
		}
	}

	/**
	 * FeedのLinkからID URIリストを取得する.
	 * @param feed Feed
	 * @return ID URIリスト
	 */
	public static List<String> getUris(FeedBase feed) {
		if (feed == null || feed.link == null) {
			return null;
		}
		List<String> idUris = new ArrayList<>();
		for (Link link : feed.link) {
			if (Link.REL_SELF.equals(link._$rel) && !StringUtils.isBlank(link._$href)) {
				idUris.add(link._$href);
			}
		}
		return idUris;
	}

	/**
	 * IDからリビジョンを取得します.
	 * id、リビジョンの指定がない場合はnullを返します。
	 * @param id ID
	 * @return リビジョン
	 */
	public static Integer getRevisionByIdStrict(String id) {
		Integer rev = null;
		String[] uriAndRev = getUriAndRevisionById(id);
		if (uriAndRev != null && uriAndRev.length >= 2) {
			try {
				rev = Integer.parseInt(uriAndRev[1]);
			} catch (Exception e) {}	// 数値でない場合はnullを返す。
		}
		return rev;
	}

	/**
	 * 指定された親階層のURIを取得.
	 * ID URIまたはエイリアスから取得。
	 * @param parentUri 親階層
	 * @param entry Entry
	 * @return 指定された親階層のURI
	 */
	public static String getUriByParent(String parentUri, EntryBase entry) {
		if (StringUtils.isBlank(parentUri) || entry == null) {
			return null;
		}
		for (Link link : entry.link) {
			if (Link.REL_SELF.equals(link._$rel) || Link.REL_ALTERNATE.equals(link._$rel)) {
				String tmpParentUri = removeLastSlash(getParentUri(link._$href));
				if (parentUri.equals(tmpParentUri)) {
					return link._$href;
				}
			}
		}
		// IDをチェック
		String idUri = getUriById(entry.id);
		String tmpParentUri = removeLastSlash(getParentUri(idUri));
		if (parentUri.equals(tmpParentUri)) {
			return idUri;
		}

		return null;
	}

	/**
	 * FeedのlinkリストからIDリストを取得する.
	 * <link rel="self" title="{ID}">
	 * @param feed IDが設定されたlinkリスト
	 * @return IDリスト
	 */
	public static List<String> getIds(FeedBase feed) {
		// <link rel="self" title="{ID}">
		if (feed == null || feed.link == null || feed.link.isEmpty()) {
			return null;
		}
		List<String> ids = new ArrayList<>();
		for (Link link : feed.link) {
			if (Link.REL_SELF.equals(link._$rel)) {
				String id = link._$title;
				if (!StringUtils.isBlank(id)) {
					ids.add(id);
				}
			}
		}
		return ids;
	}
	
	/**
	 * 検索URIにカーソルを追加する.
	 * @param uri URI
	 * @param cursorStr カーソル
	 * @return カーソルを追加したURI
	 */
	public static String addCursorToUri(String uri, String cursorStr) {
		if (StringUtils.isBlank(cursorStr)) {
			return uri;
		}
		return UrlUtil.addParam(uri, RequestType.PARAM_NEXT, cursorStr);
	}

}
