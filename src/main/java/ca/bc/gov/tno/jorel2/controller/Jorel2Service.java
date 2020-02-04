package ca.bc.gov.tno.jorel2.controller;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Spring framework service that executes all tasks performed by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public final class Jorel2Service extends Jorel2Root {
	
    @Inject
    private ThreadPoolTaskScheduler taskScheduler;
    
    @Inject
    private ApplicationContext ctx;

	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init(){
    	CronTrigger cronTrigger = new CronTrigger("0/30 * 2-23 * * ?");
        Jorel2Thread myThread = ctx.getBean(Jorel2Thread.class);
        taskScheduler.schedule(myThread, cronTrigger);    	
    }
}
