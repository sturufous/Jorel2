package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import java.util.Properties;
import java.io.InputStream;

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
public class Jorel2Configuration {
	
	private Properties config;
	public static final String SYSTEM_DESCRIPTOR = "systemdescriptor.properties";
    private static final Logger logger = LogManager.getLogger(Jorel2Main.class);
	
	/**
	 * Loads all configuration information for use throughout the system.
	 */
	public Jorel2Configuration(Environment env) {
		
		// Empty for now
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

	/**
	 * Provides access to the server configuration object for this execution.
	 * 
	 * @return Configuration properties object
	 */
	
    public Properties exportConfig() {
    	
		return config;
	}
}