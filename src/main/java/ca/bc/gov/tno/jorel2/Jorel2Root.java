package ca.bc.gov.tno.jorel2;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.persistence.MappedSuperclass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.NamedQuery;
import org.hibernate.annotations.NamedQueries;

@NamedQueries({
	@NamedQuery(name = "Events_FindEventsForProcessing", 
	query = "from EventsDao e where e.process=:process and e.lastFtpRun <> :runDate"),
	@NamedQuery(name = "Events_FindEventsByEventType", 
	query = "from EventsDao e inner join e.eventType as et where e.process=:process and et.eventType=:eventtype"),
	@NamedQuery(name = "Events_FindElligibleEventsByEventType", 
	query = "from EventsDao e inner join e.eventType as et where e.process=:process and et.eventType=:eventtype and e.lastFtpRun <> :runDate"),
	@NamedQuery(name = "Quotes_FindWordsByType", 
	query = "from WordsDao w where w.type=:type"),
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
    
	public enum EventType {
		
		DURATION, CHANNELWATCHER, INFLUENCERSCORE, TRENDINGSCRAPER, SMAMONITOR, SMAPOLLING, FTP, SCHEDULE, ARCHIVER, USER, 
		HTML, TRIGGER, PLSQL, MONITOR, LAUNCH, SYNC, CLEANUP, EXPIRE, LOADBINARY, CLEANBINARYROOT, ALERT, CONVERTER, EXPIRE3GP,
		PAGEWATCHER, RSS, NEWRSS, CLEANLOCALBINARYROOT, CAPTURE, SHELLCOMMAND, AUTORUN, BUZZSUMMARY, SYNDICATION
	}
	
	public enum RssSource {
		
		IPOLITICS, DAILYHIVE, CBC, CPNEWS, BIV, GEORGIASTRAIGHT;
	}
	
	public enum WordType {
		
		VERB, NOISE, TITLE, NOISENAME;
	}
	
	protected static void skip() {
		
	}
	
	protected static String getDateNow() {
		
		// Process the current date using the JDK 1.8 Time API
		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy");
		
		// Format the current date to match values in LAST_FTP_RUN
		String dateMatch = now.format(formatter);
		
		return dateMatch;
	}
	
}
