package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;
import org.hibernate.SessionFactory;
import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Abstract class allowing the application to leverage Spring's <code>profile</code> insfrastructure for Hibernate configuration.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public abstract class DataSourceConfig extends Jorel2Root {
	
	public abstract String getSystemName();
	public abstract Optional<SessionFactory> getSessionFactory();
	
	protected DataSourceConfig() {
		
		logger.trace("Creating: " + this.getClass().toString());
	}
}