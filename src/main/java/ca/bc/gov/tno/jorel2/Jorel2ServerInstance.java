package ca.bc.gov.tno.jorel2;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.springframework.jmx.export.annotation.ManagedResource;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedAttribute;

import static java.util.stream.Collectors.*;
import static java.util.Map.Entry.*;

/**
 * Indicates the process we're running on (e.g. "jorel", "jorelMini3"). Control over the order in which JMX attributes are displayed in
 * VisualVM is achieved by naming related attributes with the same prefix, e.g. getStorageBinaryRoot, getStorageArchiveTo etc. In the case
 * of important properties, that must be displayed at the top of the dashboard, they are prefixed using a letter of the alphabet followed
 * by a number, e.g. A1, B4 etc.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 *
 */

@ManagedResource(
        objectName="Jorel2Instance:name=jorel2Mbean",
        description="Jorel2 Managed Bean",
        currencyTimeLimit=1)

public class Jorel2ServerInstance extends Jorel2Root {
	
//	Map<String, Long> threadDurations = new ConcurrentHashMap<>();
	Map<String, Integer> articleCounts = new ConcurrentHashMap<>();
	Map<String, Integer> wordCounts = new HashMap<>();
	String lastInterruptionKey = "";
	long minDuration = 0L;
	long maxDuration = 0L;
	long threadCount = 0L;
	long lastDurationTimestamp = 0L;
	
	/** Keeps track of date and time of database connection interruptions, i.e., entering offline mode */
	private ConcurrentHashMap<String, String> databaseInterruptions = new ConcurrentHashMap<>();
	
	/** Keeps track of date and time of https failures, i.e., no data returned, timeout */
	private ConcurrentHashMap<String, String> httpFailures = new ConcurrentHashMap<>();
	
	/** Central location for event types that are not thread safe. Ensures mutual exclusion. */
	private Map<EventType, String> activeEvents = new ConcurrentHashMap<>();
	
	LocalDateTime startTime = null;

	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration config;
	
	/** Allows charting of individual run times in VisualVM */
	private long lastDuration = 0;
	
	/** Keeps a tally of how many alert emails have been sent out */
	private long alertCounter = 0;
	
	/** Indicates whether this instance currently has access to a network connection */
	private ConnectionStatus connectionStatus = ConnectionStatus.OFFLINE;
	
	/** Indicates that a shutdown request has been received, but one or more threads are currently running */
	public boolean stopRequestReceived = false;
	
	/**
	 * Construct this object and set the startTime to the time now.
	 */
	Jorel2ServerInstance() {
		startTime = LocalDateTime.now();
	}
	
	/**
	 * Expose the build number for this instance.
	 * 
	 * @return The build number for the current instance
	 */
	@ManagedAttribute(description="The build number of this Jorel instance", currencyTimeLimit=15)
	public String getBuildNumber() {
		
		return buildNumber;
	}
	
	/**
	 * Expose the number of threads currently running on this instance.
	 * 
	 * @return the number of threads currently running on this instance.
	 */
	@ManagedAttribute(description="The number of threads currently running on this Jorel instance", currencyTimeLimit=-1)
	public int getActiveThreads() {
		
		return activeThreads.size();
	}
	
	/**
	 * Expose A list of event types handled by this instance.
	 * 
	 * @return A list of event types handled by this instance.
	 */
	@ManagedAttribute(description="A list of event types handled by this instance", currencyTimeLimit=15)
	public String getEventTypesHandled() {
		String result = ""; // Should use stringbuilder
		
		for (Entry entry : eventTypesProcessed.entrySet()) {
			result = result + entry.getKey() + " ";
		}
		
		return result;
	}
	
	/**
	 * Expose the system architecture string for the server this instance is running on.
	 * 
	 * @return The system architecture string for the server this instance is running on.
	 */
	@ManagedAttribute(description="The system architecture string for the server this instance is running on", currencyTimeLimit=15)
	public String getServerDetails() {
		
		return "Java " + System.getProperty("java.version") + ", " + System.getProperty("os.arch") +
				", " + System.getProperty("os.name") + " " + System.getProperty("os.version");
	}
	
