package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * FolderItem generated by hbm2java
 */
@Entity
@Table(name = "FOLDER_ITEM", schema = "TNO")
public class FolderItemDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private BigDecimal folderRsn;
	private BigDecimal itemRsn;
	private Boolean autoRunFlag;

	public FolderItemDao() {
	}

	public FolderItemDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public FolderItemDao(BigDecimal rsn, BigDecimal folderRsn, BigDecimal itemRsn, Boolean autoRunFlag) {
		this.rsn = rsn;
		this.folderRsn = folderRsn;
		this.itemRsn = itemRsn;
		this.autoRunFlag = autoRunFlag;
	}

	@Id

	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Column(name = "FOLDER_RSN", precision = 38, scale = 0)
	public BigDecimal getFolderRsn() {
		return this.folderRsn;
	}

	public void setFolderRsn(BigDecimal folderRsn) {
		this.folderRsn = folderRsn;
	}

	@Column(name = "ITEM_RSN", precision = 38, scale = 0)
	public BigDecimal getItemRsn() {
		return this.itemRsn;
	}

	public void setItemRsn(BigDecimal itemRsn) {
		this.itemRsn = itemRsn;
	}

	@Column(name = "AUTO_RUN_FLAG", precision = 1, scale = 0)
	public Boolean getAutoRunFlag() {
		return this.autoRunFlag;
	}

	public void setAutoRunFlag(Boolean autoRunFlag) {
		this.autoRunFlag = autoRunFlag;
	}
	
	/**
	 * 
	 * @param rsn The list of NEWS_ITEMS rsn to clear.
	 * @param useLastDoSyncIndex Use the date in LastDoSyncIndexDao to filter the list of records updated.
	 * @param session
	 */
	public static void updateAutoRun(long frsn, Session session){

		String sqlStmt = "update FolderItemDao set autoRunFlag = 0 where autoRunFlag = 1 and folderRsn = :frsn";

		Query<?> query = session.createQuery(sqlStmt);
		query.setParameter("frsn", BigDecimal.valueOf(frsn));
		session.beginTransaction();
		query.executeUpdate();
		session.getTransaction().commit();
	}
}
