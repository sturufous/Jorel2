package ca.bc.gov.tno.jorel2.controller;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;

/**
 * Monitors a set of web sites for changes. The list of sites is stored in the PAGEWATCHERS table.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class ShellCommandEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/**
	 * Process all eligible ShellCommand events.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
		
    	try {
    		logger.trace(StringUtil.getLogMarker(INDENT1) + "Starting ShellCommand event processing" + StringUtil.getThreadNumber());
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing PageWatcher entries.", e);
    	}
    	
		logger.trace(StringUtil.getLogMarker(INDENT1) + "Completing ShellCommand event processing" + StringUtil.getThreadNumber());
    	return Optional.of("complete");
	}
}