package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
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
		logger.trace("Setting offline directory to: " + offlineDir);
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

		CaptureCommand capture = new CaptureCommand(captureEvent);

		PrintWriter offlineWriter = null;
		if (offlineDir != null) {
			try {
				offlineWriter = new PrintWriter(offlineDir.getPath() + System.getProperty("file.separator") + "capturecmd_" + capture.event_name + ".txt");
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
	public void captureEventOffline() {

		decoratedTrace(INDENT1, "Starting offline ShellCommand event processing");
		
		if (instance.isExclusiveEventActive(EventType.SHELLCOMMAND)) {
			decoratedTrace(INDENT1, "ShellCommand event processing already active. Skipping."); 
		} else {
			try {
    			instance.addExclusiveEvent(EventType.SHELLCOMMAND);
				for(File offlineFile : offlineDir.listFiles()) {
					
					if (offlineFile.getName().startsWith("capturecmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						CaptureCommand capture = new CaptureCommand(adq);
		
						PrintWriter offlineWriter = null;
						if (offlineDir != null) {
							try {
								offlineWriter = new PrintWriter(offlineDir.getPath() + System.getProperty("file.separator") + "capturecmd_" + capture.event_name + ".txt");
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
		
						CaptureCommand capture = new CaptureCommand(adq);
		
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
	
	/**
	 * Nested class that provides the functionality and data storage required to process both offline and online capture events.
	 * 
	 * @author StuartM
	 * @version 0.0.1
	 */
	
	@SuppressWarnings("unused")
	private class CaptureCommand {
		
		BigDecimal rsn = BigDecimal.valueOf(0.0D);
		String event_name="";
		String cmd="";
		String startTime="";
		String frequency="";
		String lastFtpRun="";
		String now = "";

		/**
		 * Create an online ShellCommand object using an EventsDao object retrieved from the database.
		 * @param captureEvt A capture event record retrieved from the EVENTS table.
		 */
		private CaptureCommand(EventsDao captureEvt) {
			
			rsn = captureEvt.getRsn();
			event_name = captureEvt.getName();
			cmd = captureEvt.getCaptureCommand();
			startTime = captureEvt.getStartTime();
			frequency = captureEvt.getFrequency();
			lastFtpRun = captureEvt.getLastFtpRun();
			now = DateUtil.getDateNow();
		}

		/**
		 * Create an offline ShellCommand object using commands stored in the queue parameter.
		 * @param queue Queue containing the command to execute locally.
		 */
		private CaptureCommand(ArrayDeque<String> queue) {
			
			try {
				String rsnStr = (String) queue.removeFirst();
				try { rsn = new BigDecimal(rsnStr); } catch (Exception ex) {;}
				event_name = (String) queue.removeFirst();
				cmd = (String)queue.removeFirst();
				startTime = (String) queue.removeFirst();
				frequency = (String) queue.removeFirst();
				lastFtpRun = (String) queue.removeFirst();
			} 
			catch (Exception ex) { 
				decoratedError(INDENT0, "Error reading offline Capture data.", ex); 
			}
		}

		/**
		 * Writes a representation of this ShellCommand to the file managed by the PrintWriter parameter.
		 * @param offlineWriter PrintWriter used to store a representation of this object in the <code>offline</code> directory.
		 */
		void writeOffline(PrintWriter offlineWriter) {
			if (offlineWriter!=null) {
				try {
					offlineWriter.println(rsn);
					offlineWriter.println(event_name);
					offlineWriter.println(cmd);
					offlineWriter.println(startTime);
					offlineWriter.println(frequency);
					offlineWriter.println(lastFtpRun);
				} catch (Exception ex) { ; }
			}
		}

		/**
		 * Execute the command represented by this ShellCommand object. If the database connection is online the first parameter will be
		 * a valid EventsDao object which is used when updating the lastFtpRun time. If it is running offline, both parameters will be null.
		 * 
		 * @param captureEvent The EVENTS table record representing the capture command, or null if offline.
		 * @param session The current Hibernate persistence context, or null if offline.
		 */
		void doCapture(EventsDao captureEvent, Session session) {
			
			String now = DateUtil.getDateNow();
			
			// Add space because we want to check this event (to write to offline cache) even if it has run already
			if ((!this.lastFtpRun.equals(now)) && (!this.lastFtpRun.equals(now + " "))) { 
				String cmd = this.cmd;

				// What time is the event to start?
				Calendar startCal = DateUtil.createTime(this.startTime, this.frequency);

				long startMS = startCal.getTime().getTime(); // system milliseconds at which this should start
				long nowMS = (new java.util.Date()).getTime(); // current system milliseconds
				long seconds = (startMS - nowMS) / 1000;

				// Is it time to start this event?
				if ( seconds < 120 ) // Less than two minutes until start time
				{
					if (seconds > 0) cmd = "sleep " + seconds + "; " + cmd;

					String[] cmda = {
							"/bin/sh",
							"-c",
							cmd
					};

					try {
						Runtime.getRuntime().exec(cmda);
						System.out.println("Filler.");
					} catch (Exception e) {
						logger.error("Exception launching command '" + cmd + "'", e);
					}

					this.lastFtpRun = now + " ";

					if (session != null) {
						//Update this record to reflect that it has run
						captureEvent.setLastFtpRun(this.lastFtpRun);
						session.beginTransaction();
						session.persist(captureEvent);
						session.getTransaction().commit();
					}

					decoratedTrace(INDENT2, "Capture command executed '" + cmd + "'");
				}
			}
		}
	}
}