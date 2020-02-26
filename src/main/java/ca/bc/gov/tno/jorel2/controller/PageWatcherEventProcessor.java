package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.sql.Clob;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Inject;
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
		
		Calendar calendar = Calendar.getInstance();

    	try {
    		logger.trace(StringUtil.getLogMarker(INDENT1) + "Starting PageWatcher event processing" + StringUtil.getThreadNumber());
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		boolean runToday = DateUtil.runnableToday(currentEvent.getFrequency());
	        		
	        		if (runToday) {
	        			processPageWatchers(currentEvent, session);
	        		}
	        	}
	        }
    	} 
    	catch (Exception e) {
    		logger.error("Processing PageWatcher entries.", e);
    	}
    	
		logger.trace(StringUtil.getLogMarker(INDENT1) + "Completing PageWatcher event processing" + StringUtil.getThreadNumber());
    	return Optional.of("complete");
	}
	
	// Process the Pagewatcher event
	private void processPageWatchers(EventsDao currentEvent, Session session) {

		try {
			HttpURLConnection http = null;
			BufferedReader read = null;
			
	        List<PagewatchersDao> results = PagewatchersDao.getActivePageWatchers(instance, session);
			session.beginTransaction();
	        
	        for (PagewatchersDao watcher : results) {
				String url = watcher.getUrl();
				String name = watcher.getName();
				Clob clobContent = watcher.getPageContent();
				String pageContent = "";
	        	boolean sendEmail = false;
				
	        	pageContent = (clobContent != null) ? StringUtil.clobToString(clobContent) : "";

				String pageContent2 = "";
				StringBuffer contents = new StringBuffer();
				String changes = "";
				pageContent2 = UrlUtil.retrievePageContent(url);
	        
				if (pageContent2.equals(pageContent)) {
					// content did not change, update last checked date
					watcher.setPageResultCode(BigDecimal.valueOf(100));
					watcher.setLastCheck(new Date());
				} else {
					changes = StringUtil.diff(pageContent, pageContent2);
					if (changes.equals("")) {
						// no reported changes
						watcher.setPageResultCode(BigDecimal.valueOf(200));
						watcher.setLastCheck(new Date());
					} else {
						// content changed - update record and send emails
						watcher.setPageLastModified(BigDecimal.valueOf(new Date().getTime()));
						watcher.setPageResultCode(BigDecimal.valueOf(300));
						watcher.setPageContent(StringUtil.stringToClob(pageContent2));
						watcher.setDateModified(new Date());
						//sendMail(watcher, changes);
						logger.trace(StringUtil.getLogMarker(INDENT1) + "ProcessPageWatchers: " + name + " changed");
					}
				}
				session.persist(watcher);
	        }
	        
			session.getTransaction().commit();
		} catch (Exception err) {
			logger.error("PagewatcherEvent", err);
		}
	}

	
	/**
	 * Sends an email message informing the recipient list that the web site in question has been modified since the last
	 * PageWatcher event was run (currently weekly).
	 * 
	 * @param currentEvent The PageWatcher event being processed.
	 */
	private void sendMail (PagewatchersDao watcher, String changes) {
	
		Properties props = System.getProperties();
		props.put("mail.host", "192.168.4.10");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.port", 25);
		javax.mail.Session session = javax.mail.Session.getDefaultInstance(props);
		session.setDebug(true);
		BigDecimal lastModified = watcher.getPageLastModified();
		String url = watcher.getUrl();
		
		//String email_recipients = pw.getEmail_Recipients(); //get recipients from pagewatchers table
		String name = watcher.getName();
		String email_recipients = "stuart.morse@quartech.com";
		String subject = "TNO Page Watcher: " + name;
		String message = "The web page <a href=\"" + url + "\">" + name + "</a> has been modified.<br>\n<br>\n";
		message += "Name: " + name + "<br>\n";
		message += "Address: <a href=\"" + watcher.getUrl() + "\">" + watcher.getUrl() + "</a><br>\n";
		message += "Last Modified: " + DateUtil.unixTimestampToDate(lastModified) + "<br>\n<br>\n";

		message += "Summary of changes:<br>\n";
		message += "-------------------<br>\n";

		message += changes + "<br>\n<br>\n";
		
		try {
			InternetAddress[] addresses = new InternetAddress[1];
			addresses[0] = new InternetAddress("stuart.morse@quartech.com");
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("u2ubesant@gmail.com"));
			msg.setRecipient(Message.RecipientType.TO, addresses[0]);
			msg.setSubject(subject);
			msg.setText(message);
			msg.setHeader("Content-Type", "text/html");// charset=\"UTF-8\"");

			Transport.send(msg, addresses, "u2ubesant@gmail.com", "");
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}