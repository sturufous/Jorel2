package ca.bc.gov.tno.jorel2.controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.controller.RssEventProcessor;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Scope("prototype")
final class Jorel2Thread extends Jorel2Root implements Runnable {
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Inject
	private DataSourceConfig config;
	
	/** Environment variable used for retrieving active profiles */
	@Inject
    private Environment environment;
		
	/** RSS Event processor service */
	@Inject
    private RssEventProcessor rssEventProcessor;
	
	/** 
	 * Contains a list of Jorel tasks for processing. E.g. if a single occurrence, or multiple occurrences, of RSS 
	 * is present, RSS processing is triggered. This is true for other event types like monitor, schedule and capture.
	 * Conversion to lower case mitigates the inconsistent use of camel case in the EVENT_TYPES.EVENT_TYPE column.
	 */
	List<String> tasksUpperCase = new ArrayList<>();
    
	/**
	 * Perform some initial setup tasks and then enter a loop that repeatedly gets events to process and calls their
	 * respective handlers.
	 */
	public void run() {
    	   			
    	Optional<String> rssResult;

    	while(true) {
	    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
	        
	    	if(sessionFactory.isEmpty()) {
	    		IllegalStateException e = new IllegalStateException("No session factory provided.");
	    		logger.error("Getting TNO session factory.", e);
	    		throw e;
	    	} else {
		        Session session = sessionFactory.get().openSession();
		        String taskUpperCase;
		        Map<String, EventType> eventMap = new HashMap<>();
		    	
		    	session.beginTransaction();
		        List<EventsDao> results = EventsDao.getEventsForProcessing(session);
		        
		        // Create Map containing list of unique event-types for processing
		        for(EventsDao event : results) {
		        	EventTypesDao thisEvent = event.getEventType();
					taskUpperCase = thisEvent.getEventType().toUpperCase().replace("/", "");
					eventMap.put(taskUpperCase, EventType.valueOf(taskUpperCase));
		        }
		              
		        // Trigger processing of each event type in eventMap
		        for (EventType eventEnum : eventMap.values()) {
		        	switch (eventEnum) {
		        		case NEWRSS: 
				        	rssResult = rssEventProcessor.processEvents();
				        	System.out.println("RSS Title: " + rssResult.get().toString());
				        	break;
				        default:
				        	break;
		        	}
		        }
		        
		        session.getTransaction().commit();
		        session.close();
	    	}
	        
	        try {
	        	Thread.sleep(10000);
	        } 
	        catch (InterruptedException e) {
	        	e.printStackTrace();
	        }
	    }
    }
}
