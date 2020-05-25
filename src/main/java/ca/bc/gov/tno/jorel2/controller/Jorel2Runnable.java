package ca.bc.gov.tno.jorel2.controller;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.inject.Inject;
import javax.persistence.PersistenceException;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.core.env.Environment;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public final class Jorel2Runnable extends Jorel2Root implements Runnable {
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Inject
	private DataSourceConfig config;
	
	/** Environment variable used for retrieving active profiles */
	@Inject
    private Environment environment;
		
	/** RSS Event processor service */
	@Inject
    private RssEventProcessor rssEventProcessor;
	
	/** RSS Event processor service */
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
	
	/** Monitor Event processor service */
	@Inject
    private SyncEventProcessor syncEventProcessor;
	
	/** Info regarding the process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2Instance instance;
	
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
    	Session session = sessionFactory.get().openSession();
    	LocalDateTime startTime = null;
    	//instance.loadPreferences(session);
				
		startTime = logThreadStartup();
		
    	if(instance.getConnectionStatus() == ConnectionStatus.ONLINE) {
    		processOnlineEvents(session);
    	} 
    	else if (instance.getConnectionStatus() == ConnectionStatus.OFFLINE) {
    		if (!sessionFactory.isEmpty()) {
    			if (isConnectionLive(session)) {
    		        instance.setConnectionStatus(ConnectionStatus.ONLINE);        				
        			decoratedTrace(INDENT1, "Connection to TNO database is back online.");
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
    	
    	logThreadCompletion(startTime);
	}
	
	/**
	 * Retrieves all eligible event records from the EVENTS table and dispatches them to their respective event processors. If a persistence exception
	 * is thrown during the execution of this method the connection status of this instance is set to OFFLINE. The <code>run()</code> method will 
	 * return the connection status to ONLINE.
	 * 
	 * @param session Current Hibernate persistence context.
	 */
	@SuppressWarnings("preview")
	private void processOnlineEvents(Session session) {
		
        Map<EventType, String> eventMap = new HashMap<>();
		Optional<String> eventResult;
        
		try {
			// This is the first query to run in Jorel2Runnable. It is used to determine the offline status of the system.
			// If a query is inserted in the code prior to this one, without the try-catch below, offline mode will not function.
	        List<EventsDao> results = EventsDao.getEventsForProcessing(instance.getInstanceName(), session);
	        
	        getUniqueEventTypes(eventMap, results);
	        
	        // Trigger processing of each event type in eventMap
	        for (Entry<EventType, String> eventEntry : eventMap.entrySet()) {
	        	EventType eventEnum = eventEntry.getKey();
	        	String eventTypeName = eventEntry.getValue();
	        	
	        	eventResult = switch (eventEnum) {
	        		case NEWRSS -> rssEventProcessor.processEvents(eventTypeName, session);
	        		case RSS -> rssEventProcessor.processEvents(eventTypeName, session);
	        		case SYNDICATION -> syndicationEventProcessor.processEvents(eventTypeName, session);
	        		case PAGEWATCHER -> pageWatcherEventProcessor.processEvents(eventTypeName, session);
	        		case SHELLCOMMAND -> shellCommandEventProcessor.processEvents(eventTypeName, session);
	        		case DURATION -> durationEventProcessor.processEvents(eventTypeName, session);
	        		case CLEANBINARYROOT -> cleanBinaryRootEventProcessor.processEvents(eventTypeName, session);
	        		case MONITOR -> monitorEventProcessor.processEvents(eventTypeName, session);
	        		case SYNC -> syncEventProcessor.processEvents(eventTypeName, session);
			        default -> Optional.empty();
	        	};
	        }
		}
		catch (PersistenceException e) {
	    	logger.error("In main event processing loop. Going offline.", e);
	    	instance.setConnectionStatus(ConnectionStatus.OFFLINE);
	    	instance.addDatabaseInterruption(Thread.currentThread().getName());
	    }
        
        session.close();
	}
	
	/**
	 * Processes all events that support offline processing. At present this includes ShellCommand and Capture events.
	 */
	private void processOfflineEvents() {
		
		shellCommandEventProcessor.shellCommandEventOffline();
	}
	
	/**
	 * Performs updates to the database to record information about tasks that were executed while Jorel2 was offline.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	private void postProcessOfflineEvents(Session session) {
		
		shellCommandEventProcessor.shellCommandEventUpdate(session);
	}
	
	/**
	 * Take the list of events matching the named query <code>Events_FindEventsForProcessing</code> and remove duplicate entries
	 * putting the resulting list of unique event types to be processed in eventMap. An EventType <code>enum</code> is used as the
	 * key to eventMap, and the case dependent event type name is stored as the value. This case-dependent string is used in later
	 * processing.
	 * 
	 * @param eventMap Map of Unique event types to be processed by this runnable.
	 * @param results The query results, potentially containing multiple entries for each event type.
	 */
	private void getUniqueEventTypes(Map<EventType, String> eventMap, List<EventsDao> results) {
        String taskUpperCase;
        
        for(EventsDao event : results) {
        	EventTypesDao thisEvent = event.getEventType();
        	String eventTypeName = thisEvent.getEventType();
			taskUpperCase = eventTypeName.toUpperCase().replace("/", "");
			eventMap.put(EventType.valueOf(taskUpperCase), eventTypeName);
        } 
	}
	
	/**
	 * Create an entry in the log file recording the start of a new thread's execution. Return the start time of the thread for storage
	 * in the <code>threadStartTimestamps</code> hash.
	 * 
	 * @return The start time of this thread.
	 */
	public LocalDateTime logThreadStartup() {
		// Get the start time
		LocalDateTime start = LocalDateTime.now();
		
      	String name = Thread.currentThread().getName();
      	System.out.println("Starting thread:   " + name);
   	   			
		logger.trace("Starting thread:   " + name);
		
		return start;
	}
	
	/**
	 * Create an entry in the log file recording the completion of a thread's execution. Calculate the duration of the thread's execution
	 * by comparing the current time with the startTime parameter.
	 * 
	 * @param startTime The time when this thread commenced execution.
	 */
	private void logThreadCompletion(LocalDateTime startTime) {
    	LocalDateTime stop = LocalDateTime.now();
	    long diff = ChronoUnit.SECONDS.between(startTime, stop);		
      	String name = Thread.currentThread().getName();

      	instance.addThreadDuration(Thread.currentThread().getName(), diff);
		logger.trace("Completing thread: " + name + ", task took " + diff + " seconds");
      	System.out.println("Completing thread: " + name);
	
		jorelScheduler.notifyThreadComplete(Thread.currentThread());
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
				ResultSet rs = stmt.executeQuery(query);
			});
			result = true;
		}
		catch (HibernateException e) {
			result = false;
		}
		
		return result;
	}
}
