package ca.bc.gov.tno.jorel2;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedResource;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 * Indicates the process we're running on (e.g. "jorel", "jorelMini3")
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
	
	/** Keeps track of date and time of database connection interruptions, i.e., entering offline mode */
	private ConcurrentHashMap<String, String> databaseInterruptions = new ConcurrentHashMap<>();
	
	/** Central location for event types that are not thread safe. Ensures mutual exclusion. */
	private Map<EventType, String> activeEvents = new ConcurrentHashMap<>();
	
	LocalDateTime startTime = null;

	/** The name of the current instance */
	@Value("${instanceName}")
	public String instanceName;
	
	/** The maximum time a thread can run before the VM exits */
	@Value("${maxThreadRuntime}")
	public String maxThreadRuntimeString;
	
	/** The from address of any Jorel2 emails */
	@Value("${mail.from}")
	public String mailFromAddress;
	
	/** The host address of the smtp server used for sending emails */
	@Value("${mail.host}")
	public String mailHostAddress;
	
	/** The port of the smtp server used for sending emails */
	@Value("${mail.portNumber}")
	public String mailPortNumber;
	
	/** Integer version of maxThreadRuntimeString which is exposed as a JMX managed operation */
	private int maxThreadRuntime = 0;
	
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
	
	@PostConstruct
	public void init() {
		maxThreadRuntime = Integer.valueOf(maxThreadRuntimeString);
	}
	
	/**
	 * Expose the name of the instance this execution is running on.
	 * 
	 * @return The name of the current instance
	 */
	
	@ManagedAttribute(description="Name of this Jorel instance", currencyTimeLimit=15)
	public String getInstanceName() {
		
		return instanceName;
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
		
		this.instanceName = instanceName;
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
	 * Exposes the <code>articleCounts</code> map as a JMX attribute. This contains a list of article counts, by source, that have been added
	 * since this Jorel2 instance was started. 
	 * 
	 * @return The list of sources from which articles have been added, and their respective counts.
	 */
	
	@ManagedAttribute(description="Number of articles added by source", currencyTimeLimit=15)
	public Map<String, Integer> getArticleCounts() {
		
		return articleCounts;
	}	
	
	/**
	 * Exposes the a string representation of <code>startTime</code> as a JMX attribute. 
	 * 
	 * @return The startTime attribute for this instance.
	 */
	
	@ManagedAttribute(description="Start time of this instance", currencyTimeLimit=15)
	public String getStartTime() {
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E, d LLL yyyy HH:mm:ss");
		String dateMatch = startTime.format(formatter);

		return dateMatch;
	}	

	/**
	 * Exposes <code>lastDuration</code> as a JMX attribute. 
	 * 
	 * @return The startTime attribute for this instance.
	 */
	
	@ManagedAttribute(description="Length of last run duration in seconds", currencyTimeLimit=15)
	public long getLastDuration() {
		
		return lastDuration;
	}	

	/**
	 * Exposes the length of time this instance has been running as a JMX attribute. This is formatted from a long using this string 
	 * <code>"%d Days, %d Hours, %02d Minutes, %02d Seconds"</code>.
	 * 
	 * @return The length of time this instance has been running.
	 */
	
	@ManagedAttribute(description="Run time of this instance", currencyTimeLimit=15)
	public String getInstanceRunTime() {
		
		String instanceRunTime = "";
    	LocalDateTime now = LocalDateTime.now();
	    long diff = ChronoUnit.SECONDS.between(startTime, now);
	    
	    instanceRunTime = String.format("%d Days, %d Hours, %02d Minutes, %02d Seconds", diff / 86400, (diff / 3600) % 24, (diff % 3600) / 60, (diff % 60));

		return instanceRunTime;
	}	
	
	/**
	 * Exposes the number of seconds the longest running thread took to complete, since this Jorel2 instance was started, as a JMX attribute.
	 * 
	 * @return The number of seconds.
	 */
	
	@ManagedAttribute(description="Max thread run time of this instance", currencyTimeLimit=15)
	public int getMaxThreadRuntime() {
		
		return maxThreadRuntime;
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
		
		this.maxThreadRuntime = maxThreadRuntime;
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
	 * Exposes the eMail from address as a JMX attribute.
	 * 
	 * @return The eMail from address.
	 */
	
	@ManagedAttribute(description="From address for use when sending Jorel2 emails", currencyTimeLimit=15)
	public String getMailFromAddress() {
		
		return mailFromAddress;
	}
	
	/**
	 * Exposes the eMail host address as a JMX attribute.
	 * 
	 * @return The eMail host address.
	 */
	
	@ManagedAttribute(description="Host address for use when sending Jorel2 emails", currencyTimeLimit=15)
	public String getMailHostAddress() {
		
		return mailHostAddress;
	}
	
	/**
	 * Exposes the eMail port number as a JMX attribute.
	 * 
	 * @return The eMail port number.
	 */
	
	@ManagedAttribute(description="Number of quote extractor words by type", currencyTimeLimit=15)
	public String getMailPortNumber() {
		
		return mailPortNumber;
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
	
	/**
	 * Adds an entry to the databaseInterruptions Map to record the interruption.
	 * 
	 * @param threadName The name of the current thread.
	 */
	
	public void addDatabaseInterruption(String threadName) {
		
		databaseInterruptions.put(threadName, LocalDateTime.now().toString());
	}
	
	/**
	 * Exposes the online status as a JMX attribute.
	 * 
	 * @return The eMail port number.
	 */
	
	@ManagedAttribute(description="Connection status - ONLINE or OFFLINE", currencyTimeLimit=15)
	public String getConnectionStatusStr() {
		
		return connectionStatus.toString();
	}
	
	/**
	 * Exposes the online status.
	 * 
	 * @return The eMail port number.
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
	 * for an entry in the <code>activeEvents MAP</code> that matches the event type passed in the single parameter.
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
		
		List<PreferencesDao> preferenceList = PreferencesDao.getPreferencesByRsn(BigDecimal.valueOf(0L), session);
		
		if (preferenceList.size() > 0) {
			preferences = preferenceList.get(0);
		} else {
			decoratedError(INDENT0, "Reading preferences from database.", new IOException("Unable to retried preferences from TNO database."));
		}
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
