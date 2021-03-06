package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.query.Query;
import org.hibernate.Session;

/**
 * SocialMediaLinks generated by hbm2java
 */
@Entity
@Table(name = "SOCIAL_MEDIA_LINKS", schema = "TNO")
public class SocialMediaLinksDao implements java.io.Serializable {

	//private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private String link;
	private String url;
	private String urlTitle;
	private Boolean urlProcessed;
	private String channel;
	private String author;
	private BigDecimal influencerRsn;
	private BigDecimal score;
	private String responseCode;
	private Date dateCreated;
	private BigDecimal itemRsn;
	private Boolean deleted;

	public SocialMediaLinksDao() {
	}

	public SocialMediaLinksDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public SocialMediaLinksDao(BigDecimal rsn, String link, String url, String urlTitle, Boolean urlProcessed,
			String channel, String author, BigDecimal influencerRsn, BigDecimal score, String responseCode,
			Date dateCreated, BigDecimal itemRsn, Boolean deleted) {
		this.rsn = rsn;
		this.link = link;
		this.url = url;
		this.urlTitle = urlTitle;
		this.urlProcessed = urlProcessed;
		this.channel = channel;
		this.author = author;
		this.influencerRsn = influencerRsn;
		this.score = score;
		this.responseCode = responseCode;
		this.dateCreated = dateCreated;
		this.itemRsn = itemRsn;
		this.deleted = deleted;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "next_rsn")
	@SequenceGenerator(name = "next_rsn", sequenceName = "NEXT_RSN", allocationSize=1)
	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Column(name = "LINK", length = 1000)
	public String getLink() {
		return this.link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	@Column(name = "URL", length = 1000)
	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Column(name = "URL_TITLE", length = 1000)
	public String getUrlTitle() {
		return this.urlTitle;
	}

	public void setUrlTitle(String urlTitle) {
		this.urlTitle = urlTitle;
	}

	@Column(name = "URL_PROCESSED", precision = 1, scale = 0)
	public Boolean getUrlProcessed() {
		return this.urlProcessed;
	}

	public void setUrlProcessed(Boolean urlProcessed) {
		this.urlProcessed = urlProcessed;
	}

	@Column(name = "CHANNEL", length = 100)
	public String getChannel() {
		return this.channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	@Column(name = "AUTHOR", length = 100)
	public String getAuthor() {
		return this.author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	@Column(name = "INFLUENCER_RSN", precision = 38, scale = 0)
	public BigDecimal getInfluencerRsn() {
		return this.influencerRsn;
	}

	public void setInfluencerRsn(BigDecimal influencerRsn) {
		this.influencerRsn = influencerRsn;
	}

	@Column(name = "SCORE", precision = 15)
	public BigDecimal getScore() {
		return this.score;
	}

	public void setScore(BigDecimal score) {
		this.score = score;
	}

	@Column(name = "RESPONSE_CODE", length = 10)
	public String getResponseCode() {
		return this.responseCode;
	}

	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}

	@Temporal(TemporalType.DATE)
	@Column(name = "DATE_CREATED", length = 7)
	public Date getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	@Column(name = "ITEM_RSN", precision = 38, scale = 0)
	public BigDecimal getItemRsn() {
		return this.itemRsn;
	}

	public void setItemRsn(BigDecimal itemRsn) {
		this.itemRsn = itemRsn;
	}

	@Column(name = "DELETED", precision = 1, scale = 0)
	public Boolean getDeleted() {
		return this.deleted;
	}

	public void setDeleted(Boolean deleted) {
		this.deleted = deleted;
	}
	
	/**
	 * Determine if a record exists for this url, but it has been deleted
	 *
	 * @param link The Url to match.
	 * @param session The current Hibernate persistence context.
	 * @return boolean indicating whether this record has been deleted.
	 */
	public static boolean wasDeleted(String link, Session session) {
		
		boolean result = false;
		
		String sqlStmt = "select count(*) from SocialMediaLinksDao where link = :link and deleted = 1";
		
		Query<Long> query = session.createQuery(sqlStmt, Long.class);
		query.setParameter("link", link);
        List<Long> results = query.getResultList();
        
        if(results != null) {
        	result = results.get(0) > 0;
        }
        
        return result;
	}
	
	/**
	 * Get the number of NEWS_ITEMS records for this webPath and source. Used to check if an article already exists.
	 *
	 * @param source The publisher associated with the current RSS event
	 * @param session The current Hibernate persistence context
	 * @return The number of NEWS_ITMES records generated by this publisher
	 */
	public static Long getLinkCountByUrlAndAuthor(String url, String author, Session session) {
		
		long result = 0;
		
		String sqlStmt = "select count(*) from SocialMediaLinksDao where link = :link and author = :author";
		
		Query<Long> query = session.createQuery(sqlStmt, Long.class);
		query.setParameter("link", url);
		query.setParameter("author", author);
        List<Long> results = query.getResultList();
        
        if(results != null) {
        	result = results.get(0);
        }
        
        return result;
	}
			


}
