package ca.bc.gov.tno.jorel2.controller;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;

/**
 * Spring framework service that executes all tasks performed by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
final class Jorel2Service extends Jorel2Root {
	
    @Inject
    private TaskExecutor taskExecutor;
    
    @Inject
    private ApplicationContext ctx;

	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init(){
        Jorel2Thread myThread = ctx.getBean(Jorel2Thread.class);
        taskExecutor.execute(myThread);    	
    }
}
