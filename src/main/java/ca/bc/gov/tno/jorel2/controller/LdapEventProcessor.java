package ca.bc.gov.tno.jorel2.controller;


import java.io.IOException;
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
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.Jorel2Root.EventType;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.LdapAddressesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;

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
    		
    		if (instance.isExclusiveEventActive(EventType.LDAP)) {
    			decoratedTrace(INDENT1, "LDAP event processing already active. Skipping.");    			
    		} else {
    			instance.addExclusiveEvent(EventType.LDAP);
    			
		        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
		        for (Object[] entityPair : results) {
		        	if (entityPair[0] instanceof EventsDao) {
		        		EventsDao currentEvent = (EventsDao) entityPair[0];
		        
		        		DbUtil.updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
		        		ldapEvent(currentEvent, session);
		        	}
		        }
		        
		        instance.removeExclusiveEvent(EventType.LDAP);
    		}
	        
    		decoratedTrace(INDENT1, "Completed LDAP event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing  LDAP event entries.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	private void ldapEvent(EventsDao currentEvent, Session session) {
		
		try {		    
		    byte[] cookie = null;
		    int total;
		    int pageCount = 1;
		    
		    LdapAddressesDao.deleteAllRecords(session);
		    
		    LdapContext ctx = getPagingLdapContext();
	        SearchControls searchControls = new SearchControls();
	        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
	        
	        do {
			    NamingEnumeration answer = ctx.search("ou=bcgov,dc=idir,dc=bcgov", "(&(mail=*)(displayName=*))", searchControls);
			    
			    // Process one page of new LDAP entries, create LdapAddressesDao for each.
			    session.beginTransaction();
			    while (answer.hasMore()) {
			        SearchResult sr = (SearchResult) answer.next();
			        //System.out.println(">>>" + sr.getName());
			        processEntry(sr, session);
			    }
			    session.getTransaction().commit();
			    
		        Control[] returnedControls = ctx.getResponseControls();
		        if (returnedControls != null) {
		            for (int i = 0; i < returnedControls.length; i++) {
		                if (returnedControls[i] instanceof PagedResultsResponseControl) {
		                    PagedResultsResponseControl prrc = (PagedResultsResponseControl) returnedControls[i];
		                    total = prrc.getResultSize();
		                    cookie = prrc.getCookie();
		                }
		            }
		        }
		        
		        System.out.println("Page " + pageCount++);
		        ctx.setRequestControls(new Control[] { new PagedResultsControl(LDAP_PAGE_SIZE, cookie, Control.CRITICAL) });
	        }
	        while (cookie != null);
	        
		    ctx.close();
		 
		} catch (AuthenticationNotSupportedException ex) {
		    System.out.println("The authentication is not supported by the server");
		} catch (AuthenticationException ex) {
 		    System.out.println("incorrect password or username");
		} catch (NamingException ex) {
			session.getTransaction().rollback();
		    System.out.println("error when trying to create the context");
		} catch (IOException iox) {
			session.getTransaction().rollback();
		    System.out.println("error when trying to create the context");			
		}
	}
	
	private LdapContext getPagingLdapContext() throws NamingException, IOException {
		
		Hashtable env = new Hashtable();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, LDAP_SERVER_URL);
		env.put(Context.SECURITY_AUTHENTICATION, "simple");
		env.put(Context.SECURITY_PRINCIPAL, "idir\\TNOJOREL");
		env.put(Context.SECURITY_CREDENTIALS, "SP()ang949");
		
	    LdapContext ctx = null;
	    
		Control[] pageControl = new Control[] { new PagedResultsControl(LDAP_PAGE_SIZE, Control.CRITICAL) };
		ctx = new InitialLdapContext(env, null);
		ctx.setRequestControls(pageControl);

		return ctx;
	}
	
	private void processEntry(SearchResult sr, Session session) throws NamingException {

		Attributes attrs = sr.getAttributes();
		Attribute email = attrs.get("mail");
		Attribute displayName = attrs.get("displayName");

		LdapAddressesDao newAddress = new LdapAddressesDao();
		
		String displayNameStr = (String) displayName.get();
		
		newAddress.setDisplayName(displayNameStr);
		newAddress.setDisplayUppername(displayNameStr.toUpperCase());
		newAddress.setEmailAddress((String) email.get());
		
		session.persist(newAddress);
	}
}