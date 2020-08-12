package ca.bc.gov.tno.jorel2.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.LdapAddressesDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;

/**
 * Populates the LDAP_ADDRESSES table from the Active Directory LDAP repository "ou=bcgov,dc=idir,dc=bcgov" at idir.bcgov. Also allows custom
 * email addresses and display names to be added from the local file <code>localemail.csv</code>.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class LdapEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/**
	 * Processes all LDAP events that are eligible for execution (based on the event's FREQUENCY) and have not yet been executed today.
	 * This event runs as an exclusive event, as the process of copying email addresses and display names into the LDAP_ADDRESSES table is an 
	 * atomic operation (in the sense that it cannot be broken down into sub-tasks). The atomicity of a single event is enforced in two ways 
	 * in a belt-an-braces fashion:
	 * 
	 * <ol>
	 * <li>An EventType.LDAP enumeration is added to the <code>activeEvents</code> HashMap to lock out other threads in this instance.
	 * <li>The LAST_FTP_RUN value in the current event is set to today's date to ensure that no other Jorel2 instance will run it.
	 * </ol>
	 * 
	 * In order to prevent multiple LDAP events from running on different Jorel2 instances, there must be only one LDAP event in the EVENTS table.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting LDAP event processing");
    		
    		if (instance.isExclusiveEventActive(EventType.LDAP)) {
    			decoratedTrace(INDENT1, "LDAP event processing already active. Skipping.");    			
    		} else {
    			instance.addExclusiveEvent(EventType.LDAP);
    			
		        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
		        for (Object[] entityPair : results) {
		        	if (entityPair[0] instanceof EventsDao) {
		        		EventsDao currentEvent = (EventsDao) entityPair[0];
		        
		        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
		        			runnable.getJorel2ThreadInstance().setTimeoutSeconds(currentEvent.getTimeout().longValue());
			        		ldapEvent(currentEvent, session);
		        		}
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
	
	/**
	 * Establishes an <code>LdapContext</code> with an associated <code>PagedResultsControl</code> and reads pages of email entries, adding 
	 * each entry to the LDAP_ADDRESSES table. This task will not occur until the start_time of the event has passed. This method initially imports
	 * email addresses from the local file <code>localemail.csv</code>, if it exists, and then imports from Active Directory. The contents of the
	 * LDAP_ADDRESSES table are cleared prior to processing.
	 * 
	 * @param currentEvent The LDAP event currently being processed.
	 * @param session The current Hibernate persistence context.
	 */
	private void ldapEvent(EventsDao currentEvent, Session session) {
		
		try {		    
			String startTimeStr = currentEvent.getStartTime() == null ? "00:00:00" : currentEvent.getStartTime();
			LocalDateTime now = LocalDateTime.now();
			String startHoursMinutes = startTimeStr.substring(0, 5);
			String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());
			
			if ((nowHoursMinutes.compareTo(startHoursMinutes) >= 0)) {
        		DbUtil.updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
        		
        		// Load email addresses from local file
        		LdapAddressesDao.deleteAllRecords(LOCALFILE_SOURCE, session);
        		File localFile = new File("localemail.csv");
        		processLocalFile(localFile, session);

        		// Load email addresses from Active Directory repository
			    LdapAddressesDao.deleteAllRecords(LDAP_SOURCE, session);
			    LdapContext ctx = getPagingLdapContext();
		        SearchControls searchControls = new SearchControls();
		        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		        processLdapPages(ctx, searchControls, session);
			    ctx.close();
			}
		} catch (Exception ex) {
			session.getTransaction().rollback();
		    decoratedError(INDENT0, "Processing LDAP entries.", ex);			
		}
	}
	
	/**
	 * Reads all entries from the csv file <code>localFile</code>, splits them into the fields <code>displayName</code> and <code>emailAddress</code>
	 * and calls <code>insertEmailEntry</code> to persist them to the LDAP_ADDRESSES table.
	 * 
	 * This method exits immediately if the file represented by the <code>localFile</code> parameter does not exist. The insertion of all entries 
	 * in the file is managed by a single transaction.
	 * 
	 * @param localFile Abstract representation of the file containing the email addresses.
	 * @param session The current Hibernate persistence context.
	 * @throws FileNotFoundException
	 */
	private void processLocalFile(File localFile, Session session) throws FileNotFoundException {
		
		int recordCount = 0;
		
		decoratedTrace(INDENT2, "Importing email addresses from local file.");
		
		if(localFile.exists()) {
			Scanner reader = new Scanner(localFile);
			
		    session.beginTransaction();
		    
			while(reader.hasNextLine()) {
				String emailEntry = reader.nextLine();
				String[] elements = emailEntry.split(",");
				
				insertEmailEntry(elements[0], elements[1], LOCALFILE_SOURCE, session);
				recordCount++;
			}
			
			session.getTransaction().commit();
			
			decoratedTrace(INDENT2, "Imported " + recordCount + " email addresses from file " + localFile);
		} else {
			decoratedTrace(INDENT2, "The file " + localFile + " does not exist.");
		}
	}
	
	/**
	 * Processes all pages of email address records from the Active Directory server. The records are selected from the repository using the 
	 * name "ou=bcgov,dc=idir,dc=bcgov" and the search string "(&(mail=*)(displayName=*))". This search returns all email entries that also 
	 * have non-null displayName attributes. A new transaction is commenced for each page of records.
	 * 
	 * @param ctx The <code>LdapContext</code> from which email entries should be read.
	 * @param searchControls The search controls that define the style of search to use (SearchControls.SUBTREE_SCOPE).
	 * @param session The current Hibernate persistence context.
	 * @throws NamingException
	 * @throws IOException
	 */
	private void processLdapPages(LdapContext ctx, SearchControls searchControls, Session session) throws NamingException, IOException {
		
	    byte[] cookie = null;
	    int pageCount = 0;
	    
	    decoratedTrace(INDENT2, "Importing email addresses from Active Directory.");
	    
        do {
		    NamingEnumeration<?> answer = ctx.search("ou=bcgov,dc=idir,dc=bcgov", "(&(mail=*)(displayName=*))", searchControls);
		    
		    // Process one page of new LDAP entries, create an LdapAddressesDao object for each record.
		    session.beginTransaction();
		    while (answer.hasMore()) {
		        SearchResult sr = (SearchResult) answer.next();
		        processLdapEntry(sr, session);
		    }
		    session.getTransaction().commit();
		    
		    // Get the current page PagedResultsControl and grab the cookie for use in retrieving the next page.
	        Control[] returnedControls = ctx.getResponseControls();
	        if (returnedControls != null) {
	            for (int i = 0; i < returnedControls.length; i++) {
	                if (returnedControls[i] instanceof PagedResultsResponseControl) {
	                    PagedResultsResponseControl prrc = (PagedResultsResponseControl) returnedControls[i];
	                    cookie = prrc.getCookie();
	                }
	            }
	        }
	        
	        pageCount++;
	        
	        if(pageCount %5 == 0) {
	        	int currentCount = pageCount * LDAP_PAGE_SIZE;
	        	decoratedTrace(INDENT2, "Imported " + currentCount + " LDAP email addresses so far.");
	        }
	        
	        // Create a new PagedResultsControl and assign the cookie just retrieved.
	        ctx.setRequestControls(new Control[] { new PagedResultsControl(LDAP_PAGE_SIZE, cookie, Control.CRITICAL) });
        }
        while (cookie != null);
        
        decoratedTrace(INDENT2, "Completed import of email addresses from Active Directory");
	}
	
	/** 
	 * Establishes an LDAP connection with <code>idir.bcgov</code> and attaches a <code>PagedResultsControl</code> to it.
	 * 
	 * @return An open paged <code>LdapContext</code> object.
	 * @throws NamingException
	 * @throws IOException
	 */
	private LdapContext getPagingLdapContext() throws NamingException, IOException {
		
		Hashtable<String, String> env = new Hashtable<>();
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
	
	/**
	 * Extracts the email address and displayName from the search result, and inserts a new record containing those data 
	 * in the LDAP_ADDRESSES table.
	 * 
	 * @param sr The current email address being processed.
	 * @param session The current Hibernate persistence context.
	 * @throws NamingException
	 */
	private void processLdapEntry(SearchResult sr, Session session) throws NamingException {

		Attributes attrs = sr.getAttributes();
		Attribute email = attrs.get("mail");
		Attribute displayName = attrs.get("displayName");
		String emailAddressStr = (String) email.get();
		String displayNameStr = (String) displayName.get();

		insertEmailEntry(emailAddressStr, displayNameStr, LDAP_SOURCE, session);
	}
	
	/**
	 * Persists a new LdapAddressesDao record for the current email address. Checks are performed on the length of the email address and
	 * display name to ensure no "value too large" exceptions occur.
	 * 
	 * @param emailAddressStr The email address to add.
	 * @param displayNameStr The display name to add.
	 * @param source The source of the imported email data (ldap or localfile).
	 * @param session The current Hibernate persistence context.
	 */
	private void insertEmailEntry(String emailAddressStr, String displayNameStr, String source, Session session) {
		
		// If the email address exceeds 100 characters in length, it is unusable
		if(emailAddressStr.length() <= 100) {
			LdapAddressesDao newAddress = new LdapAddressesDao();
			
			// The length of the displayName may exceed the field length in LDAP_ADDRESSES, so truncate if necessary.
			if(displayNameStr.length() > 100) {
				displayNameStr = displayNameStr.substring(0, 100);
			}
			
			// Create and persist the new LDAP_ADDRESSES record (transactions are committed when each page has been processed).
			newAddress.setDisplayName(displayNameStr);
			newAddress.setDisplayUppername(displayNameStr.toUpperCase());
			newAddress.setEmailAddress(emailAddressStr);
			newAddress.setSource(source);
			
			session.persist(newAddress);
		}
	}
}