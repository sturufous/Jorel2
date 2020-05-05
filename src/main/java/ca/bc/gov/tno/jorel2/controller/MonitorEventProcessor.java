package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
import ca.bc.gov.tno.jorel2.model.NewsItemQuotesDao;
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
	Jorel2Instance instance;
	
	/** Maximum age of files for import in hours */
	@Value("${importFileHours}")
	private String importFileHoursStr;
	
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
	
	private void monitorEvent(EventsDao currentEvent, Session session) {

		//setCountFilesImported(0);

		//String userDir = System.getProperty("user.dir");
		String fileSep = System.getProperty("file.separator");
		String startTimeStr = currentEvent.getStartTime();
		if (startTimeStr == null) startTimeStr = "00:00:00";

		LocalDateTime now = LocalDateTime.now();
		if (startTimeStr.length() == 8 && startTimeStr.indexOf(":") > 0) {
			String startHoursMinutes = startTimeStr.substring(0, 5);
			String nowHoursMinutes = "02:30"; // String.format("%02d:%02d", now.getHour(), now.getMinute());

			if (nowHoursMinutes.equals(startHoursMinutes)) {

				// Block sync and other monitor events
				//frame.indexBlockSet();

				try {

					// Monitors the directory
					String dirName = currentEvent.getFileName();
					String definitionName = currentEvent.getDefinitionName();
					boolean triggerImport = currentEvent.getTriggerImport();
					String charEncoding = currentEvent.getTitle();
					// frame.addJLog("Monitor Scan directory '"+dirName+"' ["+startHour+":"+startMinute+"] at "+
					// calendar.get(Calendar.HOUR_OF_DAY)+":"+calendar.get(Calendar.MINUTE));

					NewsItemQuotesDao quotes = null;
					FilesImportedDao filesImported = null;

					File dir = new File(dirName);
					if (dir.isDirectory()) {							
						boolean importedOne=true;
						int importFileHours = Integer.valueOf(importFileHoursStr);
						while (importedOne) {
							//int sc=getCountFilesImported(); // start count of number of files imported

							List<String> fileList = new ArrayList<>(Arrays.asList(dir.list()));
							
							for (String currentFile : fileList) {
								String file4Import = "";
								if (!dirName.endsWith(fileSep))
									file4Import = dirName + fileSep + currentFile;
								else
									file4Import = dirName + currentFile;

								File f = new File(file4Import);

								boolean fileOK=true;

								if (fileIsValid(f, importFileHours, file4Import)) {

									// Make sure the file is completely downloaded
									long size = 0;
									long oldSize = 0;
									int count = 0;
									int wait = 1000;
									boolean moveFile = false;
									String moveFilePrefix = "";
									PreferencesDao preferences = instance.getPreferences();

									// Move this file into the database or just move it around the file system
									// THIS LOOKS SUSPICIOUSLY LIKE IT'S NEVER USED
									boolean storeITinOracle=false;
									if ((size/1024) < preferences.getMinBinarySize().longValue()) storeITinOracle=true;
									//if (System.getProperty("java.version").startsWith("1.1")) storeITinOracle=false;

									// Make sure this file has not already been imported
									List<FilesImportedDao> imported = FilesImportedDao.getFilesImportedByFileName(currentFile, session);
									boolean ok = imported.size() == 0;
									
									
									if (ok) {

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

										// Zip file
										if (currentFile.toLowerCase().endsWith(".zip")){

											if (definitionName.equalsIgnoreCase("infomart")) {
												// Infomart image zip file
												moveFile = infomartImages(currentFile, file4Import);
											} else {
												moveFile = true;

											}

											// Jpg file
										} else	if (currentFile.toLowerCase().endsWith(".jpg")) {

											if (definitionName.equalsIgnoreCase("Globe and Mail")) {
												// Globe image file
												moveFile = gandmImage(currentFile, file4Import);
											}

											// Not a zip or jpg file. Process as normal.

											// PDF file
										} else	if (currentFile.toLowerCase().endsWith(".pdf")) {

											if (definitionName.equalsIgnoreCase("Vancouver 24 hrs")) {
												// Globe image file
												moveFile = van24Image(currentFile, file4Import);
												//moveFile = true;
											}

											// Not a zip or jpg file. Process as normal.
										} else {

											moveFile = true;

											// globe and mail fudge to add CDATA tags
											if (definitionName.equalsIgnoreCase("Globe and Mail XML")) {
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
												moveFile = false; // don't move the G&M files
											}

											//setImportingNow(true);
											//doImport importFile = new doImport(frame, this, dirName, s[i], storeITinOracle, definitionName, charEncoding, triggerImport, quotes, moveFile );
											//importFile.start();
											moveFile = false; // the doimport procedure will have moved this file

											//do the import single file style for now
											//while (isImportingNow()) {
											//	try { Thread.sleep(1000*10); }
											//	catch (InterruptedException e) { if(false) System.out.println("Thread was interrupted: " + e); }
											//}

										}

									} else {
										if (!definitionName.equalsIgnoreCase("Globe and Mail XML")) {
											//frame.addJLog(eventLog("DailyFunctions.monitorEvent(): File already processed "+s[i]), true);
											moveFile = true;
											moveFilePrefix = "fap_";
										}
									}

									// Move this file elsewhere
									if(moveFile)
									{
										//movePaperFile(f, fileSep, moveFilePrefix);
									}
								} // if (fileOk)
							} // for (int i=0; i<s.length; i++)

							// Any files imported??
							//int ec=getCountFilesImported();   // end count of files imported
							//if (sc == ec) importedOne=false; else importedOne=true;
						} // while (importedOne)

					} //if (dir.isDirectory())
					} catch (Exception ex) { 
						//frame.addJLog(eventLog("doImport.run(): unknown error: "+ex.getMessage()));
					}
	
				// Remove block
				//frame.indexBlockRemove();
	
			} // if ((startHour == 0) | ...

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
	
	private boolean  infomartImages(String one, String two) {
		
		return true;
	}
	
	private boolean  gandmImage(String one, String two) {
		
		return true;
	}
	
	private boolean  van24Image(String one, String two) {
		
		return true;
	}
	
	/**
	 * Determines the validity of the import file based on several criteria. If the file fails any of the tests 
	 * this method returns true.
	 * 
	 * @param f Abstract representation of the file name representing the file in question.
	 * @param importFileHours Length of time since this file was last modified.
	 * @param currentFile The string representation of this file's path name.
	 * @return Whether this file should be skipped.
	 */
	
	private boolean fileIsValid(File f, int importFileHours, String currentFile) {
		
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
}
