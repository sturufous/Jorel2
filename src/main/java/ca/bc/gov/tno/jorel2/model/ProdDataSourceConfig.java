package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;

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
final class ProdDataSourceConfig extends DataSourceConfig {

	public String systemName = "scorelli.tno.gov.bc.ca";
	
	public Optional<SessionFactory> getSessionFactory() {
		
		logger.trace("Getting production Hibernate session factory.");
		
		return Optional.empty();
	}

	public String getSystemName() {
		
		return systemName;
	}
}
