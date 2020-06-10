package ca.bc.gov.tno.jorel2;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedResource;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
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
        currencyTimeLimit=15)

public class Jorel2Instance extends Jorel2Root {
	
	Map<String, Long> threadDurations = new ConcurrentHashMap<>();
	Map<String, Integer> articleCounts = new ConcurrentHashMap<>();
	Map<String, Integer> wordCounts = new HashMap<>();
	String lastInterruptionKey = "";
	
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
	
	/** Indicates whether this instance currently has access to a network connection */
	private ConnectionStatus connectionStatus = ConnectionStatus.OFFLINE;
	
	private PreferencesDao preferences = null;
	
	/**
	 * Construct this object and set the startTime to the time now.
	 */
	Jorel2Instance() {
		startTime = LocalDateTime.now();
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
	 * Exposes the list of database interruptions that took place during this run cycle.
	 * 
	 * @return The list of database interruptions.
	 */
	@ManagedAttribute(description="Records times when database interruptions took place", currencyTimeLimit=15)
	public ConcurrentHashMap<String, String> getDatabaseInterruptions() {
		
		return databaseInterruptions;
	}
	
	/* # Ftp settings used by Archiver event
	ftp.host=alfredo.tno.gov.bc.ca
	ftp.password=vgXjRUkTG9LEmGXk
	ftp.userName=stmorse
	ftp.type=passive
	ftp.secure=yes */
	
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
		
		httpFailures.put(DateUtil.getTimeNow(), message);
	}
	
	/**
	 * Exposes the list of http failures that took place during this run cycle.
	 * 
	 * @return The list of http failure times and their causes.
	 */
	@SuppressWarnings("unchecked")
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
	@ManagedAttribute(description="Length of last run duration in seconds", currencyTimeLimit=15)
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
	 * Add the latest thread execution duration to the <code>threadDurations</code> Map so that it can be used in
	 * calculating longest, shortest and mean thread execution times.
	 * 
	 * @param threadName Name of the currently executing thread used as key the the threadDurations map.
	 * @param duration Number of seconds it took for this thread to complete event processing.
	 */
	public void addThreadDuration(String threadName, long duration) {
		
		threadDurations.put(threadName, Long.valueOf(duration));
		lastDuration = duration;
	}

	/**
	 * Exposes the number of seconds the longest running thread took to complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	@ManagedAttribute(description="Maximum thread duration since startup", currencyTimeLimit=15)
	public Long getThreadMaxDurationSeconds() {
		
		OptionalLong max = threadDurations.values().stream().mapToLong(v -> v).max();
		return max.getAsLong();
	}

	/**
	 * Exposes the number of seconds the shortest running thread took to complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	@ManagedAttribute(description="Minimum thread duration since startup", currencyTimeLimit=15)
	public Long getThreadMinDurationSeconds() {
		
		OptionalLong min = threadDurations.values().stream().mapToLong(v -> v).min();
		return min.getAsLong();
	}

	/**
	 * Exposes the mean number of seconds all threads took complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	@ManagedAttribute(description="Mean thread duration since startup", currencyTimeLimit=15)
	public Double getThreadMeanDurationSeconds() {
		
		OptionalDouble mean = threadDurations.values().stream().mapToLong(v -> v).average();
		return mean.getAsDouble();
	}
	
	/**
	 * Exposes the number of threads that have run, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of threads that have run.
	 */
	@ManagedAttribute(description="Number of thread runs included in stats", currencyTimeLimit=15)
	public int getThreadCompleteCount() {
		
		return threadDurations.size();
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
		
		return config.getString("mail.to");
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
	 * Exposes the maxCdSize property as a JMX attribute.
	 * 
	 * @return The maxCdSize property.
	 */
	@ManagedAttribute(description="Maximum number of bytes in a CD archive (in K) before rollover to next disk", currencyTimeLimit=15)
	public String getStorageMaxCdSize() {
		
		return config.getString("maxCdSize");
	}
	
	/**
	 * Adds an entry to the databaseInterruptions Map to record the interruption.
	 * 
	 * @param threadName The name of the current thread.
	 */
	public void addDatabaseInterruption(String threadName) {
		
		String interruptionTime = DateUtil.getTimeNow();
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
	 * Loads the single record from the PREFERENCES table in the TNO database. This is then stored as a PreferencesDao object in
	 * this instance.
	 * 
	 * @param session The current Hibernate persistence context.
	 */
	public void loadPreferences(Session session) {
		
		List<PreferencesDao> preferenceList = PreferencesDao.getPreferencesByRsn(PREFERENCES_RSN, session);
		
		if (preferenceList.size() > 0) {
			preferences = preferenceList.get(0);
		} else {
			decoratedError(INDENT0, "Reading preferences from database.", new IOException("Unable to retried preferences from TNO database."));
		}
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
	
	/**
	 * Returns the PreferencesDao object that was loaded by <code>loadPreferences()</code>. This object is used very rarely and is
	 * likely a legacy feature of the TNO system.
	 * 
	 * @return The preferences object.
	 */
	public PreferencesDao getPreferences() {
		
		return preferences;
	}
}