	/**
	 * Expose the user for the server this instance is running on.
	 * 
	 * @return The user for the server this instance is running on.
	 */
	@ManagedAttribute(description="The user for the server this instance is running on", currencyTimeLimit=15)
	public String getServerUser() {
		
		return System.getProperty("user.name");
	}
	
	/**
	 * Expose the name of the instance this execution is running on.
	 * 
	 * @return The name of the current instance
	 */
	@ManagedAttribute(description="Name of this Jorel instance", currencyTimeLimit=15)
	public String getAppInstanceName() {
		
		return config.getString("instanceName");
	}
	
	/**
	 * Allows the <code>instanceName</code> JMX attribute to be set remotely.
	 * 
	 * @param instanceName The name of the currenly executing Jorel2 instance.
	 */
	@ManagedOperation(description="Set the instance name")
	  @ManagedOperationParameters({
	    @ManagedOperationParameter(name = "instanceName", description = "The name of the currently executing instance."),
	})
	public void setInstanceName(String instanceName) {
		
		config.setProperty("instanceName", instanceName);
	}
	
	/**
	 * Exposes the <code>articleCounts</code> map as a JMX attribute. This contains a list of article counts, by source, that have been added
	 * since this Jorel2 instance was started. 
	 * 
	 * @return The list of sources from which articles have been added, and their respective counts.
	 */
	@ManagedAttribute(description="Number of articles added by source", currencyTimeLimit=15)
	public Map<String, Integer> getAppArticleCounts() {
		
		return articleCounts;
	}
	
	/**
	 * Exposes the a string representation of <code>startTime</code> as a JMX attribute. 
	 * 
	 * @return The startTime attribute for this instance.
	 */
	@ManagedAttribute(description="Start time of this instance", currencyTimeLimit=15)
	public String getAppStartTime() {
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E, d LLL yyyy HH:mm:ss");
		String dateMatch = startTime.format(formatter);

		return dateMatch;
	}
	
	/**
	 * Exposes the length of time this instance has been running as a JMX attribute. This is formatted from a long using this string 
	 * <code>"%d Days, %d Hours, %02d Minutes, %02d Seconds"</code>.
	 * 
	 * @return The length of time this instance has been running.
	 */
	@ManagedAttribute(description="Run time of this instance", currencyTimeLimit=15)
	public String getAppInstanceRunTime() {
		
		String instanceRunTime = "";
    	LocalDateTime now = LocalDateTime.now();
	    long diff = ChronoUnit.SECONDS.between(startTime, now);
	    
	    instanceRunTime = String.format("%d Days, %d Hours, %02d Minutes, %02d Seconds", diff / 86400, (diff / 3600) % 24, (diff % 3600) / 60, (diff % 60));

		return instanceRunTime;
	}
	
	/**
	 * Exposes the online status as a JMX attribute.
	 * 
	 * @return The connection status enum as a string.
	 */
	@ManagedAttribute(description="DB Connection status - ONLINE or OFFLINE", currencyTimeLimit=15)
	public String getAppConnectionStatusStr() {
		
		return connectionStatus.toString();
	}
	
	/**
	 * Exposes the online status.
	 * 
	 * @return The connection status as an enum.
	 */
	public ConnectionStatus getConnectionStatus() {
		
		return connectionStatus;
	}
	
	/**
	 * Allows the online status for this Jorel2 instance to be set based on Hibernate behaviour.
	 * 
	 * @param status Enum indicating whether the database connection is online or offline.
	 */
	public void setConnectionStatus(ConnectionStatus status) {
		
		this.connectionStatus = status;
	}
	
	/**
	 * Exposes database profile name as a JMX attribute.
	 * 
	 * @return The database profile name - System.getProperty("dbProfile").
	 */
	@ManagedAttribute(description="Database profile name.", currencyTimeLimit=15)
	public String getAppDatabaseProfileName() {
		
		return System.getProperty("dbProfile").toUpperCase();
	}
	
	/**
	 * Exposes database profile name as a JMX attribute.
	 * 
	 * @return The database profile name - System.getProperty("dbProfile").
	 */
	@ManagedAttribute(description="Description of the execution environment.", currencyTimeLimit=15)
	public String getAppA1LocalEnvironment() {
		
		return config.getString("localDesc");
	}
	
