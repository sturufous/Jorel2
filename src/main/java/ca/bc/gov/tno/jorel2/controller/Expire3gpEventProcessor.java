package ca.bc.gov.tno.jorel2.controller;


import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 *  Performs a cleanup of the 3gp and mp3 files in the binaryroot directory. Retrieves the <code>daysago</code> value (age of the files at which cleanup
 *  should start) from the event's <code>source</code> field, and the fudge value (number of days of files to be cleaned) from the <code>fileName</code>
 *  field. Loops through binaryroot directories of the form <code>yyyy/mm/dd</code> removing elligible files from directories for dates between today's
 *  date minues daysago and today's date minus (daysago + fudge).
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class Expire3gpEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration config;
	
	private String sep = System.getProperty("file.separator");
	
	/**
	 * Loop through all expire3gp events assigned to this instance of Jorel2. After processing, the last_ftp_run column is set to the current date
	 * to ensure this event only runs once per day.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
   
    	try {
    		decoratedTrace(INDENT1, "Starting Expire3gp event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
        			setThreadTimeout(runnable, currentEvent, instance);
	        
	        		expire3gpEvent(currentEvent, session);
	        		updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
	        	}
	        }
	        
    		decoratedTrace(INDENT1, "Completed Expire3gp event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing Expire event.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	/**
	 * If the start time of this event has passed then perform cleanup of the directories identified by the daysago and fudge values. 
	 * 
	 * @param currentEvent The event against which the six methods above should be called.
	 * @param session The current Hibernate persistence context.
	 */
	private void expire3gpEvent(EventsDao currentEvent, Session session) {
		
		String startTimeStr = currentEvent.getStartTime() == null ? "00:00:00" : currentEvent.getStartTime();
		LocalDateTime now = LocalDateTime.now();
		LocalDate nowDate = LocalDate.now();
		String startHoursMinutes = startTimeStr.substring(0, 5);
		String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());
		
		if ((nowHoursMinutes.compareTo(startHoursMinutes) >= 0)) {
			int daysago = 0;
			int fudge = 0;
			int deletedCounter = 0;

			try { daysago=Integer.parseInt(currentEvent.getSource()); } catch (Exception err) { daysago = 0; }
			try { fudge=Integer.parseInt(currentEvent.getFileName()); } catch (Exception err) { fudge = 0; }

			if (daysago > 0) {

				int ii;
				LocalDate targetDate = nowDate.minusDays(daysago);

				for (ii = 0; ii <= fudge; ii++) { // for each of the fudge days

					int itemYear = targetDate.getYear();
					int itemMonth = targetDate.getMonth().getValue();
					int itemDay = targetDate.getDayOfMonth();
					
					// Check directory for 3gp/mp3 files
					String dirTargetName = String.format("%04d%s%02d%s%02d", itemYear, sep, itemMonth, sep, itemDay);
					File dirTarget = new File(config.getString("binaryRoot") + sep + dirTargetName);
					
					if (dirTarget.isDirectory()) {

						File delFile = null;
						String extension = "";

						String fileList[] = dirTarget.list();
						for (int i = 0; i < fileList.length; i++) {

							delFile = new File(config.getString("binaryRoot") + sep + dirTargetName + sep + fileList[i]);
							int p = delFile.getName().indexOf(".");
							if (p != -1) {
								extension = delFile.getName().substring(p+1);

								// If this is a 3gp/mp3 file - delete it
								if (extension.equalsIgnoreCase("3gp") || extension.equalsIgnoreCase("mp3")) {
									if (!delFile.delete()) {
										IOException e = new IOException("Unable to delete file: " + delFile.getAbsolutePath());
										decoratedError(INDENT0, "Expiring 3gp file.", e);
									} else {
										deletedCounter++;
									}
								}
							}
						}
					}

					targetDate = targetDate.minusDays(1L);  // subtract another day

				} // for each fudge day
			}

			decoratedTrace(INDENT2, "Expire3gp: " + deletedCounter + " deleted.");
		}
	}

	/**
	 * Updates lastFtpRun of the current event to the value provided.
	 * 
	 * @param value The value to store in lastFtpRun field of currentEvent.
	 * @param currentEvent The monitor event currently being processed.
	 * @param session The current Hibernate persistence context.
	 */
	
	private void updateLastFtpRun(String value, EventsDao currentEvent, Session session) {
	
		//Update this record to reflect that it has run and can now be run again
		currentEvent.setLastFtpRun(value);
		session.beginTransaction();
		session.persist(currentEvent);
		session.getTransaction().commit();
	}
}