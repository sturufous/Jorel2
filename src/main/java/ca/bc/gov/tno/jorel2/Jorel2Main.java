/**
 * This is the main entry point for the Java 13 rewrite of Jorel. 
 */

package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main program for Jorel2. .
 * 
 * This package will follow these conventions:
 * <ul>
 * <li>There will be no conditional logic in constructors, therefore they will not be unit tested.
 * </ul>
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */
public class Jorel2Main {
	
    private static final Logger logger = LogManager.getLogger(Jorel2Main.class);
    
	/**
	 * Configures the Spring IOC application context and refreshes the context.
	 * 
	 * @param args Not used.
	 */
    
	public static void main(String[] args) {
		
		try {
			System.out.println("hello world!");
			
			// Get the Spring environment
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(); 
            ConfigurableEnvironment env = ctx.getEnvironment(); 
            
	        logger.trace("log this!");
        	// Set the active DataSourceConfig to that in the command arguments
	        env.setActiveProfiles(args[0]);
	        
	        // Register the configurator, which performs a component scan and other initialization functions
	        ctx.register(Jorel2Configuration.class);
	        ctx.refresh();
	    } catch (Exception ex) {
	        logger.error("Occurred when initializing the application.", ex);
	    }
	}
}