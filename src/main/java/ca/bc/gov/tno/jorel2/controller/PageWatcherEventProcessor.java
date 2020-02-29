package ca.bc.gov.tno.jorel2.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.PagewatchersDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
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
	Jorel2Instance instance;
	
	/**
	 * Process all eligible PageWatcher events.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
		
    	try {
    		logger.trace(StringUtil.getLogMarker(INDENT1) + "Starting PageWatcher event processing" + StringUtil.getThreadNumber());
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getElligibleEventsByEventType method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		boolean runToday = DateUtil.runnableToday(currentEvent.getFrequency());
	        		
	        		if (runToday) {
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
    	
		logger.trace(StringUtil.getLogMarker(INDENT1) + "Completing PageWatcher event processing" + StringUtil.getThreadNumber());
    	return Optional.of("complete");
	}
	
	/**
	 * Retrieves all active pagewatcher records and sends an email message if the page being watched has changed since the last run.
	 *  
	 * @param session The current Hibernate persistence context.
	 */
	@SuppressWarnings("preview")
	private void processPageWatchers(Session session) {

		try {
	        List<PagewatchersDao> results = PagewatchersDao.getActivePageWatchers(instance, session);
			session.beginTransaction();
	        
	        for (PagewatchersDao watcher : results) {
				Clob clobContent = watcher.getPageContent();
				String pageContent = (clobContent != null) ? StringUtil.clobToString(clobContent) : "";
				String pageContent2 = UrlUtil.retrievePageContent(watcher.getUrl());
				
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
	 * @param watcher The watcher record being processed
	 * @return The updated watcher
	 */
	private PagewatchersDao getChangedWatcher (PagewatchersDao watcher, String changes, String pageContent2) {
		
		watcher.setPageLastModified(BigDecimal.valueOf(new Date().getTime()));
		watcher.setPageResultCode(BigDecimal.valueOf(200));
		watcher.setPageContent(StringUtil.stringToClob(pageContent2));
		watcher.setDateModified(new Date());
		watcher.setLastCheck(new Date());
		sendMail(watcher, changes);
		logger.trace(StringUtil.getLogMarker(INDENT1) + "ProcessPageWatchers: " + watcher.getName() + " changed");
		
		return watcher;
	}

	/**
	 * Sends an email message informing the recipient list that the web site in question has been modified since the last
	 * PageWatcher event was run (currently weekly).
	 * 
	 * @param watcher The PageWatcher event being processed.
	 * @param changes A description of the changes made to the page being watched.
	 */
	private void sendMail (PagewatchersDao watcher, String changes) {
	
		javax.mail.Session session = getEmailSession();
		String subject = "TNO Page Watcher: " + watcher.getName();
		String message = formatEmailMessage(watcher, changes);
		InternetAddress[] typeMarker = new InternetAddress[1];
		
		try {
			String toWhome = watcher.getEmailRecipients();
					
			if (toWhome == null) {
				logger.error("Attempting to send pagewatcher email.", new IllegalStateException("The email recipient list for " + watcher.getName() + " is null."));
			} else {
				ArrayList<InternetAddress> addresses = new ArrayList<>();
				String[] emailRecipients = toWhome.split(",");
				
				for (String emailAddress : emailRecipients) {
					addresses.add(new InternetAddress(emailAddress));
				}
				
				MimeMessage msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress(instance.getMailFromAddress()));
				msg.setRecipients(Message.RecipientType.TO, addresses.toArray(typeMarker));
				msg.setSubject(subject);
				msg.setText(message);
				msg.setHeader("Content-Type", "text/html");// charset=\"UTF-8\"");
	
				Transport.send(msg); 
			}
		} catch (MessagingException e) {
			logger.error("Attempting to send pagewatcher email.", e);
		}
	}
	
	/**
	 * Returns an email session for use in creating a Mime message.
	 * @return The session instantiated with host name and port.
	 */
	private javax.mail.Session getEmailSession() {
		
		Properties props = new Properties();
		props.put("mail.host", instance.getMailHostAddress());
		props.put("mail.smtp.port", instance.getMailPortNumber());
		javax.mail.Session session = javax.mail.Session.getDefaultInstance(props);
		session.setDebug(false);

		return session;
	}
	
	/**
	 * Creates a formatted email message notifying the recipient(s) that a change has occurred on the web site being watched.
	 * 
	 * @param watcher The pagewatcher record being processed
	 * @param changes The differences between the current page and the one saved on the previous run
	 * @return
	 */
	private String formatEmailMessage(PagewatchersDao watcher, String changes) {
		
		String name = watcher.getName();
		BigDecimal lastModified = watcher.getPageLastModified();
		String url = watcher.getUrl();
		
		String message = "The web page <a href=\"" + url + "\">" + name + "</a> has been modified.<br>\n<br>\n";
		message += "Name: " + name + "<br>\n";
		message += "Address: <a href=\"" + watcher.getUrl() + "\">" + watcher.getUrl() + "</a><br>\n";
		message += "Last Modified: " + DateUtil.unixTimestampToDate(lastModified) + "<br>\n<br>\n";
		message += "Summary of changes:<br>\n";
		message += "-------------------<br>\n";
		message += changes + "<br>\n<br>\n";

		return message;
	}
}