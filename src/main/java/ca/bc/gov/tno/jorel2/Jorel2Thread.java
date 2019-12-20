package ca.bc.gov.tno.jorel2;

import java.util.List;
import java.util.Optional;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ca.bc.gov.tno.jorel2.Preferences;

/**
 * Implementation of Runnable interface that performs the long-running Jorel scheduler loop.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */


@Component
@Scope("prototype")
class Jorel2Thread implements Runnable {
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Autowired
	private DataSourceConfig config;
	
	/** Environment variable used for retrieving active profiles */
	@Autowired
    private Environment environment;
    

    @SuppressWarnings({ "rawtypes", "unchecked" })
	public void run() {
    	
    	Preferences prefs = new Preferences();
    	
    	while(true) {
	        System.out.println("Fixed delay task - " + System.currentTimeMillis() / 1000);
	    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
	    	
	    	Session session = sessionFactory.get().openSession();
	    	session.beginTransaction();

	    	// Check database version
	    	String sql = "FROM ca.bc.gov.tno.jorel2.Preferences";

	    	Query<Preferences> query = session.createQuery(sql, Preferences.class);
	        List results = (List) query.getResultList();
	        
	        session.getTransaction().commit();
	        session.close();
	        
	        try {
	        	Thread.sleep(3000);
	        } 
	        catch (InterruptedException e) {
	        	e.printStackTrace();
	        }
	    }
    }
}
