package ca.bc.gov.tno.jorel2.controller;

import java.util.List;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Component
@Scope("prototype")
class Jorel2Thread extends Jorel2Root implements Runnable {
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Autowired
	private DataSourceConfig config;
	
	/** Environment variable used for retrieving active profiles */
	@Autowired
    private Environment environment;
    
	public void run() {
    	   	
    	while(true) {
	        System.out.println("Fixed delay task - " + System.currentTimeMillis() / 1000);
	    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
	        
	    	if(sessionFactory.isEmpty()) {
	    		throw new IllegalStateException("No session factory provided.");
	    	} else {
		        Session session = sessionFactory.get().openSession();
		    	
		    	session.beginTransaction();
		    	String sql = "FROM PreferencesDao";
		        List<PreferencesDao> results = session.createQuery(sql, PreferencesDao.class).getResultList();
		        
		        for(PreferencesDao pref : results) {
		        	System.out.println(pref);
		        }
		        
		        session.getTransaction().commit();
		        session.close();
	    	}
	        
	        try {
	        	Thread.sleep(2000);
	        } 
	        catch (InterruptedException e) {
	        	e.printStackTrace();
	        }
	    }
    }
}
