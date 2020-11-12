package ca.bc.gov.tno.jorel2;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.persistence.MappedSuperclass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.NamedQuery;

import ca.bc.gov.tno.jorel2.controller.Jorel2Runnable;
import ca.bc.gov.tno.jorel2.model.EventActivityLogDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;

import org.hibernate.Session;
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
 * Maintains a global repository of variable, methods and annotation definitions that are available throughout the system.
 * A primary role of this root superclass is the provision of a global logger.
 * 
 * @author Stuart Morse
 * @version 0.0.1 All previous updates.
 * @version 0.0.2 03 Sep 20 - Changing the logging rollover policy from size based to time based.
 * @version 0.0.13 04 Sep 20 - Adding small screen device format to Jorel reports.
 * @version 0.0.14 15 Sep 20 - Changing connection release policy to ConnectionReleaseMode.ON_CLOSE.
 * @version 0.0.15 16 Sep 20 - Fix appearance of "<**images**>" in alert emails.
 * @version 0.0.16 21 Sep 20 - Adding Channelwatcher event handling.
 * @version 0.0.17 25 Sep 20 - Added getBuildNumber(), getActiveThreads(), getServerDetails() to Jorel2ServerInstance.
 * @version 0.0.18 08 Oct 20 - Added array-formatted output for database interruptions to Jorel2ServerInstance.
 * @version 0.0.19 13 Oct 20 - Implemented remote shutdown.
 * @version 0.0.20 14 Oct 20 - Implemented handling for the commentary PL/SQL event.
 * @version 0.0.21 29 Oct 20 - Changed password for LDAP event and made the event live.
 * @version 0.0.22 05 Nov 20 - Store front page images in source_paper_images.
 */

@MappedSuperclass
public class Jorel2Root {
	
	/** Constants for use throughout Jorel2 */
	protected static final String buildNumber = "0.0.22";
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
    protected static final int SHUTDOWN_REQUESTED = 1000;

    
    protected static final String TOS_MSG_DFLT = "This e-mail is a service provided by the Public Affairs Bureau and is only intended for the original addressee. All content is the copyrighted property of a third party creator of the material. Copying, retransmitting, redistributing, selling, licensing, or emailing the material to any third party or any employee of the Province who is not authorized to access the material is prohibited.";
    protected static final String V35ALERTSEMAIL_DFLT = "<html>The Following (<**num**>) story(s) were added to TNO: <br><**story_links**></html>";
    protected static final String V35ALERTSEMAILLINE_DFLT = "<A HREF = \"<**httphost**>command=showstory&rsn=<**rsn**>\"><**title**></A><**images**><br>";
    protected static final String REQUEST_TRANSCRIPT_DFLT = "<A HREF = \\\"<**httphost**>command=showstory&rsn=<**rsn**>\\\"><**title**></A><br>";
    protected static final String V35ALERTSEMAILSINGLE_DFLT = "<A HREF = \"<**httphost**>command=showstory&rsn=<**rsn**>\"><**title**></A><br>";
    protected static final String V61TOS_SCRUM_DFLT = "This e-mail is a service provided by the Public Affairs Bureau and is only intended for the original addressee.";
    
    protected static String rootInstanceName = "";
    protected static final int LDAP_PAGE_SIZE = 500;
    protected static final String LDAP_SERVER_URL = "ldap://idir.bcgov:389";
    
	// URL matching patterns
	private final static String URL_VALID_PRECEEDING_CHARS = "(?:[^/\"':!=]|^|\\:)";
	private final static String URL_VALID_DOMAIN = "(?:[\\.-]|[^\\p{Punct}])+\\.[a-z]{2,}(?::[0-9]+)?";
	private final static String URL_VALID_URL_PATH_CHARS = "[a-z0-9!\\*'\\(\\);:&=\\+\\$/%#\\[\\]\\-_\\.,~]";
	
	// Valid end-of-path chracters (so /foo. does not gobble the period).
	//   1. Allow ) for Wikipedia URLs.
	//   2. Allow =&# for empty URL parameters and other URL-join artifacts
	private final static String URL_VALID_URL_PATH_ENDING_CHARS = "[a-z0-9\\)=#/]";
	private final static String URL_VALID_URL_QUERY_CHARS = "[a-z0-9!\\*'\\(\\);:&=\\+\\$/%#\\[\\]\\-_\\.,~]";
	private final static String URL_VALID_URL_QUERY_ENDING_CHARS = "[a-z0-9_&=#]";
	protected final static String VALID_URL_PATTERN_STRING = "(" +     //  $1 total match
	"(" + URL_VALID_PRECEEDING_CHARS + ")" +                  //  $2 Preceeding chracter
	"(" +                                                     //  $3 URL
	"(https?://|www\\.)" +                                    //  $4 Protocol or beginning
	"(" + URL_VALID_DOMAIN + ")" +                            //  $5 Domain(s) and optional port number
	"(/" + URL_VALID_URL_PATH_CHARS + "*" +                   //  $6 URL Path
	URL_VALID_URL_PATH_ENDING_CHARS + "?)?" +
	"(\\?" + URL_VALID_URL_QUERY_CHARS + "*" +                //  $7 Query String
	URL_VALID_URL_QUERY_ENDING_CHARS + ")?" +
	")" +
	")";
	
