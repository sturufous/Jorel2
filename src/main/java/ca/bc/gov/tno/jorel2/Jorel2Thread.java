package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Scope("prototype")
class Jorel2Thread implements Runnable {
	
    @Autowired
    private Environment environment;
    
    @Autowired
    private DataSourceConfig config;
	
	/**
	 * Start the scheduler loop.
	 */
	
    @Override
    public void run() {
    	System.out.println("Hi!");
        for (String profileName : environment.getActiveProfiles()) {
            System.out.println("Current host name - " + config.getSystemName());
        }  
    }
}
