package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import ca.bc.gov.tno.jorel2.model.NewsItemQuotesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
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
	
	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private FrontPageImageHandler imageHandler;
	
	/** Maximum age of files for import in hours */
	@Value("${importFileHours}")
	private String importFileHoursStr;
	
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
		File dir = new File(dirName);
		
		if (nowHoursMinutes.equals(startHoursMinutes) && dir.isDirectory()) {
			String definitionName = currentEvent.getDefinitionName();
			boolean triggerImport = currentEvent.getTriggerImport();

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

						moveFile = performMediaTypeSwitching(currentFile, fileForImport, definitionName, f, session);

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
	private boolean performMediaTypeSwitching(String currentFile, String fileForImport, String definitionName, File f, Session session) {
		// Make sure the file is completely downloaded
		boolean moveFile = false;
		String moveFilePrefix = "";
		PreferencesDao preferences = instance.getPreferences();

		// Move this file into the database or just move it around the file system
		// THIS LOOKS SUSPICIOUSLY LIKE IT'S NEVER USED
		boolean storeITinOracle=false;
		//if ((size/1024) < preferences.getMinBinarySize().longValue()) storeITinOracle=true;
		//if (System.getProperty("java.version").startsWith("1.1")) storeITinOracle=false;

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
				default -> processNewsItem(currentFile, fileForImport, definitionName, f, suffix);
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

	/** Manages the extraction of front page images from Pdf files. Currently this format is used exclusively by Vancouver 24 hrs (which may 
	 * no longer be needed. Consider this incomplete and untested.
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
	
	private boolean processNewsItem(String currentFile, String fileForImport, String definitionName, File f, String suffix) {

		// globe and mail fudge to add CDATA tags
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

		//setImportingNow(true);
		//doImport importFile = new doImport(frame, this, dirName, s[i], storeITinOracle, definitionName, charEncoding, triggerImport, quotes, moveFile );
		//importFile.start();
		//moveFile = false; // the doimport procedure will have moved this file

		//do the import single file style for now
		//while (isImportingNow()) {
		//	try { Thread.sleep(1000*10); }
		//	catch (InterruptedException e) { if(false) System.out.println("Thread was interrupted: " + e); }
		//}
		
		return false;

	}
	
	
	@SuppressWarnings("preview")
	private boolean doImport(String sourceName, String currentFile, boolean triggerImport, Session session) {
		File bFile = null;
		FileInputStream bin = null;
		BufferedReader in=null;
		String importFileName="";
		String charEncoding = "";
		boolean success = true;

		//dbImport_Definitions imports = new dbImport_Definitions(frame);
		//ResultSet importsRS = imports.select(getImport_definition());
		// select * from tno.import_definitions where name = ?
		List<ImportDefinitionsDao> definitions = ImportDefinitionsDao.getEventByRsn(sourceName, session);
		if (definitions.size() > 0) {
			ImportDefinitionsDao importMeta = definitions.get(0);

			try {
				String dirName = currentFile; //getFilePath();
				String fileForImport = dirName.endsWith(sep) ? dirName + currentFile : dirName + sep + currentFile;

				// CLOB and BLOB files are opened so the data can be streamed directly to the CLOB or BLOB
				if ((importMeta.getType().equalsIgnoreCase("text")) | (importMeta.getType().equalsIgnoreCase("binary"))) {
					bFile = new File(importFileName);
					bin = new FileInputStream(bFile);
				} else {
					// text files require a 1.2 jre so that CLOBs can be imported
					if (charEncoding.equals("")) { // no character encoding provided?
						FileReader file = new FileReader(importFileName);
						in = new BufferedReader(file);
					} else { // open with a particular character encoding
						FileInputStream fis = new FileInputStream(importFileName);
						InputStreamReader isr = new InputStreamReader(fis, charEncoding);
						in = new BufferedReader(isr);
					}
				}
			} catch (IOException err) {
				if(false) System.out.println("IOException "+err);
			}

			// Create the news item object and import data into it.
			//NewsItemsDao newsItem = NewsItemFactory.create
			dbNews_Items newsItem = new dbNews_Items(frame);
			//String msg=newsItem.getLastError();
			//if (msg.length() <= 1) {
			//	newsItem.initialize();

				// Import the data only if the trigger is on
			if (triggerImport) {

				// Flag this file as being imported
				//dbFiles_Imported filesImported = new dbFiles_Imported(frame);
				
				FilesImportedDao filesImported = new FilesImportedDao();
				session.beginTransaction();
				filesImported.setFileName(currentFile);
				session.getTransaction().commit();

				String importType = importMeta.getType();
				
				// Do the import
				success = switch(importType) {
					case "freeform" -> doFreeFormImport();
					case "xml" -> doXmlImport();
					default -> false;
				};
			}

			// Close the file
			try {
				if ((imports.getType().equalsIgnoreCase("text")) | (imports.getType().equalsIgnoreCase("binary"))) {
					if (imports.getType().equalsIgnoreCase("text")) Bin.close();
					Bfile.delete();

				} else {
					if (!imports.getType().equalsIgnoreCase("xml")) {
						try { in.close(); } catch (IOException e) {;}
					}
					dailyFunctions.incCountFilesImported();

					if (moveFile) {
						// Move this file elsewhere
						String newFileName="";
						String moveTo = frame.getProcessedFiles();
						String sep = System.getProperty("file.separator");
						if ( moveTo.charAt(moveTo.length()-1) == sep.charAt(0) )
							newFileName = moveTo + getFileName();
						else
							newFileName = moveTo + sep + getFileName();
	
						if (importFileName.length() > 0) {
							File f = new File(importFileName);
							File newFile = new File(newFileName);
	
							// Delete the destination file if it still exists
							if (newFile.exists()) newFile.delete();
	
							// Rename the file just imported
							if (f.renameTo(newFile)) {
								frame.addJLog(dailyFunctions.eventLog("doImport.run(): File renamed to "+newFileName));
							} else {
								frame.addJLog(dailyFunctions.eventLog("doImport.run(): Error renaming file to "+newFileName));
							}
						}
					}
				}
			} catch (IOException err) { ; }
			newsItem.destroy();
			newsItem=null;

			frame.addJLog(dailyFunctions.eventLog("doImport.run(): File imported '"+getFileName()+"'"));
		}
		try { importsRS.close(); } catch (SQLException err) {;}
		imports.destroy();
		imports=null;

		dailyFunctions.setImportingNow(false);	
	}
	
	private boolean doFreeFormImport() {
		
		return true;
	}
	
	private boolean doXmlImport() {
		
		return true;
	}

}
