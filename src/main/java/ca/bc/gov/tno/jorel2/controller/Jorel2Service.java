package ca.bc.gov.tno.jorel2.controller;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Service that executes all tasks performed by Jorel2. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public final class Jorel2Service extends Jorel2Root {
	
    /** Context from which to extract the Jorel2Thread singleton */
    @Inject
    private ApplicationContext ctx;
    
	/** Task scheduler used to manage CronTrigger based scheduling */    
	@Inject
	private FifoThreadPoolScheduler jorelScheduler;
	
	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init() {
        
    }
    
    public synchronized void notifyThreadComplete(Thread initiator) {
    	
    	jorelScheduler.notifyThreadComplete(initiator);
    	
    }
}
