package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * Processes the list of Capture events for this Jorel2 instance as described in the EVENTS table.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class CaptureEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/** The home directory of the current user */
	private String userDir = "";
	
	/** The system's file separator. */
	private String fileSep = "";
	
	/** The full path to the directory containing offline capture command files */
	private String offlineDirPath = "";
	
	/** java.io.File object used to list the offline files to be processed */
	private File offlineDir = null;
	
	/**
	 * Initializes the instance variables used to retrieve text files that describe offline capture commands. 
	 */
	@PostConstruct
	private void init() {
		
		userDir = System.getProperty("user.dir");
		fileSep = System.getProperty("file.separator");
		offlineDirPath = userDir + fileSep + "offline" + fileSep;
		offlineDir = new File(offlineDirPath);
		if (!offlineDir.isDirectory()) offlineDir = null;
		logger.trace("Capture Event Processor: Setting offline directory to: " + offlineDir);
	}
	
	/**
	 * Process all eligible ShellCommand events from the EVENTS table.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
		
    	try {
    		if (instance.isExclusiveEventActive(EventType.CAPTURE)) {
    			decoratedTrace(INDENT1, "Capture event processing already active. Skipping.");    			
    		} else {
    			instance.addExclusiveEvent(EventType.CAPTURE);
    			decoratedTrace(INDENT1, "Starting Capture event processing");
	    		
		        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
		        
		        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
		        for (Object[] entityPair : results) {
		        	if (entityPair[0] instanceof EventsDao) {
		        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        			setThreadTimeout(runnable, currentEvent, instance);
		        		
		        		captureEventOnline(currentEvent, session);
		        	}
		        }
		        
		        instance.removeExclusiveEvent(EventType.CAPTURE);
	    	} 
    	}
    	catch (Exception e) {
    		instance.removeExclusiveEvent(EventType.CAPTURE);
    		logger.error("Processing Capture entries.", e);
    	}
    	
    	decoratedTrace(INDENT1, "Completing Capture event processing");
    	return Optional.of("complete");
	}
	
	/**
	 * Executes the capture event described by the <code>captureEvent</code> parameter. 
	 * 
	 * @param captureEvent Entity describing the event to run locally.
	 * @param session The current Hibernate persistence context.
	 */
	private void captureEventOnline(EventsDao captureEvent, Session session) {

		Capture capture = new Capture(captureEvent, session);

		PrintWriter offlineWriter = null;
		if (offlineDir != null) {
			try {
				offlineWriter = new PrintWriter(offlineDir.getPath() + System.getProperty("file.separator") + "capturecmd_" + capture.eventName + ".txt");
			} catch (Exception ex) {
				offlineWriter = null;
			}
		}

		capture.doCapture(captureEvent, session); // do the command (if it is time)
		capture.writeOffline(offlineWriter); // write to the offline cache

		if (offlineWriter != null) {
			try {
				offlineWriter.close();
			} catch (Exception ex) { ; }
		}
	}
	
	/**
	 * Executes capture command events when the connection to the database is down. The commands are read from files in the <code>offline</code>
	 * directory which have the following naming convention: <code>capturecmd_[event-name].txt</code>. These files are created when 
	 * <code>captureCommandEventOnline()</code> is executed. 
	 */
	public void captureEventOffline(Session session) {

		decoratedTrace(INDENT1, "Starting offline ShellCommand event processing");
		
		if (instance.isExclusiveEventActive(EventType.CAPTURE)) {
			decoratedTrace(INDENT1, "ShellCommand event processing already active. Skipping."); 
		} else {
			try {
    			instance.addExclusiveEvent(EventType.CAPTURE);
				for(File offlineFile : offlineDir.listFiles()) {
					
					if (offlineFile.getName().startsWith("capturecmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						Capture capture = new Capture(adq, session);
		
						PrintWriter offlineWriter = null;
						if (offlineDir != null) {
							try {
								offlineWriter = new PrintWriter(offlineDir.getPath() + System.getProperty("file.separator") + "capturecmd_" + capture.eventName + ".txt");
							} catch (Exception ex) {
								offlineWriter = null;
							}
						}
		
						capture.doCapture(null, null); // do the command (if it is time)
						capture.writeOffline(offlineWriter); // write to the offline cache
		
						if (offlineWriter!=null) {
							try {
								offlineWriter.close();
							} catch (Exception ex) { ; }
						}
					}
				}
    			instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
			}
			catch (Exception e) {
    			instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
	    		logger.error("While processing offline capture command.", e); 				
			}
		}
		
		decoratedTrace(INDENT1, "Completing offline Capture event processing");
	}
		
	/**
	 * Loads the lines of text contained in the offLine file into the Queue variable for storage in a ShellCommand object.
	 * 
	 * @param offlineFile A java.io.File object identifying the file, in the offline directory, that contains the command.
	 * @param adq The queue into which the lines are loaded.
	 */
	private void loadOffline(File offlineFile, ArrayDeque<String> adq) {
		try {
			// load a file into a queue of strings
			BufferedReader br = new BufferedReader(new FileReader(offlineFile));
			try {
				String line = br.readLine();
				while (line != null) {
					adq.addLast(line);
					line = br.readLine();
				}
			} finally {
				br.close();
			}
		} catch (Exception ex) {
			logger.error("Error opening offline file '" + offlineFile.getName() + "'", ex);
		}
	}
	
	/**
	 * Updates the lastFtpRun field for all ShellCommand events in the EVENTS table that correspond with files in the 
	 * offline directory. This ensures that, if the command was processed while the database connection was down, the
	 * command is not run again based on an inaccurate last-run date.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	public void captureCommandEventUpdate(Session session) {

		try {
			if (instance.isExclusiveEventActive(EventType.SHELLCOMMAND)) {
				decoratedTrace(INDENT1, "ShellCommand event processing already active. Skipping."); 
			} else {
				instance.addExclusiveEvent(EventType.SHELLCOMMAND);
				decoratedTrace(INDENT2, "Updating lastFtpRun field for all commands in offline directory: " + offlineDir);
				
				for(File offlineFile: offlineDir.listFiles()) {
					if (offlineFile.getName().startsWith("capturecmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						Capture capture = new Capture(adq, session);
		
						decoratedTrace(INDENT1, "Update capture event, set lastFtpRun='" + capture.lastFtpRun + "' for rsn=" + capture.rsn);
		
						// update event
						EventsDao captureEvt = EventsDao.getEventByRsn(capture.rsn, session).get(0);
						captureEvt.setLastFtpRun(capture.lastFtpRun);
						session.getTransaction().begin();
						session.persist(captureEvt);
						session.getTransaction().commit();
					}
				}
				
				instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
			}
		}
		catch (Exception e) {
			instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
    		logger.error("While post-processing capture event after network reconnect.", e); 							
		}
	}
	
	// create a full file path based on date and source for use in captureEvent()
	// does NOT include file extension
	private String clipPath(Calendar cal, String source, String name, Session session) {
		String path;

		source = source.replace(' ', '_').replaceAll("[^a-zA-Z0-9\\_]", "");		
		name = name.replace(' ', '_').replaceAll("[^a-zA-Z0-9\\_]", "");		

		String xyear = "00" + (cal.get(Calendar.YEAR) % 100);
		xyear = xyear.substring(xyear.length()-2);
		String xmonth = "00" + (cal.get(Calendar.MONTH) + 1);
		xmonth = xmonth.substring(xmonth.length()-2);
		String xday = "00" + cal.get(Calendar.DAY_OF_MONTH);
		xday = xday.substring(xday.length()-2);
		String xhour = "00" + cal.get(Calendar.HOUR_OF_DAY);
		xhour = xhour.substring(xhour.length()-2);
		String xminute = "00" + cal.get(Calendar.MINUTE);
		xminute = xminute.substring(xminute.length()-2);
		String xsecond = "00" + cal.get(Calendar.SECOND);
		xsecond = xsecond.substring(xsecond.length()-2);

		// directory
		path = instance.getStorageBinaryRoot() + "/" + xyear + xmonth + xday + "/" + source.replace(' ', '_').replace('.', '_') + "/";

		// create directory if neccessary
		File dirTarget = new File(path);
		if (!dirTarget.isDirectory()) {
			try {
				if (!(dirTarget.mkdirs())) {
					decoratedTrace(INDENT2, "DailyFunctions.captureEvent(): Could not create directory '"+path+"'", session);
				}
			} catch (Exception ex) {
				decoratedError(INDENT0, "DailyFunctions.captureEvent(): Exception creating directory '"+path+"': '", ex);
			}
		}

		// file name
		path = path + source;		
		if (!name.equals("")) {
			path = path + "_" + name;
		}
		path = path + "_" + xyear + xmonth + xday + "_" + xhour + xminute + xsecond;

		return path;
	}
		
	private class Capture {
		BigDecimal rsn = null;
		String cmd = "";
		String eventName = "";
		String clipCmd = "";
		boolean ccCapture = false;
		String title = "";
		String type = "";
		String channel = "";
		String source = "";
		String frequency = "";
		String startTime = "";
		String stopTime = "";
		String launchTime = "";
		String lastFtpRun = "";

		String fullOutputFilename = "";
		String fullOutput = "";
		Calendar startCal; // when this event starts next
		Calendar stopCal; // when this event stops next
		Calendar launchCal; // when this event was launched
		String streamOutput = ""; // full path to the streaming file if stored on the NAS (minus extension)
		String streamFile = ""; // file name of the streaming file (minus extension)
		String nasFolder = ""; 
		String streamLocal = ""; // full path to the streaming file if stored locally (minus extension)	

		private Capture(EventsDao captureEvt, Session session) {
			rsn = captureEvt.getRsn();
			eventName = captureEvt.getName();
			cmd = captureEvt.getCaptureCommand();
			clipCmd = captureEvt.getClipCommand();
			ccCapture = captureEvt.getCcCapture();
			title = captureEvt.getTitle();
			type = captureEvt.getFileName();
			channel = captureEvt.getChannel();
			source = captureEvt.getSource();
			frequency = captureEvt.getFrequency();
			startTime = captureEvt.getStartTime();
			stopTime = captureEvt.getStopTime();
			launchTime = captureEvt.getLaunchTime();
			lastFtpRun = captureEvt.getLastFtpRun();
			this.setUp(session);
		}

		private Capture(ArrayDeque adq, Session session) {
			try {
				String rsnStr = (String)adq.removeFirst();
				try { rsn = new BigDecimal(rsnStr); } catch (Exception ex) {;}
				eventName = (String)adq.removeFirst();
				cmd = (String)adq.removeFirst();
				clipCmd = (String)adq.removeFirst();
				String ccCaptureStr = (String)adq.removeFirst();
				try { ccCapture = Boolean.parseBoolean(ccCaptureStr); } catch (Exception ex) {;}
				title = (String)adq.removeFirst();
				type = (String)adq.removeFirst();
				channel = (String)adq.removeFirst();
				source = (String)adq.removeFirst();
				frequency = (String)adq.removeFirst();
				startTime = (String)adq.removeFirst();
				stopTime = (String)adq.removeFirst();
				launchTime = (String)adq.removeFirst();
				lastFtpRun = (String)adq.removeFirst();
				this.setUp(session);
			} catch (Exception ex) { 
				decoratedError(INDENT0, "Error reading offline capture data", ex); 
			}
		}

		void setUp(Session session) {
			// full path to the capture file
			fullOutputFilename = this.channel.replace(' ', '_').replace('/', '_').replaceAll("[^a-zA-Z0-9\\_]", "");
			fullOutput = instance.getStorageCaptureDir() + "/" + fullOutputFilename + ".mpg";

			startCal = DateUtil.createTime(this.startTime, this.frequency); // when this event starts next
			stopCal = DateUtil.createTime(this.stopTime, this.frequency); // when this event stops next
			launchCal = DateUtil.createTime(this.launchTime, this.frequency); // when this event was launched

			// full file path for the stream + create directory if neccessary
			streamOutput = clipPath(startCal, this.source, "full", session); // full path to the streaming file if stored on the NAS (minus extension)
			streamFile = new File(streamOutput).getName(); // file name of the streaming file (minus extension)
			nasFolder = new File(streamOutput).getParent(); 
			streamLocal = instance.getStorageCaptureDir() + "/" + streamFile; // full path to the streaming file if stored locally (minus extension)	
		}

		void writeOffline(PrintWriter offlineWriter) {
			if (offlineWriter!=null) {
				try {
					offlineWriter.println(rsn);
					offlineWriter.println(eventName);
					offlineWriter.println(cmd);
					offlineWriter.println(clipCmd);
					offlineWriter.println(ccCapture);
					offlineWriter.println(title);
					offlineWriter.println(type);
					offlineWriter.println(channel);
					offlineWriter.println(source);
					offlineWriter.println(frequency);
					offlineWriter.println(startTime);
					offlineWriter.println(stopTime);
					offlineWriter.println(launchTime);
					offlineWriter.println(lastFtpRun);
				} catch (Exception ex) { ; }
			}
		}

		void doCapture(EventsDao captureEvt, Session session) {
			
			String now = DateUtil.localDateToTnoDateFormat(LocalDate.now());
			
			// has not run today --- add space because we want to check this event (for clips) even if it has run already
			//  or the channel file does not exist - probably because of restart
			if ( (!this.lastFtpRun.equals(now + " ")) || (!new File(this.fullOutput).exists()) ) { 

				long startMS = this.startCal.getTime().getTime(); // system milliseconds at which this should start
				long nowMS = (new java.util.Date()).getTime(); // current system milliseconds
				long seconds = (startMS - nowMS) / 1000;
				if (seconds<0) seconds = 0;

				//frame.addJLog(eventLog("Capture event '"+cmd+"' to execute in "+seconds+" seconds"), true);

				// Is it time to start this event?
				if ( seconds < 120 ) { // less than two minutes until start time

					nowMS = (new java.util.Date()).getTime(); // current system milliseconds
					long startSec = (this.startCal.getTime().getTime() - nowMS)/1000; // seconds from now until start
					long stopSec = (this.stopCal.getTime().getTime() - nowMS)/1000; // seconds from now until stop
					if (startSec<0) startSec = 0;
					long duration = (stopSec - startSec);

					boolean launch_ok = false;

					String cmd = this.cmd;
					if (!cmd.equals("")) {

						cmd = cmd.replace("[channel]", this.channel);
						cmd = cmd.replace("[capture]", this.fullOutput);
						cmd = cmd.replace("[output]", this.fullOutput);
						cmd = cmd.replace("[start]", ""+startSec);
						cmd = cmd.replace("[duration]", ""+duration);
						cmd = cmd.replace("[stream]", this.streamOutput);
						cmd = cmd.replace("[streamlocal]", this.streamLocal);
						cmd = cmd.replace("[streamfile]", this.streamFile);
						cmd = cmd.replace("[nas]", this.nasFolder);

						if (seconds > 0) this.cmd = "sleep "+seconds+"; "+cmd;

						String[] cmda = {
								"/bin/sh",
								"-c",
								cmd
						};

						try {
							Process p=new ProcessBuilder(cmda).start();
						} catch (Exception e) {
							decoratedError(INDENT0, "DailyFunctions.captureEvent(): Exception launching capture command '" + cmd + "': '" + e.getMessage() + "'", e);
						}

						launch_ok = true;

						decoratedTrace(INDENT2, "Capture command executed '" + cmd + "'", session);
					}

					// capture cc
					if (this.ccCapture) {
						String ccCmd = "ccextractor -i " + this.fullOutput + " -s -out=bin -o " + this.fullOutput + ".cc &> /dev/null";
						ccCmd = "sleep " + (seconds + 30) + "; " + ccCmd; // start a little bit later - it can catch up

						String[] cmda = {
								"/bin/sh",
								"-c",
								ccCmd
						};

						try {
							Process p2 = new ProcessBuilder(cmda).start();
						} catch (Exception e) {
							decoratedError(INDENT0, "DailyFunctions.captureEvent(): Exception launching cc command '" + ccCmd + "': '", e);
						}

						decoratedTrace(INDENT2, "CC command executed '" + ccCmd+"'", session);
					}

					if (launch_ok) {
						this.launchCal = Calendar.getInstance();
						this.launchCal.setTimeInMillis(nowMS+seconds*1000);
						long launchSecs = this.launchCal.get(Calendar.HOUR_OF_DAY)*3600+this.launchCal.get(Calendar.MINUTE)*60+this.launchCal.get(Calendar.SECOND);
						this.launchTime = DateUtil.secs2Time(launchSecs);
					}

					this.lastFtpRun = now + " ";

					if (captureEvt!=null) {
						//Update this record to reflect that it has run
						if (launch_ok)
							captureEvt.setLaunchTime(this.launchTime);
						captureEvt.setLastFtpRun(this.lastFtpRun);
						session.beginTransaction();
						session.persist(captureEvt);
						session.getTransaction().commit();
					}

				} // if time to start

			} // not run today
		}
	}
}