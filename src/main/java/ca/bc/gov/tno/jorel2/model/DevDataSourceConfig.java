package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import org.hibernate.cfg.Configuration;

/**
 * Development configuration for Hibernate data access.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Profile("dev")
final class DevDataSourceConfig extends DataSourceConfig {

	private String systemName = "vongole.tno.gov.bc.ca";
	private String port = "1521";
	private String sid = "tnotst02";
	private String systemIp = "142.34.249.240";
	
	private static StandardServiceRegistry registry = null;
	private static SessionFactory sessionFactory = null;
	
	public Optional<SessionFactory> getSessionFactory() {
		
		Configuration config = new Configuration();
		
		if (sessionFactory == null)
		try {
			logger.trace("Getting development Hibernate session factory.");
						
			Properties settings = new Properties();
	        settings.put(Environment.DRIVER, "oracle.jdbc.OracleDriver");
	        settings.put(Environment.URL, "jdbc:oracle:thin:@" + systemName + ":" + port + ":" + sid);
	        settings.put(Environment.USER, "tno");
	        settings.put(Environment.PASS, "tn29tst");
	        settings.put(Environment.DIALECT, "org.hibernate.dialect.Oracle12cDialect");
	        settings.put(Environment.SHOW_SQL, "true");
	        
	        config.setProperties(settings);
	        
	        // Register all Hibernate classes used in Jorel2
	        config.addAnnotatedClass(PreferencesDao.class);
	        config.addAnnotatedClass(EventsDao.class);
	        config.addAnnotatedClass(EventTypesDao.class);
	        config.addAnnotatedClass(NewsItemsDao.class);
	        config.addAnnotatedClass(IssuesDao.class);
	        config.addAnnotatedClass(NewsItemIssuesDao.class);
	        
	        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(config.getProperties()).build();
	        
	        sessionFactory = config.buildSessionFactory(serviceRegistry);
	     } catch (Exception e) {
	    	 logger.error(e);
	     }
	
        return Optional.of(sessionFactory);
	}
	
	public void setup() {
	}
	
	public String getSystemName() {
		
		return systemName;
	}
}
