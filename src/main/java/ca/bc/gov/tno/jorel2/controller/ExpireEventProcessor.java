package ca.bc.gov.tno.jorel2.controller;


import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class ExpireEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration config;
	
	private String sep = System.getProperty("file.separator");
	
	/**
	 * This event expires both full broadcasts and news items of expiring source types.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
   
    	try {
    		decoratedTrace(INDENT1, "Starting Expire event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        
	        		expireEvent(currentEvent, session);
	        		//updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing Expire event.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	private void expireEvent(EventsDao currentEvent, Session session) {
		
		String startTimeStr = currentEvent.getStartTime() == null ? "00:00:00" : currentEvent.getStartTime();
		LocalDateTime now = LocalDateTime.now();
		String startHoursMinutes = startTimeStr.substring(0, 5);
		String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());
		
		if ((nowHoursMinutes.compareTo(startHoursMinutes) >= 0)) {
			List<PreferencesDao> preferences = PreferencesDao.getPreferencesByRsn(PREFERENCES_RSN, session);
			
			if (preferences.size() == 1 && preferences.get(0) instanceof PreferencesDao) {
				PreferencesDao prefs = preferences.get(0);
				BigDecimal retainDays = prefs.getRetainFullBroadcast();
				
				deleteFullBroadcast(retainDays, session);
			}
		}
	}
	
	private void deleteFullBroadcast(BigDecimal retainDays, Session session) {

		List<NewsItemsDao> expiredItems = NewsItemsDao.getExpiredFullBroadcasts(retainDays, session);
		long deletedCounter = 0;
		long missingCounter = 0;

		// delete each expired full broadcast
		for(NewsItemsDao item : expiredItems) {

			BigDecimal rsn = item.getRsn();

			// Clear the blob or delete the external binary
			boolean externalbinary = item.getExternalbinary();
			if (externalbinary) { // if it is, delete it

				String filename = item.getFullfilepath();

				if (filename == null) filename = "null";
				filename = config.getString("binaryRoot") + sep + filename;
				File delFile = new File(filename);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + filename);
						decoratedError(INDENT2, "Deleting full broadcast items.", e);
					} else {
						session.beginTransaction();
						item.deleteRecordByRsn(rsn, session);
						session.getTransaction().commit();
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "The file " + filename + " is missing from BinaryRoot.");
					missingCounter++;
				}
			}
		}
	}
	
	/**
	 * Updates lastFtpRun to the value provided.
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