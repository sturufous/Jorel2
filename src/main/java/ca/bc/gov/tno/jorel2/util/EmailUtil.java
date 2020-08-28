package ca.bc.gov.tno.jorel2.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Properties;

import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.PropertiesConfiguration;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.PagewatchersDao;

public class EmailUtil extends Jorel2Root {
	
	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration config;
	
	/**
	 * Sends an email message informing the recipient list that the web site in question has been modified since the last
	 * PageWatcher event was run (currently weekly).
	 * 
	 * @param watcher The PageWatcher event being processed.
	 * @param changes A description of the changes made to the page being watched.
	 * @param hostAddress The address of the mail server to connect to.
	 * @param portNumber The port number on the host on which the smtp server is listening.
	 * @param fromAddress The sender's email address.
	 */
	public static void pageWatcherSendMail (PagewatchersDao watcher, String changes, String hostAddress, String portNumber, String fromAddress) {
	
		javax.mail.Session session = getEmailSession(hostAddress, portNumber);
		String subject = "TNO Page Watcher: " + watcher.getName();
		String message = formatWatcherEmailMessage(watcher, changes);
		InternetAddress[] inetAddress = new InternetAddress[1]; // Only used to dictate the type of the array contents
		
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
				msg.setFrom(new InternetAddress(fromAddress));
				msg.setRecipients(Message.RecipientType.TO, addresses.toArray(inetAddress));
				msg.setSubject(subject);
				msg.setText(message);
				msg.setHeader("Content-Type", "text/html");// charset=\"UTF-8\"");
	
				Transport.send(msg); 
			}
		} catch (MessagingException e) {
			logger.error("Attempting to send pagewatcher email.", e);
		}
	}
	
	public static void archiverSendMail (String hostAddress, String portNumber, String fromAddress, String toAddress, String message) {
		javax.mail.Session session = EmailUtil.getEmailSession(hostAddress, portNumber);
		String subject = "TNO Archive Folder Ready";
		message = "The following directories can be copied to new CD(s):\r\n" + message;
		InternetAddress[] inetAddress = new InternetAddress[1]; // Only used to dictate the type of the array contents
		
		try {
			if (toAddress == null) {
				logger.error("Attempting to send archiver email.", new IllegalStateException("The mail.to attribute in the Jorel2 properties file is null."));
			} else {
				ArrayList<InternetAddress> addresses = new ArrayList<>();
				String[] emailRecipients = toAddress.split(",");
				
				for (String emailAddress : emailRecipients) {
					addresses.add(new InternetAddress(emailAddress));
				}
				
				MimeMessage msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress(fromAddress));
				msg.setRecipients(Message.RecipientType.TO, addresses.toArray(inetAddress));
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
	 * Creates a formatted email message notifying the recipient(s) that a change has occurred on the web site being watched.
	 * 
	 * @param watcher The pagewatcher record being processed
	 * @param changes The differences between the current page and the one saved on the previous run
	 * @return The email message.
	 */
	private static String formatWatcherEmailMessage(PagewatchersDao watcher, String changes) {
		
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
	
	public static String sendAlertEmail(String hostAddress, String portNumber, String username, String recipients, String from, String subject, String message){
		
		String result = "";
		Session session = getEmailSession(hostAddress, portNumber);
		session.setDebug(false);
		InternetAddress[] inetAddress = new InternetAddress[1]; // Only used to dictate the type of the array contents
		
		//return "javax.mail.SendFailedException: Sending failed";

		try {
			if (recipients == null) {
				logger.error("Attempting to send alert email.", new IllegalStateException("The email recipients list is null."));
			} else {
				ArrayList<InternetAddress> addresses = new ArrayList<>();
				String[] emailRecipients = recipients.split("~");
				
				for (String emailAddress : emailRecipients) {
					addresses.add(new InternetAddress(emailAddress));
					result = result + "Email sent to: " + emailAddress + " : " + subject + "\n";
				}
				
				MimeMessage msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress(from));
				msg.setRecipients(Message.RecipientType.BCC, addresses.toArray(inetAddress));
				msg.setSubject(subject);
				msg.setText(message);
				msg.setHeader("Content-Type", "text/html");// charset=\"UTF-8\"");
	
				Transport.send(msg); 
			}
		} catch (MessagingException e) {
			logger.error("Attempting to send pagewatcher email.", e);
		}
		
		return result;
	}
	
	/**
	 * Returns an email session for use in creating a Mime message.
	 * 
	 * @param hostAddress The address of the email host to connect to.
	 * @param portNumber The port number of the host on which the smtp server is listening.
	 * @return The session instantiated with host name and port.
	 */
	public static javax.mail.Session getEmailSession(String hostAddress, String portNumber) {
		
		Properties props = new Properties();
		props.put("mail.host", hostAddress);
		props.put("mail.smtp.port", portNumber);
		javax.mail.Session session = javax.mail.Session.getDefaultInstance(props);
		session.setDebug(false);

		return session;
	}
}
