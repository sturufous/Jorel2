package ca.bc.gov.tno.jorel2.model;
// Generated Dec 24, 2019, 8:06:31 AM by Hibernate Tools 5.0.6.Final

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.Session;
import org.hibernate.query.Query;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;

/**
 * Hibernate entity representing a record in TNO.EVENTS.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Entity
@Table(name = "EVENTS", schema = "TNO")
public class EventsDao extends Jorel2Root implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private BigDecimal rsn;
	private String name;
	private BigDecimal eventTypeRsn;
	private String process;
	private String channel;
	private String source;
	private String fileName;
	private String lastFtpRun;
	private String startTime;
	private String stopTime;
	private String frequency;
	private BigDecimal keepTrying;
	private String keepTryingStatus;
	private BigDecimal attemptsLimit;
	private BigDecimal attemptsMade;
	private Boolean triggerImport;
	private String definitionName;
	private String title;
	private String launchTime;
	private String captureCommand;
	private String clipCommand;
	private Boolean ccCapture;
	private EventTypesDao eventType;
	
	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
	@JoinColumn(name = "EVENT_TYPE_RSN")
	public EventTypesDao getEventType() {
		return this.eventType;
	}
	
	public void setEventType(EventTypesDao eventType) {
		this.eventType = eventType;
	}
	
	public EventsDao() {
	}

	public EventsDao(BigDecimal rsn, String name, BigDecimal eventTypeRsn, String process) {
		this.rsn = rsn;
		this.name = name;
		this.eventTypeRsn = eventTypeRsn;
		this.process = process;
	}

	public EventsDao(BigDecimal rsn, String name, BigDecimal eventTypeRsn, String process, String channel, String source,
			String fileName, String lastFtpRun, String startTime, String stopTime, String frequency,
			BigDecimal keepTrying, String keepTryingStatus, BigDecimal attemptsLimit, BigDecimal attemptsMade,
			Boolean triggerImport, String definitionName, String title, String launchTime, String captureCommand,
			String clipCommand, Boolean ccCapture) {
		this.rsn = rsn;
		this.name = name;
		this.eventTypeRsn = eventTypeRsn;
		this.process = process;
		this.channel = channel;
		this.source = source;
		this.fileName = fileName;
		this.lastFtpRun = lastFtpRun;
		this.startTime = startTime;
		this.stopTime = stopTime;
		this.frequency = frequency;
		this.keepTrying = keepTrying;
		this.keepTryingStatus = keepTryingStatus;
		this.attemptsLimit = attemptsLimit;
		this.attemptsMade = attemptsMade;
		this.triggerImport = triggerImport;
		this.definitionName = definitionName;
		this.title = title;
		this.launchTime = launchTime;
		this.captureCommand = captureCommand;
		this.clipCommand = clipCommand;
		this.ccCapture = ccCapture;
	}

	@Id
	@Column(name = "RSN", unique = true, nullable = false, precision = 38, scale = 0)
	public BigDecimal getRsn() {
		return this.rsn;
	}

	public void setRsn(BigDecimal rsn) {
		this.rsn = rsn;
	}

	@Column(name = "NAME", nullable = false, length = 50)
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setEventTypeRsn(BigDecimal eventTypeRsn) {
		this.eventTypeRsn = eventTypeRsn;
	}

	@Column(name = "PROCESS", nullable = false, length = 30)
	public String getProcess() {
		return this.process;
	}

	public void setProcess(String process) {
		this.process = process;
	}

	@Column(name = "CHANNEL", length = 100)
	public String getChannel() {
		return this.channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	@Column(name = "SOURCE", length = 100)
	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	@Column(name = "FILE_NAME", length = 1000)
	public String getFileName() {
		return this.fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	@Column(name = "LAST_FTP_RUN", length = 20)
	public String getLastFtpRun() {
		return this.lastFtpRun;
	}

	public void setLastFtpRun(String lastFtpRun) {
		this.lastFtpRun = lastFtpRun;
	}

	@Column(name = "START_TIME", length = 10)
	public String getStartTime() {
		return this.startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	@Column(name = "STOP_TIME", length = 10)
	public String getStopTime() {
		return this.stopTime;
	}

	public void setStopTime(String stopTime) {
		this.stopTime = stopTime;
	}

	@Column(name = "FREQUENCY", length = 10)
	public String getFrequency() {
		return this.frequency;
	}

	public void setFrequency(String frequency) {
		this.frequency = frequency;
	}

	@Column(name = "KEEP_TRYING", precision = 38, scale = 0)
	public BigDecimal getKeepTrying() {
		return this.keepTrying;
	}

	public void setKeepTrying(BigDecimal keepTrying) {
		this.keepTrying = keepTrying;
	}

	@Column(name = "KEEP_TRYING_STATUS", length = 20)
	public String getKeepTryingStatus() {
		return this.keepTryingStatus;
	}

	public void setKeepTryingStatus(String keepTryingStatus) {
		this.keepTryingStatus = keepTryingStatus;
	}

	@Column(name = "ATTEMPTS_LIMIT", precision = 38, scale = 0)
	public BigDecimal getAttemptsLimit() {
		return this.attemptsLimit;
	}

	public void setAttemptsLimit(BigDecimal attemptsLimit) {
		this.attemptsLimit = attemptsLimit;
	}

	@Column(name = "ATTEMPTS_MADE", precision = 38, scale = 0)
	public BigDecimal getAttemptsMade() {
		return this.attemptsMade;
	}

	public void setAttemptsMade(BigDecimal attemptsMade) {
		this.attemptsMade = attemptsMade;
	}

	@Column(name = "TRIGGER_IMPORT", precision = 1, scale = 0)
	public Boolean getTriggerImport() {
		return this.triggerImport;
	}

	public void setTriggerImport(Boolean triggerImport) {
		this.triggerImport = triggerImport;
	}

	@Column(name = "DEFINITION_NAME", length = 30)
	public String getDefinitionName() {
		return this.definitionName;
	}

	public void setDefinitionName(String definitionName) {
		this.definitionName = definitionName;
	}

	@Column(name = "TITLE", length = 1000)
	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@Column(name = "LAUNCH_TIME", length = 10)
	public String getLaunchTime() {
		return this.launchTime;
	}

	public void setLaunchTime(String launchTime) {
		this.launchTime = launchTime;
	}

	@Column(name = "CAPTURE_COMMAND", length = 1000)
	public String getCaptureCommand() {
		return this.captureCommand;
	}

	public void setCaptureCommand(String captureCommand) {
		this.captureCommand = captureCommand;
	}

	@Column(name = "CLIP_COMMAND", length = 1000)
	public String getClipCommand() {
		return this.clipCommand;
	}

	public void setClipCommand(String clipCommand) {
		this.clipCommand = clipCommand;
	}

	@Column(name = "CC_CAPTURE", precision = 1, scale = 0)
	public Boolean getCcCapture() {
		return this.ccCapture;
	}

	public void setCcCapture(Boolean ccCapture) {
		this.ccCapture = ccCapture;
	}
	
	public String toString() {
		return "Name = " + name;
	}
	
	/**
	 * Returns all Jorel entries in the EVENTS table that have a LAST_FTP_RUN value prior to the current date.
	 * 
	 * @param session - The currently active Hibernate DB session
	 * @return List of EventsDao objects that match the Events_FindEventsForProcessing named query.
	 */
	public static List<EventsDao> getEventsForProcessing(Session session) {

		// Process the current date using the JDK 1.8 Time API
		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy");
		
		// Format the current date to match values in LAST_FTP_RUN
		String dateMatch = now.format(formatter);
		
		Query<EventsDao> query = session.createNamedQuery("Events_FindEventsForProcessing", EventsDao.class);
		query.setParameter("process", "jorel");
		query.setParameter("runDate", dateMatch);
        List<EventsDao> results = query.getResultList();
        
        return results;
	}
	
	/**
	 * Returns all Jorel entries in the EVENTS table that have an EVENT_TYPE of RSS or NEWRSS (NEWRSS created for testing).
	 * 
	 * @param session - The currently active Hibernate DB session
	 * @return List of EventsDao objects that match the Events_FindRssEvents named query.
	 */
	public static List<Object[]> getRssEvents(Session session) {

		Query<Object[]> query = session.createQuery("from EventsDao e inner join e.eventType as et where e.process=:process and et.eventType=:eventtype");
		query.setParameter("process", "jorel");
		query.setParameter("eventtype", "NEWRSS");
        List<Object[]> results = query.getResultList();
        
        return results;
	}

}
