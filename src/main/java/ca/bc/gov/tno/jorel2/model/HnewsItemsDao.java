package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * HnewsItems generated by hbm2java
 */
@Entity
@Table(name = "HNEWS_ITEMS", schema = "TNO")
public class HnewsItemsDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private Date itemDate;
	private String source;
	private Date itemTime;
	private String summary;
	private String title;
	private String type;
	private Boolean frontpagestory;
	private Boolean published;
	private Boolean archived;
	private String archivedTo;
	private Date recordCreated;
	private Date recordModified;
	private String string1;
	private String string2;
	private String string3;
	private String string4;
	private String string5;
	private String string6;
	private String string7;
	private String string8;
	private String string9;
	private BigDecimal number1;
	private BigDecimal number2;
	private Date date1;
	private Date date2;
	private String filename;
	private String fullfilepath;
	private String webpath;
	private Boolean thisjustin;
	private String importedfrom;
	private BigDecimal expireRule;
	private Boolean commentary;
	private Clob text;
	private Blob binary;
	private String contenttype;
	private Boolean binaryloaded;
	private Boolean loadbinary;
	private Boolean externalbinary;
	private Boolean cbraNonqsm;
	private String postedby;
	private Boolean onticker;
	private Boolean waptopstory;
	private Boolean alert;
	private BigDecimal autoTone;
	private Boolean categoriesLocked;
	private Boolean coreAlert;
	private Double commentaryTimeout;
	private BigDecimal commentaryExpireTime;
	private Clob transcript;
	private String eodCategory;
	private String eodCategoryGroup;
	private String eodDate;

	public HnewsItemsDao() {
	}

	public HnewsItemsDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public HnewsItemsDao(BigDecimal rsn, Date itemDate, String source, Date itemTime, String summary, String title,
			String type, Boolean frontpagestory, Boolean published, Boolean archived, String archivedTo,
			Date recordCreated, Date recordModified, String string1, String string2, String string3, String string4,
			String string5, String string6, String string7, String string8, String string9, BigDecimal number1,
			BigDecimal number2, Date date1, Date date2, String filename, String fullfilepath, String webpath,
			Boolean thisjustin, String importedfrom, BigDecimal expireRule, Boolean commentary, Clob text, Blob binary,
			String contenttype, Boolean binaryloaded, Boolean loadbinary, Boolean externalbinary, Boolean cbraNonqsm,
			String postedby, Boolean onticker, Boolean waptopstory, Boolean alert, BigDecimal autoTone,
			Boolean categoriesLocked, Boolean coreAlert, Double commentaryTimeout, BigDecimal commentaryExpireTime,
			Clob transcript, String eodCategory, String eodCategoryGroup, String eodDate) {
		this.rsn = rsn;
		this.itemDate = itemDate;
		this.source = source;
		this.itemTime = itemTime;
		this.summary = summary;
		this.title = title;
		this.type = type;
		this.frontpagestory = frontpagestory;
		this.published = published;
		this.archived = archived;
		this.archivedTo = archivedTo;
		this.recordCreated = recordCreated;
		this.recordModified = recordModified;
		this.string1 = string1;
		this.string2 = string2;
		this.string3 = string3;
		this.string4 = string4;
		this.string5 = string5;
		this.string6 = string6;
		this.string7 = string7;
		this.string8 = string8;
		this.string9 = string9;
		this.number1 = number1;
		this.number2 = number2;
		this.date1 = date1;
		this.date2 = date2;
		this.filename = filename;
		this.fullfilepath = fullfilepath;
		this.webpath = webpath;
		this.thisjustin = thisjustin;
		this.importedfrom = importedfrom;
		this.expireRule = expireRule;
		this.commentary = commentary;
		this.text = text;
		this.binary = binary;
		this.contenttype = contenttype;
		this.binaryloaded = binaryloaded;
		this.loadbinary = loadbinary;
		this.externalbinary = externalbinary;
		this.cbraNonqsm = cbraNonqsm;
		this.postedby = postedby;
		this.onticker = onticker;
		this.waptopstory = waptopstory;
		this.alert = alert;
		this.autoTone = autoTone;
		this.categoriesLocked = categoriesLocked;
		this.coreAlert = coreAlert;
		this.commentaryTimeout = commentaryTimeout;
		this.commentaryExpireTime = commentaryExpireTime;
		this.transcript = transcript;
		this.eodCategory = eodCategory;
		this.eodCategoryGroup = eodCategoryGroup;
		this.eodDate = eodDate;
	}

	@Id

	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "ITEM_DATE", length = 7)
	public Date getItemDate() {
		return this.itemDate;
	}

	public void setItemDate(Date itemDate) {
		this.itemDate = itemDate;
	}

	@Column(name = "SOURCE", length = 100)
	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "ITEM_TIME", length = 7)
	public Date getItemTime() {
		return this.itemTime;
	}

	public void setItemTime(Date itemTime) {
		this.itemTime = itemTime;
	}

	@Column(name = "SUMMARY", length = 2000)
	public String getSummary() {
		return this.summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	@Column(name = "TITLE", length = 1000)
	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@Column(name = "TYPE", length = 30)
	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@Column(name = "FRONTPAGESTORY", precision = 1, scale = 0)
	public Boolean getFrontpagestory() {
		return this.frontpagestory;
	}

	public void setFrontpagestory(Boolean frontpagestory) {
		this.frontpagestory = frontpagestory;
	}

	@Column(name = "PUBLISHED", precision = 1, scale = 0)
	public Boolean getPublished() {
		return this.published;
	}

	public void setPublished(Boolean published) {
		this.published = published;
	}

	@Column(name = "ARCHIVED", precision = 1, scale = 0)
	public Boolean getArchived() {
		return this.archived;
	}

	public void setArchived(Boolean archived) {
		this.archived = archived;
	}

	@Column(name = "ARCHIVED_TO", length = 30)
	public String getArchivedTo() {
		return this.archivedTo;
	}

	public void setArchivedTo(String archivedTo) {
		this.archivedTo = archivedTo;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "RECORD_CREATED", length = 7)
	public Date getRecordCreated() {
		return this.recordCreated;
	}

	public void setRecordCreated(Date recordCreated) {
		this.recordCreated = recordCreated;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "RECORD_MODIFIED", length = 7)
	public Date getRecordModified() {
		return this.recordModified;
	}

	public void setRecordModified(Date recordModified) {
		this.recordModified = recordModified;
	}

	@Column(name = "STRING1", length = 100)
	public String getString1() {
		return this.string1;
	}

	public void setString1(String string1) {
		this.string1 = string1;
	}

	@Column(name = "STRING2", length = 100)
	public String getString2() {
		return this.string2;
	}

	public void setString2(String string2) {
		this.string2 = string2;
	}

	@Column(name = "STRING3", length = 100)
	public String getString3() {
		return this.string3;
	}

	public void setString3(String string3) {
		this.string3 = string3;
	}

	@Column(name = "STRING4", length = 100)
	public String getString4() {
		return this.string4;
	}

	public void setString4(String string4) {
		this.string4 = string4;
	}

	@Column(name = "STRING5", length = 100)
	public String getString5() {
		return this.string5;
	}

	public void setString5(String string5) {
		this.string5 = string5;
	}

	@Column(name = "STRING6", length = 250)
	public String getString6() {
		return this.string6;
	}

	public void setString6(String string6) {
		this.string6 = string6;
	}

	@Column(name = "STRING7", length = 250)
	public String getString7() {
		return this.string7;
	}

	public void setString7(String string7) {
		this.string7 = string7;
	}

	@Column(name = "STRING8", length = 2000)
	public String getString8() {
		return this.string8;
	}

	public void setString8(String string8) {
		this.string8 = string8;
	}

	@Column(name = "STRING9", length = 2000)
	public String getString9() {
		return this.string9;
	}

	public void setString9(String string9) {
		this.string9 = string9;
	}

	@Column(name = "NUMBER1", precision = 38, scale = 0)
	public BigDecimal getNumber1() {
		return this.number1;
	}

	public void setNumber1(BigDecimal number1) {
		this.number1 = number1;
	}

	@Column(name = "NUMBER2", precision = 38, scale = 0)
	public BigDecimal getNumber2() {
		return this.number2;
	}

	public void setNumber2(BigDecimal number2) {
		this.number2 = number2;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "DATE1", length = 7)
	public Date getDate1() {
		return this.date1;
	}

	public void setDate1(Date date1) {
		this.date1 = date1;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "DATE2", length = 7)
	public Date getDate2() {
		return this.date2;
	}

	public void setDate2(Date date2) {
		this.date2 = date2;
	}

	@Column(name = "FILENAME", length = 100)
	public String getFilename() {
		return this.filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	@Column(name = "FULLFILEPATH", length = 1000)
	public String getFullfilepath() {
		return this.fullfilepath;
	}

	public void setFullfilepath(String fullfilepath) {
		this.fullfilepath = fullfilepath;
	}

	@Column(name = "WEBPATH", length = 1000)
	public String getWebpath() {
		return this.webpath;
	}

	public void setWebpath(String webpath) {
		this.webpath = webpath;
	}

	@Column(name = "THISJUSTIN", precision = 1, scale = 0)
	public Boolean getThisjustin() {
		return this.thisjustin;
	}

	public void setThisjustin(Boolean thisjustin) {
		this.thisjustin = thisjustin;
	}

	@Column(name = "IMPORTEDFROM", length = 100)
	public String getImportedfrom() {
		return this.importedfrom;
	}

	public void setImportedfrom(String importedfrom) {
		this.importedfrom = importedfrom;
	}

	@Column(name = "EXPIRE_RULE", precision = 38, scale = 0)
	public BigDecimal getExpireRule() {
		return this.expireRule;
	}

	public void setExpireRule(BigDecimal expireRule) {
		this.expireRule = expireRule;
	}

	@Column(name = "COMMENTARY", precision = 1, scale = 0)
	public Boolean getCommentary() {
		return this.commentary;
	}

	public void setCommentary(Boolean commentary) {
		this.commentary = commentary;
	}

	@Column(name = "TEXT")
	public Clob getText() {
		return this.text;
	}

	public void setText(Clob text) {
		this.text = text;
	}

	@Column(name = "BINARY")
	public Blob getBinary() {
		return this.binary;
	}

	public void setBinary(Blob binary) {
		this.binary = binary;
	}

	@Column(name = "CONTENTTYPE", length = 20)
	public String getContenttype() {
		return this.contenttype;
	}

	public void setContenttype(String contenttype) {
		this.contenttype = contenttype;
	}

	@Column(name = "BINARYLOADED", precision = 1, scale = 0)
	public Boolean getBinaryloaded() {
		return this.binaryloaded;
	}

	public void setBinaryloaded(Boolean binaryloaded) {
		this.binaryloaded = binaryloaded;
	}

	@Column(name = "LOADBINARY", precision = 1, scale = 0)
	public Boolean getLoadbinary() {
		return this.loadbinary;
	}

	public void setLoadbinary(Boolean loadbinary) {
		this.loadbinary = loadbinary;
	}

	@Column(name = "EXTERNALBINARY", precision = 1, scale = 0)
	public Boolean getExternalbinary() {
		return this.externalbinary;
	}

	public void setExternalbinary(Boolean externalbinary) {
		this.externalbinary = externalbinary;
	}

	@Column(name = "CBRA_NONQSM", precision = 1, scale = 0)
	public Boolean getCbraNonqsm() {
		return this.cbraNonqsm;
	}

	public void setCbraNonqsm(Boolean cbraNonqsm) {
		this.cbraNonqsm = cbraNonqsm;
	}

	@Column(name = "POSTEDBY", length = 20)
	public String getPostedby() {
		return this.postedby;
	}

	public void setPostedby(String postedby) {
		this.postedby = postedby;
	}

	@Column(name = "ONTICKER", precision = 1, scale = 0)
	public Boolean getOnticker() {
		return this.onticker;
	}

	public void setOnticker(Boolean onticker) {
		this.onticker = onticker;
	}

	@Column(name = "WAPTOPSTORY", precision = 1, scale = 0)
	public Boolean getWaptopstory() {
		return this.waptopstory;
	}

	public void setWaptopstory(Boolean waptopstory) {
		this.waptopstory = waptopstory;
	}

	@Column(name = "ALERT", precision = 1, scale = 0)
	public Boolean getAlert() {
		return this.alert;
	}

	public void setAlert(Boolean alert) {
		this.alert = alert;
	}

	@Column(name = "AUTO_TONE", precision = 38, scale = 0)
	public BigDecimal getAutoTone() {
		return this.autoTone;
	}

	public void setAutoTone(BigDecimal autoTone) {
		this.autoTone = autoTone;
	}

	@Column(name = "CATEGORIES_LOCKED", precision = 1, scale = 0)
	public Boolean getCategoriesLocked() {
		return this.categoriesLocked;
	}

	public void setCategoriesLocked(Boolean categoriesLocked) {
		this.categoriesLocked = categoriesLocked;
	}

	@Column(name = "CORE_ALERT", precision = 1, scale = 0)
	public Boolean getCoreAlert() {
		return this.coreAlert;
	}

	public void setCoreAlert(Boolean coreAlert) {
		this.coreAlert = coreAlert;
	}

	@Column(name = "COMMENTARY_TIMEOUT", precision = 63, scale = 0)
	public Double getCommentaryTimeout() {
		return this.commentaryTimeout;
	}

	public void setCommentaryTimeout(Double commentaryTimeout) {
		this.commentaryTimeout = commentaryTimeout;
	}

	@Column(name = "COMMENTARY_EXPIRE_TIME", precision = 38, scale = 0)
	public BigDecimal getCommentaryExpireTime() {
		return this.commentaryExpireTime;
	}

	public void setCommentaryExpireTime(BigDecimal commentaryExpireTime) {
		this.commentaryExpireTime = commentaryExpireTime;
	}

	@Column(name = "TRANSCRIPT")
	public Clob getTranscript() {
		return this.transcript;
	}

	public void setTranscript(Clob transcript) {
		this.transcript = transcript;
	}

	@Column(name = "EOD_CATEGORY", length = 250)
	public String getEodCategory() {
		return this.eodCategory;
	}

	public void setEodCategory(String eodCategory) {
		this.eodCategory = eodCategory;
	}

	@Column(name = "EOD_CATEGORY_GROUP", length = 40)
	public String getEodCategoryGroup() {
		return this.eodCategoryGroup;
	}

	public void setEodCategoryGroup(String eodCategoryGroup) {
		this.eodCategoryGroup = eodCategoryGroup;
	}

	@Column(name = "EOD_DATE", length = 10)
	public String getEodDate() {
		return this.eodDate;
	}

	public void setEodDate(String eodDate) {
		this.eodDate = eodDate;
	}

	/**
	 * Get the historical news item record matching the RSN. 
	 * 
	 * @param rsn The rsn for the record to match.
	 * @param session The current Hibernate persistence context
	 * @return A list containing the single matching record, or empty if no match.
	 */
	public static List<HnewsItemsDao> getItemByRsn(BigDecimal rsn, Session session) {
		
		String sqlStmt = "from HnewsItemsDao ni where ni.rsn =:rsn";

		Query<HnewsItemsDao> query = session.createQuery(sqlStmt, HnewsItemsDao.class);
		query.setParameter("rsn", rsn);
        List<HnewsItemsDao> results = query.getResultList();
        
		return results;
	}
	
	/**
	 * 
	 * @param session The current Hibernate persistence context
	 * @return A list of this publisher's news items created since yesterday
	 */
	public static List<Object[]> getEligibleForArchive(Session session) {
		
		String sqlStmt = "select n.rsn, n.type, n.itemDate from HnewsItemsDao n, SourceTypesDao t, SourcesDao s where n.type = t.type" + 
				" and n.source = s.source and n.archived = 0 and ((t.action = 1 and ((n.expireRule = 0 and FLOOR(sysdate - n.itemDate) > t.days)" + 
				" or (n.expireRule = 1 and FLOOR(sysdate - n.itemDate) > t.special))) OR (t.action = 2 and s.tvarchive = 1" + 
				" and ((n.expireRule = 0 and FLOOR(sysdate - n.itemDate) > t.days) or (n.expireRule = 1 and FLOOR(sysdate - n.itemDate) > t.special)))) order by n.itemDate";

		@SuppressWarnings("unchecked")
		Query<Object[]> query = session.createQuery(sqlStmt);
        List<Object[]> results = query.getResultList();
        
		return results;
	}
	
	/**
	 * Get expired news items for ExpireEventProcessor.clearExpiringSourceTypes().
	 * 
	 * @param sourceType The type of source for which news items should be retrieved.
	 * @param tvSources Ignore any hnews_items records with sources in this list.
	 * @param retainDays The number of days before the news item expires.
	 * @param expireRule Only retrieve hnews_items that match this expire rule.
	 * @param session The current Hibernate persistence context.
	 * @return A list containing all the records that meet the expiry criteria.
	 */
	public static List<HnewsItemsDao> getExpiredItems(String sourceType, String tvSources, BigDecimal retainDays, BigDecimal expireRule, Session session) {
		
		String sqlStmt = "";
		Query<HnewsItemsDao> query = null; 
		
		if(sourceType.equalsIgnoreCase("TV News")) {
			sqlStmt = "from HnewsItemsDao where type = 'TV News' and source not in (:TVSources) and itemDate < (sysdate - :retainDays) and expireRule = :expiryRule and archived = 0";
			query = session.createQuery(sqlStmt, HnewsItemsDao.class);
			query.setParameter("TVSources", tvSources);
		} else {
			sqlStmt = "from HnewsItemsDao where type = :sourceType and itemDate < (sysdate - :retainDays) and expireRule = :expiryRule and archived = 0";
			query = session.createQuery(sqlStmt, HnewsItemsDao.class);
			query.setParameter("sourceType", sourceType);
		};

		query.setParameter("retainDays", retainDays);
		query.setParameter("expiryRule", expireRule);
        List<HnewsItemsDao> results = query.getResultList();
        
		return results;
	}
	
	/**
	 * Get expired items for ExpireEventProcessor.clearExpiringSources(). 
	 * 
	 * @param source Only retrieve hnews_items that match this source.
	 * @param days Return only items that are at least "days" old.
	 * @param session The current Hibernate persistence context.
	 * @return A list containing all the records that meet the expiry criteria.
	 */
	public static List<HnewsItemsDao> getExpiredItems(String source, BigDecimal days, Session session) {
		
		String sqlStmt = "from HnewsItemsDao where source = :source and itemDate < (sysdate - :days) and expireRule = 0 and archived = 0";

		Query<HnewsItemsDao> query = session.createQuery(sqlStmt, HnewsItemsDao.class);
		query.setParameter("source", source);
		query.setParameter("days", days);
        List<HnewsItemsDao> results = query.getResultList();
        
		return results;
	}
	
	/**
	 * Delete the record corresponding with the object <code>item</code>.
	 * 
	 * @param item The object representing the item in the NewsItems table to be deleted.
	 * @param session The current Hibernate persistence context.
	 */
	public static void deleteRecord(HnewsItemsDao item, Session session) {
		
		session.remove(item);
		session.flush();
		session.clear();
	}
}
