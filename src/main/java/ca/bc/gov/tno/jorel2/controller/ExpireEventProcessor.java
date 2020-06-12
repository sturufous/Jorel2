package ca.bc.gov.tno.jorel2.controller;


import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
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
import ca.bc.gov.tno.jorel2.model.HnewsItemsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.model.SourceTypesDao;
import ca.bc.gov.tno.jorel2.model.SourcesDao;

/**
 * Imposes several different expiration policies on records in the NEWS_ITEMS and HNEWS_ITEMS tables. 
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
	        
    		decoratedTrace(INDENT1, "Completed Expire event processing");
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
				
				//deleteFullBroadcasts(retainDays, session);
				clearExpiringSourceTypes(retainDays, session);
			}
		}
	}
	
	/**
	 * Delete binary root files identified by the <code>fullpathname</code> of each news item returned by <code>getExpiredFullBroadcasts()</code>
	 * and then deletes the news item.
	 *  
	 * @param retainDays Do not process any news items newer than today's date minus retainDays.
	 * @param session The current Hibernate persistence context.
	 */
	private void deleteFullBroadcasts(BigDecimal retainDays, Session session) {

		List<NewsItemsDao> expiredItems = NewsItemsDao.getExpiredFullBroadcasts(retainDays, session);
		long deletedCounter = 0;
		long missingCounter = 0;
		
		decoratedTrace(INDENT2, "Deleting expired full broadcasts.");

		// delete each expired full broadcast
		for(NewsItemsDao item : expiredItems) {

			BigDecimal rsn = item.getRsn();

			boolean externalbinary = item.getExternalbinary();
			if (externalbinary) {

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
					decoratedTrace(INDENT2, "The file " + filename + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
		}
	}
	
	/**
	 * Loops through all expiring source types calling <code>deleteBinaryThenArchive()</code> for each source type. Media sources that qualify for 
	 * archiving using the Archiver event are skipped if the source type is <code>TV News</code>. 
	 * 
	 * @param retainDays Do not process any news items newer than today's date minus retainDays.
	 * @param session The current Hibernate persistence context.
	 */
	private void clearExpiringSourceTypes(BigDecimal retainDays, Session session) {
		
		String sType = "";
		String TVSources = "";
		List<SourceTypesDao> sourceTypes = SourceTypesDao.getExpiringSourceTypes(session);
		
		// Get a list of sources that do not expire and are archived by the archiver
		TVSources = getNonExpiringSourceTypes(session);
		decoratedTrace(INDENT2, "Expiring items - ignoring these sources " + TVSources);

		// loop through source types
		try {
			for(SourceTypesDao sourceType : sourceTypes) {
				//BigDecimal days = sourceType.getDays();
				BigDecimal days = BigDecimal.valueOf(0L); // For testing
				BigDecimal specialdays = sourceType.getSpecial();
				BigDecimal expireRule = null;
				sType = sourceType.getType();

				// loop through regular expire items
				expireRule = BigDecimal.valueOf(0L);
				List<NewsItemsDao> newsItems = NewsItemsDao.getExpiredItems(sType, TVSources, days, expireRule, session);
				//List<HnewsItemsDao> hNewsItems = HnewsItemsDao.getExpiredItems(sType, TVSources, days, 0, session);
				deleteBinaryThenArchive(newsItems, sType, "Regular", session);
				
				// Loop through special expire items
				expireRule = BigDecimal.valueOf(1L);
				newsItems = NewsItemsDao.getExpiredItems(sType, TVSources, specialdays, expireRule, session);
				deleteBinaryThenArchive(newsItems, sType, "Special", session);
			}
		} catch (Exception ex) {
			decoratedError(INDENT2, "Processing source types for expiry.", ex);
		}
	}
	
	/**
	 * Obtains a list of non-expiring source types by calling <code>SourcesDao.getNonExpiringSources()</code> and builds a comma delimited list
	 * of source types for filtering result sets returned by HQL queries. The queries in question are in <code>NewsItemsDao.getExpiredItems()</code>
	 * and <code>HnewsItemsDao.getExpiredItems()</code>.
	 * 
	 * @param session Current Hibnernate persistence context.
	 * @return A comma delimited list of quoted source names for use in HQL queries.
	 */
	private String getNonExpiringSourceTypes(Session session) {
		
		String TVSources = "";
		
		List<SourcesDao> sources = SourcesDao.getNonExpiringSources(session);
		try {
			for(SourcesDao source : sources) {
				String thisSource = source.getSource();
				if (thisSource != null) {
					if (TVSources.length() > 0) TVSources = TVSources + ",";
					TVSources = TVSources + "'" + thisSource + "'";
				}
			}
		} catch (Exception ex) {
			decoratedError(INDENT2, "Retrieving list of non-expiring sources (tvarchive = 1).", ex);
		}
		
		if (TVSources.length() <= 0) TVSources = "'junk'";
		
		return TVSources;
	}
	
	/**
	 * Iterates through all entries in <code>newsItems</code> deleting binaries identified by the item's <code>fullfilepath</code> column, sets 
	 * the item's <code>archived</code> column to <code>true</code> and blanks out the item's archivedTo column.
	 * 
	 * @param newsItems The list of NewsItemsDao objects to process.
	 * @param sourceType The sourceType associates with the list of news items (used for logging only).
	 * @param expirePolicy Contains the value Regular or Special (used for logging only).
	 * @param session The current Hibernate persistence context.
	 */
	private void deleteBinaryThenArchive(List<NewsItemsDao> newsItems, String sourceType, String expirePolicy, Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		
		for(NewsItemsDao item : newsItems) {
			BigDecimal delRSN = item.getRsn();

			String filename = item.getFullfilepath();

			if (filename == null) filename = "null";
			filename = config.getString("binaryRoot") + sep + filename;
			File delFile = new File(filename);
			if (delFile.exists()) {
				if (!delFile.delete()) {
					IOException e = new IOException("Unable to delete file: " + filename);
					decoratedError(INDENT2, "Deleting and archiving by source type.", e);
				} else {
					/*session.beginTransaction();
					item.setArchived(true);
					item.setArchivedTo(" ");
					session.persist(item);
					session.getTransaction().commit();*/
					deletedCounter++;
				}
			} else {
				decoratedTrace(INDENT2, "Expire " + expirePolicy + ": The file " + filename + " is not in BinaryRoot.");
				missingCounter++;
			}
		}
		
		decoratedTrace(INDENT2, "Expire " + expirePolicy + ": " + sourceType + " - " + deletedCounter + " deleted, " + missingCounter + " missing.");
	}
	
	/**
	 * Iterates through all entries in <code>hnewsItems</code> deleting binaries identified by the item's <code>fullfilepath</code> column, sets 
	 * the item's <code>archived</code> column to <code>true</code> and blanks out the item's archivedTo column.
	 * 
	 * @param newsItems The list of HnewsItemsDao objects to process.
	 * @param sourceType The sourceType associates with the list of news items (used for logging only).
	 * @param expirePolicy Contains the value Regular or Special (used for logging only).
	 * @param session The current Hibernate persistence context.
	 */
	private void deleteBinaryThenArchiveHistorical(List<HnewsItemsDao> hnewsItems, String sourceType, String expirePolicy, Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		
		for(HnewsItemsDao item : hnewsItems) {
			BigDecimal delRSN = item.getRsn();

			String filename = item.getFullfilepath();

			if (filename == null) filename = "null";
			filename = config.getString("binaryRoot") + sep + filename;
			File delFile = new File(filename);
			if (delFile.exists()) {
				if (!delFile.delete()) {
					IOException e = new IOException("Unable to delete file: " + filename);
					decoratedError(INDENT2, "Deleting and archiving by source type.", e);
				} else {
					/*session.beginTransaction();
					item.setArchived(true);
					item.setArchivedTo(" ");
					session.persist(item);
					session.getTransaction().commit();*/
					deletedCounter++;
				}
			} else {
				decoratedTrace(INDENT2, "Expire " + expirePolicy + ": The file " + filename + " is not in BinaryRoot.");
				missingCounter++;
			}
		}
		
		decoratedTrace(INDENT2, "Expire " + expirePolicy + ": " + sourceType + " - " + deletedCounter + " deleted, " + missingCounter + " missing.");
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