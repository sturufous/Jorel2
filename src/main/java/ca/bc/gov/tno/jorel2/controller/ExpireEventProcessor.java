package ca.bc.gov.tno.jorel2.controller;


import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import ca.bc.gov.tno.jorel2.model.HnewsItemsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemImagesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.model.SourcePaperImagesDao;
import ca.bc.gov.tno.jorel2.model.SourceTypesDao;
import ca.bc.gov.tno.jorel2.model.SourcesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Imposes several different expiration policies on records in the NEWS_ITEMS, HNEWS_ITEMS tables and their supporing image tables. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class ExpireEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
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
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
   
    	try {
    		decoratedTrace(INDENT1, "Starting Expire event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
        			setThreadTimeout(runnable, currentEvent, instance);
	        
	        		expireEvent(currentEvent, session);
	        		updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
	        	}
	        }
	        
    		decoratedTrace(INDENT1, "Completed Expire event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing Expire event.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	/**
	 * Ensures that the start time for executing the Expire event has been passed, and maintains control over the expiration process
	 * by calling the following methods in order:
	 * 
	 * <ol>
	 * <li>deleteFullBroadcasts()</li>
	 * <li>clearExpiringSourceTypes()</li>
	 * <li>clearExpiringSources()</li>
	 * <li>clearExpiringImages()</li>
	 * <li>clearExpiringSourceImages()</li>
	 * <li>clearOrphanedImages()</li>
	 * </ol>
	 * 
	 * @param currentEvent The event against which the six methods above should be called.
	 * @param session The current Hibernate persistence context.
	 */
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
				BigDecimal retainImages = prefs.getRetainImages();
				
				deleteFullBroadcasts(retainDays, session);
				clearExpiringSourceTypes(session);
				clearExpiringSources(session);
				clearExpiringImages(retainImages, session);
				clearExpiringSourceImages(retainImages, session);
				clearOrphanedImages(session);
			}
		}
	}
	
	/**
	 * Deletes binary root files identified by the <code>fullpathname</code> of each news item returned by <code>getExpiredFullBroadcasts()</code>
	 * and then deletes the news item.
	 *  
	 * @param retainDays Do not process any news items newer than today's date minus retainDays.
	 * @param session The current Hibernate persistence context.
	 */
	private void deleteFullBroadcasts(BigDecimal retainDays, Session session) {

		long deletedCounter = 0;
		long missingCounter = 0;
		
		decoratedTrace(INDENT2, "Deleting expired full broadcasts.");

		// delete each expired full broadcast
		try {
			List<NewsItemsDao> expiredItems = NewsItemsDao.getExpiredFullBroadcasts(retainDays, session);
			session.beginTransaction();
			for(NewsItemsDao item : expiredItems) {
	
				boolean externalbinary = item.getExternalbinary();
				if (externalbinary) {
	
					String filename = item.getFullfilepath();
	
					if (filename == null) filename = "null";
					filename = config.getString("binaryRoot") + sep + filename;
					File delFile = new File(filename);
					if (delFile.exists()) {
						if (!delFile.delete()) {
							IOException e = new IOException("Unable to delete file: " + filename);
							decoratedError(INDENT0, "Deleting full broadcast items.", e);
						} else {
							NewsItemsDao.deleteRecord(item, session);
							deletedCounter++;
						}
					} else {
						decoratedTrace(INDENT2, "The file " + filename + " is not in BinaryRoot.");
						missingCounter++;
					}
				}
			}
			session.getTransaction().commit();
			
			if(expiredItems.size() > 0) {
				decoratedTrace(INDENT2, "Expire full broadcasts: " + deletedCounter + " deleted, " + missingCounter + " missing.");
			}
		} catch (Exception ex) {
			decoratedError(INDENT0, "Deleting full broadcasts.", ex);
			session.getTransaction().rollback();
		}
	}
	
	/**
	 * Loops through all expiring source types calling <code>deleteBinaryThenArchive()</code> for each source type. Media sources that qualify for 
	 * archiving using the Archiver event are skipped if the source type is <code>TV News</code>. This method does not delete NewsItemsDao or
	 * HnewsItemsDao objects, it merely sets their Archived columns to <code>true</code>.
	 * 
	 * @param retainDays Do not process any news items newer than today's date minus retainDays.
	 * @param session The current Hibernate persistence context.
	 */
	private void clearExpiringSourceTypes(Session session) {
		
		String sType = "";
		String TVSources = "";
		decoratedTrace(INDENT2, "Expiring items - ignoring these sources " + TVSources);

		// loop through source types
		try {
			List<SourceTypesDao> sourceTypes = SourceTypesDao.getExpiringSourceTypes(session);
			
			// Get a list of sources that do not expire and are archived by the archiver
			TVSources = getNonExpiringSourceTypes(session);

			for(SourceTypesDao sourceType : sourceTypes) {
				BigDecimal days = sourceType.getDays();
				BigDecimal specialdays = sourceType.getSpecial();
				BigDecimal expireRule = null;
				sType = sourceType.getType();

				// loop through regular expire items
				expireRule = BigDecimal.valueOf(0L);
				List<NewsItemsDao> newsItems = NewsItemsDao.getExpiredItems(sType, TVSources, days, expireRule, session);
				List<HnewsItemsDao> hNewsItems = HnewsItemsDao.getExpiredItems(sType, TVSources, days, expireRule, session);
				deleteBinaryThenArchive(newsItems, sType, "Regular", session);
				deleteBinaryThenArchiveHistorical(hNewsItems, sType, "Regular", session);
				
				// Loop through special expire items
				expireRule = BigDecimal.valueOf(1L);
				newsItems = NewsItemsDao.getExpiredItems(sType, TVSources, specialdays, expireRule, session);
				hNewsItems = HnewsItemsDao.getExpiredItems(sType, TVSources, specialdays, expireRule, session);
				deleteBinaryThenArchive(newsItems, sType, "Special", session);
				deleteBinaryThenArchiveHistorical(hNewsItems, sType, "Special", session);
			}
		} catch (Exception ex) {
			decoratedError(INDENT0, "Processing source types for expiry.", ex);
		}
	}
	
	/**
	 * Loops through all expiring sources calling <code>deleteBinaryAndNewsItem()</code> for each source.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	private void clearExpiringSources(Session session) {
		
		try {
			List<SourcesDao> expiringSources = SourcesDao.getExpiringSources(session);
			
			for (SourcesDao currentSource : expiringSources) {
				BigDecimal days = currentSource.getExpireDays();
				String source = currentSource.getSource();
	
				List<NewsItemsDao> newsItems = NewsItemsDao.getExpiredItems(source, days, session);
				List<HnewsItemsDao> hNewsItems = HnewsItemsDao.getExpiredItems(source, days, session);
				deleteBinaryAndNewsItem(newsItems, source, session);
				deleteBinaryAndNewsItemHistorical(hNewsItems, source, session);
			}
		} catch (Exception ex) {
			decoratedError(INDENT0, "Processing sources for expiry.", ex);
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
		
		try {
			List<SourcesDao> sources = SourcesDao.getNonExpiringSources(session);
			
			for(SourcesDao source : sources) {
				String thisSource = source.getSource();
				if (thisSource != null) {
					if (TVSources.length() > 0) TVSources = TVSources + ",";
					TVSources = TVSources + "'" + thisSource + "'";
				}
			}
		} catch (Exception ex) {
			decoratedError(INDENT0, "Retrieving list of non-expiring sources (tvarchive = 1).", ex);
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
		
		try {
			session.beginTransaction();
			for(NewsItemsDao item : newsItems) {
				String filename = item.getFullfilepath();
	
				if (filename == null) filename = "null";
				filename = config.getString("binaryRoot") + sep + filename;
				File delFile = new File(filename);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + filename);
						decoratedError(INDENT0, "Deleting and archiving by source type.", e);
					} else {
						item.setArchived(true);
						item.setArchivedTo(" ");
						session.persist(item);
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "Expire " + expirePolicy + ": The file " + filename + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Deleting and archiving news items for expired source types.", ex);
			session.getTransaction().rollback();
		}
		
		if(newsItems.size() > 0) {
			decoratedTrace(INDENT2, "Expire " + expirePolicy + ": " + sourceType + " - " + deletedCounter + " deleted, " + missingCounter + " missing.");
		}
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
		
		try {
			session.beginTransaction();
			for(HnewsItemsDao item : hnewsItems) {
				String filename = item.getFullfilepath();
	
				if (filename == null) filename = "null";
				filename = config.getString("binaryRoot") + sep + filename;
				File delFile = new File(filename);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + filename);
						decoratedError(INDENT0, "Deleting and archiving by source type.", e);
					} else {
						item.setArchived(true);
						item.setArchivedTo(" ");
						session.persist(item);
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "Expire " + expirePolicy + ": The file " + filename + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Deleting and archiving historical news items for expired source types.", ex);
			session.getTransaction().rollback();
		}
		
		if (hnewsItems.size() > 0) {
			decoratedTrace(INDENT2, "Expire " + expirePolicy + ": " + sourceType + " - " + deletedCounter + " deleted, " + missingCounter + " missing.");
		}
	}

	
	/**
	 * Iterates through all entries in <code>newsItems</code> deleting binaries identified by the item's <code>fullfilepath</code> column and the
	 * news item record.
	 * 
	 * @param newsItems The list of NewsItemsDao objects to process.
	 * @param source The source associates with the list of news items (used for logging only).
	 * @param session The current Hibernate persistence context.
	 */
	private void deleteBinaryAndNewsItem(List<NewsItemsDao> newsItems, String source, Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		
		try {
			session.beginTransaction();
			for(NewsItemsDao item : newsItems) {
				String filename = item.getFullfilepath();
	
				if (filename == null) filename = "null";
				filename = config.getString("binaryRoot") + sep + filename;
				File delFile = new File(filename);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + filename);
						decoratedError(INDENT0, "Deleting and archiving for source: " + source, e);
					} else {
						NewsItemsDao.deleteRecord(item, session);
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "Expire binary and item: The file " + filename + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
			
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Deleting binary and news items by source: " + source, ex);
			session.getTransaction().rollback();
		}
		
		if(newsItems.size() > 0) {
			decoratedTrace(INDENT2, "Expire news item: " + source + " - " + deletedCounter + " deleted, " + missingCounter + " missing.");
		}
	}
	
	/**
	 * Iterates through all entries in <code>hnewsItems</code> deleting binaries identified by the item's <code>fullfilepath</code> column and the
	 * news item record.
	 * 
	 * @param newsItems The list of HnewsItemsDao objects to process.
	 * @param sourceType The sourceType associates with the list of news items (used for logging only).
	 * @param expirePolicy Contains the value Regular or Special (used for logging only).
	 * @param session The current Hibernate persistence context.
	 */
	private void deleteBinaryAndNewsItemHistorical(List<HnewsItemsDao> hnewsItems, String source, Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		
		try {
			session.beginTransaction();
			for(HnewsItemsDao item : hnewsItems) {
				String filename = item.getFullfilepath();
	
				if (filename == null) filename = "null";
				filename = config.getString("binaryRoot") + sep + filename;
				File delFile = new File(filename);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + filename);
						decoratedError(INDENT0, "Deleting and archiving for source: " + source, e);
					} else {
						HnewsItemsDao.deleteRecord(item, session);
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "Expire binary and item: The file " + filename + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Deleting binary and news items for source: " + source, ex);
			session.getTransaction().rollback();
		}

		if(hnewsItems.size() > 0) {
			decoratedTrace(INDENT2, "Expire binary and item: " + source + " - " + deletedCounter + " deleted, " + missingCounter + " missing.");
		}
	}
	
	/**
	 * Find all NewsItemImagesDao records with associated NewsItemsDao or HnewsItemsDao records and delete the binary from storage and the
	 * item from the NewsItemImages table.
	 * 
	 * @param retainImages How many days to retain the images (stored in preferences.retain_images).
	 * @param session The current Hibernate persistence context.
	 */
	private void clearExpiringImages(BigDecimal retainImages, Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		List<NewsItemImagesDao> imagesToDelete = NewsItemImagesDao.getExpiringImages(retainImages, session);
		
		try {
			
			session.beginTransaction();
			for (NewsItemImagesDao itemImage : imagesToDelete) {
				List<NewsItemImagesDao> shared = NewsItemImagesDao.getSharedImages(itemImage, session);
				
				if(shared.size() == 0) { // There are no other item image records that share this binary
					String path = itemImage.getBinaryPath();
					String fileName = itemImage.getFileName();
					
					if (path.startsWith(config.getString("wwwBinaryRoot"))) {
						path = config.getString("binaryRoot") + path.substring(config.getString("wwwBinaryRoot").length());
					}
					
					File delFile = new File(path + fileName);
					if (delFile.exists()) {
						if (!delFile.delete()) {
							IOException e = new IOException("Unable to delete file: " + path + fileName);
							decoratedError(INDENT0, "Finding and clearing expired images.", e);
						} else {
							NewsItemImagesDao.deleteRecord(itemImage, session);
							deletedCounter++;
						}
					} else {
						decoratedTrace(INDENT2, "Expire images: The file " + fileName + " is not in BinaryRoot.");
						missingCounter++;
					}
				}
			}
			
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Finding and clearing expiring images.", ex);
			session.getTransaction().rollback();
		}
		
		if(imagesToDelete.size() > 0) {
			decoratedTrace(INDENT2, "Expire images: " + deletedCounter + " deleted, " + missingCounter + " missing.");
		}
	}
	
	/**
	 * Find all SourceItemImagesDao records with associated NewsItemsDao or HnewsItemsDao records and delete the binary from storage and the
	 * item from the SourceItemImages table.
	 * 
	 * @param retainImages How many days to retain the images (stored in preferences.retain_images).
	 * @param session The current Hibernate persistence context.
	 */
	private void clearExpiringSourceImages(BigDecimal retainImages, Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		List<SourcePaperImagesDao> imagesToDelete = SourcePaperImagesDao.getExpiringImages(retainImages, session);
		
		try {
			
			session.beginTransaction();
			for (SourcePaperImagesDao itemImage : imagesToDelete) {
				String path = itemImage.getBinaryPath();
				String fileName = itemImage.getFileName();
				
				if (path.startsWith(config.getString("wwwBinaryRoot"))) {
					path = config.getString("binaryRoot") + path.substring(config.getString("wwwBinaryRoot").length());
				}
				
				File delFile = new File(path + fileName);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + path + fileName);
						decoratedError(INDENT0, "Finding and clearing expired source images.", e);
					} else {
						SourcePaperImagesDao.deleteRecord(itemImage, session);
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "Expire source images: The file " + fileName + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
			
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Finding and clearing expiring source images.", ex);
			session.getTransaction().rollback();
		}
		
		if(imagesToDelete.size() > 0) {
			decoratedTrace(INDENT2, "Expire source images: " + deletedCounter + " deleted, " + missingCounter + " missing.");
		}
	}
	
	/**
	 * Find all NewsItemImagesDao records with no associated NewsItemsDao or HnewsItemsDao records and delete the binary from storage and the
	 * item from the NewsItemImagesDao table.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	private void clearOrphanedImages(Session session) {
		long deletedCounter = 0;
		long missingCounter = 0;
		List<Object[]> imagesToDelete = NewsItemImagesDao.getOrphanedImages(session);
		
		try {
			
			session.beginTransaction();
			for (Object[] entityPair : imagesToDelete) {
				NewsItemImagesDao itemImage = (NewsItemImagesDao) entityPair[0];
				String path = itemImage.getBinaryPath();
				String fileName = itemImage.getFileName();
				
				if (path.startsWith(config.getString("wwwBinaryRoot"))) {
					path = config.getString("binaryRoot") + path.substring(config.getString("wwwBinaryRoot").length());
				}
				
				File delFile = new File(path + fileName);
				if (delFile.exists()) {
					if (!delFile.delete()) {
						IOException e = new IOException("Unable to delete file: " + path + fileName);
						decoratedError(INDENT0, "Finding and clearing orphaned images.", e);
					} else {
						NewsItemImagesDao.deleteRecord(itemImage, session);
						deletedCounter++;
					}
				} else {
					decoratedTrace(INDENT2, "Expire orphaned images: The file " + fileName + " is not in BinaryRoot.");
					missingCounter++;
				}
			}
			
			session.getTransaction().commit();
		} catch (Exception ex) {
			decoratedError(INDENT0, "Finding and clearing orphaned images.", ex);
			session.getTransaction().rollback();
		}
		
		if(imagesToDelete.size() > 0) {
			decoratedTrace(INDENT2, "Expire orphaned images: " + deletedCounter + " deleted, " + missingCounter + " missing.");
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