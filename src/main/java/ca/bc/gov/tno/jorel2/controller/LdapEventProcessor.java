package ca.bc.gov.tno.jorel2.controller;


import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.naming.AuthenticationException;
import javax.naming.AuthenticationNotSupportedException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;

/**
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class LdapEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/**
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting LDAP event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        
	        		ldapEvent(currentEvent, session);
	        		//updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
	        	}
	        }
	        
    		decoratedTrace(INDENT1, "Completed LDAP event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing  LDAP event entries.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	private void ldapEvent(EventsDao currentEvent, Session session) {
		
		String url = "ldap://umbrella.idir.bcgov:389";
		
		Hashtable env = new Hashtable();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, url);
		env.put(Context.SECURITY_AUTHENTICATION, "simple");
		env.put(Context.SECURITY_PRINCIPAL, "idir\\TNOJOREL");
		env.put(Context.SECURITY_CREDENTIALS, "SP()ang949");
		
		try {
		    DirContext ctx = new InitialDirContext(env);
		    System.out.println("connected");
		    System.out.println(ctx.getEnvironment());
		    
		    Attributes matchAttrs = new BasicAttributes(false);
		    matchAttrs.put(new BasicAttribute("mail"));
		    
	        SearchControls controls = new SearchControls();
	        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

		    NamingEnumeration answer = ctx.search("ou=bcgov,dc=idir,dc=bcgov", "(mail=*Ryckman*)", controls);
		    
		    while (answer.hasMore()) {
		        SearchResult sr = (SearchResult)answer.next();
		        System.out.println(">>>" + sr.getName());
		        processEntry(sr);
		        //printAttrs(sr.getAttributes());
		    }
		    
		    ctx.close();
		 
		} catch (AuthenticationNotSupportedException ex) {
		    System.out.println("The authentication is not supported by the server");
		} catch (AuthenticationException ex) {
 		    System.out.println("incorrect password or username");
		} catch (NamingException ex) {
		    System.out.println("error when trying to create the context");
		}
	}
	
	private void processEntry(SearchResult sr) {

		Attributes attrs = sr.getAttributes();
		Attribute email = attrs.get("mail");
		Attribute displayName = attrs.get("displayName");

		int x = 1;
	}
}