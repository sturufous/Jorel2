package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Production configuration for Hibernate data access. The instantiation of this class is managed automatically by Spring's <code>profiles</code>
 * feature. On invocation, Jorel2 obtains the data-source profile name from the command line and sets this as the active profile. If the profile
 * name matches the one in the @Profile annotation below ('prod') an object of this class will be created and added to the Spring context.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */


@Component
@Profile("prod")
final class ProdDataSourceConfig extends DataSourceConfig {

	/** The system name to use in connecting to the database, e.g., vongole.tno.gov.bc.ca */
	@Value("${prod.dbSystemName}")
	private String systemName;
	
	/** The port number used in connecting to the database, e.g., 1521 */
	@Value("${prod.dbPort}")
	private String port;
	
	/** The sid used in connecting to the database, e.g., tnotst02 */
	@Value("${prod.dbSid}")
	private String sid;
	
	/** The user name used in connecting to the database */
	@Value("${prod.dbUserName}")
	private String userId;
	
	/** The password used in connecting to the database */
	@Value("${prod.dbPassword}")
	private String userPw;
	
	/** The Hibernate dialect to use in communicating with the database */
	@Value("${prod.dbDialect}")	
	private String dialect;
	
	private static StandardServiceRegistry registry = null;
	private static SessionFactory sessionFactory = null;
	
	/**
	 * This method initializes the Hibernate framework for use throughout the execution of this Jorel2 invocation. It creates a properties object
	 * containing the connection parameters, and adds this to the Hibernate <code>Configuration</code> object. All Hibernate entities are then 
	 * added to the configuration and a <code>ServiceRegistry</code> object is constructed. Finally the thread-safe <code>SessionFactory</code>
	 * is built using the compiled configuration information.
	 * 
	 * @return An Optional object which either contains a SessionFactory or is Empty().
	 */
	
	public Optional<SessionFactory> getSessionFactory() {
		
		logger.trace("Getting production Hibernate session factory.");
		
		return Optional.empty();
	}

	public String getSystemName() {
		
		return systemName;
	}
}