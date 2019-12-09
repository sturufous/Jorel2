package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import java.util.Properties;

/**
 * Spring framework controller that executes all tasks performed by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Controller
public class Jorel2Controller {
	
	Jorel2Configuration config;
	
	/**
	 * Spring framework controller that executes all tasks performed by Jorel2.
	 * 
	 * @param config Dependency injected system configuration object.
	 */
	
	public Jorel2Controller(Jorel2Configuration config) {
		
		this.config = config;
		
		System.out.println(config.exportConfig().get("host_name"));
	}
}
