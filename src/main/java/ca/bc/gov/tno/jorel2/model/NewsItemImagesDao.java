package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * NewsItemImages generated by hbm2java
 */
@Entity
@Table(name = "NEWS_ITEM_IMAGES", schema = "TNO")
public class NewsItemImagesDao implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private BigDecimal itemRsn;
	private Boolean onFrontPage;
	private BigDecimal width;
	private BigDecimal height;
	private String caption;
	private String binaryPath;
	private String fileName;
	private Boolean imageLoaded;
	private Boolean processed;

	public NewsItemImagesDao() {
	}

	public NewsItemImagesDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public NewsItemImagesDao(BigDecimal rsn, BigDecimal itemRsn, Boolean onFrontPage, BigDecimal width, BigDecimal height,
			String caption, String binaryPath, String fileName, Boolean imageLoaded, Boolean processed) {
		this.rsn = rsn;
		this.itemRsn = itemRsn;
		this.onFrontPage = onFrontPage;
		this.width = width;
		this.height = height;
		this.caption = caption;
		this.binaryPath = binaryPath;
		this.fileName = fileName;
		this.imageLoaded = imageLoaded;
		this.processed = processed;
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

	@Column(name = "ITEM_RSN", precision = 38, scale = 0)
	public BigDecimal getItemRsn() {
		return this.itemRsn;
	}

	public void setItemRsn(BigDecimal itemRsn) {
		this.itemRsn = itemRsn;
	}

	@Column(name = "ON_FRONT_PAGE", precision = 1, scale = 0)
	public Boolean getOnFrontPage() {
		return this.onFrontPage;
	}

	public void setOnFrontPage(Boolean onFrontPage) {
		this.onFrontPage = onFrontPage;
	}

	@Column(name = "WIDTH", precision = 38, scale = 0)
	public BigDecimal getWidth() {
		return this.width;
	}

	public void setWidth(BigDecimal width) {
		this.width = width;
	}

	@Column(name = "HEIGHT", precision = 38, scale = 0)
	public BigDecimal getHeight() {
		return this.height;
	}

	public void setHeight(BigDecimal height) {
		this.height = height;
	}

	@Column(name = "CAPTION", length = 2000)
	public String getCaption() {
		return this.caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	@Column(name = "BINARY_PATH", length = 1000)
	public String getBinaryPath() {
		return this.binaryPath;
	}

	public void setBinaryPath(String binaryPath) {
		this.binaryPath = binaryPath;
	}

	@Column(name = "FILE_NAME", length = 100)
	public String getFileName() {
		return this.fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	@Column(name = "IMAGE_LOADED", precision = 1, scale = 0)
	public Boolean getImageLoaded() {
		return this.imageLoaded;
	}

	public void setImageLoaded(Boolean imageLoaded) {
		this.imageLoaded = imageLoaded;
	}

	@Column(name = "PROCESSED", precision = 1, scale = 0)
	public Boolean getProcessed() {
		return this.processed;
	}

	public void setProcessed(Boolean processed) {
		this.processed = processed;
	}
	
	/**
	 * Get the NEWS_ITEM_IMAGES record(s) matching the fileName.
	 *
	 * @param fileName The fileName to match.
	 * @param session The current Hibernate persistence context
	 * @return The record(s) matching fileName
	 */
	public static List<NewsItemImagesDao> getImageRecordsByFileName(String fileName, String avPath, Session session) {
		
		String sqlStmt = "from NewsItemImagesDao where fileName=:fileName and binaryPath=:binaryPath";
		
		Query<NewsItemImagesDao> query = session.createQuery(sqlStmt, NewsItemImagesDao.class);
		query.setParameter("fileName", fileName);
		query.setParameter("binaryPath", avPath);
        List<NewsItemImagesDao> results = query.getResultList();
        
        return results;
	}
}
