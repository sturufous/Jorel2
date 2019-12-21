package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

/**
 * Spring Framework configuration for Jorel2. Reads the system id (dev, prod etc.) from the SimpleCommandLinePropertySource set 
 * in the main program. The properties file for that system is then loaded and assigned to the instance variable <code>config</code>. 
 * The <code>Jorel2Configuration</code> object is registered with the application context and is loaded into the 
 * <code>Jorel2Controller</code> by constructor dependency injection.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Configuration
@EnableScheduling
@ComponentScan("ca.bc.gov.tno.jorel2")
public class Jorel2Configuration extends Jorel2Root {
	
	/**
	 * Loads all configuration information for use throughout the system.
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