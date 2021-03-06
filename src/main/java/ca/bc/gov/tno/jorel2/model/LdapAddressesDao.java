package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
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
 * LdapAddresses generated by hbm2java
 */
@Entity
@Table(name = "LDAP_ADDRESSES", schema = "TNO")
public class LdapAddressesDao implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private String displayName;
	private String displayUppername;
	private String emailAddress;
	private String source;

	public LdapAddressesDao() {
	}

	public LdapAddressesDao(BigDecimal rsn, String displayName, String displayUppername, String emailAddress, String source) {
		this.rsn = rsn;
		this.displayName = displayName;
		this.displayUppername = displayUppername;
		this.emailAddress = emailAddress;
		this.source = source;
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

	@Column(name = "DISPLAY_NAME", nullable = false, length = 100)
	public String getDisplayName() {
		return this.displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	@Column(name = "DISPLAY_UPPERNAME", nullable = false, length = 100)
	public String getDisplayUppername() {
		return this.displayUppername;
	}

	public void setDisplayUppername(String displayUppername) {
		this.displayUppername = displayUppername;
	}

	@Column(name = "EMAIL_ADDRESS", nullable = false, length = 100)
	public String getEmailAddress() {
		return this.emailAddress;
	}

	@Column(name = "SOURCE", nullable = false, length = 10)
	public String getSource() {
		return this.source;
	}
	
	public void setSource(String source) {
		this.source = source;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}
	
	/**
	 * Delete all records in the LDAP_ADDRESSES table that match source.
	 * 
	 * @param source The source from which the record was imported ("ldap" or "localfile")
	 * @param session The current Hibernate persistence context.
	 */
	public static void deleteAllRecords(String source, Session session) {
		
		String sqlStmt = "delete from LdapAddressesDao where source=:source";
		
		session.beginTransaction();
		Query<?> deleteQuery = session.createQuery(sqlStmt);
		deleteQuery.setParameter("source", source);
		deleteQuery.executeUpdate();
		session.getTransaction().commit();
	}
}
