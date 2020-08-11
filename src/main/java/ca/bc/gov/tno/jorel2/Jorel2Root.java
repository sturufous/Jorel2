package ca.bc.gov.tno.jorel2;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.MappedSuperclass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.NamedQuery;

import ca.bc.gov.tno.jorel2.model.EventActivityLogDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.NamedQueries;

@NamedQueries({
	@NamedQuery(name = "Events_FindEventsForProcessing", 
	query = "from EventsDao e where e.process=:process and e.lastFtpRun <> :runDate"),
	@NamedQuery(name = "Events_FindEventsByEventType", 
	query = "from EventsDao e inner join e.eventType as et where e.process=:process and et.eventType=:eventtype"),
	@NamedQuery(name = "Events_FindElligibleEventsByEventType", 
	query = "from EventsDao e inner join e.eventType as et where e.process=:process and et.eventType=:eventtype and e.lastFtpRun <> :runDate order by et.eventType"),
	@NamedQuery(name = "Events_FindMonitorEventsByDate", 
	query = "from EventsDao e inner join e.eventType as et where et.eventType=:eventType and e.lastFtpRun = :runDate"),
	@NamedQuery(name = "Events_GetEventByRsn", 
	query = "from EventsDao e where e.rsn = :rsn"),
	@NamedQuery(name = "Quotes_FindWordsByType", 
	query = "from WordsDao w where w.type=:type"),
	@NamedQuery(name = "Pagewatchers_FindActivePageWatchers", 
	query = "from PagewatchersDao p where p.active=1"),
	@NamedQuery(name = "Preferences_GetPreferencesByRsn", 
	query = "from PreferencesDao p where p.rsn = :rsn"),
})
/**
 * Maintains a global repository of variable and annotation definitions that are available throughout the system.
 * A primary role of this root superclass is the provision of a global logger.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@MappedSuperclass
public class Jorel2Root {
	
    protected static final Logger logger = LogManager.getLogger(Jorel2Root.class);
    protected static final int THREAD_POOL_SIZE = 3;
    protected static final int FATAL_CONDITION = -1;
    protected static final String INDENT0 = "";
    protected static final String INDENT1 = "    ";
    protected static final String INDENT2 = "        ";
    protected static final int DB_CONNECTION_TIMEOUT = 3000;
    protected static final String INFOMART_ID_STRING = "infomart";
    protected static final String GANDM_ID_STRING = "Globe and Mail";
    protected static final String VAN24_ID_STRING = "Vancouver 24 hrs";
    protected static final int URL_READ_TIMEOUT = 10000;
    protected static final int URL_CONNECTION_TIMEOUT = 10000;
    protected static final BigDecimal PREFERENCES_RSN = BigDecimal.valueOf(0L);
    protected static final String GANDM_DEFINITION_ID_STRING = "Globe and Mail XML";
    protected static final String LOCALFILE_SOURCE = "localfile";
    protected static final String LDAP_SOURCE = "ldap";

    
    protected static final String TOS_MSG_DFLT = "This e-mail is a service provided by the Public Affairs Bureau and is only intended for the original addressee. All content is the copyrighted property of a third party creator of the material. Copying, retransmitting, redistributing, selling, licensing, or emailing the material to any third party or any employee of the Province who is not authorized to access the material is prohibited.";
    protected static final String V35ALERTSEMAIL_DFLT = "<html>The Following (<**num**>) story(s) were added to TNO: <br><**story_links**></html>";
    protected static final String V35ALERTSEMAILLINE_DFLT = "<A HREF = \"<**httphost**>command=showstory&rsn=<**rsn**>\"><**title**></A><**images**><br>";
    protected static final String REQUEST_TRANSCRIPT_DFLT = "<A HREF = \\\"<**httphost**>command=showstory&rsn=<**rsn**>\\\"><**title**></A><br>";
    protected static final String V35ALERTSEMAILSINGLE_DFLT = "<A HREF = \"<**httphost**>command=showstory&rsn=<**rsn**>\"><**title**></A><br>";
    protected static final String V61TOS_SCRUM_DFLT = "This e-mail is a service provided by the Public Affairs Bureau and is only intended for the original addressee.";
    
    protected static String rootInstanceName = "";
    protected static final int LDAP_PAGE_SIZE = 500;
    protected static final String LDAP_SERVER_URL = "ldap://idir.bcgov:389";
    
    public enum EventType {
		
		DURATION, CHANNELWATCHER, INFLUENCERSCORE, TRENDINGSCRAPER, SMAMONITOR, SMAPOLLING, FTP, SCHEDULE, ARCHIVER, USER, 
		HTML, TRIGGER, PLSQL, MONITOR, LAUNCH, SYNC, CLEANUP, EXPIRE, LOADBINARY, CLEANBINARYROOT, ALERT, CONVERTER, EXPIRE3GP,
		PAGEWATCHER, RSS, NEWRSS, CLEANLOCALBINARYROOT, CAPTURE, SHELLCOMMAND, AUTORUN, BUZZSUMMARY, SYNDICATION, LDAP
	}
	
	public enum WordType {
		
		Verb, Noise, Title, NoiseName;
	}
	
	public enum ChangedStatus {
		CHANGED, UNCHANGED;
	}
	
	public enum ConnectionStatus {
		ONLINE, OFFLINE;
	}
	
    /** Map used to record the start times of each thread. This is used for logging and the enforcement of the maxThreadRuntime property. */
	protected static Map<Thread, Instant> threadStartTimestamps = new ConcurrentHashMap<>();
	
	protected static void skip() {
		
	}
	
	protected static void decoratedError(String indent, String message, Exception e) {
		
		String decoratedMsg = StringUtil.getLogMarker(indent) + message;
		logger.error(decoratedMsg, e);
	}
	
	protected static void decoratedTrace(String indent, String message, Session session) {
		
		EventActivityLogDao logEntry = new EventActivityLogDao(null, rootInstanceName, new Date(), message);
		boolean transactionActive = session.getTransaction().isActive();
		
		if (!transactionActive) {
			session.beginTransaction();
		}
		
		session.persist(logEntry);
		
		if (!transactionActive) {
			session.getTransaction().commit();
		}
		
		decoratedTrace(indent, message);
	}
	
	protected static void decoratedTrace(String indent, String message) {
		
		String decoratedMsg = StringUtil.getLogMarker(indent) + message + StringUtil.getThreadNumber();
		logger.trace(decoratedMsg);
	}
}