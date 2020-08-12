package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableMBeanExport;
import ca.bc.gov.tno.jorel2.controller.Jorel2Runnable;
import ca.bc.gov.tno.jorel2.controller.QuoteExtractor;
import ca.bc.gov.tno.jorel2.jaxb.JaxbUnmarshallerFactory;
import ca.bc.gov.tno.jorel2.controller.FifoThreadQueueScheduler;

/**
 * Spring Framework configuration for Jorel2. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Configuration
@EnableScheduling
@EnableMBeanExport
//@PropertySource(value = "file:properties/jorel.properties")
@ComponentScan("ca.bc.gov.tno.jorel2")
public class Jorel2Configuration extends Jorel2Root {
	
	/**
	 * Creates an Apache Commons PropertiesConfiguration bean which loads the contents of jorel.properties and continually monitors it
	 * for changes. If changes occur, the new property values are reloaded for use by subsequent events. The following properties will be
	 * updated on the fly, along with the others, but because they are only used once when Jorel2 is launched these changes will not take
	 * effect until Jorel2 is restarted:
	 * 
	 * <ul>
	 * <li>All properties relating to the dev and prod database connections</li>
	 * <li>The cron expression that describes the thread execution schedule</li>
	 * </ul>
	 * 
	 * @return
	 * @throws ConfigurationException
	 */
	@Bean
	public PropertiesConfiguration propertiesConfiguration() throws ConfigurationException {
	    String filePath = "properties/jorel.properties";
	    PropertiesConfiguration configuration = new PropertiesConfiguration(
	      new File(filePath));
	    configuration.setReloadingStrategy(new FileChangedReloadingStrategy());
	    return configuration;
	}
	
	/**
	 * Instantiate the QuoteExtractor, the object that isolates quotes and their speakers from RSS based news items. Initialization of
	 * this object involves the loading of over 9,000 words from the TNO.WORDS table. 
	 * 
	 * @return a new instance of QuoteExtractor.
	 */
	@Bean("quoteExtractor")
    @Scope("singleton")
	public QuoteExtractor quoteExtractor() {
		return new QuoteExtractor();
	}
	
	/**
	 * Create the scheduler object that maintains control over the lifecycle of Jorel2Runnable objects. This scheduler provides
	 * the entry point for all Jorel2 execution cycles. There is no explicit method call from anywhere else in the package.
	 * 
	 * @return The FifoThreadQueueScheduler singleton that maintains control over thread creation and scheduling.
	 */
    @Bean("jorel2Scheduler")
    @Scope("singleton")
    public FifoThreadQueueScheduler jorel2Scheduler() {
    	
    	FifoThreadQueueScheduler scheduler = new FifoThreadQueueScheduler();
    	return scheduler;
    }
    
    /**
     * The runnable object that executes the event processing cycle.
     * 
     * @return a new instance of Jorel2Thread.
     */
    @Bean("jorel2Runnable")
    @Scope("prototype")
    @DependsOn({"jorel2Scheduler", "jaxbUnmarshallers", "jorel2Instance", "quoteExtractor"})
    public Jorel2Runnable jorel2Thread() {
    	return new Jorel2Runnable();
    }
    
    /**
     * Creates a JaxbUnmrshallerFactory object containing unmarshallers for all supported XML import formats (e.g. Nitf, Rss)
     * 
     * @return The JaxbUnmarshallerFactory created.
     */
    @Bean("jaxbUnmarshallers")
    @Scope("singleton")
    public JaxbUnmarshallerFactory jaxbUnmarshallerFactory() {
    			
		return new JaxbUnmarshallerFactory();
    }

    /**
     * Jorel2Instance object that encapsulates data regarding the current instance of Jorel2. The primary datum is the name of the instance.
     * 
     * @return a new instance of Jorel2Process.
     */
    @Bean("jorel2Instance")
    @Scope("singleton")
    public Jorel2ServerInstance getInstance() {
    	
		return new Jorel2ServerInstance();
    }
}