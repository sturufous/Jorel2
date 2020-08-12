package ca.bc.gov.tno.jorel2.controller;


import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class DirectoryEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/**
	 * Process all eligible RSS event records from the TNO_EVENTS table.  The goal is that separate threads can process different RSS events. An earlier 
	 * version of this code added the synchronized modifier
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting RSS event processing");
    		
	        //List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEvenTypeName(), session);
    	} 
    	catch (Exception e) {
    		logger.error("Processing user directory entries.", e);
    	}
    	
    	return Optional.of("complete");
	}
}