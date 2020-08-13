package ca.bc.gov.tno.jorel2.controller;

import java.math.BigDecimal;
import java.sql.Clob;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.PagewatchersDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.EmailUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;
import ca.bc.gov.tno.jorel2.util.UrlUtil;

/**
 * Monitors a set of web sites for changes. The list of sites is stored in the PAGEWATCHERS table.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class PageWatcherEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/**
	 * Process all eligible PageWatcher events.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
		
    	try {
    		decoratedTrace(INDENT1, "Starting PageWatcher event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        
	        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
        			setThreadTimeout(runnable, currentEvent, instance);
	        		
	        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
	        			processPageWatchers(session);
	        		}
	        		
	        		// Update the lastFtpRun to today's date to prevent pageWatchers from running again until tomorrow.
	        		String currentDate = DateUtil.getDateNow();
	        		currentEvent.setLastFtpRun(currentDate);
	        		session.beginTransaction();
	        		session.persist(currentEvent);
	        		session.getTransaction().commit();
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing PageWatcher entries.", e);
    	}
    	
		decoratedTrace(INDENT1, "Completing PageWatcher event processing");
    	return Optional.of("complete");
	}
	
	/**
	 * Retrieves all active pagewatcher records and sends an email message if the page being watched has changed since the last run.
	 *  
	 * @param session The current Hibernate persistence context.
	 */
	private void processPageWatchers(Session session) {

		try {
	        List<PagewatchersDao> results = PagewatchersDao.getActivePageWatchers(instance, session);
			session.beginTransaction();
	        
	        for (PagewatchersDao watcher : results) {
				Clob clobContent = watcher.getPageContent();
				String pageContent = (clobContent != null) ? StringUtil.clobToString(clobContent) : "";
				String pageContent2 = UrlUtil.retrievePageContent(watcher.getUrl(), instance);
				
				if (pageContent2 != null) {
					pageContent2 = StringUtil.fix(pageContent2, watcher.getStartString(), watcher.getEndString());
					String changes = StringUtil.diff(pageContent, pageContent2);
					
					ChangedStatus changed = (pageContent2.equals(pageContent) || changes == "") ? ChangedStatus.UNCHANGED : ChangedStatus.CHANGED;
					
		        	watcher = switch (changed) {
	        			case UNCHANGED -> getUnchangedWatcher(watcher);
	        			case CHANGED -> getChangedWatcher(watcher, changes, pageContent2);
	        			default -> null;
		        	};
	
		        	if (watcher != null) {
		        		session.persist(watcher);
		        	} else {
		        		logger.error("Cannot determine if watched page changed.", new IllegalStateException("Changed status is null."));
		        	}
				}
	        }
	        
			session.getTransaction().commit();
		} catch (Exception err) {
			logger.error("PagewatcherEvent", err);
		}
	}
	
	/**
	 * Sets the HTTP result code and the last check date for this watcher.
	 * 
	 * @param watcher The watcher record being processed
	 * @return The updated watcher
	 */
	private PagewatchersDao getUnchangedWatcher (PagewatchersDao watcher) {
		
		watcher.setPageResultCode(BigDecimal.valueOf(200));
		watcher.setLastCheck(new Date());
		
		return watcher;
	}

	/**
	 * Sets the lastModified, resultCode, pageContent and dateModified for this watcher record. Also notifies the watcher's recipient
	 * list, by email, that a change has occurred and writes a log message. 
	 * 
	 * @param watcher The watcher record being processed.
	 * @param changes The changes that occurred on this watched page.
	 * @param pageContent2 The contents of the updated page.
	 * @return The updated watcher
	 */
	private PagewatchersDao getChangedWatcher (PagewatchersDao watcher, String changes, String pageContent2) {
		
		watcher.setPageLastModified(BigDecimal.valueOf(new Date().getTime()));
		watcher.setPageResultCode(BigDecimal.valueOf(200));
		watcher.setPageContent(StringUtil.stringToClob(pageContent2));
		watcher.setDateModified(new Date());
		watcher.setLastCheck(new Date());
		EmailUtil.pageWatcherSendMail(watcher, changes, instance.getMailHostAddress(), instance.getMailPortNumber(), instance.getMailFromAddress());
		decoratedTrace(INDENT2, "ProcessPageWatchers: " + watcher.getName() + " changed");
		
		return watcher;
	}
}