	/**
	 * Exposes <code>cronExpression</code> as a JMX attribute. 
	 * 
	 * @return The cronExpression attribute for this instance.
	 */
	@ManagedAttribute(description="Cron expression used for thread execution scheduling", currencyTimeLimit=15)
	public String getCronExpression() {
		
		return config.getString("cron.expression");
	}
	
	/**
	 * Exposes a sorted list of database interruptions that took place during this run cycle. Because this is consumed by Angular
	 * (in Overseer) using *ngFor, it needs to be an array rather than an object with multiple members. This means you can't 
	 * see the values in VirtualVM, but Overseer provides a more powerful overview which makes it worth it.
	 * 
	 * @return The list of database interruptions.
	 */
	@ManagedAttribute(description="Records times when database interruptions took place", currencyTimeLimit=15)
	public String[][] getDatabaseInterruptions() {
		
		String[][] arr = null;
		
		Map<String, String> sorted = databaseInterruptions.entrySet().stream().sorted(comparingByKey())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		
		if(sorted.size() == 0) {
			arr = new String[1][2];
			arr[0][0] = "No database";
			arr[0][1] = "Interruptions";
		} else {
			arr = new String[sorted.size()][2];
			Set entries = sorted.entrySet();
			Iterator entriesIterator = entries.iterator();
	
			int i = 0;
			while(entriesIterator.hasNext()){
	
			    Map.Entry mapping = (Map.Entry) entriesIterator.next();
	
			    arr[i][0] = (String) mapping.getKey();
			    arr[i][1] = (String) mapping.getValue();
	
			    i++;
			}
		}
		
		return arr;
	}
	
	/**
	 * Exposes the ftp host as a JMX attribute.
	 * 
	 * @return The ftp host name.
	 */
	@ManagedAttribute(description="The ftp host name used by the Archiver event.", currencyTimeLimit=15)
	public String getFtpHostName() {
		
		return config.getString("ftp.host");
	}
	
	/**
	 * Exposes the ftp user name as a JMX attribute.
	 * 
	 * @return The ftp user name.
	 */
	@ManagedAttribute(description="The ftp user name used by the Archiver event.", currencyTimeLimit=15)
	public String getFtpUserName() {
		
		return config.getString("ftp.userName");
	}
	
	/**
	 * Exposes the ftp type (active or passive) as a JMX attribute .
	 * 
	 * @return The ftp type.
	 */
	@ManagedAttribute(description="The ftp type used by the Archiver event (active or passive).", currencyTimeLimit=15)
	public String getFtpType() {
		
		return config.getString("ftp.type");
	}
	
	/**
	 * Exposes the ftp secure status as a JMX attribute.
	 * 
	 * @return The ftp secure status.
	 */
	@ManagedAttribute(description="The ftp secure status used by the Archiver event.", currencyTimeLimit=15)
	public String getFtpSecure() {
		
		return config.getString("ftp.secure");
	}
	
	/**
	 * Exposes the http failure count as a JMX attribute.
	 * 
	 * @return The number of http failures that occurred during this run.
	 */
	@ManagedAttribute(description="The number of HTTP failures that occurred since this Jorel2 process started.", currencyTimeLimit=15)
	public int getHttpFailureCount() {
		
		return httpFailures.size();
	}
	
	/**
	 * Adds an entry to the httpFailures Map to record the error condition.
	 * 
	 * @param message The failure description (e.g. timeout) and url that caused the failure.
	 */
	public void addHttpFailure(String message) {
		
		httpFailures.put(DateUtil.getLoggerKeyDateTimeNow(), message);
	}
	
	/**
	 * Adds an entry to the httpFailures Map to record the error condition.
	 * 
	 * @param message The failure description (e.g. timeout) and url that caused the failure.
	 */
	public void incrementAlertCounter() {
		
		alertCounter++;
	}
	
	/**
	 * Exposes <code>appAlertCounter</code> as a JMX attribute. 
	 * 
	 * @return The appAlertCounter attribute for this instance.
	 */
	@ManagedAttribute(description="The number of alert emails sent out", currencyTimeLimit=15)
	public long getAppAlertCounter() {
		
		return alertCounter;
	}
	
