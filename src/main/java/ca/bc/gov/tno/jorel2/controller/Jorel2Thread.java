package ca.bc.gov.tno.jorel2.controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import ca.bc.gov.tno.jorel2.Jorel2Process;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.controller.RssEventProcessor;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public final class Jorel2Thread extends Jorel2Root implements Runnable {
	
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
	
	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2Process process;
	
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
	public void run() {
		
		System.out.println("Running Jorel2Thread");
    	   			
    	Optional<String> rssResult;
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
    	
    	if(sessionFactory.isEmpty()) {
    		logger.error("Getting TNO session factory.", new IllegalStateException("No session factory provided."));
    	} else {
	        Session session = sessionFactory.get().openSession();
	        String taskUpperCase;
	        Map<EventType, String> eventMap = new HashMap<>();
	    	
	        // Retrieve the events for processing 
	    	session.beginTransaction();
	        List<EventsDao> results = EventsDao.getEventsForProcessing(session);
	        session.getTransaction().commit();
	        
	        
	        // Create Set containing list of unique event-types for processing
	        for(EventsDao event : results) {
	        	EventTypesDao thisEvent = event.getEventType();
	        	String eventTypeName = thisEvent.getEventType();
				taskUpperCase = eventTypeName.toUpperCase().replace("/", "");
				eventMap.put(EventType.valueOf(taskUpperCase), eventTypeName);
	        } 
	              
	        // Trigger processing of each event type in eventMap
	        for (Entry<EventType, String> eventEntry : eventMap.entrySet()) {
	        	EventType eventEnum = eventEntry.getKey();
	        	String eventTypeName = eventEntry.getValue();
	        	
	        	rssResult = switch (eventEnum) {
	        		case NEWRSS -> rssEventProcessor.processEvents(eventTypeName, session);
	        		case SYNDICATION -> syndicationEventProcessor.processEvents(eventTypeName, session);
			        default -> Optional.empty();
	        	};
	        }
	        
	        session.close();
    	} 
    }
}
