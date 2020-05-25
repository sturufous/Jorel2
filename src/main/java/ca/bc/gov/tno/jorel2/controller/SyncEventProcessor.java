package ca.bc.gov.tno.jorel2.controller;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.Jorel2Root.EventType;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.SyncIndexDao;

/**
 * Manages the re-indexing of the NEWS_ITEMS table.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class SyncEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/**
	 * Process all eligible sync event records from the TNO_EVENTS table.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting sync event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		
	    			if (instance.isExclusiveEventActive(EventType.SYNC)) {
	    				decoratedTrace(INDENT1, "Sync event processing already active. Skipping."); 
	    			} else {
	    				instance.addExclusiveEvent(EventType.SYNC);
		        		List<SyncIndexDao> syncIndex = SyncIndexDao.getSyncIndexRecords(session);
		        		
		        		if (syncIndex.size() > 0) {
			        		// Check for any other Jorel2 that is doing a paper import right now
		        			List<Object[]> imports = EventsDao.getMonitorEventsRunningNow(session);
		        			
		        			if (imports.size() == 0) {
		        				reIndexNewsItems(session);
		        			} else {
		        				decoratedTrace(INDENT1, "Sync event: news item import in progress.");
		        			}
		        		}
		        		
						instance.removeExclusiveEvent(EventType.SYNC);
	        		}
	        	}
	        		
        		/*
        		// Sync away
        		setSyncFlag(true);
        		SyncThread st = new SyncThread(frame,this);
        		st.start();
        		activityCounter++;
        		return; */
	        }
	        
			decoratedTrace(INDENT1, "Completing sync event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing sync event.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	/**
	 * Re-index the NEWS_ITEMS table.
	 * 
	 * @param session The current Hibernate presistence context.
	 * @return true if the connection referenced by <code>session</code> is an active connection.
	 */
	
	private boolean reIndexNewsItems(Session session) {
		
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