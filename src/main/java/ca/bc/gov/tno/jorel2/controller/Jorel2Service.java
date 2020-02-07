package ca.bc.gov.tno.jorel2.controller;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import ca.bc.gov.tno.jorel2.Jorel2Root;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Service that executes all tasks performed by Jorel2. It uses a ThreadPoolTaskScheduler to spin off Jorel2 execution
 * threads according to the schedule described in the <code>cron</code> property.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public final class Jorel2Service extends Jorel2Root {
	
	/** Task scheduler used to manage CronTrigger based scheduling */
    @Inject
    private ThreadPoolTaskScheduler taskScheduler;
    
    /** Context from which to extract the Jorel2Thread singleton */
    @Inject
    private ApplicationContext ctx;
    
    /** Cron expression retrieved from the properties/jorel.properties file */
	@Value("${cron}")
	private String cronExp;

	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init() {
    	CronTrigger cronTrigger = new CronTrigger(cronExp);
        Jorel2Thread myThread = ctx.getBean(Jorel2Thread.class);
        taskScheduler.schedule(myThread, cronTrigger);    	
    }
}
