/**
 * This is the main entry point for the Java 13 rewrite of Jorel. 
 */

package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Main program for Jorel2.
 * 
 * This package will follow these conventions:
 * <ul>
 * <li>There will be no conditional logic in constructors, therefore they will not be unit tested.
 * <li>All Hibernate named queries are defined in Jore2Root
 * </ul>
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

//@EnableAspectJAutoProxy
public final class Jorel2Main extends Jorel2Root {
	   
	/**
	 * Configures the Spring IOC application context and refreshes the context.
	 * 
	 * @param args Not used.
	 */
    
	public static void main(String[] args) {
		
		try {
			System.out.println("hello world!");
			logger.trace("/============================= Execution Start ===============================/");
			
			// Get the Spring environment
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(); 
            ConfigurableEnvironment env = ctx.getEnvironment(); 
                        
        	// Set the active DataSourceConfig to that identified in the command arguments. Assume a single profile argument.
        	String dbProfile = System.getProperty("dbProfile");
	        if(dbProfile.length() == 0) {
	        	throw new IllegalArgumentException("Database profile name missing from System.properties.");
	        } else {
	        	env.setActiveProfiles(dbProfile);
	        }
	        
	        // Register the configurator, which performs a component scan and other initialization functions
	        ctx.register(Jorel2Configuration.class);
	        ctx.refresh();
	    } catch (Exception ex) {
	        logger.error("Occurred when initializing the application.", ex);
	    }
	}
}