package ca.bc.gov.tno.jorel2;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;

/**
 * Abstract class allowing the application to leverage Spring's <code>profile</code> insfrastructure for Hibernate configuration.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public abstract class DataSourceConfig {
	
    protected static final Logger logger = LogManager.getLogger(Jorel2Main.class);
	public abstract String getSystemName();
	public abstract Optional getSessionFactory();
	
	protected DataSourceConfig() {
		
		logger.trace("Creating: " + this.getClass().toString());
	}
}