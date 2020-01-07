package ca.bc.gov.tno.jorel2.controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
		
	List        tasksLowerCase = new ArrayList<String>();
    
	public void run() {
    	   	
    	while(true) {
	    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
	        
	    	if(sessionFactory.isEmpty()) {
	    		IllegalStateException e = new IllegalStateException("No session factory provided.");
	    		logger.error("Occurrend getting TNO session factory.", e);
	    		throw e;
	    	} else {
		        Session session = sessionFactory.get().openSession();
		    	
		    	session.beginTransaction();
		        List<EventsDao> results = EventsDao.getEventsForProcessing(session);
		        
		        for(EventsDao event : results) {
		        	EventTypesDao thisEvent = event.getEventType();
					tasksLowerCase.add(thisEvent.getEventType().toLowerCase());
		        }
		        
		        session.getTransaction().commit();
		        session.close();
	    	}
	        
	        try {
	        	Thread.sleep(2000);
	        } 
	        catch (InterruptedException e) {
	        	e.printStackTrace();
	        }
	    }
    }
}
