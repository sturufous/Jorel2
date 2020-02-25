package ca.bc.gov.tno.jorel2.controller;


import java.util.Calendar;
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
import ca.bc.gov.tno.jorel2.util.JorelAuthenticator;
import ca.bc.gov.tno.jorel2.util.StringUtil;

/**
 * Monitors a set of web sites for changes. The list of sites is stored in the 
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
    		logger.trace(StringUtil.getLogMarker(INDENT1) + "Starting RSS event processing" + StringUtil.getThreadNumber());
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		//sendMail();
	        	}
	        }
	        
			String freq = "mtwtf--";

			int day=-1;
			boolean dayOK=true;
			if (freq == null) freq="";
			if (freq.length() == 7) {
				day = calendar.get(Calendar.DAY_OF_WEEK)-2;
				if (day < 0) day=day+7;
				String c=freq.substring(day);
				if (c.startsWith("-")) dayOK=false;
			}
    	} 
    	catch (Exception e) {
    		logger.error("Processing user directory entries.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	private void sendMail () {
		Properties props = System.getProperties();
		JorelAuthenticator auth = new JorelAuthenticator();
		props.put("mail.host", "192.168.4.10");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.port", 25);
		javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, auth);
		session.setDebug(true);
		
		InternetAddress[] addresses = new InternetAddress[1];
		try {
			addresses[0] = new InternetAddress("stuart.morse@quartech.com");
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("u2ubesant@gmail.com"));
			msg.setRecipient(Message.RecipientType.TO, addresses[0]);
			msg.setSubject("Testing page watcher");
			msg.setText("This is the message text");
			msg.setHeader("Content-Type", "text/html");// charset=\"UTF-8\"");

			Transport.send(msg, addresses, "u2ubesant@gmail.com", "");
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}