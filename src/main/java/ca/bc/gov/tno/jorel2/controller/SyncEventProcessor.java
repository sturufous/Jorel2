package ca.bc.gov.tno.jorel2.controller;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.persistence.StoredProcedureQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.SyncIndexDao;

/**
 * Manages the re-indexing of the NEWS_ITEMS table. Re-indexing occurs when an event adds content to the NEWS_ITEMS table and then
 * requests that this sync event processor call the DOSYNCINDEX stored procedure. This is achieved by adding a record to the SYNC_INDEX 
 * table. Simply put, this processor ensures that no other event handler is currently importing data into NEWS_ITEMS, if no import is in
 * progress, it checks 
 * SYNC_INDEX for records. If records exist it calls DOSYNCINDEX.
 * 
 * Records are added to SYNC_INDEX by the Monitor event and the REINDEX_CONTENT stored procedure. REINDEX_CONTENT is called in response
 * to a PL/SQL event with a FILE_NAME value of REINDEX_CONTENT. There are currently 105 events of this type which run at the designated
 * START_TIME. This means a record is added to SYNC_INDEX roughly four times per hour, ensuring that the index is updated on a regular
 * basis. The REINDEX_CONTENT process handles the re-indexing of news items added by RSS events. The Monitor task requests that the indexes 
 * be updated immediately after each newspaper import. The last step performed by DOSYNCINDEX is to delete all records in SYNC_INDEX.
 * 
 * The Sync event (if present in the EVENTS table and associated with the current Jorel2 instance) will be run every time Jorel2 runs.
 * This ensures that if a request has been made to re-index NEWS_ITEMS it is initiated within thirty seconds.
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
    		decoratedTrace(INDENT1, "Starting Sync event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {	        		
	    			if (instance.isExclusiveEventActive(EventType.SYNC)) {
	    				decoratedTrace(INDENT1, "Sync event processing already active. Skipping."); 
	    			} else {
	    				instance.addExclusiveEvent(EventType.SYNC);
		        		List<SyncIndexDao> syncIndex = SyncIndexDao.getSyncIndexRecords(session);
		        		
		        		if (syncIndex.size() > 0) {
			        		// Check for any other Jorel2 that is doing a paper import right now.
		        			List<Object[]> imports = EventsDao.getMonitorEventsRunningNow(session);
		        			
		        			if (imports.size() == 0) {
		        				reIndexNewsItems(session);
		        			} else {
		        				decoratedTrace(INDENT2, "Sync: news item import in progress.");
		        			}
		        		}
		        		
						instance.removeExclusiveEvent(EventType.SYNC);
	        		}
	        	}
	        }
	        
			decoratedTrace(INDENT1, "Completing Sync event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing sync event.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	/**
	 * Re-index the NEWS_ITEMS table using a Hibernate StoredProcedureQuery for the procedure DOSYNCINDEX.
	 * 
	 * @param session The current Hibernate presistence context.
	 * @return the result of the query.execute() call.
	 */
	
	private boolean reIndexNewsItems(Session session) {
		
		boolean result = false;
		
		StoredProcedureQuery query = session.createStoredProcedureQuery("DOSYNCINDEX");
		result = query.execute();
		decoratedTrace(INDENT2, "Sync: Executed stored procedure DOSYNCINDEX");
				
		return result;
	}
}