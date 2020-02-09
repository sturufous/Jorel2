package ca.bc.gov.tno.jorel2.controller;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Service that executes all tasks performed by Jorel2. It uses a ThreadPoolTaskScheduler to spin off Jorel2 execution
 * threads according to the schedule described in the <code>cron</code> property.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public final class Jorel2Service extends Jorel2Root {
	
    /** Context from which to extract the Jorel2Thread singleton */
    @Inject
    private ApplicationContext ctx;
    
    /** Cron expression retrieved from the properties/jorel.properties file */
	@Value("${cron}")
	private String cronExp;
	
	/** Task scheduler used to manage CronTrigger based scheduling */    
	@Inject
	private FifoThreadPoolScheduler jorelScheduler;
	
	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init() {
        
        /*while(true) {
        	
	        // Shut down if a thread takes more than 30 minutes to complete
	        try {
				Thread.sleep(20000);
				if (jorelScheduler.getMaxRunTime() > MAX_THREAD_RUN_TIME) {
					System.exit(-1);
				}
			} catch (InterruptedException e) {
				logger.error("Thread monitor loop interrupted.", e);
			}
        }*/
    }
    
    public synchronized void notifyThreadComplete(Thread initiator) {
    	
    	jorelScheduler.notifyThreadComplete(initiator);
    	
    }
}