	protected static final Pattern VALID_URL = Pattern.compile(VALID_URL_PATTERN_STRING, Pattern.CASE_INSENSITIVE);
	protected static final Pattern TITLE_TAG = Pattern.compile("\\<title>(.*)\\</title>", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

    
    /** Enumeration containing entries for all event types processed by Jorel2 */
    public enum EventType {
		
		DURATION, CHANNELWATCHER, ARCHIVER, USER, HTML, PLSQL, SYNC, ALERT, AUTORUN, MONITOR, EXPIRE, CLEANBINARYROOT, 
		CONVERTER, EXPIRE3GP, PAGEWATCHER, RSS, CAPTURE, SHELLCOMMAND, SYNDICATION, LDAP
	}
	
    /** Enumeration used to categorize different words in the quote extractor object */
	public enum WordType {
		
		Verb, Noise, Title, NoiseName;
	}
	
	/** Enumeration that indicates whether an observed entity has been changed */
	public enum ChangedStatus {
		
		CHANGED, UNCHANGED;
	}
	
	/** Enumeration that indicates whether the Hibernate database connection is online or offline */
	public enum ConnectionStatus {
		
		ONLINE, OFFLINE;
	}
	
    /** Map used to record the start times of each thread. This is used for logging and the enforcement of the maxThreadRuntime property. */
	protected static Map<Jorel2ThreadInstance, String> activeThreads = new ConcurrentHashMap<>();
	
    /** Map used to record the start times of each thread. This is used for logging and the enforcement of the maxThreadRuntime property. */
	protected static Map<String, String> eventTypesProcessed = new ConcurrentHashMap<>();
	
	/** 
	 * Method that does nothing, for use as a pattern in if/else statements for readability 
	 */
	protected static void skip() {
		
	}
	
	/**
	 * Writes error messages, including an exception stack tract, to the jorel2.log file.
	 * 
	 * @param indent String to use as an indent prior the the message text.
	 * @param message Contextual message indicating the task being executed when the error occurred.
	 * @param e An exception, which may have been generated by the VM or created in the calling method.
	 */
	protected static void decoratedError(String indent, String message, Exception e) {
		
		String decoratedMsg = StringUtil.getLogMarker(indent) + message;
		logger.error(decoratedMsg, e);
	}
	
	/**
	 * Writes a trace message to the log file and the event activity log. This method may be called as part of an existing transaction
	 * or stand-alone, so the Hibernate session is checked for the existence of an active transaction prior to persisting the activity log
	 * entry. If no active transaction exists, one is created and the new record is committed. If one does exist, the code relies on a commit
	 * operation in the calling code.
	 * 
	 * @param indent String to use as an indent prior the the message text.
	 * @param message Contextual message indicating the task being executed when the error occurred.
	 * @param session The current Hibernate persistence context.
	 */
	@SuppressWarnings("unused")
	protected static void decoratedTrace(String indent, String message, Session session) {
				
		if (session == null) {
			decoratedTrace(indent, message);
		} else {
			if (message.length() > 2000) {
				message = message.substring(0, 1999);
			}
			
			EventActivityLogDao logEntry = new EventActivityLogDao(null, rootInstanceName, new Date(), message);
			boolean transactionActive = session.getTransaction().isActive();

			if (transactionActive) {
				session.persist(logEntry);
			} else {
				session.beginTransaction();
				session.persist(logEntry);
				session.getTransaction().commit();
			}
			
			decoratedTrace(indent, message);
		}
	}
	
	/**
	 * Writes a trace message to the log file. 
	 * 
	 * @param indent String to use as an indent prior the the message text.
	 * @param message Contextual message indicating the task being executed when the error occurred.
	 */
	protected static void decoratedTrace(String indent, String message) {
		
		String decoratedMsg = StringUtil.getLogMarker(indent) + message + StringUtil.getThreadNumber();
		logger.trace(decoratedMsg);
	}
	
	/**
	 * Sets a timeout for the current thread based on the long value (in seconds) contained in EventsDao.timeout. If this timeout 
	 * value is null, the default timeout (from the properties file) is used instead.
	 * 
	 * @param runnable The runnable object currently being executed.
	 * @param currentEvent The event currently being processed
	 * @param instance The server instance object that contains the default timeout value.
	 */
	protected static void setThreadTimeout(Jorel2Runnable runnable, EventsDao currentEvent, Jorel2ServerInstance instance) {
		
		if (currentEvent.getTimeout() == null) {
			runnable.getJorel2ThreadInstance().setTimeoutSeconds(instance.getMaxThreadRuntime());
		} else {
			runnable.getJorel2ThreadInstance().setTimeoutSeconds(currentEvent.getTimeout().longValue());
		}
	}
}