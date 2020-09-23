/**
 * This is the main entry point for the Java 13 rewrite of Jorel. 
 */

package ca.bc.gov.tno.jorel2;

import org.hibernate.ConnectionReleaseMode;
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
 * @version 0.0.1 All previous updates.
 * @version 0.0.2 03 Sep 20 - Changing the logging rollover policy from size based to time based.
 * @version 0.0.13 04 Sep 20 - Adding small screen device format to Jorel reports.
 * @version 0.0.14 15 Sep 20 - Changing connection release policy to ConnectionReleaseMode.ON_CLOSE.
 * @version 0.0.15 16 Sep 20 - Fix appearance of "<**images**>" in alert emails.
 * @version 0.0.16 21 Sep 20 - Adding Channelwatcher event handling
 */

//@EnableAspectJAutoProxy
//@SuppressWarnings("")
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
            @SuppressWarnings("resource")
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