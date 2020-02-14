package ca.bc.gov.tno.jorel2;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedResource;

import ca.bc.gov.tno.jorel2.util.DateUtil;

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
	String startTime = "";

	/** The name of the process */
	@Value("${instanceName}")
	public String instanceName;
	
	/** The maximum time a thread can run before the VM exits */
	@Value("${maxThreadRuntime}")
	public String maxThreadRuntimeString;
	
	private int maxThreadRuntime = 0;
	
	Jorel2Instance() {
		startTime = DateUtil.getTimeNow();
	}
	
	@PostConstruct
	public void init() {
		maxThreadRuntime = Integer.valueOf(maxThreadRuntimeString);
	}
	
	/**
	 * Get the name of the process this execution is running on.
	 * 
	 * @return The name of the current process
	 */
	
	@ManagedAttribute(description="Name of this Jorel instance", currencyTimeLimit=15)
	public String getInstanceName() {
		
		return instanceName;
	}
	
	public void addThreadDuration(String threadName, long duration) {
		
		threadDurations.put(threadName, Long.valueOf(duration));
		
	}

	public void incrementArticleCount(String source, int count) {
		
		if (articleCounts.containsKey(source)) {
			Integer sourceCount = articleCounts.get(source);
			sourceCount += count;
			articleCounts.replace(source, sourceCount);
		} else {
			articleCounts.put(source, count);
		}
	}

	@ManagedAttribute(description="Maximum thread duration since startup", currencyTimeLimit=15)
	public Long getMaxThreadDurationSeconds() {
		
		OptionalLong max = threadDurations.values().stream().mapToLong(v -> v).max();
		return max.getAsLong();
	}

	@ManagedAttribute(description="Minimum thread duration since startup", currencyTimeLimit=15)
	public Long getMinThreadDurationSeconds() {
		
		OptionalLong min = threadDurations.values().stream().mapToLong(v -> v).min();
		return min.getAsLong();
	}

	@ManagedAttribute(description="Mean thread duration since startup", currencyTimeLimit=15)
	public Double getMeanThreadDurationSeconds() {
		
		OptionalDouble mean = threadDurations.values().stream().mapToLong(v -> v).average();
		return mean.getAsDouble();
	}
	
	@ManagedAttribute(description="Number of thread runs included in stats", currencyTimeLimit=15)
	public int getThreadCompleteCount() {
		
		return threadDurations.size();
	}
	
	@ManagedAttribute(description="Number of articles added by source", currencyTimeLimit=15)
	public Map<String, Integer> getArticleCounts() {
		
		return articleCounts;
	}	
	
	@ManagedAttribute(description="Start time of this instance", currencyTimeLimit=15)
	public String getStartTime() {
		
		return startTime;
	}	

	@ManagedAttribute(description="Start time of this instance", currencyTimeLimit=15)
	public int getMaxThreadRuntime() {
		
		return maxThreadRuntime;
	}	
	
	@ManagedOperation(description="Add two numbers")
	  @ManagedOperationParameters({
	    @ManagedOperationParameter(name = "maxThreadRuntime", description = "How long a thread can run before the VM exits."),
	})
	public void setMaxThreadruntime(int maxThreadRuntime) {
		
		this.maxThreadRuntime = maxThreadRuntime;
	}
}
