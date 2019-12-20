package ca.bc.gov.tno.jorel2;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * Production configuration for Hibernate data access.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Profile("prod")
class ProdDataSourceConfig extends DataSourceConfig {

	public String systemName = "scorelli.tno.gov.bc.ca";
	
	public Optional getSessionFactory() {
		
		logger.trace("Getting production Hibernate session factory.");
		
		return Optional.empty();
	}

	public String getSystemName() {
		
		return systemName;
	}
}
