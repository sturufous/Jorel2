/**
 * This is the main entry point for the Java 13 rewrite of Jorel. 
 */

package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main program for Jorel2. This stand-alone application retrieves its configuration information from files in the
 * <code>properties</code> directory and does not utilize the args parameter.
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
		    SimpleCommandLinePropertySource clps = new SimpleCommandLinePropertySource(args);
		    logger.debug("Log this!");
	
	        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
	        ctx.getEnvironment().getPropertySources().addFirst(clps);
	        ctx.register(Jorel2Configuration.class);
	        ctx.refresh();
	    } catch (Exception ex) {
	        logger.error("Occurred when initializing the application.", ex);
	    }
	}
}
