package ca.bc.gov.tno.jorel2.controller;

import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.toMap;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.inject.Inject;
import javax.persistence.PersistenceException;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2ThreadInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;

import static java.util.stream.Collectors.*;
import static java.util.Map.Entry.*;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public final class Jorel2Runnable extends Jorel2Root implements Runnable {
	
	/** Gives access to the thread and other metrics associated with this Jorel2Runnable */
	private Jorel2ThreadInstance jorelThread = null;
	
	/** The case-dependent name of this event type */
	private String eventTypeName = "";
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Inject
	private DataSourceConfig config;
	
	/** RSS Event processor service */
	@Inject
    private RssEventProcessor rssEventProcessor;
	
	/** Syndication Event processor service */
	@Inject
    private SyndicationEventProcessor syndicationEventProcessor;
	
	/** PageWatcher Event processor service */
	@Inject
    private PageWatcherEventProcessor pageWatcherEventProcessor;
	
	/** Shell Command Event processor service */
	@Inject
    private ShellCommandEventProcessor shellCommandEventProcessor;
	
	/** Duration Event processor service */
	@Inject
    private DurationEventProcessor durationEventProcessor;
	
	/** CleanBinaryRoot Event processor service */
	@Inject
    private CleanBinaryRootEventProcessor cleanBinaryRootEventProcessor;
	
	/** Monitor Event processor service */
	@Inject
    private MonitorEventProcessor monitorEventProcessor;
	
	/** Sync Event processor service */
	@Inject
    private SyncEventProcessor syncEventProcessor;
	
	/** PlSql Event processor service */
	@Inject
    private PlSqlEventProcessor plSqlEventProcessor;
	
	/** Archiver Event processor service */
	@Inject
    private ArchiverEventProcessor archiverEventProcessor;
	
	/** Expire Event processor service */
	@Inject
    private ExpireEventProcessor expireEventProcessor;
	
	/** Expire Event processor service */
	@Inject
    private Expire3gpEventProcessor expire3gpEventProcessor;
	
	/** Autorun Event processor service */
	@Inject
    private AutorunEventProcessor autorunEventProcessor;
	
	/** Alert Event processor service */
	@Inject
    private AlertEventProcessor alertEventProcessor;
	
	/** Ldap Event processor service */
	@Inject
    private LdapEventProcessor ldapEventProcessor;
	
	/** Capture Event processor service */
	@Inject
    private CaptureEventProcessor captureEventProcessor;
	
	/** Channelwatcher Event processor service */
	@Inject
    private ChannelwatcherEventProcessor channelwatcherEventProcessor;
	
	/** Html Event processor service */
	@Inject
    private HtmlEventProcessor htmlEventProcessor;
	
	/** Info regarding the process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2ServerInstance instance;
	
	/** Task scheduler used to manage CronTrigger based scheduling */    
	@Inject
	private FifoThreadQueueScheduler jorelScheduler;
	
	/** 
	 * Contains a list of Jorel tasks for processing. E.g. if a single occurrence, or multiple occurrences, of RSS 
	 * is present, RSS processing is triggered. This is also true for other event types like monitor, schedule and capture.
	 * Conversion to upper case mitigates the inconsistent use of camel case in the EVENT_TYPES.EVENT_TYPE column.
	 */
	List<String> tasksUpperCase = new ArrayList<>();
    
	/**
	 * Handles the processing of online and offline events depending on the status of the network connection. If the network is OFFLINE, threads 
	 * running this method will check the validity of the java.sql.Connection object underlying the session, and if valid the status will be 
	 * returned to ONLINE. If the network connection remains in an OFFLINE state, events that can be run offline will be processed.
	 */
	@Override
	public void run() {
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
    	Session session = null;
    			
    	try {
	    	session = sessionFactory.get().openSession();
	    	
			logThreadStartup();
			
	    	if(instance.getConnectionStatus() == ConnectionStatus.ONLINE) {
	    		processOnlineEvents(session);
	    	} else if (instance.getConnectionStatus() == ConnectionStatus.OFFLINE) {
	    		if (!sessionFactory.isEmpty()) {
	    			if (isConnectionLive(session)) {
	    		        instance.setConnectionStatus(ConnectionStatus.ONLINE);        				
	        			decoratedTrace(INDENT1, "Connection to TNO database is back online.");
	        			instance.logDbOutageDuration();
	        			postProcessOfflineEvents(session);
	    			} else {
	    				decoratedTrace(INDENT1, "Connection to TNO database is still offline.");
	    				processOfflineEvents();
	    			}
	    		} else {
	    			// Unlikely to happen as c3p0 always returns a session factory
	    			decoratedTrace(INDENT1, "Connection to TNO database is still offline");
	    			processOfflineEvents();
	    		}
	    	}
	    	
	    	logThreadCompletion(jorelThread);
	    	session.close();
    	} catch (Exception e) {
    		decoratedError(INDENT0, "In Jorel2Runnable.", e);
    		
    		if (session != null) {
    			session.close();
    		}
    	}
	}
	
	/**
	 * Retrieves all eligible event records from the EVENTS table and dispatches them to their respective event processors. If a persistence exception
	 * is thrown during the execution of this method the connection status of this instance is set to OFFLINE. The <code>run()</code> method will 
	 * return the connection status to ONLINE.
	 * 
	 * @param session Current Hibernate persistence context.
	 */
	private void processOnlineEvents(Session session) {
		
        Map<EventType, String> eventMap = null;
        
		try {
			// This is the first query to run in Jorel2Runnable. It is used to determine the offline status of the system.
			// If a query is inserted in the code prior to this one, without the try-catch below, offline mode will not function.
			rootInstanceName = instance.getAppInstanceName();
	        List<EventsDao> results = EventsDao.getEventsForProcessing(instance.getAppInstanceName(), session);
	        
	        eventMap = getUniqueEventTypes(results);
	        
	        // Trigger processing of each event type in eventMap
	        for (Entry<EventType, String> eventEntry : eventMap.entrySet()) {
	        	EventType eventEnum = eventEntry.getKey();
	        	eventTypeName = eventEntry.getValue();
	        	
	        	switch (eventEnum) {
	        		case RSS -> rssEventProcessor.processEvents(this, session);
	        		case SYNDICATION -> syndicationEventProcessor.processEvents(this, session);
	        		case PAGEWATCHER -> pageWatcherEventProcessor.processEvents(this, session);
	        		case SHELLCOMMAND -> shellCommandEventProcessor.processEvents(this, session);
	        		case DURATION -> durationEventProcessor.processEvents(this, session);
	        		case CLEANBINARYROOT -> cleanBinaryRootEventProcessor.processEvents(this, session);
	        		case MONITOR -> monitorEventProcessor.processEvents(this, session);
	        		case SYNC -> syncEventProcessor.processEvents(this, session);
	        		case PLSQL -> plSqlEventProcessor.processEvents(this, session);
	        		case ARCHIVER -> archiverEventProcessor.processEvents(this, session);
	        		case EXPIRE -> expireEventProcessor.processEvents(this, session);
	        		case EXPIRE3GP -> expire3gpEventProcessor.processEvents(this, session);
	        		case AUTORUN -> autorunEventProcessor.processEvents(this, session);
	        		case ALERT -> alertEventProcessor.processEvents(this, session);
	        		case LDAP -> ldapEventProcessor.processEvents(this, session);
	        		case CAPTURE -> captureEventProcessor.processEvents(this, session);
	        		case HTML -> htmlEventProcessor.processEvents(this, session);
	        		case CHANNELWATCHER -> channelwatcherEventProcessor.processEvents(this, session);
			        default -> Optional.empty();
	        	};
	        }
		}
		catch (PersistenceException e) {
	    	logger.error("In main event processing loop. Going offline.", e);
	    	instance.setConnectionStatus(ConnectionStatus.OFFLINE);
	    	instance.addDatabaseInterruption();
	    }
	}
	
	/**
	 * Processes all events that support offline processing. At present this includes ShellCommand and Capture events.
	 */
	private void processOfflineEvents() {
		
		shellCommandEventProcessor.shellCommandEventOffline();
		captureEventProcessor.captureEventOffline();
	}
	
	/**
	 * Performs updates to the database to record information about tasks that were executed while Jorel2 was offline.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	private void postProcessOfflineEvents(Session session) {
		
		decoratedTrace(INDENT2, "Post-processing Offline events using shellCommandEventProcessor.");
		shellCommandEventProcessor.shellCommandEventUpdate(session);
		captureEventProcessor.captureCommandEventUpdate(session);
	}
	
	/**
	 * Take the list of events matching the named query <code>Events_FindEventsForProcessing</code> and remove duplicate entries
	 * putting the resulting list of unique event types to be processed in eventMap. An EventType <code>enum</code> is used as the
	 * key to eventMap, and the case dependent event type name is stored as the value. This case-dependent string is used in later
	 * processing.
	 * 
	 * Some events are dependent on the prior execution of other events. For example, the Sync event inserts a record in the
	 * ALERT_TRIGGER table that the Alert event requires in order to run. In the same way, the Alert event inserts a record in
	 * the AUTO_RUN table which triggers Autorun event processing. The order in which events execute in a thread is important in
	 * ensuring that dependent events will run in that same thread. If, for example, the Alert event ran before the Sync event it will 
	 * not benefit from the the Sync event's insertion of the ALERT_TRIGGER record within that same thread. The Alert event will not
	 * be processed until the next scheduled execution cycle (currently 30 seconds). For this reason entries in the Map returned by
	 * this method are sorted by the ordinal value of their enum keys. The order of execution is therefore determined by the order in
	 * which Event enums are defined in the EventType enumeration.
	 * 
	 * @param eventMap Map of Unique event types to be processed by this runnable.
	 * @param results The query results, potentially containing multiple entries for each event type.
	 */
	private Map<EventType, String> getUniqueEventTypes(List<EventsDao> results) {
        String taskUpperCase;
        Map<EventType, String> eventMap = new HashMap<>();
        
        // Populate eventMap
        for(EventsDao event : results) {
        	EventTypesDao thisEvent = event.getEventType();
        	String eventTypeName = thisEvent.getEventType();
			taskUpperCase = eventTypeName.toUpperCase().replace("/", "");
			eventMap.put(EventType.valueOf(taskUpperCase), eventTypeName);
        } 
        
        // Sort eventMap - ensures event execution occurs in the right order 
		Map<EventType, String> sorted = eventMap.entrySet().stream().sorted(comparingByKey())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		
		// Detect which even types have been selected. Used in reporting via Jorel2ServerInstance mbean.
		for(Entry eventType : sorted.entrySet()) {
			if (!eventTypesProcessed.containsKey(eventType.getValue())) {
				eventTypesProcessed.put((String) eventType.getValue(), "");
			}
		}
		
		return sorted;
	}
	
	/**
	 * Create an entry in the log file recording the start of a new thread's execution.
	 * 
	 * @return The start time of this thread.
	 */
	public void logThreadStartup() {
				
      	String name = Thread.currentThread().getName();
      	System.out.println("Starting thread:   " + name);
   	   			
		logger.trace("Starting thread:   " + name);
	}
	
	/**
	 * Create an entry in the log file recording the completion of a thread's execution. Calculate the duration of the thread's execution
	 * by comparing the current time with the startTime parameter.
	 * 
	 * @param startTime The time when this thread commenced execution.
	 */
	private void logThreadCompletion(Jorel2ThreadInstance jorelThread) {
	    long duration = jorelThread.getDurationSeconds();		
      	String name = Thread.currentThread().getName();

      	instance.addThreadDuration(Thread.currentThread().getName(), duration);
		logger.trace("Completing thread: " + name + ", task took " + duration + " seconds");
      	System.out.println("Completing thread: " + name);
	
		jorelScheduler.notifyThreadComplete(jorelThread);
	}
	
	/**
	 * Determines whether there is a live connection between Jorel2 and the TNO database. As the session object represents a relationship between 
	 * the running application and the Hibernate framework, it is always seen as connected. Session.isOpen() and Session.isConnected() will return
	 * true regardless of the connected status of the underlying java.sql.connection object. This method retrieves the connection itself using
	 * Session's doWork() method. Once retrieved, the connection is tested by running a select query. This statement will throw a HibernateException,
	 * if the connection is disconnected, indicating that this method should return <code>false</code>.
	 * 
	 * @param session The current Hibernate presistence context.
	 * @return true if the connection referenced by <code>session</code> is an active connection.
	 */
	private boolean isConnectionLive(Session session) {
		
		boolean result = false;
		final String query = "select * from dual";
		
		try {
			session.doWork(connection -> {
				PreparedStatement stmt = connection.prepareStatement(query);
				stmt.executeQuery(query);
			});
			result = true;
		}
		catch (HibernateException e) {
			result = false;
		}
		
		return result;
	}
	
	/**
	 * Instantiates the class variable <code>jorelThread</code> to the value of the single parameter <code>thread</code>. This gives event
	 * processors access to the Jorel2ThreadInstance associated with this runnable, so that the thread timeout period can be updated to match
	 * the one configured for the event currently being processed.
	 * 
	 * @param thread The thread meta-data object associated with this runnable.
	 */
	public void setJorel2ThreadInstance(Jorel2ThreadInstance thread) {
		         
		this.jorelThread = thread;
	}
	
	/**
	 * Gets the thread meta-data object associated with this runnable.
	 * 
	 * @return The thread meta-data
	 */
	public Jorel2ThreadInstance getJorel2ThreadInstance() {
		
		return jorelThread;
	}
	
	/**
	 * Provides a case-dependent event type name for use by event processors when matching elligible events in the EVENTS table. Keeping it in 
	 * the Jorel2Runnable class reduces the number of parameters that need to be included in the <code>processEvents()</code> method of the 
	 * <code>EventProcessor</code> interface.
	 * 
	 * @return The eventTypeName of the event currently being processed.
	 */
	public String getEventTypeName() {
		
		return eventTypeName;
	}
}
