package ca.bc.gov.tno.jorel2.controller;


import java.io.File;
import java.io.IOException;
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
import ca.bc.gov.tno.jorel2.model.NewsItemImagesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class CleanBinaryRootEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration config;
	
	/**
	 * Process all eligible CleanBinaryRootEventProcessor event records from the EVENTS table. 
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting CleanBinaryRoot event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        
	        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		
	        		cleanBinaryRootEvent(currentEvent, session);
	        		
	        		// Update the lastFtpRun to today's date to prevent CleanBinaryRoot event from running again until tomorrow.
	        		String currentDate = DateUtil.getDateNow();
	        		currentEvent.setLastFtpRun(currentDate);
	        		session.beginTransaction();
	        		session.persist(currentEvent);
	        		session.getTransaction().commit();
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing CleanBinaryRoot entries.", e);
    	}
    	
		decoratedTrace(INDENT1, "Completed CleanBinaryRoot event processing");

    	
    	return Optional.of("complete");
	}
	
	/**
	 * Calculate a list of yyyy/mm/dd directories in the <code>binaryRoot</code> directory and begin recursing through each one looking for files to delete.
	 * Files will be retained if they are images and have a corresponding record in NEWS_ITEM_IMAGES, or if the file name identifies an existing RSN 
	 * in the NEWS_ITEMS table. The number of consecutive days to process is stored in the <code>source</code> field of the current event.
	 * 
	 * @param currentEvent The current EVENTS record for being processed
	 * @param session The current Hibernate persistence context
	 */
	
	private void cleanBinaryRootEvent(EventsDao currentEvent, Session session) {
		String currentDate = DateUtil.getDateNow();
		
		String lastRun = currentEvent.getLastFtpRun();
		String startTimeStr = currentEvent.getStartTime();
		if (startTimeStr == null) startTimeStr="00:00:00";

		if (lastRun != null && !lastRun.equals(currentDate)) {    // Do not execute again today
			LocalDateTime now = LocalDateTime.now();
			
			if (startTimeStr.length() == 8 && startTimeStr.indexOf(":") > 0) {
				String startHoursMinutes = startTimeStr.substring(0, 5);
				String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());

				if (nowHoursMinutes.equals(startHoursMinutes)) {
	
					int numOfDays = 1;
					String numOfDaysString = currentEvent.getSource();
					try { numOfDays=Integer.parseInt(numOfDaysString); } catch (Exception err) { numOfDays=1; }
	
					String subDirectory = currentEvent.getFileName();
					String justLogit = currentEvent.getTitle();
	
					for (int ii = 1; ii <= numOfDays; ii++) {
						String itemMonthString = String.format("%02d", now.getMonth().getValue());
						String itemDayString = String.format("%02d", now.getDayOfMonth());
						String itemYearString = String.format("%04d", now.getYear());
						
						String sub = subDirectory.replaceAll("#y#",itemYearString);
						sub = sub.replaceAll("#m#",itemMonthString);
						sub = sub.replaceAll("#d#",itemDayString);
	
						String path = config.getString("binaryRoot") + "/" + sub;
			    		decoratedTrace(INDENT2, "Begin clean binary directory: " + path);
	
						recurseBinaryRoot(path, justLogit, session);
			    		
			    		decoratedTrace(INDENT2, "Finish clean binary root directory");
						now = now.minusDays(1L);
	
					}
				}
			} else {
				IllegalArgumentException e = new IllegalArgumentException("Processing cleanBinaryRoot event.");
				decoratedError(INDENT0, "Error in format of StartTime for CleanBinaryRoot event.", e);
			}
		}
	}
	
	/**
	 * Recurse through the directory passed from <code>cleanBinaryRootEvent</code> deleting images with no corresponding record in NEWS_ITEM_IMAGES
	 * and other files with an RSN file name that has no corresponding record in NEWS_ITEMS.
	 * 
	 * @param filePath The file path of the directory/file being processed at the current level of recursion.
	 * @param justLogit The action to take if a file is found to be invalid.
	 * @param session The current Hibernate persistence context.
	 */
	
	private void recurseBinaryRoot(String filePath, String justLogit, Session session) {

		File file = new File(filePath);

		if (file.exists()) {

			if (file.isDirectory()) { // directory - recurse into it

				String fileList[] = file.list();
				for (int i=0; i < fileList.length; i++) {
					recurseBinaryRoot(filePath + "/" + fileList[i], justLogit, session);
				}
			} else {  // file - make sure it is valid

				String shortname = file.getName();
				int p = shortname.indexOf(".");
				if (p != -1) {
					shortname = shortname.substring(0,p);
				}

				// images - should be a news_item_images record
				if (file.getName().endsWith(".jpg")) {

					String avpath = filePath;
					if (avpath.startsWith(config.getString("binaryRoot"))) {
						avpath = config.getString("wwwBinaryRoot") + avpath.substring(config.getString("binaryRoot").length());
					}
					
					if (avpath.endsWith(file.getName())) {
						avpath = avpath.substring(0, avpath.length() - file.getName().length());
					}

					String notThumbFileName = file.getName();
					if (notThumbFileName.endsWith("-thumb.jpg")) {
						notThumbFileName = notThumbFileName.substring(0, notThumbFileName.length() - 10)+".jpg";
					}

					try {
						List<NewsItemImagesDao> results = NewsItemImagesDao.getImageRecordsByFileName(notThumbFileName, avpath, session);
						
						if (results.size() == 0) {
							if (justLogit.equalsIgnoreCase("deleteit")) {
								if (!file.delete()) {
									IOException e = new IOException("Attempting to clean up file.");
									decoratedError(INDENT0, "Deleting file " + file.getName(), e);
								}
							}
						}
					} catch (Exception ex) {
						decoratedError(INDENT0, "Reading from NEWS_ITEM_IMAGES.", ex);
					}

				// not image - filename should be RSN
				} else {
					long fileRsn = 0;
					try {
						fileRsn = Long.parseLong(shortname);
					} catch (NumberFormatException ex) {
						decoratedError(INDENT0, "Parsing NEWS_ITEM RSN: " + file.getName(), ex);
						fileRsn = 0;
					}

					if (fileRsn > 0) {
						try {
							List<NewsItemsDao> results = NewsItemsDao.getItemByRsn(fileRsn, session);
							if (results.size() == 0) {  // no record in the database with this RSN --> invalid file
								if (justLogit.equalsIgnoreCase("deleteit")) {
									if (!file.delete()) {
										IOException e = new IOException("Attempting to clean up file.");
										decoratedError(INDENT0, "Deleting file " + file.getName(), e);
									}
								}
							}
						} catch (Exception ex) {
							decoratedError(INDENT0, "Reading from NEWS_ITEMS.", ex);
						}
					}
				} 
			}
		} 
	}
}