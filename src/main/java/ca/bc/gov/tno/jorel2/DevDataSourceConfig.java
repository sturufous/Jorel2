package ca.bc.gov.tno.jorel2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

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
		
		if (sessionFactory == null)
		try {
			logger.trace("Getting development Hibernate session factory.");
			
			StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
			
			Map<String, String> settings = new HashMap<>();
	        settings.put(Environment.DRIVER, "oracle.jdbc.OracleDriver");
	        settings.put(Environment.URL, "jdbc:oracle:thin:@142.34.249.240:1521:tnotst02");
	        settings.put(Environment.USER, "tno");
	        settings.put(Environment.PASS, "tn29tst");
	        settings.put(Environment.DIALECT, "org.hibernate.dialect.Oracle12cDialect");
	        
	        registryBuilder.applySettings(settings);
	
	        // Create registry
	        registry = registryBuilder.build();
	
	        // Create MetadataSources
	        MetadataSources sources = new MetadataSources(registry);
	
	        // Create Metadata
	        Metadata metadata = sources.getMetadataBuilder().build();
	
	        // Create SessionFactory
	        sessionFactory = metadata.getSessionFactoryBuilder().build();
	
	     	} catch (Exception e) {
	    	 e.printStackTrace();
	        	if (registry != null) {
	        		StandardServiceRegistryBuilder.destroy(registry);
	        }
	    }
	
        return Optional.of(sessionFactory);
	}
	
	public void setup() {
	}
	
	public String getSystemName() {
		
		return systemName;
	}
}
