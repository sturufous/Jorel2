package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Spring framework service that executes all tasks performed by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class Jorel2Service {
	
	/** Environment variable used for retrieving active profiles */
	@Autowired
    private Environment environment;
    
	/** Configuration object for the active data source. Contains system_name, port etc. */
    @Autowired
    private DataSourceConfig config;
	
	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init(){
        //Jorel2Thread myThread = ctx.getBean(Jorel2Thread.class);
        //taskExecutor.execute(myThread);
    }
    
    @Async
    @Scheduled(fixedDelay = 1000)
    public void scheduleFixedDelayTask() {
        System.out.println("Fixed delay task - " + System.currentTimeMillis() / 1000);
        for (String profileName : environment.getActiveProfiles()) {
            System.out.println("Current host name - " + config.getSystemName());
        }  
    }
}
