package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;

/**
 * Spring framework service that executes all tasks performed by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class Jorel2Service {
	
    @Autowired
    private ApplicationContext ctx;
    
    @Autowired
    private TaskExecutor taskExecutor;
	
	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init(){
        Jorel2Thread myThread = ctx.getBean(Jorel2Thread.class);
        taskExecutor.execute(myThread);
    }
}
