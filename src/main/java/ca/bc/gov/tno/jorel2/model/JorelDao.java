package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.time.LocalDateTime;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Jorel generated by hbm2java
 */
@Entity
@Table(name = "JOREL", schema = "TNO")
public class JorelDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private String lastAlertRun;

	public JorelDao() {
	}

	public JorelDao(String lastAlertRun) {
		this.lastAlertRun = lastAlertRun;
	}

	@Id

	@Column(name = "LAST_ALERT_RUN", length = 50)
	public String getLastAlertRun() {
		return this.lastAlertRun;
	}

	public void setLastAlertRun(String lastAlertRun) {
		this.lastAlertRun = lastAlertRun;
	}
	
	public static void updateLastAlertRunTime(String runTime, Session session) {
		
		String sqlStmt = "from JorelDao";
		
		Query<JorelDao> query = session.createQuery(sqlStmt, JorelDao.class);
        List<JorelDao> results = query.getResultList();
        
        if(results.size() > 0) {
			String time = LocalDateTime.now().toString();
			JorelDao jorelRecord = results.get(0);
			JorelDao newRecord = new JorelDao(time);
			//jorelRecord.setLastAlertRun(time);
			session.beginTransaction();
			session.delete(jorelRecord);
			session.persist(newRecord);
			session.getTransaction().commit();
        }
	}
}
