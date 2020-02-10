package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import ca.bc.gov.tno.jorel2.controller.Jorel2Runnable;
import ca.bc.gov.tno.jorel2.controller.QuoteExtractor;
import ca.bc.gov.tno.jorel2.controller.FifoThreadPoolScheduler;

/**
 * Spring Framework configuration for Jorel2. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Configuration
@EnableScheduling
@PropertySource(value = "file:properties/jorel.properties")
@ComponentScan("ca.bc.gov.tno.jorel2")
public class Jorel2Configuration extends Jorel2Root {
	
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
	
    @Bean("jorel2Scheduler")
	@DependsOn({"quoteExtractor"})
    @Scope("singleton")
    public FifoThreadPoolScheduler jorel2Scheduler() {
    	
    	FifoThreadPoolScheduler scheduler = new FifoThreadPoolScheduler();
    	return scheduler;
    }
    
    /**
     * The runnable object that executes the event processing cycle.
     * 
     * @return a new instance of Jorel2Thread.
     */
    @Bean("jorel2Runnable")
    @Scope("prototype")
    @DependsOn({"jorel2Scheduler"})
    public Jorel2Runnable jorel2Thread() {
    	return new Jorel2Runnable();
    }
    
    /**
     * Jorel2Process object that encapsulates data regarding the current instance of Jorel2. The primary datum is the name of the instance.
     * 
     * @return a new instance of Jorel2Process.
     */
    @Bean("getProcess")
    @Scope("singleton")
    public Jorel2Process getProcess() {
    	
		return new Jorel2Process();
    }
}