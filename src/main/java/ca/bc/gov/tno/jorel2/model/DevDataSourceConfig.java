package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;
import java.util.Properties;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root.ConnectionStatus;

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
@Profile("dev")
final class DevDataSourceConfig extends DataSourceConfig {

	/** The system name to use in connecting to the database, e.g., vongole.tno.gov.bc.ca */
	@Value("${dev.dbSystemName}")
	private String systemName;
	
	/** The port number used in connecting to the database, e.g., 1521 */
	@Value("${dev.dbPort}")
	private String port;
	
	/** The sid used in connecting to the database, e.g., tnotst02 */
	@Value("${dev.dbSid}")
	private String sid;
	
	/** The user name used in connecting to the database */
	@Value("${dev.dbUserName}")
	private String userId;
	
	/** The password used in connecting to the database */
	@Value("${dev.dbPassword}")
	private String userPw;
	
	/** The Hibernate dialect to use in communicating with the database */
	@Value("${dev.dbDialect}")	
	private String dialect;
	
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
		
		Configuration config = new Configuration();
		
		if (sessionFactoryOptional.isEmpty()) {
			try {
				logger.debug("Getting development Hibernate session factory.");
							
				Properties settings = new Properties();
		        settings.put(Environment.DRIVER, "oracle.jdbc.OracleDriver");
		        settings.put(Environment.URL, "jdbc:oracle:thin:@" + systemName + ":" + port + ":" + sid);
		        settings.put(Environment.USER, userId);
		        settings.put(Environment.PASS, userPw);
		        settings.put(Environment.DIALECT, dialect);
		        settings.put("checkoutTimeout", CONNECTION_TIMEOUT);
		        //settings.put(Environment.SHOW_SQL, "true");
		        
		        config.setProperties(settings);
		        
		        // Register all Hibernate classes used in Jorel2
		        config.addAnnotatedClass(PreferencesDao.class);
		        config.addAnnotatedClass(EventsDao.class);
		        config.addAnnotatedClass(EventTypesDao.class);
		        config.addAnnotatedClass(NewsItemsDao.class);
		        config.addAnnotatedClass(IssuesDao.class);
		        config.addAnnotatedClass(NewsItemIssuesDao.class);
		        config.addAnnotatedClass(WordsDao.class);
		        config.addAnnotatedClass(NewsItemQuotesDao.class);
		        config.addAnnotatedClass(PagewatchersDao.class);
		        config.addAnnotatedClass(FileQueueDao.class);
		        config.addAnnotatedClass(NewsItemImagesDao.class);
		        config.addAnnotatedClass(PreferencesDao.class);
		        config.addAnnotatedClass(FilesImportedDao.class);
		        
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
	
	/** 
	 * Provides access to the name of the system that this configuration is communicating with 
	 * @return The system name.
	 */
	public String getSystemName() {
		
		return systemName;
	}
}
