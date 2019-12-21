package ca.bc.gov.tno.jorel2.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
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
class DevDataSourceConfig extends DataSourceConfig {

	public String systemName = "vongole.tno.gov.bc.ca";
	
	private static StandardServiceRegistry registry = null;
	private static SessionFactory sessionFactory = null;
	
	public Optional<SessionFactory> getSessionFactory() {
		
		Configuration config = new Configuration();
		
		if (sessionFactory == null)
		try {
			logger.trace("Getting development Hibernate session factory.");
						
			Properties settings = new Properties();
	        settings.put(Environment.DRIVER, "oracle.jdbc.OracleDriver");
	        settings.put(Environment.URL, "jdbc:oracle:thin:@142.34.249.240:1521:tnotst02");
	        settings.put(Environment.USER, "tno");
	        settings.put(Environment.PASS, "tn29tst");
	        settings.put(Environment.DIALECT, "org.hibernate.dialect.Oracle12cDialect");
	        settings.put(Environment.SHOW_SQL, "true");
	        
	        config.setProperties(settings);
	        config.addAnnotatedClass(PreferencesDao.class);
	        
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