	/**
	 * Exposes the list of http failures that took place during this run cycle.
	 * 
	 * @return The list of http failure times and their causes.
	 */
	@ManagedAttribute(description="Records times when httpFailures took place, and the url being accessed", currencyTimeLimit=15)
	public LinkedHashMap<String, String> getHttpFailures() {
		
		Map<String, String> sorted = httpFailures.entrySet().stream().sorted(comparingByKey())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		
		return (LinkedHashMap<String, String>) sorted;
	}
	
	/**
	 * Exposes <code>lastDuration</code> as a JMX attribute. 
	 * 
	 * @return The lastDuration attribute for this instance.
	 */
	@ManagedAttribute(description="Length of last run duration in seconds", currencyTimeLimit=1, persistPolicy="never")
	public long getLastDuration() {
		
		return lastDuration;
	}
	
	/**
	 * Increments the count of articles, by source, that have been added by this instance since the process started.
	 * 
	 * @param source The source of the RSS articles added.
	 * @param count The number of articles added my the thread that just completed.
	 */
	public void incrementArticleCount(String source, int count) {
		
		if (articleCounts.containsKey(source)) {
			Integer sourceCount = articleCounts.get(source);
			sourceCount += count;
			articleCounts.replace(source, sourceCount);
		} else {
			articleCounts.put(source, count);
		}
	}
	
	/**
	 * Add the latest thread execution duration. As this value is used for monitoring thread performance by Overseer and VisualVM, we want to focus
	 * on the big numbers rather than the small ones. As this value is being polled every 3 seconds we want to discard the lastDuration currently being 
	 * set, if it is smaller than the previous one. This ensures that when the value is next polled the largest value of thread durations within the polling
	 * period is returned. The saving of the current duration is overridden, following this protocol, for 5000 milliseconds.
	 * 
	 * @param threadName Name of the currently executing thread used as key the the threadDurations map.
	 * @param duration Number of seconds it took for this thread to complete event processing.
	 */
	public void addThreadDuration(String threadName, long duration) {
		
		if(duration > maxDuration) {
			maxDuration = duration;
		}
		
		if(duration < minDuration) {
			minDuration = duration;
		}
		
		Date now = new Date();
		long nowMilliseconds = now.getTime();
		
		// If the lastDuration was set recently, assume it hasn't been polled by overseer or VisualVM since the last update and keep the biggest value.
		if(nowMilliseconds - lastDurationTimestamp < 5000) {
			if(lastDuration < duration) {
				lastDuration = duration;
				decoratedTrace(INDENT2, "Keeping larger duration measure. Timestamp difference = " + (nowMilliseconds - lastDurationTimestamp));
			}
		} else {
			lastDuration = duration;
		}
		
		threadCount++;
		lastDurationTimestamp = nowMilliseconds;
	}

	/**
	 * Exposes the number of seconds the longest running thread took to complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	@ManagedAttribute(description="Maximum thread duration since startup", currencyTimeLimit=15)
	public Long getThreadMaxDurationSeconds() {
		
		return maxDuration;
	}

	/**
	 * Exposes the number of seconds the shortest running thread took to complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	@ManagedAttribute(description="Minimum thread duration since startup", currencyTimeLimit=15)
	public Long getThreadMinDurationSeconds() {
		
		return minDuration;
	}

	/**
	 * Exposes the number of threads that have run, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of threads that have run.
	 */
	@ManagedAttribute(description="Number of thread runs included in stats", currencyTimeLimit=15)
	public long getThreadCompleteCount() {
		
		return threadCount;
	}
	
	/**
	 * Allows the <code>maxThreadRuntime</code> JMX attribute to be set remotely.
	 * 
	 * @param maxThreadRuntime The maximum time, in seconds, before Jorel2 will timeout and exit.
	 */
	@ManagedOperation(description="Set the thread timeout value")
	  @ManagedOperationParameters({
	    @ManagedOperationParameter(name = "maxThreadRuntime", description = "How long a thread can run before the VM exits."),
	})
	public void setMaxThreadruntime(int maxThreadRuntime) {
		
		config.setProperty("maxThreadRuntime", maxThreadRuntime);
	}
	
