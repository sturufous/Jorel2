package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * SyncIndex generated by hbm2java
 */
@Entity
@Table(name = "SYNC_INDEX", schema = "TNO")
public class SyncIndexDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private Date dateCreated;
	private String userName;
	private String message;

	public SyncIndexDao() {
	}

	public SyncIndexDao(Date dateCreated, String userName, String message) {
		this.dateCreated = dateCreated;
		this.userName = userName;
		this.message = message;
	}

	@Id
	@Column(name = "DATE_CREATED", unique = false, nullable = false)
	public Date getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	@Column(name = "USER_NAME", length = 25)
	public String getUserName() {
		return this.userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	@Column(name = "MESSAGE", length = 2000)
	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
