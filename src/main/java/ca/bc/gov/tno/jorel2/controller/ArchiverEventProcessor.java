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
	@Value("${temparchive}")
	private String tempArchive;
	
	/** Maximum CD capacity in kilobytes */
	@Value("${maxCdSize}")
	private String maxCdSize;
	
	/** Rollover period in days, after which CD images should be deleted. */
	@Value("${rolloverPeriod}")
	private String rolloverPeriod;
	
	/** Location of tno related data on remote server. */
	@Value("${ftp.root}")
	private String ftpRoot;
	
	private String sep = System.getProperty("file.separator");
	
	/**
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
	
	private void archiverEvent(EventsDao currentEvent, Session session) {
				
		try {
			if (ftpService.connect()) {
				//updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);
				List<PreferencesDao> preferences = PreferencesDao.getPreferencesByRsn(PREFERENCES_RSN, session);
				
				if (preferences.size() == 1 && preferences.get(0) instanceof PreferencesDao) {
					PreferencesDao prefs = preferences.get(0);
					String lastLabel = prefs.getLastArchiveRun();
					long cdSize = calcCDFileSize(lastLabel);
					ArchiveEventMetadata meta = new ArchiveEventMetadata(cdSize, lastLabel, prefs);
					
					List<Object[]> results = NewsItemsDao.getEligibleForArchive(session);
					
			        for (Object[] fieldSet : results) {
			        	BigDecimal rsn = (BigDecimal) fieldSet[0];
			        	String type = (String) fieldSet[1];
			        	Date itemDate = (Date) fieldSet[2];
			        	
			        	if(ftpService.isConnected()) {
			        		manageCdFullRollover(meta, session);
							String archiveDir = tempArchive + sep + meta.label + sep + type + sep;
			        		archiveFile(rsn, archiveDir, meta, session);
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
	
	private void archiveFile(BigDecimal rsn, String archiveDir, ArchiveEventMetadata meta, Session session) throws IOException, IllegalArgumentException {
		
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
			
			if(currentItem.getExternalbinary()) {
				meta.currentItem = currentItem;
				String tempFilePath = archiveDir + fileName;
				File tempLog = new File(tempFilePath);
				if (!tempLog.createNewFile()) {
					throw new IOException("Creating file " + tempFilePath);
				} else {
					meta.remoteFile = ftpRoot + filePath;

					if (ftpService.exists(meta.remoteFile)) {
						downloadFile(tempFilePath, meta, tempLog, rsn, session);
					}
				}
			}
		} else {
			HibernateException e = new HibernateException("Retrieving the hnews_items record.");
			decoratedError(INDENT2, "Zero records returned, or unexpected return format.", e);
		}
	}
	
	private void downloadFile(String tempFilePath, ArchiveEventMetadata meta, File tempLog, BigDecimal rsn, Session session) throws IOException {
		
		long fileSize = 0;
		ftpService.setTypeBinary();
		if (!ftpService.download(tempFilePath, meta.remoteFile) ) {
			throw new IOException("Downloading file " + meta.remoteFile + " from server.");
		} else {
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
	
	private long calcCDFileSize( String label ) {
		String fileSep = System.getProperty("file.separator");
		long size = 0;

		File tempdir=new File(tempArchive);
		try {
			if (tempdir.exists()) {
				size = calcDirFileSize( tempdir );
			}
		} catch (Exception err) { ; }

		return size;
	}

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
	
	private void updateArchivedStatus(HnewsItemsDao currentItem, String archivedPath, BigDecimal rsn, Session session) {
		//update tno.hnews_items set archived = 1, archived_to = ? where rsn = ?
	}
	
	private void manageCdFullRollover(ArchiveEventMetadata meta, Session session) {
		
		String lastLabel = "";
		
		if (meta.cdSize > meta.maxSize) {
			meta.emailMessage = meta.emailMessage + "  " + meta.label + ", ";
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

	private String calcNextLabel(String label) {
		//*********************************************************************************
		// Since the event record keeps track of when the event was last executed, the
		// application preference 'Last Archive Run' is used to keep track of
		// the label used on the CDR.  Every time this event is executed the
		// label number is incremented...CD0003 - CD0004 - CD0005 - ...
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

		// Update the application pref
		//if (frame.isArchiverUpdating()) {
		//	prefs.setLast_archive_run(nextLabel);
		//	prefs.update();
		//}

		return nextLabel;
	}
	
	private class ArchiveEventMetadata {
		
		public long cdSize;
		public String emailMessage;
		public String label;
		public PreferencesDao prefs;
		public long maxSize = Integer.parseInt(maxCdSize) * 1024 * 1024;
		boolean sendMessage = false;
		HnewsItemsDao currentItem = null;
		String remoteFile = "";

		public ArchiveEventMetadata(long cdSize, String label, PreferencesDao prefs) {
			this.cdSize = cdSize;
			this.label = label;
			this.prefs = prefs;
			this.emailMessage = "";			
		}
		
	}
}