	/**
	 * Exposes the eMail from address as a JMX attribute.
	 * 
	 * @return The eMail from address.
	 */
	@ManagedAttribute(description="From address for use when sending Jorel2 emails", currencyTimeLimit=15)
	public String getMailFromAddress() {
		
		return config.getString("mail.from");
	}
	
	/**
	 * Exposes the eMail-to address (or addresses) as a JMX attribute.
	 * 
	 * @return The eMail-to address.
	 */
	@ManagedAttribute(description="To address for use when sending Jorel2 emails", currencyTimeLimit=15)
	public String getMailToAddress() {
		
		String[] distributionList = config.getStringArray("mail.to");
		String listCommaDelimited = "";
		
		for(int idx=0; idx < distributionList.length; idx++) {
			if(idx < distributionList.length - 1) {
				listCommaDelimited = listCommaDelimited + distributionList[idx] + ",";
			} else {
				listCommaDelimited = listCommaDelimited + distributionList[idx];
			}
					
		}
		return listCommaDelimited;
	}
	
	/**
	 * Exposes the eMail host address as a JMX attribute.
	 * 
	 * @return The eMail host address.
	 */
	@ManagedAttribute(description="Host address for use when sending Jorel2 emails", currencyTimeLimit=15)
	public String getMailHostAddress() {
		
		return config.getString("mail.host");
	}
	
	/**
	 * Exposes the eMail port number as a JMX attribute.
	 * 
	 * @return The eMail port number.
	 */
	@ManagedAttribute(description="Number of quote extractor words by type", currencyTimeLimit=15)
	public String getMailPortNumber() {
		
		return config.getString("mail.portNumber");
	}
	
	/**
	 * Exposes the number of seconds the longest running thread took to complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	@ManagedAttribute(description="Max thread run time of this instance", currencyTimeLimit=15)
	public int getMaxThreadRuntime() {
		
		return config.getInt("maxThreadRuntime");
	}	
	
	/**
	 * Exposes the binary root path as a JMX attribute.
	 * 
	 * @return The binary root path.
	 */
	@ManagedAttribute(description="Binary root path in which media are stored", currencyTimeLimit=15)
	public String getStorageBinaryRoot() {
		
		return config.getString("binaryRoot");
	}
	
	/**
	 * Exposes the ftp root path as a JMX attribute.
	 * 
	 * @return The ftp root path.
	 */
	@ManagedAttribute(description="HTTP address of the Otis instance", currencyTimeLimit=15)
	public String getStorageAvHost() {
		
		return config.getString("avHost");
	}
	
	/**
	 * Exposes the ftp root path as a JMX attribute.
	 * 
	 * @return The ftp root path.
	 */
	@ManagedAttribute(description="Ftp root path in which media are stored", currencyTimeLimit=15)
	public String getStorageFtpRoot() {
		
		return config.getString("ftp.root");
	}
	
	/**
	 * Exposes the importFileHours property as a JMX attribute.
	 * 
	 * @return The importFileHours property.
	 */
	@ManagedAttribute(description="The maximum time, in seconds, since last-modified before import files are rejected", currencyTimeLimit=15)
	public String getStorageImportFileHours() {
		
		return config.getString("importFileHours");
	}
	
	/**
	 * Exposes the www root path as a JMX attribute.
	 * 
	 * @return The www root path.
	 */
	@ManagedAttribute(description="The www root path (media root directory within web hierarchy)", currencyTimeLimit=15)
	public String getStorageWwwRoot() {
		
		return config.getString("wwwBinaryRoot");
	}
	
	/**
	 * Exposes the processedLoc property as a JMX attribute.
	 * 
	 * @return The processedLoc property.
	 */
	@ManagedAttribute(description="Location into which import files are copied post-processing", currencyTimeLimit=15)
	public String getStorageProcessedLoc() {
		
		return config.getString("processedFilesLoc");
	}
	
	/**
	 * Exposes the archiveTo property as a JMX attribute.
	 * 
	 * @return The archiveTo property.
	 */
	@ManagedAttribute(description="Location into which media files are archived", currencyTimeLimit=15)
	public String getStorageArchiveTo() {
		
		return config.getString("archiveTo");
	}
	
	/**
	 * Exposes the archiveTo property as a JMX attribute.
	 * 
	 * @return The archiveTo property.
	 */
	@ManagedAttribute(description="Location into which captured media are stored", currencyTimeLimit=15)
	public String getStorageCaptureDir() {
		
		return config.getString("captureDir");
	}
	
