package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import ca.bc.gov.tno.jorel2.controller.Jorel2Service;
import ca.bc.gov.tno.jorel2.controller.Jorel2Thread;
import ca.bc.gov.tno.jorel2.controller.QuoteExtractor;

/**
 * Spring Framework configuration for Jorel2. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Configuration
@ComponentScan("ca.bc.gov.tno.jorel2")
public class Jorel2Configuration extends Jorel2Root {
	

	@Bean("jorel2Service") 
	@DependsOn({"quoteExtractor", "taskExecutor"})
	public Jorel2Service jorel2Service() {
		return new Jorel2Service();
	}
	
	@Bean("quoteExtractor")
	public QuoteExtractor quoteExtractor() {
		return new QuoteExtractor();
	}
	
    @Bean("taskExecutor")
    public TaskExecutor threadPoolTaskExecutor() {
    	
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("default_task_executor_thread");
        executor.initialize();
        return executor;
    }
    
    @Bean("jorel2Thread")
    public Jorel2Thread jorel2Thread() {
    	return new Jorel2Thread();
    }
    
    @Bean("getProcess")
    public Jorel2Process getProcess() {
    	
		return new Jorel2Process("jorel");
    }
}