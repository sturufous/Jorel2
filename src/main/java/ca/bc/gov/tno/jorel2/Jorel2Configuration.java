package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableMBeanExport;

import ca.bc.gov.tno.jorel2.controller.Jorel2Runnable;
import ca.bc.gov.tno.jorel2.controller.QuoteExtractor;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
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
	
	/**
	 * Create the scheduler object that maintains control over the lifecycle of Jorel2Runnable objects. This scheduler provides
	 * the entry point for all Jorel2 execution cycles. There is no explicit method call from anywhere else in the package.
	 * 
	 * @return 
	 */
    @Bean("jorel2Scheduler")
	@DependsOn({"quoteExtractor"})
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
    @DependsOn({"jorel2Scheduler", "jaxbContext"})
    public Jorel2Runnable jorel2Thread() {
    	return new Jorel2Runnable();
    }
    
    /**
     * 
     * @return
     */
    @Bean("jaxbContext")
    @Scope("singleton")
    public Unmarshaller jaxbUnmarshaller() {
    	Unmarshaller unmarshaller = null;
    	
		try {
			JAXBContext context = JAXBContext.newInstance(Rss.class);
	    	unmarshaller = context.createUnmarshaller();
		} catch (JAXBException e) {
			logger.error("Instantiating the RSS feed unmarshaller.", e);
		}
		
		return unmarshaller;
    }

    /**
     * Jorel2Instance object that encapsulates data regarding the current instance of Jorel2. The primary datum is the name of the instance.
     * 
     * @return a new instance of Jorel2Process.
     */
    @Bean("getInstance")
    @Scope("singleton")
    public Jorel2Instance getInstance() {
    	
		return new Jorel2Instance();
    }
}