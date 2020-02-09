package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import ca.bc.gov.tno.jorel2.controller.Jorel2Service;
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
	 * Instantiate the Jorel2Service, the object that oversees the execution of all Jorel2 threads. This object must not run until
	 * the quoteExtractor and taskScheduler beans have been instantiated.
	 * 
	 * @return a new instance of Jorel2Service.
	 */
	@Bean("jorel2Service") 
	@DependsOn({"quoteExtractor"})
	public Jorel2Service jorel2Service() {
		return new Jorel2Service();
	}
	
	/**
	 * Instantiate the QuoteExtractor, the object that isolates quotes and their speakers from RSS based news items. Initialization of
	 * this object involves the loading of over 9,000 words from the TNO.WORDS table. the initialization performed here is lazy. Actual
	 * initialization is performed in the processEvents() method of RssEventProcessor and SyndicationEventProcessor.
	 * 
	 * @return a new instance of QuoteExtractor.
	 */
	@Bean("quoteExtractor")
	public QuoteExtractor quoteExtractor() {
		return new QuoteExtractor();
	}
	
    @Bean("jorel2Scheduler")
	@DependsOn({"jorel2Runnable"})
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
    public Jorel2Runnable jorel2Thread() {
    	return new Jorel2Runnable();
    }
    
    /**
     * Jorel2Process object that encapsulates data regarding the current instance of Jorel2. The primary datum is the name of the instance.
     * 
     * @return a new instance of Jorel2Process.
     */
    @Bean("getProcess")
    public Jorel2Process getProcess() {
    	
		return new Jorel2Process();
    }
}