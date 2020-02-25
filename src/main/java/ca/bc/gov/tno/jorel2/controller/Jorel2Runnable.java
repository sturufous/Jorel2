package ca.bc.gov.tno.jorel2.controller;

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
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.core.env.Environment;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.Jorel2Root.EventType;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;
import ca.bc.gov.tno.jorel2.controller.RssEventProcessor;

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
		Optional<String> rssResult;
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
    	LocalDateTime startTime = null;
				
		startTime = logThreadStartup();
		
    	if(sessionFactory.isEmpty()) {
    		logger.error("Getting TNO session factory.", new IllegalStateException("No session factory provided."));
    	} else {
	        Session session = sessionFactory.get().openSession();
	        Map<EventType, String> eventMap = new HashMap<>();
	    	
	        // Retrieve the events for processing 
	    	session.beginTransaction();
	        List<EventsDao> results = EventsDao.getEventsForProcessing(instance.getInstanceName(), session);
	        session.getTransaction().commit();
	        
	        getUniqueEventTypes(eventMap, results);
	        
	        // Trigger processing of each event type in eventMap
	        for (Entry<EventType, String> eventEntry : eventMap.entrySet()) {
	        	EventType eventEnum = eventEntry.getKey();
	        	String eventTypeName = eventEntry.getValue();
	        	
	        	rssResult = switch (eventEnum) {
	        		case NEWRSS -> rssEventProcessor.processEvents(eventTypeName, session);
	        		case SYNDICATION -> syndicationEventProcessor.processEvents(eventTypeName, session);
	        		case PAGEWATCHER -> pageWatcherEventProcessor.processEvents(eventTypeName, session);
			        default -> Optional.empty();
	        	};
	        }
	        
	        session.close();
    	}
    	
    	logThreadCompletion(startTime);
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
}
