package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;

/**
 * Spring Framework configuration for Jorel2. 
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Configuration
@ComponentScan("ca.bc.gov.tno.jorel2")
public class Jorel2Configuration extends Jorel2Root {
	
	@Inject
	
	/**
	 * Loads all configuration information for use throughout the system.
	 * 
	 * @param env - Constructor injection of the current spring Environment.
	 */
	public Jorel2Configuration(Environment env) {
		
		// Unused for now

	}
	
    @Bean
    public TaskExecutor threadPoolTaskExecutor() {
    	
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("default_task_executor_thread");
        executor.initialize();
        return executor;
    }
}