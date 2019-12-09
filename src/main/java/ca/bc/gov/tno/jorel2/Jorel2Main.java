/**
 * This is the main entry point for the Java 13 rewrite of Jorel. 
 */

package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.SimpleCommandLinePropertySource;

/**
 * Main program for Jorel2. This stand-alone application retrieves its configuration information from files in the
 * <code>properties</code> directory and does not utilize the args parameter.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */
public class Jorel2Main {
	
	/**
	 * Configures the Spring IOC application context and refreshes the context.
	 * 
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		
		System.out.println("hello world!");
	    SimpleCommandLinePropertySource clps = new SimpleCommandLinePropertySource(args);
	    
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(clps);
        ctx.register(Jorel2Configuration.class);
        ctx.refresh();
	}
}
