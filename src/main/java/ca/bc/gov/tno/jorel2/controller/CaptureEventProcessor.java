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
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventClipsDao;
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
	 * Process all eligible Capture events from the EVENTS table.
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
		        		
		        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
		        			captureEventOnline(currentEvent, session);
		        		}
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
	 * Executes the capture event described by the <code>captureEvent</code> parameter and any associated clip events. This method opens a PrintWriter 
	 * to a file in the offline directory and writes the details of any capture and clip events to it for consumption by the <code>captureEventsOffline()</code> 
	 * method. The capture event is executed first, and its <code>launchTime</code> attribute may be used by any associated clips to determine 
	 * whether the content on which they rely has been captured. The clips associated with this capture event are retrieved from an 
	 * <code>EventClipsDao</code> object.
	 * 
	 * @param captureEvent Entity describing the event to run.
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
		
		List<EventClipsDao> results = EventClipsDao.getClipsByEventRsn(captureEvent.getRsn(), session);
		
		for (EventClipsDao clipEntry : results) {
			Clip clip = new Clip(clipEntry);
			clip.doClip(capture, clipEntry, session); // do the clip (if it is time)
			clip.writeOffline(offlineWriter); // write to the offline cache
		}

		if (offlineWriter != null) {
			try {
				offlineWriter.close();
			} catch (Exception ex) { ; }
		}
	}
	
	/**
	 * Executes capture command events when the connection to the database is down. The commands are read from files in the <code>offline</code>
	 * directory which have the following naming convention: <code>capturecmd_[event-name].txt</code>. These files are created when 
	 * <code>captureEventOnline()</code> is executed. This method opens a PrintWriter to the file and writes the details of any capture and clip 
	 * events to it for later consumption by the <code>captureEventsOffline()</code> method.
	 * 
	 * The last-run times, for capture and clip, and the launch-time for capture events are written to the file to maintain synchronization
	 * between consecutive thread executions. The capture event is executed first, and its <code>launchTime</code> attribute may be used by any 
	 * associated clips to determine whether the content on which they depend has been captured. The clips associated with a capture event are 
	 * retrieved from the <code>EVENT_CLIPS</code> table.
	 */
	public void captureEventOffline() {

		decoratedTrace(INDENT1, "Starting offline Capture event processing");
		
		if (instance.isExclusiveEventActive(EventType.CAPTURE)) {
			decoratedTrace(INDENT1, "Capture event offline processing already active. Skipping."); 
		} else {
			try {
    			instance.addExclusiveEvent(EventType.CAPTURE);
				for(File offlineFile : offlineDir.listFiles()) {
					
					if (offlineFile.getName().startsWith("capturecmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						Capture capture = new Capture(adq, null);
		
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
						
						// get clips
						Clip clip = new Clip(adq);
						while (clip.valid) {
							clip.doClip(capture, null, null); // do the clip (if it is time)
							clip.writeOffline(offlineWriter); // write to the offline cache
							clip = new Clip(adq);
						}
		
						if (offlineWriter!=null) {
							try {
								offlineWriter.close();
							} catch (Exception ex) { ; }
						}
					}
				}
    			instance.removeExclusiveEvent(EventType.CAPTURE);
			}
			catch (Exception e) {
    			instance.removeExclusiveEvent(EventType.CAPTURE);
	    		logger.error("While processing offline capture command.", e); 				
			}
		}
		
		decoratedTrace(INDENT1, "Completing offline Capture event processing");
	}
		
	/**
	 * Loads the lines of text contained in the offLine file into the ArrayDeque variable for storage in a Capture object. Any clip entries
	 * in this file will also be loaded and subsequently stored in Clip objects.
	 * 
	 * @param offlineFile A java.io.File object identifying the file containing the Capture event and its clips.
	 * @param adq The queue into which the lines are to be loaded.
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
	 * Updates the lastFtpRun field for all Capture events in the EVENTS table that correspond with files in the 
	 * offline directory. This ensures that, if the command was processed while the database connection was down, the
	 * command is not run again based on an inaccurate last-run date.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	public void captureCommandEventUpdate(Session session) {

		try {
			if (instance.isExclusiveEventActive(EventType.CAPTURE)) {
				decoratedTrace(INDENT1, "Offline Capture event post-processing already active. Skipping."); 
			} else {
				instance.addExclusiveEvent(EventType.CAPTURE);
				decoratedTrace(INDENT2, "Updating lastFtpRun field for all Capture commands in offline directory: " + offlineDir);
				
				for(File offlineFile: offlineDir.listFiles()) {
					if (offlineFile.getName().startsWith("capturecmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						Capture capture = new Capture(adq, session);
		
						decoratedTrace(INDENT2, "Update capture event, set lastFtpRun='" + capture.lastFtpRun + "' for rsn = " + capture.rsn);
		
						// update event
						EventsDao captureEvt = EventsDao.getEventByRsn(capture.rsn, session).get(0);
						captureEvt.setLastFtpRun(capture.lastFtpRun);
						session.getTransaction().begin();
						session.persist(captureEvt);
						session.getTransaction().commit();
					}
				}
				
				instance.removeExclusiveEvent(EventType.CAPTURE);
			}
		}
		catch (Exception e) {
			instance.removeExclusiveEvent(EventType.CAPTURE);
    		logger.error("While post-processing capture event after network reconnect.", e); 							
		}
	}
	
	/**
	 *  Create a full file path based on date and source for use in captureEvent(). Also create any directories in this path that do not exist
	 *  at the time of execution. The path does NOT include file extension.
	 *  
	 * @param cal Calendar date to use when constructing the directory names and file name.
	 * @param source The value of the SOURCE column in the current Capture event.
	 * @param name The value of the NAME column in the current Capture event.
	 * @return The fully qualified clip path name.
	 */
	private String clipPath(Calendar cal, String source, String name) {
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
					decoratedTrace(INDENT2, "CaptureEventProcessor: Could not create directory '" + path + "'");
				}
			} catch (Exception ex) {
				decoratedError(INDENT0, "CaptureEventProcessor: Exception creating directory '" + path + "': '", ex);
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
		
	/**
	 * Class that encapsulates all the data and functionality relating to a capture event. Objects of this class can be created using two data
	 * sources - either an EventsDao object (representing a record in the EVENTS table) when the database connection is active, or an ArrayDeque
	 * object containing lines of text from a file in the <code>offline</code> directory if Jorel2 is operating in OFFLINE mode.
	 * 
	 * @author StuartM
	 */
	private class Capture {
		BigDecimal rsn = null;
		String cmd = "";
		String eventName = "";
		String clipCmd = "";
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
		
		/**
		 * Create a Capture event from a record in the EVENTS table.
		 * 
		 * @param captureEvt Hibernate object representing the EVENTS record.
		 * @param session The current Hibernate persistence context.
		 */
		private Capture(EventsDao captureEvt, Session session) {
			rsn = captureEvt.getRsn();
			eventName = captureEvt.getName();
			cmd = captureEvt.getCaptureCommand();
			clipCmd = captureEvt.getClipCommand();
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

		/**
		 * Create a Capture event from lines of text imported from a <code>capturecmd_</code> file in the <code>offline</code> directory.
		 * 
		 * @param adq Queue of text lines from which to obtain the event's attributes.
		 * @param session The current Hibernate persistence context, which will be null in this case as Jorel2 is offline.
		 */
		private Capture(ArrayDeque<String> adq, Session session) {
			try {
				String rsnStr = (String) adq.removeFirst();
				try { rsn = new BigDecimal(rsnStr); } catch (Exception ex) {;}
				eventName = (String) adq.removeFirst();
				cmd = (String) adq.removeFirst();
				clipCmd = (String) adq.removeFirst();
				title = (String) adq.removeFirst();
				type = (String) adq.removeFirst();
				channel = (String) adq.removeFirst();
				source = (String) adq.removeFirst();
				frequency = (String) adq.removeFirst();
				startTime = (String) adq.removeFirst();
				stopTime = (String) adq.removeFirst();
				launchTime = (String) adq.removeFirst();
				lastFtpRun = (String) adq.removeFirst();
				this.setUp(session);
			} catch (Exception ex) { 
				decoratedError(INDENT0, "Error reading offline capture data", ex); 
			}
		}

		/**
		 * Initialize file, folder and date/time variables for this Capture event.
		 * 
		 * @param session The current Hibernate persistence context (will be null if Jorel2 is in offline mode).
		 */
		void setUp(Session session) {
			// full path to the capture file
			fullOutputFilename = this.channel.replace(' ', '_').replace('/', '_').replaceAll("[^a-zA-Z0-9\\_]", "");
			fullOutput = instance.getStorageCaptureDir() + "/" + fullOutputFilename + ".mpg";

			startCal = DateUtil.createTime(this.startTime, this.frequency); // when this event starts next
			stopCal = DateUtil.createTime(this.stopTime, this.frequency); // when this event stops next
			launchCal = DateUtil.createTime(this.launchTime, this.frequency); // when this event was launched

			// full file path for the stream + create directory if neccessary
			streamOutput = clipPath(startCal, this.source, "full"); // full path to the streaming file if stored on the NAS (minus extension)
			streamFile = new File(streamOutput).getName(); // file name of the streaming file (minus extension)
			nasFolder = new File(streamOutput).getParent(); 
			streamLocal = instance.getStorageCaptureDir() + "/" + streamFile; // full path to the streaming file if stored locally (minus extension)	
		}

		/**
		 * Writes the attributes of this capture event to the offline file.
		 * 
		 * @param offlineWriter An open PrintWriter object pointing to the offline file for this Capture event.
		 */
		void writeOffline(PrintWriter offlineWriter) {
			if (offlineWriter != null) {
				try {
					offlineWriter.println(rsn);
					offlineWriter.println(eventName);
					offlineWriter.println(cmd);
					offlineWriter.println(clipCmd);
					offlineWriter.println(title);
					offlineWriter.println(type);
					offlineWriter.println(channel);
					offlineWriter.println(source);
					offlineWriter.println(frequency);
					offlineWriter.println(startTime);
					offlineWriter.println(stopTime);
					offlineWriter.println(launchTime);
					offlineWriter.println(lastFtpRun);
					offlineWriter.flush();
				} catch (Exception ex) { ; }
			}
		}

		/**
		 * Determine if this Capture command should run, based on the lastFtpRun date and the start time of the event, and, if so, run it. 
		 * Capture events only run once per day, whereas their associated clips must be checked in each Jorel2 execution cycle. If Jorel2 is online 
		 * the lastFtpRun date is set to the current date, otherwise future offline run cycles retrieve this information from the offline file. If 
		 * the command was successfully executed, the launch time in the current event (or offline file) is updated to the current time. This is 
		 * used by any clip commands to determine if the content relating to the clip is present in the captured video.
		 * 
		 * Capture events, and ShellCommand events, bypass the normal event filtering process by inserting a space after the date written to the
		 * lastFtpRun column of the event, or the offline file. Eligible events are normally filtered, in EventsDao.getElligibleEventsByEventType(), 
		 * based on the value of the lastFtpRun column (e.g. "31-AUG-20"). To qualify, the value of this column must NOT be today's date, allowing 
		 * once-per-day events to be ignored if they have already run today. Some events force eligibility by setting lastFtpRun to "idle" or "?", 
		 * Capture events accomplish this, for example, by setting lastFtpRun to "31-AUG-20 ". The appended space allows all Capture events to appear 
		 * eligible for execution, according to the normal rules, and this doCapture() method will make the determination itself. 
		 * 
		 * This non-standard approach is required because of the one-to-many relationship between Captures and Clips, and the fact that they are both
		 * triggered by a single capture event. If the once-per-day execution of a Capture event were managed in the normal way, its associate Clips 
		 * would never be processed.
		 * 
		 * @param captureEvt Record obtained from the EVENTS table (will be null if Jorel2 is offline)
		 * @param session Current Hibernate persistence context (will be null if Jorel2 is offline)
		 */
		void doCapture(EventsDao captureEvt, Session session) {
			
			String now = DateUtil.localDateToTnoDateFormat(LocalDate.now());
			
			// has not run today --- add space because we want to check this event (for clips) even if it has run already
			//  or the channel file does not exist - probably because of restart
			if ( (!this.lastFtpRun.equals(now + " ")) || (!new File(this.fullOutput).exists()) ) { 

				long startMS = this.startCal.getTime().getTime(); // system milliseconds at which this should start
				long nowMS = (new java.util.Date()).getTime(); // current system milliseconds
				long seconds = (startMS - nowMS) / 1000;
				if (seconds < 0) seconds = 0;

				//frame.addJLog(eventLog("Capture event '"+cmd+"' to execute in "+seconds+" seconds"), true);

				// Is it time to start this event?
				if ( seconds < 120 ) { // less than two minutes until start time

					nowMS = (new java.util.Date()).getTime(); // current system milliseconds
					long startSec = (this.startCal.getTime().getTime() - nowMS) / 1000; // seconds from now until start
					long stopSec = (this.stopCal.getTime().getTime() - nowMS) / 1000; // seconds from now until stop
					if (startSec < 0) startSec = 0;
					long duration = (stopSec - startSec);

					boolean launchOk = false;

					String cmd = this.cmd;
					if (!cmd.equals("")) {

						cmd = cmd.replace("[channel]", this.channel);
						cmd = cmd.replace("[capture]", this.fullOutput);
						cmd = cmd.replace("[output]", this.fullOutput);
						cmd = cmd.replace("[start]", "" + startSec);
						cmd = cmd.replace("[duration]", "" + duration);
						cmd = cmd.replace("[stream]", this.streamOutput);
						cmd = cmd.replace("[streamlocal]", this.streamLocal);
						cmd = cmd.replace("[streamfile]", this.streamFile);
						cmd = cmd.replace("[nas]", this.nasFolder);

						if (seconds > 0) this.cmd = "sleep " + seconds + "; " + cmd;

						String[] cmda = {
								"/bin/sh",
								"-c",
								cmd
						};

						try {
							Process p = new ProcessBuilder(cmda).start();
							launchOk = true;
							decoratedTrace(INDENT2, "Capture command executed '" + cmd + "'", session);
						} catch (Exception e) {
							decoratedError(INDENT0, "CaptureEventProcessor: Exception launching capture command '" + cmd + "': '" + e.getMessage() + "'", e);
							launchOk = false;
						}
					}

					if (launchOk) {
						this.launchCal = Calendar.getInstance();
						this.launchCal.setTimeInMillis(nowMS + seconds * 1000);
						long launchSecs = this.launchCal.get(Calendar.HOUR_OF_DAY) * 3600 + this.launchCal.get(Calendar.MINUTE) * 60 + this.launchCal.get(Calendar.SECOND);
						this.launchTime = DateUtil.secs2Time(launchSecs);
					}

					this.lastFtpRun = now + " ";

					if (captureEvt != null) {
						//Update this record to reflect that it has run
						if (launchOk)
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
	
	/**
	 * Class that encapsulates all the data and functionality relating to a clip event. Objects of this class can be created using two data
	 * sources - either an EventClipsDao object (representing a record in the EVENTS_CLIPS table) when the database connection is active, or an 
	 * ArrayDeque object containing lines of text from a file in the <code>offline</code> directory if Jorel2 is operating in OFFLINE mode.
	 * 
	 * @author StuartM
	 */
	private class Clip {
		BigDecimal rsn = null;
		String name="";
		String frequency="";
		String startTime="";
		String stopTime="";
		String lastRun="";

		boolean valid = true;

		/**
		 * Create a Clip event from a record in the EVENT_CLIPS table.
		 * 
		 * @param captureEvt Hibernate object representing the EVENTS_CLIPS record.
		 * @param session The current Hibernate persistence context.
		 */
		private Clip(EventClipsDao clipData) {
			rsn = clipData.getRsn();
			name = clipData.getName();
			frequency = clipData.getFrequency();
			startTime = clipData.getStartTime();
			stopTime = clipData.getStopTime();
			lastRun = clipData.getLastRun();
		}

		/**
		 * Create a Clip event from lines of text imported from a <code>capturecmd_</code> file in the <code>offline</code> directory.
		 * Because there is a one-to-many relationship between Capture events and Clip events, the Clip attributes are stored as a repeating
		 * set of lines in the offline file, following the attributes of the Capture command. Each clip entry in the file is delimited by a
		 * line containing the string "---". If no such line exists in the offline file the <code>valid</code> attribute of the Clip object
		 * is set to false.
		 * 
		 * @param adq Queue of text lines from which to obtain the event's attributes.
		 * @param session The current Hibernate persistence context, which will be null in this case as Jorel2 is offline.
		 */
		private Clip(ArrayDeque<String> adq) {
			try {

				try {
					String test = "";
					do {
						test = (String) adq.removeFirst();
					} while (!test.equalsIgnoreCase("---"));

				} catch (NoSuchElementException ex) {
					valid = false;
					return;
				}

				String rsnStr = (String) adq.removeFirst();
				try { rsn = new BigDecimal(rsnStr); } catch (Exception ex) {;}
				name = (String) adq.removeFirst();
				frequency = (String) adq.removeFirst();
				startTime = (String) adq.removeFirst();
				stopTime = (String) adq.removeFirst();
				lastRun = (String) adq.removeFirst();
			} catch (Exception ex) { 
				decoratedError(INDENT0, "Error reading offline clip data", ex); 
			}
		}

		/**
		 * Writes the attributes of this Clip event to the offline file.
		 * 
		 * @param offlineWriter An open PrintWriter object pointing to the offline file for this Capture/Clip event.
		 */
		void writeOffline(PrintWriter offlineWriter) {
			if (offlineWriter!=null) {
				try {
					offlineWriter.println("---");
					offlineWriter.println(rsn);
					offlineWriter.println(name);
					offlineWriter.println(frequency);
					offlineWriter.println(startTime);
					offlineWriter.println(stopTime);
					offlineWriter.println(lastRun);
					offlineWriter.flush();
				} catch (Exception ex) { ; }
			}
		}

		/**
		 * Determine if this Clip command should run, based on the lastRun date of the EventClipsDao entry, and, if so, run it. Clip events only run 
		 * once per day, but there may be many Clips associated with each Capture event, so they must all be checked for execution eligibility in each 
		 * Jorel2 run cycle. If Jorel2 is online the lastRun date in the EventClipsDao object is set to the current date, otherwise future offline run 
		 * cycles retrieve this information from the offline file.
		 * 
		 * The date format of EventClips.lastRun is different from that used in the lastFtpRun column of the EVENTS table, and there is no need to
		 * append a space to this value, as is done with the lstFtpRun column of the Capture event. To determine the eligibility of this Clip to
		 * run, it's lastRun value is merely compared with the current date using the format "mmm dd yyyy". If they are the same, the clip is ignored.
		 * 
		 * The Clip will only be run if the stop-time of the clip has passed and the entire duration of the clip is since the launchTime of it's 
		 * parent Capture event.
		 * 
		 * @param capture The parent Capture event for this Clip.
		 * @param clipEntry Record obtained from the EVENT_CLIPS table (will be null if Jorel2 is offline)
		 * @param session Current Hibernate persistence context (will be null if Jorel2 is offline)
		 */
		void doClip(Capture capture, EventClipsDao clipEntry, Session session) {
			
			String now = DateUtil.getDateNow();
			boolean success = true;
			
			if (!this.lastRun.equalsIgnoreCase(now)) { // already run?

				Calendar clipStartCal = DateUtil.createTime(this.startTime, this.frequency); // when this clip starts next
				Calendar clipStopCal = DateUtil.createTime(this.stopTime, this.frequency); // when this clip stops next

				long clipStopMS = clipStopCal.getTime().getTime(); // system milliseconds at which this should stop
				long nowMS = (new java.util.Date()).getTime(); // current system milliseconds
				//long fudgeMS = (long)((clipStopMS - clipStartCal.getTime().getTime())*((float)frame.getClipHeadstartPcnt()/100));
				long fudgeMS = (long)((clipStopMS - clipStartCal.getTime().getTime())*(0));

				// Is it time to make this clip?
				//  take into account the fudge-factor which is a headstart into making the clip. This is a percentage of the clip
				//  duration and is set in the properties file
				if ( clipStopMS <= (nowMS + fudgeMS) ) { // stop time is past

					String fullClipCmd = capture.clipCmd; // full_clip_cmd will be cloaked

					long startSec = (clipStartCal.getTime().getTime() - capture.launchCal.getTime().getTime()) / 1000; // offset from start of capture
					long stopSec = (clipStopCal.getTime().getTime() - capture.launchCal.getTime().getTime()) / 1000; // offset from start of capture
					if (startSec < 0) startSec = 0;
					long duration = (stopSec - startSec);

					if ((stopSec >= 0) && (duration > 0)) {

						// construct full file path + create directory if neccessary
						String output = clipPath(clipStartCal, capture.source, this.name);

						// clip video
						if ( (!fullClipCmd.equals(""))) {

							fullClipCmd = fullClipCmd.replace("[channel]", capture.channel);
							fullClipCmd = fullClipCmd.replace("[capture]", capture.fullOutput);
							fullClipCmd = fullClipCmd.replace("[input]", capture.fullOutput);
							fullClipCmd = fullClipCmd.replace("[stream]", capture.streamOutput);
							fullClipCmd = fullClipCmd.replace("[streamlocal]", capture.streamLocal);
							fullClipCmd = fullClipCmd.replace("[streamfile]", capture.streamFile);
							fullClipCmd = fullClipCmd.replace("[nas]", capture.nasFolder);
							fullClipCmd = fullClipCmd.replace("[start]", "" + startSec);
							fullClipCmd = fullClipCmd.replace("[duration]", "" + duration);
							fullClipCmd = fullClipCmd.replace("[output]", output);
							fullClipCmd = fullClipCmd.replace("[clip]", output);

							String[] cmda = {
									"/bin/sh",
									"-c",
									fullClipCmd
							};

							try {
								Process p1=new ProcessBuilder(cmda).start();
								decoratedTrace(INDENT2, "Clip command executed '" + fullClipCmd + "'", session);
							} catch (Exception e) {
								success = false;
								decoratedError(INDENT0, "CaptureEventProcessor: Exception launching clip command '" + fullClipCmd + "': '" + e.getMessage() + "'", e);
							}
						}

						this.lastRun = now;

						if (clipEntry != null && success) {
							//Update this record to reflect that it has run
							clipEntry.setLastRun(this.lastRun);
							session.beginTransaction();
							session.persist(clipEntry);
							session.getTransaction().commit();
						}
					}

				} // if time to start

			} // not alreay run
		}
	}
}