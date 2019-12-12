package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Main program for Jorel2. This stand-alone application retrieves its configuration information from files in the
 * <code>properties</code> directory and does not utilize the args parameter.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Scope("prototype")
class Jorel2Thread implements Runnable {
	
	@Autowired
	private Jorel2Configuration config;
	
    @Override
    public void run() {
        System.out.println(config.exportConfig().get("host_name"));
    }
}
