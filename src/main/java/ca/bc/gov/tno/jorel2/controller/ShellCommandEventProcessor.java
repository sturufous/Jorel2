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
 * Processes the list of shell command events for this Jorel2 instance as described in the EVENTS table.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class ShellCommandEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/** The home directory of the current user */
	private String userDir = "";
	
	/** The system's file separator. */
	private String fileSep = "";
	
	/** The full path to the directory containing offline shell command files */
	private String offlineDirPath = "";
	
	/** java.io.File object used to list the offline files to be processed */
	private File offlineDir = null;
	
	/**
	 * Initializes the instance variables used to retrieve text files that describe offline shell commands. 
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
    		if (instance.isExclusiveEventActive(EventType.SHELLCOMMAND)) {
    			decoratedTrace(INDENT1, "ShellCommand event processing already active. Skipping.");    			
    		} else {
    			instance.addExclusiveEvent(EventType.SHELLCOMMAND);
    			decoratedTrace(INDENT1, "Starting ShellCommand event processing");
	    		
		        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
		        
		        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
		        for (Object[] entityPair : results) {
		        	if (entityPair[0] instanceof EventsDao) {
		        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        			setThreadTimeout(runnable, currentEvent, instance);
		        		
		        		shellCommandEventOnline(currentEvent, session);
		        	}
		        }
		        
		        instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
	    	} 
    	}
    	catch (Exception e) {
    		instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
    		logger.error("Processing shell command entries.", e);
    	}
    	
    	decoratedTrace(INDENT1, "Completing ShellCommand event processing");
    	return Optional.of("complete");
	}
	
	/**
	 * Executes the shell command event described by the <code>shellEvent</code> parameter. 
	 * 
	 * @param shellEvent Entity describing the event to run locally.
	 * @param session The current Hibernate persistence context.
	 */
	private void shellCommandEventOnline(EventsDao shellEvent, Session session) {

		ShellCommand shell = new ShellCommand(shellEvent);

		PrintWriter offlineWriter = null;
		if (offlineDir!=null) {
			try {
				offlineWriter = new PrintWriter(offlineDir.getPath() + System.getProperty("file.separator")+"shellcmd_" + shell.event_name + ".txt");
			} catch (Exception ex) {
				offlineWriter = null;
			}
		}

		shell.doCommand(shellEvent, session); // do the command (if it is time)
		shell.writeOffline(offlineWriter); // write to the offline cache

		if (offlineWriter!=null) {
			try {
				offlineWriter.close();
			} catch (Exception ex) { ; }
		}
	}
	
	/**
	 * Executes shell command events when the connection to the database is down. The commands are read from files in the <code>offline</code>
	 * directory which have the following naming convention: <code>shellcmd_[event-name].txt</code>. These files are created when 
	 * <code>shellCommandEventOnline()</code> is executed. 
	 */
	public void shellCommandEventOffline() {

		decoratedTrace(INDENT1, "Starting offline ShellCommand event processing");
		
		if (instance.isExclusiveEventActive(EventType.SHELLCOMMAND)) {
			decoratedTrace(INDENT1, "ShellCommand event processing already active. Skipping."); 
		} else {
			try {
    			instance.addExclusiveEvent(EventType.SHELLCOMMAND);
				for(File offlineFile : offlineDir.listFiles()) {
					
					if (offlineFile.getName().startsWith("shellcmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						ShellCommand shell = new ShellCommand(adq);
		
						PrintWriter offlineWriter = null;
						if (offlineDir!=null) {
							try {
								offlineWriter = new PrintWriter(offlineDir.getPath() + System.getProperty("file.separator") + "shellcmd_"+shell.event_name + ".txt");
							} catch (Exception ex) {
								offlineWriter = null;
							}
						}
		
						shell.doCommand(null, null); // do the command (if it is time)
						shell.writeOffline(offlineWriter); // write to the offline cache
		
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
	    		logger.error("While processing offline shell command.", e); 				
			}
		}
		
		decoratedTrace(INDENT1, "Completing offline ShellCommand event processing");
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
	public void shellCommandEventUpdate(Session session) {

		try {
			if (instance.isExclusiveEventActive(EventType.SHELLCOMMAND)) {
				decoratedTrace(INDENT1, "ShellCommand event processing already active. Skipping."); 
			} else {
				instance.addExclusiveEvent(EventType.SHELLCOMMAND);
				
				for(File offlineFile: offlineDir.listFiles()) {
					if (offlineFile.getName().startsWith("shellcmd_")) {
		
						ArrayDeque<String> adq = new ArrayDeque<>();
						loadOffline(offlineFile, adq);
		
						ShellCommand shell = new ShellCommand(adq);
		
						decoratedTrace(INDENT1, "Update shell event, set lastFtpRun='" + shell.lastFtpRun + "' for rsn=" + shell.rsn);
		
						// update event
						EventsDao shellEvt = EventsDao.getEventByRsn(shell.rsn, session).get(0);
						shellEvt.setLastFtpRun(shell.lastFtpRun);
						session.getTransaction().begin();
						session.persist(shellEvt);
						session.getTransaction().commit();
					}
				}
				
				instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
			}
		}
		catch (Exception e) {
			instance.removeExclusiveEvent(EventType.SHELLCOMMAND);
    		logger.error("While post-processing shell command after network reconnect.", e); 							
		}
	}
	
	/**
	 * Nested class that provides the functionality and data storage required to process both offline and online shell command events.
	 * 
	 * @author StuartM
	 * @version 0.0.1
	 */
	
	@SuppressWarnings("unused")
	private class ShellCommand {
		
		BigDecimal rsn = BigDecimal.valueOf(0.0D);
		String event_name="";
		String cmd="";
		String startTime="";
		String frequency="";
		String lastFtpRun="";
		String now = "";

		/**
		 * Create an online ShellCommand object using an EventsDao object retrieved from the database.
		 * @param shellEvt A shell event record retrieved from the EVENTS table.
		 */
		private ShellCommand(EventsDao shellEvt) {
			
			rsn = shellEvt.getRsn();
			event_name = shellEvt.getName();
			cmd = shellEvt.getTitle();
			startTime = shellEvt.getStartTime();
			frequency = shellEvt.getFrequency();
			lastFtpRun = shellEvt.getLastFtpRun();
			now = DateUtil.getDateNow();
		}

		/**
		 * Create an offline ShellCommand object using commands stored in the queue parameter.
		 * @param queue Queue containing the command to execute locally.
		 */
		private ShellCommand(ArrayDeque<String> queue) {
			
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
				decoratedError(INDENT0, "Error reading offline shell command data.", ex); 
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
		 * @param shellEvent The EVENTS table record representing the shell command, or null if offline.
		 * @param session The current Hibernate persistence context, or null if offline.
		 */
		void doCommand(EventsDao shellEvent, Session session) {
			
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
					if (seconds > 0) cmd = "sleep " + seconds+"; " + cmd;

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
						shellEvent.setLastFtpRun(this.lastFtpRun);
						session.beginTransaction();
						session.persist(shellEvent);
						session.getTransaction().commit();
					}

					decoratedTrace(INDENT1, "Shell command executed '" + cmd + "'");
				}
			}
		}
	}
}