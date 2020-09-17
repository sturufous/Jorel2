package ca.bc.gov.tno.jorel2.controller;


import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.ChannelsDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

/**
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class ChannelwatcherEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/**
	 * Process all eligible Channelwatcher event records from the TNO_EVENTS table.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting Channelwatcher event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
			for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
        			setThreadTimeout(runnable, currentEvent, instance);
        			
	        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
	        			channelEvent(currentEvent, session);
	        		}
	        	}
			}
		}
    	catch (Exception e) {
    		logger.error("Processing Channelwatcher events.", e);
    	}
    	
		decoratedTrace(INDENT1, "Completed Channelwatcher event processing");
		
    	return Optional.of("complete");
	}
	
	private void channelEvent(EventsDao currentEvent, Session session) {
		//System.setProperty("http.agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_7; en-ca) AppleWebKit/533.21.1 (KHTML, like Gecko)");

		List<ChannelsDao> channels = ChannelsDao.getActiveChannels(instance.getAppInstanceName(), session);
		for(ChannelsDao channel : channels) {
			BigDecimal rsn = channel.getRsn();
			String url = channel.getUrl();
			String name = channel.getChannelName();
			String source = channel.getSource();
			int count = 0;

			//if (channels.lock(rsn, frame.getName())) {

			decoratedTrace(INDENT2, "Check RSS " + name);

			if(url != null && !url.trim().equals("")) {

				SyndFeed feed = null;
				try{

					URL feedUrl = new URL(url);
					SyndFeedInput input = new SyndFeedInput();
					feed = input.build(new XmlReader(feedUrl));

					count = processRssItems(feed, source, channel, session);
					
				} catch(Exception ex){
					decoratedError(INDENT0, "While processing Channel entries.", ex);
				}

			} else {
				decoratedTrace(INDENT2, "Channel Event: URL provided was invalid for " + name);
			}

			//channels.unlock(rsn); 
		}
	} 
		
	private int processRssItems(SyndFeed feed, String source, ChannelsDao channel, Session session) throws Exception {
		
		int count = 0;
		
		for (SyndEntry item : (List<SyndEntry>) feed.getEntries()) {
			String link = StringUtil.nullToEmptyString(item.getLink());
			String author = StringUtil.nullToEmptyString(item.getAuthor());
			String authorHandle = "";
			String socialType = "";

			boolean articleAlreadyExists = (NewsItemsDao.getNewsItemCountByWebPathAndSource(link,  source, session) > 0);

			if (!articleAlreadyExists) {

				boolean spam = false;

				// extract author from url
				if (author.equalsIgnoreCase("")) {
					if ((link.startsWith("http://twitter.com/")) || (link.startsWith("https://twitter.com/"))) {
						String tempAuthor = "";
						if (link.startsWith("http://twitter.com/")) {
							tempAuthor = link.substring("http://twitter.com/".length());
						} else {
							tempAuthor = link.substring("https://twitter.com/".length());
						}
						int p = tempAuthor.indexOf('/');
						if (p>=0) {
							authorHandle = tempAuthor.substring(0, p);
							author = authorHandle;
							
							getReach();
							
							// lookup with Twitter API
							URI apiuri = new URI(
									"https", 
									"api.twitter.com", 
									"/1.1/users/lookup.json",
									"screen_name=" + authorHandle,
									null);
							
							ChannelApi jsonapi = new ChannelApi("json");
							jsonapi.call(apiuri, true);
							
							author = jsonapi.get_string("name");
							spam = isSpam(jsonapi);
						}
					}
				}

				if(saveNewsItem(spam, item, source, authorHandle, author, channel, session)) {
					count++;
					//if (urls == null) urls = new dbURLs(frame);
					//parseRSS(description, name, author, klout_score, news.getRsn(), influencer_rsn, inf, urls);
				}
			}
		}
		
		return count;
	}
	
	private void getReach() {
		
		/*if (inf.next(rsi)) {
			influencer_rsn = inf.getRsn();
			reach = (long)inf.score(influencer_rsn, "Reach"); // get reach from influencer record
			klout_score = inf.score(influencer_rsn, "Klout", false); // get klout score from influencer record
		} */
	}
		
	private boolean saveNewsItem(boolean spam, SyndEntry item, String source, String authorHandle, String author, ChannelsDao channel, Session session) {
		
		boolean success = true;
		
		if (!spam) {
			try {
				NewsItemsDao newsItem = NewsItemFactory.createChannelNewsItem(item, source, authorHandle, author, channel, instance);
				session.beginTransaction();
				session.persist(newsItem);
				session.getTransaction().commit();
			} catch (Exception e) {
				success = false;
				session.getTransaction().rollback();
				decoratedError(INDENT0, "Channelwatcher: Unable to save news item: " + item.getTitle(), e);
			}
		}
		
		return success;
	}
	
	private void sendEmail(ChannelsDao channel, Session session) {
		
		String emailRecipients = channel.getEmailRecipients();
		Date lastRunDate = channel.getLastRunDate();
		BigDecimal delay = channel.getDelay();
		
		/* if (count>0) {
		if (prefs.getRSS_alerts()) {
			if (!email_recipients.equalsIgnoreCase("")) {
				// create some properties and get the default Session
				Properties props = System.getProperties();
				props.put("mail.host", frame.getSMTPHost());
				Session session = Session.getDefaultInstance(props, null);
				try {								
					// multiple recipients is possible
					StringTokenizer st1 = new StringTokenizer(email_recipients, "~");
					int tokens = st1.countTokens();
					InternetAddress[] address = new InternetAddress[tokens];
					for (int j = 0; j < tokens; j++) {
						String emailAddress = st1.nextToken();
						address[j] = new InternetAddress(emailAddress);
					}

					MimeMessage msg = new MimeMessage(session);
					msg.setFrom(new InternetAddress(frame.getMailFrom()));
					msg.setRecipients(Message.RecipientType.BCC, address);
					msg.setSubject("New Stories for RSS Feed "+name);
					msg.setText(count + " new stories for RSS feed "+name);
					msg.setHeader("Content-Type", "text/html");// charset=\"UTF-8\"");

					Transport.send(msg);
				} catch (Exception mex) { ; }
			}
		}
	} */

	}
	
	private boolean isSpam(ChannelApi jsonapi) {
		
		boolean spam = false;
		
		String twitterName = jsonapi.get_string("name");
		if (twitterName != null) {
			if (!twitterName.equalsIgnoreCase("")) {
				long followers = jsonapi.get_long("followers_count");
				if (followers < 15) { // frame.getMinFollowers())
					spam = true; // very few followers
				}
			}
		}

		return spam;
	}
}