	/**
	 * Exposes the maxCdSize property as a JMX attribute.
	 * 
	 * @return The maxCdSize property.
	 */
	@ManagedAttribute(description="Maximum number of bytes in a CD archive (in K) before rollover to next disk", currencyTimeLimit=15)
	public String getStorageMaxCdSize() {
		
		return config.getString("maxCdSize");
	}
	
	/**
	 * Exposes the httphost property as a JMX attribute.
	 * 
	 * @return The httphost property.
	 */
	@ManagedAttribute(description="URL to the Otis Servlet", currencyTimeLimit=15)
	public String getAppHttpHost() {
		
		return config.getString("httphost");
	}
	
	/**
	 * Adds an entry to the databaseInterruptions Map to record the interruption.
	 * 
	 * @param threadName The name of the current thread.
	 */
	public void addDatabaseInterruption() {
		
		String interruptionTime = DateUtil.getLoggerKeyDateTimeNow();
		databaseInterruptions.put(interruptionTime, "In progress");
		lastInterruptionKey = interruptionTime;
	}
	
	/**
	 * Replaces the existing value of an in-progress database outage (the string "In progress.") with the duration of the outage in seconds.
	 * This method is called when the connection to the database is restored.
	 */
	public void logDbOutageDuration() {
		
		long seconds = DateUtil.secondsSinceTime(lastInterruptionKey);
		databaseInterruptions.replace(lastInterruptionKey, "Duration: " + seconds + " seconds");
	}
	
	/**
	 * Adds the enum <code>eventType</code> to the <code>activeEvents</code> Map. This ensures that events which
	 * do not support concurrent execution are only run in one thread.
	 * 
	 * @param eventType The exclusive event type to add.
	 */
	public void addExclusiveEvent(EventType eventType) {
		
		activeEvents.put(eventType, "");
	}
	
	/**
	 * This removes a previously registered event, that does not support concurrent execution, from the <code>activeEvents</code> Map.
	 * 
	 * @param eventType The event type to remove from the Map.
	 */
	public void removeExclusiveEvent(EventType eventType) {
		
		activeEvents.remove(eventType);
	}
	
	/**
	 * Checks to see if an event that does not support concurrent execution is currently active. This is done by searching 
	 * for an entry in the <code>activeEvents Map</code> that matches the event type passed in the single parameter.
	 * 
	 * @param eventType The event type to check against the <code>activeEvents</code> Map to see if it's already running.
	 * @return boolean indicating whether the event is active or not.
	 */
	public boolean isExclusiveEventActive(EventType eventType) {
		
		boolean result = false;
		
		if (activeEvents.containsKey(eventType)) {
			result = true;
		} else {
			result = false;
		}
		
		return result;
	}
	
	/**
	 * Adds a Quote Extractor word count entry. This allows a monitoring entity to see how many entries there are for the types
	 * Noise, Verb, Title and Noisename.
	 * 
	 * @param type a string representation of the type of word (e.g. Noise, Verb)
	 * @param count The number of entries for this type of word in the quote extractor.
	 */
	public void addWordCountEntry(String type, int count) {
		
		wordCounts.put(type, Integer.valueOf(count));
	}
	
	/**
	 * Exposes the <code>wordCounts</code> map as a JMX attribute. This contains a list of word counts, by category, that were added
	 * by the quote extractor when this instance was initialized. 
	 * 
	 * @return The list of sources from which articles have been added, and their respective counts.
	 */
	@ManagedAttribute(description="Number of quote extractor words by type", currencyTimeLimit=15)
	public Map<String, Integer> getWordCounts() {
		
		return wordCounts;
	}
	
	@ManagedOperation(description="Stop this instance of Jorel2")
	public void stop() {
		if (activeThreads.size() > 0) {
			stopRequestReceived = true;
			logger.trace("Stop request received. Threads are running, will shutdown after thread completion.");
		} else {
			new Thread(() -> {
				logger.trace("Stop request received. Shutting down immediately.");
				try {
					Thread.sleep(200);
					System.exit(SHUTDOWN_REQUESTED);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}).start();
		}
	}
}
