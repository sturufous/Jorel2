package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;
import java.util.Properties;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import org.hibernate.cfg.Configuration;

/**
 * Development configuration for Hibernate data access. The instantiation of this class is managed automatically by Spring's <code>profiles</code>
 * feature. On invocation, Jorel2 obtains the data-source profile name from the command line and sets this as the active profile. If the profile
 * name matches the one in the @Profile annotation below ('dev') an object of this class will be created and added to the Spring context.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Profile("prod")
final class ProdDataSourceConfig extends DataSourceConfig {

	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration c;
		
	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2Instance instance;
	
	/** Cached SessionFactory used to create a new session for each Jorel2Runnable thread */
	private Optional<SessionFactory> sessionFactoryOptional = Optional.empty();
	
	/**
	 * This method initializes the Hibernate framework for use throughout the execution of this Jorel2 invocation. It creates a properties object
	 * containing the connection parameters, and adds this to the Hibernate <code>Configuration</code> object. All Hibernate entities are then 
	 * added to the configuration and a <code>ServiceRegistry</code> object is constructed. Finally the thread-safe <code>SessionFactory</code>
	 * is built using the compiled configuration information.
	 * 
	 * @return An Optional object which either contains a SessionFactory or is Empty().
	 */
	public Optional<SessionFactory> getSessionFactory() {
		
		if (sessionFactoryOptional.isEmpty()) {
			try {
				logger.debug("Getting production Hibernate session factory.");
							
				Properties settings = populateSettings(c.getString("prod.dbSystemName"), c.getString("prod.dbPort"), c.getString("prod.dbSid"), 
						c.getString("prod.dbUserName"), c.getString("prod.dbPassword"), c.getString("prod.dbDialect"));
		        Configuration config = registerEntities();
		        config.setProperties(settings);
		        
		        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(config.getProperties()).build();
		        SessionFactory sessionFactory = config.buildSessionFactory(serviceRegistry);
		        sessionFactoryOptional = Optional.of(sessionFactory);
		        instance.setConnectionStatus(ConnectionStatus.ONLINE);
		     } catch (HibernateException  e) {
		    	 logger.error("Getting the development Hibernate session factory. Going offline.", e);
		    	 instance.setConnectionStatus(ConnectionStatus.OFFLINE);
		     }
		}
		
        return sessionFactoryOptional;
	}
	
	/**
	 * Ensure a clean shutdown of the level 2 cache.
	 */
	@PreDestroy
	private void shutDown() {
		
		sessionFactoryOptional.get().close();
	}
}
