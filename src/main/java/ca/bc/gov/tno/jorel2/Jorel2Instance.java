package ca.bc.gov.tno.jorel2;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedResource;
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

public class Jorel2Instance {
	
	Map<String, Long> threadDurations = new ConcurrentHashMap<>();
	Map<String, Integer> articleCounts = new ConcurrentHashMap<>();
	LocalDateTime startTime = null;

	/** The name of the current instance */
	@Value("${instanceName}")
	public String instanceName;
	
	/** The maximum time a thread can run before the VM exits */
	@Value("${maxThreadRuntime}")
	public String maxThreadRuntimeString;
	
	/** Integer version of maxThreadRuntimeString which is exposed as a JMX managed operation */
	private int maxThreadRuntime = 0;
	
	/** Allows charting of individual run times in VisualVM */
	private long lastDuration = 0;
	
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
	 * Get the name of the instance this execution is running on.
	 * 
	 * @return The name of the current instance
	 */
	
	@ManagedAttribute(description="Name of this Jorel instance", currencyTimeLimit=15)
	public String getInstanceName() {
		
		return instanceName;
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
	 * Allows the <code>maxThreadRuntime<code> JMX attribute to be set remotely.
	 */
	@ManagedOperation(description="Add two numbers")
	  @ManagedOperationParameters({
	    @ManagedOperationParameter(name = "maxThreadRuntime", description = "How long a thread can run before the VM exits."),
	})
	public void setMaxThreadruntime(int maxThreadRuntime) {
		
		this.maxThreadRuntime = maxThreadRuntime;
	}
}
