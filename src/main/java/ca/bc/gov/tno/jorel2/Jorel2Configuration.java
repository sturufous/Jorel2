package ca.bc.gov.tno.jorel2;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.ComponentScan;

import java.util.Properties;
import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Spring Framework configuration for Jorel2. Reads the system id (dev, prod etc.) from the <code>systemdescriptor</code> file
 * from which the <code>system_name</code> is retrieved. The properties file for that system is then loaded and assigned to
 * the instance variable <code>config</code>. The <code>Jorel2Configuration</code> object is registered with the application
 * context and is loaded into the <code>Jorel2Controller</code> by constructor dependency injection.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Configuration
@ComponentScan("ca.bc.gov.tno.jorel2")
public class Jorel2Configuration {
	
	private Properties config;
	public static final String PROPERTIES_PATH = "";
	public static final String SYSTEM_DESCRIPTOR = "systemdescriptor.properties";
	//@Inject Environment env;
	
	/**
	 * Loads all configuration information for use throught the system.
	 */
	public Jorel2Configuration(Environment env) {
		
        try {
        	String systemName = env.getRequiredProperty("system");
        	
            // Get the properties for the system retrieved above
            InputStream input = ClassLoader.getSystemClassLoader().getResourceAsStream(systemName + ".properties");
            config = new Properties();
            config.load(input);
            input.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
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