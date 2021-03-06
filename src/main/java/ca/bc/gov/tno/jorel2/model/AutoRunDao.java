package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
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
 * AutoRun generated by hbm2java
 */
@Entity
@Table(name = "AUTO_RUN", schema = "TNO")
public class AutoRunDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private Date dateCreated;

	public AutoRunDao() {
	}

	public AutoRunDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public AutoRunDao(BigDecimal rsn, Date dateCreated) {
		this.rsn = rsn;
		this.dateCreated = dateCreated;
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
	@Column(name = "DATE_CREATED", length = 7)
	public Date getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	/**
	 * Insert the single record that the AUTO_RUN table can contain. Fail if it already exists.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	
	public static void signalAutoRunEvent(Session session) {
		
		AutoRunDao autoRun = new AutoRunDao(BigDecimal.valueOf(1L), new Date());
		
		try {
			AutoRunDao.deleteAllRecords(session);
			session.beginTransaction();
			session.persist(autoRun);
			session.getTransaction().commit();
		} catch (Exception e) {
			; // Do nothing
		}
	}
	
	/**
	 * Delete all records in the AUTO_RUN table.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	public static void deleteAllRecords(Session session) {
		
		String sqlStmt = "delete from AutoRunDao";
		
		session.beginTransaction();
		Query<?> syncQuery = session.createQuery(sqlStmt);
		syncQuery.executeUpdate();
		session.getTransaction().commit();
	}

	/**
	 * Get the date trigger date and time from the AUTO_RUN table. 
	 * 
	 * @param session The current Hibernate persistence context.
	 * @return The date the auto_run record was created by the alert event handler.
	 */
	public static String getDateTrigger(Session session) {
		
		String sqlStmt = "select to_char(dateCreated,'YYYY-MM-DD HH24:MI:SS') from AutoRunDao";
		String dateStr = "";

		Query<String> query = session.createQuery(sqlStmt, String.class);
        List<String> results = query.getResultList();
        
        if(results.size() > 0) {
        	dateStr = results.get(0);
        } 
                
		return dateStr;
	}

}
