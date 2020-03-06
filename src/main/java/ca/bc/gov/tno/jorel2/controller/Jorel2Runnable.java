package ca.bc.gov.tno.jorel2.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.SharedSessionContract;
import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.jdbc.Work;
import org.springframework.core.env.Environment;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.Jorel2Root.EventType;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;
//import ca.bc.gov.tno.jorel2.controller.RssEventProcessor;

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
	
	/** PageWatcher Event processor service */
	@Inject
    private ShellCommandEventProcessor shellCommandEventProcessor;
	
	/** Process we're running as (e.g. "jorel", "jorelMini3") */
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
	 * Perform some initial setup tasks and then enter a loop that repeatedly gets events to process and calls their
	 * respective handlers.
	 */
	@SuppressWarnings("preview")
	@Override
	public void run() {
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
    	LocalDateTime startTime = null;
				
		startTime = logThreadStartup();
		
    	if(instance.getConnectionStatus() == ConnectionStatus.OFFLINE) {
    		if (!sessionFactory.isEmpty()) {
    			Session session = sessionFactory.get().openSession();
    			if (isConnectionLive(session)) {
    		        instance.setConnectionStatus(ConnectionStatus.ONLINE);        				
        			logger.trace("Connection to TNO database is back online.");
    			} else {
    				System.out.println("Connection to TNO database is still offline");
    			}
    		} else {
    			logger.trace("Connection to TNO database is still offline");
    		}
    	} else {
    		processOnlineEvents(sessionFactory.get());
    	}
    	
    	logThreadCompletion(startTime);
	}
	
	@SuppressWarnings("preview")
	private void processOnlineEvents(SessionFactory sessionFactory) {
		
        Map<EventType, String> eventMap = new HashMap<>();
        Session session = sessionFactory.openSession();
		Optional<String> eventResult;
        
		try {
	        // Retrieve the events for processing 
	    	session.beginTransaction();
	        List<EventsDao> results = EventsDao.getEventsForProcessing(instance.getInstanceName(), session);
	        session.getTransaction().commit();
	        
	        getUniqueEventTypes(eventMap, results);
	        
	        // Trigger processing of each event type in eventMap
	        for (Entry<EventType, String> eventEntry : eventMap.entrySet()) {
	        	EventType eventEnum = eventEntry.getKey();
	        	String eventTypeName = eventEntry.getValue();
	        	
	        	eventResult = switch (eventEnum) {
	        		case NEWRSS -> rssEventProcessor.processEvents(eventTypeName, session);
	        		case SYNDICATION -> syndicationEventProcessor.processEvents(eventTypeName, session);
	        		case PAGEWATCHER -> pageWatcherEventProcessor.processEvents(eventTypeName, session);
	        		case SHELLCOMMAND -> shellCommandEventProcessor.processEvents(eventTypeName, session);
			        default -> Optional.empty();
	        	};
	        }
		}
		catch (HibernateException e)
	    	logger.trace("In main event processing loop. Going offline.", e);
	    	instance.setConnectionStatus(ConnectionStatus.OFFLINE);
	    }
        
        session.close();
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
	
	public LocalDateTime logThreadStartup() {
		// Get the start time
		LocalDateTime start = LocalDateTime.now();
		
      	String name = Thread.currentThread().getName();
      	System.out.println("Starting thread:   " + name);
   	   			
		logger.trace(StringUtil.getLogMarker(INDENT0) + "Starting thread:   " + name);
		
		return start;
	}
	
	private void logThreadCompletion(LocalDateTime startTime) {
    	LocalDateTime stop = LocalDateTime.now();
	    long diff = ChronoUnit.SECONDS.between(startTime, stop);		
      	String name = Thread.currentThread().getName();

      	instance.addThreadDuration(Thread.currentThread().getName(), diff);
		logger.trace(StringUtil.getLogMarker(INDENT0) + "Completing thread: " + name + ", task took " + diff + " seconds");
      	System.out.println("Completing thread: " + name);
	
		jorelScheduler.notifyThreadComplete(Thread.currentThread());
	}
	
	/**
	 * Determines whether there is a live connection between Jorel2 and the TNO database. As the session object represents a relationship between 
	 * the running application and the Hibernate framework, it is always seen as connected. Session.isOpen() and Session.isConnected() will return
	 * true regardless of the connected status of the underlying java.sql.connection object. This method retrieves the connection itself using
	 * Session's doWork().execute() method. Once retrieved, the connection is tested using connection.isValid(3000). This statement will timeout 
	 * after 3 seconds if the connection is inactive and the timeout exception is caught, indicating that this method should return <code>false</code>.
	 * 
	 * @param session The current Hibernate presistence context.
	 * @return true if the connection referenced by <code>session</code> is an active connection.
	 */
	private boolean isConnectionLive(Session session) {
		
		boolean result = false;
		
		try {
			session.doWork((Work) new Work() {
	
				public void execute(Connection connection) throws SQLException {
					
					connection.isValid(CONNECTION_TIMEOUT);
				}
			});
			
			result = true;
		}
		catch (HibernateException e) {
			result = false;
		}
		
		return result;
	}
}
