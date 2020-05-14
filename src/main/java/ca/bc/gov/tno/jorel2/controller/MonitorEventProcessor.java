package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.FilesImportedDao;
import ca.bc.gov.tno.jorel2.model.ImportDefinitionsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class MonitorEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2Instance instance;
	
	/** Handler for processing front page images */
	@Inject
	private FrontPageImageHandler imageHandler;
	
	/** Handler for processing newspaper items */
	@Inject
	private NewspaperImportHandler importHandler;
	
	/** Maximum age of files for import in hours */
	@Value("${importFileHours}")
	private String importFileHoursStr;
	
	/** Directory to which processed files should be moved */
	@Value("${processedFilesLoc}")
	private String processedFilesLoc;
	
	private String sep = System.getProperty("file.separator");

	/**
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting Monitor event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		
	        		String currentDate = DateUtil.getDateNow();
	        		//currentEvent.setLastFtpRun(currentDate);
	        		//session.beginTransaction();
	        		//session.persist(currentEvent);
	        		//session.getTransaction().commit();
	        		
	        		monitorEvent(currentEvent, session);
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing monitor event.", e);
    	}
    	
    	decoratedTrace(INDENT1, "Completing monitor event processing");
    	return Optional.of("complete");
	}
	
	/**
	 * Retrieves the list of files identified by the <code>FileName</code> column of this event and processes them by file type.
	 * 
	 * @param currentEvent The monitor event currently being processed.
	 * @param session The current Hibernate persistence context.
	 */
	private void monitorEvent(EventsDao currentEvent, Session session) {

		//setCountFilesImported(0);

		String startTimeStr = currentEvent.getStartTime() == null ? "00:00:00" : currentEvent.getStartTime();
		LocalDateTime now = LocalDateTime.now();
		String startHoursMinutes = startTimeStr.substring(0, 5);
		String nowHoursMinutes = "02:30"; // String.format("%02d:%02d", now.getHour(), now.getMinute());
		
		String dirName = currentEvent.getFileName();
		File dir = new File(currentEvent.getFileName());
		
		if (nowHoursMinutes.equals(startHoursMinutes) && dir.isDirectory()) {
			String definitionName = currentEvent.getDefinitionName();

			// Block sync and other monitor events
			//frame.indexBlockSet();

			try {
				// Monitors the directory
				// frame.addJLog("Monitor Scan directory '"+dirName+"' ["+startHour+":"+startMinute+"] at "+

				int importFileHours = Integer.valueOf(importFileHoursStr);
				//int sc=getCountFilesImported(); // start count of number of files imported

				for (String currentFile : dir.list()) {
					String fileForImport = "";
					fileForImport = dirName.endsWith(sep) ? dirName + currentFile : dirName + sep + currentFile;
					File f = new File(fileForImport);

					if (fileIsImportFile(f, importFileHours, fileForImport)) {
						boolean moveFile = false;

						moveFile = performMediaTypeSwitching(currentEvent, currentFile, fileForImport, f, session);

						// Move this file elsewhere
						if(moveFile)
						{
							//movePaperFile(f, fileSep, moveFilePrefix);
						}
					}
				}

				// Any files imported??
				//int ec=getCountFilesImported();   // end count of files imported
				//if (sc == ec) importedOne=false; else importedOne=true;
			} catch (Exception ex) { 
				decoratedError(INDENT1, "Processing import file list.", ex);
				//frame.addJLog(eventLog("doImport.run(): unknown error: "+ex.getMessage()));
			}

			// Remove block
			//frame.indexBlockRemove();
	
			//Update this record to reflect that it has run and can now be run again
			currentEvent.setLastFtpRun("idle");
			session.beginTransaction();
			session.persist(currentEvent);
			session.getTransaction().commit();

			//} //while (monitorEvt.next(monRS))
		
			// If any files imported, then rebuild the index CONTENT_INDEX
			/* if (getCountFilesImported() > 0) {
				dbSync_Index s=new dbSync_Index(frame);
				s.insert();
				s.destroy();
				s=null;
			} */
		}
	}
	
	/**
	 * Determines how to import a file based on the suffix, and hands the file off to the corresponding handler for that media type.
	 * 
	 * @param currentFile The file to be imported.
	 * @param fileForImport Full path name of the file to be imported.
	 * @param definitionName The <code>definitionName</code> value from the monitor event record.
	 * @param f An abstract representation of the file to be processed.
	 * @param session The current Hibernate persistence context.
	 * @return Boolean indicating whether the file should be moved.
	 */
	
	@SuppressWarnings("preview")
	private boolean performMediaTypeSwitching(EventsDao currentEvent, String currentFile, String fileForImport, File f, Session session) {
		// Make sure the file is completely downloaded
		boolean moveFile = false;
		String moveFilePrefix = "";
		PreferencesDao preferences = instance.getPreferences();
		String definitionName = currentEvent.getDefinitionName();

		// Make sure this file has not already been imported
		List<FilesImportedDao> imported = FilesImportedDao.getFilesImportedByFileName(currentFile, session);
		boolean notAlreadyImported = imported.size() == 0;
		
		if (notAlreadyImported) { // Applies only to news articles
			verifyDownloadCompletion(f);
			String suffix = currentFile.substring(currentFile.toLowerCase().lastIndexOf('.') + 1);
			
			moveFile = switch(suffix) {
				case "zip" -> frontPageFromZip(currentFile, fileForImport, definitionName, session);
				case "jpg" -> frontPageFromJpg(currentFile, fileForImport, definitionName, session);
				case "pdf" -> frontPageFromPdf(currentFile, fileForImport, definitionName, session);
				default -> processNewsItem(currentEvent, currentFile, fileForImport, f, suffix, session);
			};
		} else {
			if (!definitionName.equalsIgnoreCase("Globe and Mail XML")) {
				//frame.addJLog(eventLog("DailyFunctions.monitorEvent(): File already processed "+s[i]), true);
				moveFile = true;
				moveFilePrefix = "fap_";
			}
		}
		
		return moveFile;
	}
	
	/**
	 * Determines the validity of the import file based on several criteria. If the file fails any of the tests this method returns false. 
	 * Criteria include whether the file exists, if it is too old to import, if it's hidden or is a directory.
	 * 
	 * @param f Abstract representation of the file name representing the file in question.
	 * @param importFileHours Length of time since this file was last modified.
	 * @param currentFile The string representation of this file's path name.
	 * @return Whether this file should be skipped.
	 */
	
	private boolean fileIsImportFile(File f, int importFileHours, String currentFile) {
		
		boolean isValid = true;
		
		// Any of these thing will cause this file to be skipped
		if (!f.exists()) {
			//frame.addJLog("File does not exist '"+file4Import+"'");
			isValid = false;
		}

		if (importFileHours > 0) {
			long diff = (new Date()).getTime() - f.lastModified();
			if (diff > (3600000 * importFileHours)) {
				isValid = false;
			}
		}

		if(f.isHidden()){
			//no need to log this it will happen everytime we find a .bashrc, .bashprofile etc
			isValid = false;
		}

		if (f.isDirectory() && !f.isHidden()) {
			if(currentFile.toUpperCase().indexOf(".KDE") < 1)
				//frame.addJLog("File is a directory '"+file4Import+"'");
				isValid = false;
		}
		
		if (currentFile.toUpperCase().indexOf(".LOG") > 0) {
			//frame.addJLog("File is a log file '"+file4Import+"'");
			isValid = false;
		}
		
		return isValid;
	}
	
	/**
	 * Verifies that the size of the file identified by the single parameter <code>f</code> has not changed in size for at least ten seconds.
	 * If this condition is met, assume that the file is not currently being downloaded.
	 * 
	 * @param f An abstract representation of the file to monitor.
	 */
	
	private void verifyDownloadCompletion(File f) {
		
		long size = 0;
		long oldSize = 0;
		int count = 0;
		int wait = 0;
		boolean notSameSize=true;
		
		while (notSameSize) {
			size = f.length();
			if (oldSize == size) {
				count++; wait=1000;
				if (count >= 5) notSameSize=false;
			} else {
				oldSize=size; count=0; wait=1000*5;
			}
			// Wait a second
			if (notSameSize) {
				try { Thread.sleep(wait); } catch (InterruptedException e) { if(false) System.out.println("Thread was interrupted: " + e); }
			}
		} // while (notSameSize)
	}
	
	/**
	 * Manages the extraction of front page images from Zip files. Currently this format is used exclusively by Infomart.
	 * 
	 * @param currentFile File name of zip file to import.
	 * @param fileForImport Full path of zip file to import.
	 * @param definitionName Definition name from EVENTS record.
	 * @return Whether this file should be moved.
	 */
	
	private boolean frontPageFromZip(String currentFile, String fileForImport, String definitionName, Session session) {
		
		if (definitionName.equalsIgnoreCase(INFOMART_ID_STRING)) {
			//Infomart image zip file
			return imageHandler.infomartImageHandler(currentFile, fileForImport, session);
		} else {
			return true;
		}
	}
	
	/** Manages the extraction of front page images from Jpg files. Currently this format is used exclusively by Globe and Mail.
	 * 
	 * @param currentFile File name of jpg file to import.
	 * @param fileForImport Full path of jpg file to import.
	 * @param definitionName Definition name from EVENTS record.
	 * @return Whether this file should be moved.
	 */
	
	private boolean frontPageFromJpg(String currentFile, String fileForImport, String definitionName, Session session) {
		
		if (definitionName.equalsIgnoreCase(GANDM_ID_STRING)) {
			// Globe image file
			return imageHandler.gandmImageHandler(currentFile, fileForImport, session);
		} else {
			return true;
		}
	}

	/** Manages the extraction of front page images from Pdf files. Currently this format is used exclusively by Vancouver 24 hrs (which is 
	 * no longer needed). Consider this incomplete and untested.
	 * 
	 * @param currentFile File name of pdf file to import.
	 * @param fileForImport Full path of pdf file to import.
	 * @param definitionName Definition name from EVENTS record.
	 * @return Whether this file should be moved.
	 */
	
	private boolean frontPageFromPdf(String currentFile, String fileForImport, String definitionName, Session session) {
		
		if (definitionName.equalsIgnoreCase(VAN24_ID_STRING)) {
			// Vancouver 24 image file
			return imageHandler.van24ImageHandler(currentFile, fileForImport, session);
		} else {
			return true;
		}
	}
	
	/**
	 * Handles the import of all file types other than front page images.
	 * 
	 * @param currentFile File name of pdf file to import.
	 * @param fileForImport Full path of pdf file to import.
	 * @param definitionName Definition name from EVENTS record.
	 * @param f Abstract representation of the file to be processed.
	 * @return Whether this file should be moved.
	 */
	
	private boolean processNewsItem(EventsDao currentEvent, String currentFile, String fileForImport, File f, String suffix, Session session) {

		// globe and mail fudge to add CDATA tags
		String definitionName = currentEvent.getDefinitionName();
		if (definitionName.equalsIgnoreCase("Globe and Mail XML") && suffix.equalsIgnoreCase("xml")) {
			String content = "";
			try {
				FileReader reader = new FileReader(f);
				char[] chars = new char[(int) f.length()];
				reader.read(chars);
				content = new String(chars);
				reader.close();
			} catch (Exception e) { ; }
			if (content.indexOf("<![CDATA[")<0) {
				content = content.replace("<body.content>", "<body.content><![CDATA[");
				content = content.replace("</body.content>", "]]></body.content>");
				BufferedWriter writer = null;
				try {
					writer = new BufferedWriter(new FileWriter(f));
					writer.write(content);
					writer.close();
				} catch (Exception e) { ; }
			}
			//moveFile = false; // don't move the G&M files
		}

		doImport(currentEvent, currentFile, fileForImport, true, session);
		//moveFile = false; // the doimport procedure will have moved this file

		return true;

	}
	
	/**
	 * Imports the file by calling <code>importFile()</code> and if the import is successful moves the import file to the processed directory.
	 * 
	 * @param currentEvent The monitor event record being processed.
	 * @param currentFile The file name of the import file being processed.
	 * @param fileForImport The full path name of the import file being processed.
	 * @param moveFile Whether the import completed successfully.
	 * @param session
	 * @return
	 */
	
	private boolean doImport(EventsDao currentEvent, String currentFile, String fileForImport, boolean moveFile, Session session) {
		String charEncoding = currentEvent.getTitle();
		boolean success = true;
		String sourceName = currentEvent.getDefinitionName();
		BufferedReader in = null;

		List<ImportDefinitionsDao> definitions = ImportDefinitionsDao.getDefinitionByName(sourceName, session);
		if (definitions.size() > 0) {
			try {
				
				if (currentEvent.getTriggerImport()) {
					in = getBufferedReader(fileForImport, charEncoding);
					ImportDefinitionsDao importMeta = definitions.get(0);
					success = importFile(currentEvent, fileForImport, importMeta, in, session);
					in.close();
				}			

				//dailyFunctions.incCountFilesImported();
				if (success && moveFile) {
					moveFile(currentFile, fileForImport);
				}
			} catch (Exception e) {
				decoratedError(INDENT1, "Importing news item file: " + fileForImport, e);
				success = false;
			}

			//frame.addJLog(dailyFunctions.eventLog("doImport.run(): File imported '"+getFileName()+"'"));
		} else {
			decoratedTrace (INDENT1, "No IMPORT_DEFINITIONS record for source: " + sourceName);
			success = false;
		}
		//dailyFunctions.setImportingNow(false);	
		return success;
	}
	
	/**
	 * Get a buffered reader from which to retrieve the import file contents.
	 * 
	 * @param fileForImport Full path of the file to import.
	 * @param charEncoding Character encoding to use when reading the file.
	 * @return The open BufferedReader.
	 */
	private BufferedReader getBufferedReader(String fileForImport, String charEncoding) {
		
		FileInputStream bin = null;
		BufferedReader in = null;

		try {
			if (charEncoding == null || charEncoding.equals("")) { // no character encoding provided?
				FileReader file = new FileReader(fileForImport);
				in = new BufferedReader(file);
			} else { // open with a particular character encoding
				FileInputStream fis = new FileInputStream(fileForImport);
				InputStreamReader isr = new InputStreamReader(fis, charEncoding);
				in = new BufferedReader(isr);
			}
		} catch (IOException e) {
			decoratedError(INDENT1, "Opening file for import: " + fileForImport, e);
		}

		return in;
	}
	
	/**
	 * Determines the file type from type field of <code>importMeta</code> and executes the import routine for that file type.
	 * Also creates a new record in IMPORT_DEFINITIONS indicating that this file has already been imported.
	 * 
	 * @param currentEvent The EVENTS record currently being processed.
	 * @param currentFile File name of the file to be imported.
	 * @param importMeta Definition of the import strategy for this publisher.
	 * @param in BufferedReader from which to read the file contents.
	 * @param session The current Hibernate persistence context.
	 * @return Whether the file was imported successfully.
	 */
	
	@SuppressWarnings("preview")
	private boolean importFile(EventsDao currentEvent, String currentFile, ImportDefinitionsDao importMeta, BufferedReader in, Session session) {
		
		boolean success = true;
		
		try {
			// Do the import
			success = switch(importMeta.getType()) {
				case "freeform" -> importHandler.doFreeFormImport(currentEvent, importMeta, currentFile, in, session);
				case "xml" -> importHandler.doXmlImport(currentEvent, currentFile, session);
				default -> false;
			};
			
			if (success) {
				// Flag this file as being imported
				FilesImportedDao fileImported = new FilesImportedDao();
				session.beginTransaction();
				fileImported.setFileName(currentFile.toUpperCase());
				fileImported.setDateImported(DateUtil.getDateAtMidnight());
				session.persist(fileImported);
				session.getTransaction().commit();
			}
		} catch (Exception e) {
			decoratedError(INDENT1, "Importing newspaper file.", e);
		}
		
		// Create NewsItem here

		return success;
	}
	
	/**
	 * Move the import file to the processed directory.
	 * 
	 * @param currentFile File currently being processed.
	 * @param fileForImport Full path name of file currently being processed.
	 * @return Whether the file was moved successfully.
	 */
	
	private boolean moveFile(String currentFile, String fileForImport) {
		
		boolean success;
		
		// Move this file elsewhere
		String newFileName="";
		String moveTo = processedFilesLoc;
		
		newFileName = moveTo.endsWith(sep) ? moveTo + currentFile : moveTo + sep + currentFile;

		if (fileForImport.length() > 0) {
			File f = new File(fileForImport);
			File newFile = new File(newFileName);

			// Delete the destination file if it still exists
			if (newFile.exists()) newFile.delete();

			// Rename the file just imported
			if (!f.renameTo(newFile)) {
				IOException e = new IOException("Unable to move file from export directory to: " + newFile);
				decoratedError(INDENT1, "Moving newspaper file.", e);
				success = false;
			}
		}
		
		return false;
	}
	
	private boolean doFreeFormImport(BufferedReader in) {
		
		return true;
	}
	
	private boolean doXmlImport(BufferedReader in) {
		
		return true;
	}

}
