package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
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

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Pagewatchers generated by hbm2java
 */
@Entity
@Table(name = "PAGEWATCHERS", schema = "TNO")
public class PagewatchersDao implements java.io.Serializable {

	private BigDecimal rsn;
	private String name;
	private Boolean active;
	private Date dateCreated;
	private Date dateModified;
	private String createdBy;
	private String url;
	private Date lastCheck;
	private BigDecimal watchInterval;
	private String emailRecipients;
	private BigDecimal pageResultCode;
	private BigDecimal pageLastModified;
	private Clob pageContent;
	private String startString;
	private String endString;

	public PagewatchersDao() {
	}

	public PagewatchersDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public PagewatchersDao(BigDecimal rsn, String name, Boolean active, Date dateCreated, Date dateModified,
			String createdBy, String url, Date lastCheck, BigDecimal watchInterval, String emailRecipients,
			BigDecimal pageResultCode, BigDecimal pageLastModified, Clob pageContent, String startString,
			String endString) {
		this.rsn = rsn;
		this.name = name;
		this.active = active;
		this.dateCreated = dateCreated;
		this.dateModified = dateModified;
		this.createdBy = createdBy;
		this.url = url;
		this.lastCheck = lastCheck;
		this.watchInterval = watchInterval;
		this.emailRecipients = emailRecipients;
		this.pageResultCode = pageResultCode;
		this.pageLastModified = pageLastModified;
		this.pageContent = pageContent;
		this.startString = startString;
		this.endString = endString;
	}

	@Id

	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Column(name = "NAME", length = 100)
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Column(name = "ACTIVE", precision = 1, scale = 0)
	public Boolean getActive() {
		return this.active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "DATE_CREATED", length = 7)
	public Date getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "DATE_MODIFIED", length = 7)
	public Date getDateModified() {
		return this.dateModified;
	}

	public void setDateModified(Date dateModified) {
		this.dateModified = dateModified;
	}

	@Column(name = "CREATED_BY", length = 50)
	public String getCreatedBy() {
		return this.createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	@Column(name = "URL", length = 2000)
	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "LAST_CHECK", length = 7)
	public Date getLastCheck() {
		return this.lastCheck;
	}

	public void setLastCheck(Date lastCheck) {
		this.lastCheck = lastCheck;
	}

	@Column(name = "WATCH_INTERVAL", precision = 38, scale = 0)
	public BigDecimal getWatchInterval() {
		return this.watchInterval;
	}

	public void setWatchInterval(BigDecimal watchInterval) {
		this.watchInterval = watchInterval;
	}

	@Column(name = "EMAIL_RECIPIENTS", length = 2000)
	public String getEmailRecipients() {
		return this.emailRecipients;
	}

	public void setEmailRecipients(String emailRecipients) {
		this.emailRecipients = emailRecipients;
	}

	@Column(name = "PAGE_RESULT_CODE", precision = 38, scale = 0)
	public BigDecimal getPageResultCode() {
		return this.pageResultCode;
	}

	public void setPageResultCode(BigDecimal pageResultCode) {
		this.pageResultCode = pageResultCode;
	}

	@Column(name = "PAGE_LAST_MODIFIED", precision = 38, scale = 0)
	public BigDecimal getPageLastModified() {
		return this.pageLastModified;
	}

	public void setPageLastModified(BigDecimal pageLastModified) {
		this.pageLastModified = pageLastModified;
	}

	@Column(name = "PAGE_CONTENT")
	public Clob getPageContent() {
		return this.pageContent;
	}

	public void setPageContent(Clob pageContent) {
		this.pageContent = pageContent;
	}

	@Column(name = "START_STRING", length = 50)
	public String getStartString() {
		return this.startString;
	}

	public void setStartString(String startString) {
		this.startString = startString;
	}

	@Column(name = "END_STRING", length = 50)
	public String getEndString() {
		return this.endString;
	}

	public void setEndString(String endString) {
		this.endString = endString;
	}
	
	/**
	 * 
	 * @param instance Name of the currently executing process, e.g. "jorel", "jorelMini3"
	 * @param session - The currently active Hibernate DB session
	 * @return List of EventsDao objects that match the Events_FindRssEvents named query.
	 */
	public static List<PagewatchersDao> getActivePageWatchers(Jorel2Instance instance, Session session) {

		session.beginTransaction();
		Query<PagewatchersDao> query = session.createNamedQuery("Pagewatchers_FindActivePageWatchers", PagewatchersDao.class);
        List<PagewatchersDao> results = query.getResultList();
        session.getTransaction().commit();
        
        return results;
	}
}