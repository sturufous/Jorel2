package ca.bc.gov.tno.jorel2.controller;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;

import javax.inject.Inject;
import javax.persistence.StoredProcedureQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;

/**
 * Processes PL/SQL events which run a stored procedure based on the start_time and frequency of the event. The name of the stored procedure 
 * to execute is contained in the FILE_NAME field of the current EVENTS record. The stored procedure is only run if the hour and minute value 
 * of the START_TIME (HH:MM:SS) is less than the current time, and the frequency string (e.g. mtw--ss) matches the current day.
 * 
 * Records are added to SYNC_INDEX by the Monitor event and the REINDEX_CONTENT stored procedure. REINDEX_CONTENT is called in response
 * to a PL/SQL event with a FILE_NAME value of REINDEX_CONTENT. There are currently 105 events of this type which run at the designated
 * START_TIME. This means a record is added to SYNC_INDEX roughly four times per hour, ensuring that the index is updated on a regular
 * basis. The REINDEX_CONTENT process handles the re-indexing of news items added by RSS events. The Monitor task requests that the indexes 
 * be updated immediately after each newspaper import. The last step performed by DOSYNCINDEX is to delete all records in SYNC_INDEX.
 *
 * There is only one event record per stored procedure for the remaining 9 PL/SQL events. Apart from the ones described above, the following 
 * events are the only ones that are active on production:
 * 
 * <ol>
 * <li>DURATION</li>
 * <li>DAILY</li>
 * <li>CREATE_TEXT_INDEXES</li>
 * <li>commentary</li>
 * </ol>
 * 
 * @author Stuart Morse
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
	 * Executes a stored procedure if it hasn't already been run today and the start_time value of the event is less than the current time current time.
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
				int startHour=0;
				int startMinute=0;
				int p = startTimeStr.indexOf(":");
				if (p > 0) {
					StringTokenizer st1 = new StringTokenizer(startTimeStr, ":" );
					String hour=st1.nextToken();
					String minute=st1.nextToken();
					try { startHour=Integer.parseInt(hour); } catch (Exception err) { startHour=0; }
					try { startMinute=Integer.parseInt(minute); } catch (Exception err) { startMinute=0; }
				}
	
				if (startTimeStr.length() == 8 && startTimeStr.indexOf(":") > 0) {	
					if (startHour == now.getHour() && startMinute <= now.getMinute()) {
						DbUtil.updateLastFtpRun(currentDate, currentEvent, session);
						preProcessEvent(currentEvent, session);
						executeStoredProcedure(currentEvent.getFileName(), session);
						incrementStartTime(currentEvent, startTimeStr, session);
						decoratedTrace(INDENT2, "PL/SQL: Executed " + currentEvent.getFileName() + " for event " + currentEvent.getName(), session);
					}
				} else {
					IllegalArgumentException e = new IllegalArgumentException("Processing PL/SQL event.");
					decoratedError(INDENT0, "Error in format of StartTime for PL/SQL event.", e);
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
	 * @param session The current Hibernate persistence context.
	 * @return the result of the query.execute() call.
	 */
	
	private boolean executeStoredProcedure(String name, Session session) {
		
		boolean result = false;
		
		StoredProcedureQuery query = session.createStoredProcedureQuery(name);
		result = query.execute();
				
		return result;
	}
	
	/**
	 * Performs any pre-processing that is required prior to calling a stored procedure. The only active case at present is <code>commentary</code>,
	 * and the only task performed is to expire any news items, with an associated commentary, whose commentary_expire_time has been exceeded. 
	 * 
	 * @param currentEvent The PL/SQL event currently being processed.
	 * @param session The current Hibernate persistence context.
	 */
	private void preProcessEvent(EventsDao currentEvent, Session session) {
		
		if(currentEvent.getFileName().equalsIgnoreCase("commentary"))
		{
			long now = (new java.util.Date()).getTime();
			try {
				NewsItemsDao.updateCommentary(now, session);
			} catch(Exception err) {
				decoratedError(INDENT0, "plsqlEvent(commentary): " + err.toString()+"!", err);
			}
		}

	}
	
	/**
	 * Increments the start time for the current event if the startTimeIncrement value stored in the event's <code>source</code> field is
	 * present. This allows certain PL/SQL events (at present only <code>commentary</code>) to run repeatedly throughout the day at
	 * regular intervals.
	 * 
	 * @param currentEvent The PL/SQL event currently being processed.
	 * @param startTimeStr The event's start time in the format HH:MM:SS.
	 * @param session The current Hibernate persistence context.
	 */
	private void incrementStartTime(EventsDao currentEvent, String startTimeStr, Session session) {
		
		String startTimeIncrement = currentEvent.getSource();
		int startHour = Integer.parseInt(startTimeStr.substring(0, 2));
		int startMinute = Integer.parseInt(startTimeStr.substring(3, 5));

		// Increment start time
		if ((startHour > 0) && (startTimeIncrement.length() > 0))
		{
			int increment = 0;	// minutes
			try { 
				increment = Integer.parseInt(startTimeIncrement);
				if (increment > (24*60)) {
					skip(); // do nothing, 24 hours
				}
				else {
					startMinute = startMinute + increment;
					while(startMinute >= 60) {
						startHour = startHour + 1;
						if(startHour > 23) startHour = 0;
						startMinute = startMinute - 60;
					}
					if(startHour == 0) startHour = 3; // do nothing until 3am

					String startHoursMinutes = String.format("%02d:%02d", startHour, startMinute);

					startTimeStr =  startHoursMinutes + ":00";
					decoratedTrace(INDENT2, "PL/SQL Event - Commentary: Setting startTime to: " + startTimeStr);
					currentEvent.setStartTime(startTimeStr);
					currentEvent.setLastFtpRun("idle");
					session.beginTransaction();
					session.persist(currentEvent);
					session.getTransaction().commit();
				}
			} catch (Exception err) {
				decoratedError(INDENT0, "In IncrementStartTime().", err);
			}
		}
	}
}