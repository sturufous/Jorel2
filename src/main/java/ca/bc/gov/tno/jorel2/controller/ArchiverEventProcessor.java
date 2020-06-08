package ca.bc.gov.tno.jorel2.controller;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.FtpDataSource;
import ca.bc.gov.tno.jorel2.model.HnewsItemsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.EmailUtil;

/**
 * This event processor selects all records in HNEWS_ITEMS that have not yet been archived and, if the media associated with them is stored externally,
 * moves the media from the binary root directory to a CD directory in the archiveTo directory. The CD directory name is stored in the LAST_ARCHIVE_RUN 
 * column of the PREFERENCES table, which has the format CD9999. Some media used to be stored as blobs in the TNO database, but Jorel2 does not support 
 * this functionality (it is no longer used).
 * 
 * If the contents of the CD directory exceed the number of Megabytes identified by the maxCdSize property, the numeric portion of the CD directory name
 * is incremented and a new empty directory is created for future archived files. The name of the new CD9999 directory is then stored in the 
 * last_archive_run column of PREFERENCES for use by future archive events. An email is sent to the distribution list identified by the mail.to property 
 * listing the names of all CD directories that are ready to be copied to external media. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class ArchiverEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/** Service that provides access to an FTP data source as configured in jorel2.properties */
	@Inject
	FtpDataSource ftpService;
	
	/** Root directory into which archived files are copied */
	@Value("${archiveTo}")
	private String archiveTo;
	
	/** Maximum CD capacity in kilobytes */
	@Value("${maxCdSize}")
	private String maxCdSize;
	
	/** Location of tno related data on remote ftp server (binary root). */
	@Value("${ftp.root}")
	private String ftpRoot;
	
	private String sep = System.getProperty("file.separator");
	
	/**
	 * Reads all eligible archive events from the EVENTS table and, if they are runnable today, passes them to the <code>archiverEvent()</code> method.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	public Optional<String> processEvents(String eventType, Session session) {
		
		EventsDao currentEvent = null;
    	
    	try {
    		decoratedTrace(INDENT1, "Starting Archiver event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		currentEvent = (EventsDao) entityPair[0];
	        		
	        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
	        			archiverEvent(currentEvent, session);
	        		}
	        	}
	        }
	        
    		decoratedTrace(INDENT1, "Completed Archiver event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing Archiver event " + currentEvent.getName(), e);
    	}
    	
    	return Optional.of("complete");
	}
	
	/**
	 * Obtains the CD label from PROPERTIES and loops through all records in HNEWS_ITEMS that are eligible for archive, sending them to
	 * the archiveFile() method.
	 * 
	 * @param currentEvent The current Archiver event being processed.
	 * @param session The current Hibernate persistence context.
	 */
	private void archiverEvent(EventsDao currentEvent, Session session) {
				
		try {
			if (ftpService.connect()) {
				//updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);
				List<PreferencesDao> preferences = PreferencesDao.getPreferencesByRsn(PREFERENCES_RSN, session);
				
				if (preferences.size() == 1 && preferences.get(0) instanceof PreferencesDao) {
					PreferencesDao prefs = preferences.get(0);
					String lastLabel = prefs.getLastArchiveRun();
					long cdSize = calcCDFileSize(lastLabel);
					ArchiveMetadata meta = new ArchiveMetadata(cdSize, lastLabel, prefs);
					
					List<Object[]> results = HnewsItemsDao.getEligibleForArchive(session);
					
			        for (Object[] fieldSet : results) {
			        	BigDecimal rsn = (BigDecimal) fieldSet[0];
			        	String type = (String) fieldSet[1];
			        	Date itemDate = (Date) fieldSet[2];
			        	
			        	if(ftpService.isConnected()) {
			        		manageCdFullRollover(meta, session);
							String archiveDir = archiveTo + sep + meta.label + sep + type + sep;
			        		if(!archiveFile(rsn, archiveDir, meta, session)) {
			        			// The only reason archiveFile() returns false is if disk space is exhausted, so abort event.
			        			return;
			        		}
			        	}
			        }
			        
			        if (meta.sendMessage) {
			        	EmailUtil.archiverSendMail(instance.getMailHostAddress(), instance.getMailPortNumber(), 
			        			instance.getMailFromAddress(), instance.getMailToAddress(), meta.emailMessage);
			        }
				} else {
					HibernateException e = new HibernateException("Retrieving the preferences record.");
					decoratedError(INDENT2, "Zero records returned, or unexpected return format.", e);
				}
				
				ftpService.disconnect();
			}
		} catch (Exception e) {
			decoratedError(INDENT2, "Processing Archiver event.", e);
		}
	}
	
	/**
	 * Creates the directory structure into which archived files are placed and then archives the media in the binary root identified by the 
	 * <code>fileName</code> and <code>fullFilePath</code> to the CD directory. The file is downloaded from the ftp.host to the CD directory
	 * using the <code>FtpDataSource</code> stored in <code>ftpService</code>. If the disk containing the archive directory becomes full, the 
	 * archive process is aborted.
	 * 
	 * @param rsn The key to the HNEWS_ITEMS record to be processed.
	 * @param archiveDir The directory into which the media associated with the news item should be archived.
	 * @param meta An object containing multiple data relating to the archive process.
	 * @param session The current Hibernate persistence context.
	 * @return True if the operation is successful, false otherwise.
	 * @throws IOException Thrown if the archive file cannot be created (e.g. it already exists).
	 * @throws IllegalArgumentException Thrown if the file to be archived contains zero bytes.
	 */
	private boolean archiveFile(BigDecimal rsn, String archiveDir, ArchiveMetadata meta, Session session) throws IOException, IllegalArgumentException {
		
		boolean success = true;

		// Create target directories if they don't already exist
		File tempdir = new File(archiveDir);
		try {
			if (!tempdir.exists()) {
				tempdir.mkdirs(); 
			}
		} catch (Exception err) { 
			throw new IOException("Creating temp archive directory " + tempdir, err);
		}
		
		List<HnewsItemsDao> results = HnewsItemsDao.getItemByRsn(rsn, session);
		
		if(results.size() == 1 && results.get(0) instanceof HnewsItemsDao) {
			HnewsItemsDao currentItem = results.get(0);
			String fileName = currentItem.getFilename();
			String filePath = currentItem.getFullfilepath();
			String tempFilePath = archiveDir + fileName;
			
			if(currentItem.getExternalbinary()) {
				meta.currentItem = currentItem;
				File tempLog = new File(tempFilePath);
				
				try {
					if (tempLog.createNewFile()) {
						meta.remoteFile = ftpRoot + filePath;
	
						if (ftpService.exists(meta.remoteFile)) {
							downloadFile(tempFilePath, meta, tempLog, rsn, session);
						}
					} else {
						throw new IOException("Unable to create file " + tempFilePath + ". Does it already exist?");
					}
				} catch (Exception e) {
					// Assume that any failure reason other than "out of space" is recoverable.
					if(e.getMessage().indexOf("disk space") > 0 || e.getMessage().indexOf("out of space") > 0 
					    || e.getMessage().indexOf("not enough space") > 0) {
						success = false;
					}
					decoratedError(INDENT2, "While downloading file: " + tempFilePath, e);
				}
			}
		} else {
			HibernateException e = new HibernateException("Retrieving the hnews_items record.");
			decoratedError(INDENT2, "Zero records returned, or unexpected return format.", e);
		}
		
		return success;
	}
	
	/**
	 * Uses the ftpService to download the file <code>meta.remoteFile</code> to the destination file <code>tempFilePath</code>. Once successfully
	 * archived, this method sets the archived status of the HNEWS_ITEMS record to <code>true</code>, by calling <code>updateArchivedStatus()</code>
	 * and deletes the remote file.
	 * 
	 * @param tempFilePath The path of the file into which this binary root file is archived.
	 * @param meta An object containing multiple data relating to the archive process.
	 * @param tempLog Abstract representation of the archived file.
	 * @param rsn Key of the HNEWS_ITEMS record being archived. 
	 * @param session The current Hibernate persistence context.
	 * @throws IOException Thrown if the file cannot be downloaded.
	 */
	private void downloadFile(String tempFilePath, ArchiveMetadata meta, File tempLog, BigDecimal rsn, Session session) throws IOException {
		
		boolean success = true;
		
		long fileSize = 0;
		ftpService.setTypeBinary();
		if (ftpService.download(tempFilePath, meta.remoteFile)) {
			if (tempLog.exists()) {
				fileSize = tempLog.length();

				if (fileSize < 1) {
					throw new IllegalArgumentException("Zero bytes in " + tempFilePath);
				} else {
					meta.cdSize = meta.cdSize + fileSize;
					decoratedTrace(INDENT2, "Archived file to " + tempFilePath);
					updateArchivedStatus(meta.currentItem, tempFilePath, rsn, session);
					//ftpService.delete(remoteFile);
				}
			}
		} else {
			throw new IOException("Downloading file " + meta.remoteFile + " from server. FTP error: " + ftpService.getError());
		}
	}
	
	/**
	 * Updates the database record's lastFtpRun to the value provided.
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
	
	/**
	 * Forms a directory path by concatenating the properties value <code>archiveTo</code> (which is stored in the instance variable
	 * <code>archiveTo</code>) and calls <code>calcDirFileSize()</code> to retrieve the number of bytes contained in the directory.
	 * 
	 * @param label The label of the CD currently being archived to (e.g. CD8769)
	 * @return The number of bytes contained in the directory.
	 */
	private long calcCDFileSize( String label ) {
		String fileSep = System.getProperty("file.separator");
		long size = 0;

		File tempdir=new File(archiveTo);
		try {
			if (tempdir.exists()) {
				size = calcDirFileSize( tempdir );
			}
		} catch (Exception err) { ; }

		return size;
	}

	/**
	 * Performs a recursive scan of the CD directory identified by <code>dir</code> and returns the aggregated number
	 * of bytes contained in the directory.
	 * 
	 * @param dir Abstract representation of the CD archive directory. 
	 * @return The number of bytes contained in the directory.
	 */
	private long calcDirFileSize(File dir) {
		long size = 0;
		File files[] = dir.listFiles();
		for (int i=0; i<files.length; i++) {
			if ( files[i].isDirectory() ) {
				size = size + calcDirFileSize( files[i] );
			} else {
				size = size + files[i].length();
			}
		}
		return size;
	}
	
	/**
	 * Sets the archivedPath of the historical news items object <code>currentItem</code> to the string passed in <code>archivedPath</code>,
	 * sets the archived status of that object to <code>true</code> and updates the record.
	 * 
	 * @param currentItem The HNEWS_ITEMS record that was just archived.
	 * @param archivedPath The path to which this items media was archived.
	 * @param rsn The key of the HNEWS_ITEMS record.
	 * @param session The current Hibernate persistence context.
	 */
	private void updateArchivedStatus(HnewsItemsDao currentItem, String archivedPath, BigDecimal rsn, Session session) {
		//update tno.hnews_items set archived = 1, archived_to = ? where rsn = ?
	}
	
	/**
	 * Checks whether, after the most recent download, the contents of the CD archive directory exceeds the value stored in the <code>maxCdSize</code>
	 * property. If it does, the label of the current CD is added to <code>meta.emailMessage</code> and a new CD label is calculated by calling
	 * <code>calcNextLabel()</code>. The LAST_ARCHIVE_RUN column of the PREFERENCES record stored in <code>meta.prefs</code> is set to the new label
	 * and the record is persisted to the TNO database. Each time the contents of a CD directory exceed <code>meta.maxSize</code> bytes the old label
	 * is appended to <code>meta.emailMessage</code> and, after completion of archive event processing, a message is sent to the distribution list
	 * notifying that these CD directories are ready to be stored offline.
	 * 
	 * @param meta Metadata relating to the current archive event.
	 * @param session The current Hibernate persistence context.
	 */
	private void manageCdFullRollover(ArchiveMetadata meta, Session session) {
		
		String lastLabel = "";
		
		if (meta.cdSize > meta.maxSize) {
			meta.emailMessage = meta.emailMessage + "  " + meta.label + " ";
			lastLabel = meta.label;
			meta.label = calcNextLabel(lastLabel);
			meta.cdSize = 0;
			meta.sendMessage = true;
			decoratedTrace(INDENT2, "Disc " + lastLabel + " is full. Archiving to " + meta.label);
			
			meta.prefs.setLastArchiveRun(meta.label);
			session.beginTransaction();
			session.persist(meta.prefs);
			session.getTransaction().commit();
		}
	}

	/**
	 * Given a label of the format "CD9999, this message extracts the numeric portion and increments it by one, returning a
	 * new label in the same format received.
	 *
	 * @param label The label of the current CD archive, which will now be full.
	 * @return A new label for the next CD to archive.
	 */
	private String calcNextLabel(String label) {

		String nextLabel = label.toUpperCase();
		String now = DateUtil.getDateNow();
		
		if (nextLabel.startsWith("CD")) {
			String strNum = nextLabel.substring(2);
			int i;
			try { 
				i = Integer.parseInt(strNum); 
			}
			catch (Exception err) {
				i=-1;
			}
			
			if (i>0) {
				i++;
				strNum=Integer.toString(i);
				String zero="0000";
				nextLabel="CD"+zero.substring(0,4-strNum.length())+strNum;
			} else {
				nextLabel="ERROR";
			}
		} else {
			nextLabel = now;
		}

		return nextLabel;
	}
	
	/**
	 * Private inner class used to consolidate eight separate data items relating to the current archive event. As the class is private there
	 * is no need to use getter and setter methods, and all instance variables are public.
	 * 
	 * @author StuartM
	 */
	private class ArchiveMetadata {
		
		public long cdSize;
		public String emailMessage;
		public String label;
		public PreferencesDao prefs;
		public long maxSize = Integer.parseInt(maxCdSize) * 1024 * 1024;
		public boolean sendMessage = false;
		public HnewsItemsDao currentItem = null;
		public String remoteFile = "";

		public ArchiveMetadata(long cdSize, String label, PreferencesDao prefs) {
			this.cdSize = cdSize;
			this.label = label;
			this.prefs = prefs;
			this.emailMessage = "";			
		}
	}
}