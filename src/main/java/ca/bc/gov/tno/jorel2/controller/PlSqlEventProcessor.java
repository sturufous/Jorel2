package ca.bc.gov.tno.jorel2.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.persistence.StoredProcedureQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Processes PL/SQL events which run a stored procedure based on the start_time and frequency of the event. The name of the stored procedure 
 * to execute is contained in the FILE_NAME field of the current EVENTS record. The stored procedure is only run if the hour and minute value 
 * of the START_TIME (HH:MM:SS) matches the current time, and the frequency string (e.g. mtw--ss) matches the current day.
 * 
 * Records are added to SYNC_INDEX by the Monitor event and the REINDEX_CONTENT stored procedure. REINDEX_CONTENT is called in response
 * to a PL/SQL event with a FILE_NAME value of REINDEX_CONTENT. There are currently 105 events of this type which run at the designated
 * START_TIME. This means a record is added to SYNC_INDEX roughly four times per hour, ensuring that the index is updated on a regular
 * basis. The REINDEX_CONTENT process handles the re-indexing of news items added by RSS events. The Monitor task requests that the indexes 
 * be updated immediately after each newspaper import. The last step performed by DOSYNCINDEX is to delete all records in SYNC_INDEX.
 *
 * There is only one events record per stored procedure for the remaining 9 PL/SQL events. Apart from the ones described above, the following 
 * events are the only ones that appear to be active on production:
 * 
 * <ol>
 * <li>DURATION</li>
 * <li>DAILY</li>
 * <li>CREATE_TEXT_INDEXES</li>
 * <li>commentary (possibly active)</li>
 * </ol>
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class PlSqlEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/**
	 * Process all eligible PlSqlEventProcessor event records from the EVENTS table. 
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting PL/SQL event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        
	        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
        			setThreadTimeout(runnable, currentEvent, instance);
	        		
	        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
	        			plSqlEvent(currentEvent, session);
	        		}
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing PL/SQL entries.", e);
    	}
    	
		decoratedTrace(INDENT1, "Completed PL/SQL event processing");
    	
    	return Optional.of("complete");
	}
	
	/**
	 * Executes a stored procedure if it hasn't already been run today and the start_time value of the event matches the current time.
	 * 
	 * @param currentEvent The current EVENTS record for being processed
	 * @param session The current Hibernate persistence context
	 */
	
	private void plSqlEvent(EventsDao currentEvent, Session session) {
		String currentDate = DateUtil.getDateNow();
		
		String lastRun = currentEvent.getLastFtpRun();
		String startTimeStr = currentEvent.getStartTime();
		if (startTimeStr == null) startTimeStr="00:00:00";

		try {
			if (lastRun != null && !lastRun.equals(currentDate)) {    // Do not execute again today
				LocalDateTime now = LocalDateTime.now();
				
	
				if (startTimeStr.length() == 8 && startTimeStr.indexOf(":") > 0) {
					String startHoursMinutes = startTimeStr.substring(0, 5);
					String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());
	
					if (nowHoursMinutes.equals(startHoursMinutes)) {
						
			    		// Update the lastFtpRun to today's date to indicate it has run today.
			    		currentEvent.setLastFtpRun(currentDate);
			    		session.beginTransaction();
			    		session.persist(currentEvent);
			    		session.getTransaction().commit();
			    		
						executeStoredProcedure(currentEvent.getFileName(), session);
						decoratedTrace(INDENT2, "PL/SQL: Executed " + currentEvent.getFileName() + " for event " + currentEvent.getName());
					}
				} else {
					IllegalArgumentException e = new IllegalArgumentException("Processing cleanBinaryRoot event.");
					decoratedError(INDENT0, "Error in format of StartTime for CleanBinaryRoot event.", e);
				}
			}
		} catch (Exception e) {
			decoratedError(INDENT0, "Running PL/SQL event for stored procedure " + currentEvent.getFileName(), e);
		}
	}
	
	/**
	 * Execute the stored procedure with the name matching the contents of the <code>name</parameter> using a Hibernate
	 * <code>StoredProcedureQuery</code> object.
	 * 
	 * @param name The name of the stored procedure to run.
	 * @param session The current Hibernate presistence context.
	 * @return the result of the query.execute() call.
	 */
	
	private boolean executeStoredProcedure(String name, Session session) {
		
		boolean result = false;
		
		StoredProcedureQuery query = session.createStoredProcedureQuery(name);
		result = query.execute();
				
		return result;
	}
}