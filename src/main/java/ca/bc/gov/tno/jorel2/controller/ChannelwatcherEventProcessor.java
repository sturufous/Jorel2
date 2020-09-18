package ca.bc.gov.tno.jorel2.controller;


import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

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
import ca.bc.gov.tno.jorel2.model.SocialMediaLinksDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;
import ca.bc.gov.tno.jorel2.util.UrlUtil;

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

		String instanceName = instance.getAppInstanceName();
		List<ChannelsDao> channels = ChannelsDao.getActiveUnlockedChannels(instanceName, session);
		
		try {
			for(ChannelsDao channel : channels) {
				BigDecimal rsn = channel.getRsn();
				String url = channel.getUrl();
				String source = channel.getSource();
	
				decoratedTrace(INDENT2, "Check RSS: " + url);
				
				ChannelsDao.lockChannel(instanceName, channel, session);
	
				if(url != null && !url.trim().equals("")) {
					SyndFeed feed = null;
					try{
						URL feedUrl = new URL(url);
						SyndFeedInput input = new SyndFeedInput();
						feed = input.build(new XmlReader(feedUrl));
	
						processRssItems(feed, source, channel, session);
					} catch(Exception ex){
						decoratedError(INDENT0, "While processing Channel entries.", ex);
					}
				} else {
					decoratedTrace(INDENT2, "Channel Event: URL provided was invalid: " + url);
				}
	
				ChannelsDao.unlockChannel(channel, session);
			} 
		} catch (Exception e) {
			decoratedError(INDENT0, "Processing channels.", e);			
		}
	} 
		
	private void processRssItems(SyndFeed feed, String source, ChannelsDao channel, Session session) throws Exception {
		
		for (SyndEntry item : (List<SyndEntry>) feed.getEntries()) {
			
			String link = StringUtil.nullToEmptyString(item.getLink());
			String author = StringUtil.nullToEmptyString(item.getAuthor());
			String authorHandle = "";
			String text = item.getDescription().getValue();
			String channelName = channel.getChannelName();

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
					parseRSS(text, channelName, author, session);
				}
			}
		}
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
	
	private void parseRSS(String text, String channelName, String author, Session session) {			

		Matcher matcher = VALID_URL.matcher(text);
		while (matcher.find()) {

			String url = matcher.group(3);
			if (url.startsWith("http")) {
				boolean linkAlreadyExists = SocialMediaLinksDao.getLinkCountByUrlAndAuthor(url, author, session) > 0;
				if (!linkAlreadyExists) {

					Map<String, String> urlMeta = UrlUtil.resolveURL(url);

					String longUrl = urlMeta.get("longUrl");
					if (longUrl==null) longUrl = url;
					if (longUrl.equalsIgnoreCase("")) longUrl = url;

					String urlTitle = urlMeta.get("title");
					if (urlTitle == null) urlTitle = url;
					if (urlTitle.equalsIgnoreCase("")) urlTitle = url;

					String response = urlMeta.get("responseCode");
					if (response == null) response = "";
					if (response.equalsIgnoreCase("")) response = "";

					if (!urlTitle.equalsIgnoreCase("Twitter / ?")) {
						if (!response.startsWith("3")) {
							longUrl = UrlUtil.fixURL(longUrl);
							if (longUrl == null) decoratedTrace(INDENT2, "Found a null long URL.");
							//boolean wasDeleted = SocialMediaLinksDao.wasDeleted(longUrl, session);
							
							if(!SocialMediaLinksDao.wasDeleted(longUrl, session)) {
								SocialMediaLinksDao linkRecord = new SocialMediaLinksDao();
								
								linkRecord.setLink(url);
								linkRecord.setUrl(longUrl);
								linkRecord.setUrlProcessed(true);
								linkRecord.setUrlTitle(urlTitle);
								linkRecord.setChannel(channelName);
								linkRecord.setAuthor(author);
								linkRecord.setResponseCode(response);
								linkRecord.setInfluencerRsn(BigDecimal.valueOf(0L));
								linkRecord.setScore(BigDecimal.valueOf(0L));
								linkRecord.setDateCreated(new Date());
								linkRecord.setItemRsn(BigDecimal.valueOf(0L));
								linkRecord.setDeleted(false);
								
								session.beginTransaction();
								session.persist(linkRecord);
								session.getTransaction().commit();
							}
						}
					}
				}
			}
		}
	}
}