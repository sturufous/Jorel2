package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Hibernate entity representing a record in the TNO.EVENT_TYPES table.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Entity
@Table(name = "EVENT_TYPES", schema = "TNO")
public class EventTypesDao extends Jorel2Root implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private String eventType;
	private String description;
	
	//@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	//private List<EventsDao> events = new ArrayList<>();

	public EventTypesDao() {
	}

	public EventTypesDao(BigDecimal rsn) {
		this.rsn = rsn;
	}

	public EventTypesDao(BigDecimal rsn, String eventType, String description) {
		this.rsn = rsn;
		this.eventType = eventType;
		this.description = description;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Column(name = "EVENT_TYPE", length = 100)
	public String getEventType() {
		return this.eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	@Column(name = "DESCRIPTION", length = 1000)
	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String toString() {
		return "RSN = " + rsn + ", Event Type = " + eventType;
	}
}