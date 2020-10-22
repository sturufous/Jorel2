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
import ca.bc.gov.tno.jorel2.Jorel2Root.EventType;
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
 * Processes all eligible Channelwatcher event records from the TNO_EVENTS table.
 * 
 * @author Stuart Morse
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
    		if (instance.isExclusiveEventActive(EventType.CHANNELWATCHER)) {
    			decoratedTrace(INDENT1, "Channelwatcher event processing already active. Skipping.");    			
    		} else {
    			instance.addExclusiveEvent(EventType.CHANNELWATCHER);
    			
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
				
		        instance.removeExclusiveEvent(EventType.CHANNELWATCHER);
    		}
		}
    	catch (Exception e) {
	        instance.removeExclusiveEvent(EventType.CHANNELWATCHER);
    		logger.error("Processing Channelwatcher events.", e);
    	}
    	
		decoratedTrace(INDENT1, "Completed Channelwatcher event processing");
		
    	return Optional.of("complete");
	}
	
	/**
	 * Retrieves all active unlocked channels from the Channel's table and iterates through them. Obtains the Url of the channel
	 * using its <code>getUrl()</code> method and uses a SyndFeedInput object to retrieve the XML RSS 2.0 contents of the feed.
	 * <code>processRssItems()</code> is then called to add the feed's contents to the NEWS_ITEMS table. Channels are locked
	 * (made unavailable to other instances of Jorel2 currently processing Channelwatcher events) by setting the event's 
	 * LAST_FTP_RUN field to the name of the instance that is processing it. After processing the value of the event's LAST_FTP_RUN
	 * field is set to "idle".
	 * 
	 * @param currentEvent The Channelwatcher event currently being processed.
	 * @param session The current Hibernate persistence context.
	 */
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
		
	/**
	 * Iterates over the RSS items in the <code>SyndFeed</code> object passed in the parameter <code>feed</code> obtains the content
	 * of the social media posts from the provider's API and adds the contents of the posts to the NEWS_ITEMS table. This method supports
	 * Twitter posts only, as these are the only ones provided by the current infrastructure.
	 * 
	 * @param feed The SyndFeed object containing links to the the social media posts.
	 * @param source The source of the social media post.
	 * @param channel The channel from which the SyndFeed entries were extracted.
	 * @param session The current Hibernate persistence context.
	 * @throws Exception All runtime exceptions are thrown back to <code>channelEvent()</code> for processing.
	 */
	private void processRssItems(SyndFeed feed, String source, ChannelsDao channel, Session session) throws Exception {
		
		int articleCount = 0;
		
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
					articleCount++;
					parseRSS(text, channelName, author, session);
				}
			}
		}
		
		instance.incrementArticleCount(source, articleCount);
		decoratedTrace(INDENT2, "Added: " + articleCount + " article(s) from " + source);
	}
	
	/**
	 * Creates a news item by calling <code>NewsItemFactory.createChannelNewsItem()</code> and saves this item to the NEWS_ITEMS table.
	 * 
	 * @param spam Is this social media post spam (not enough followers)
	 * @param item The RSS item being added.
	 * @param source The source of the item (usually "Social Media").
	 * @param authorHandle The author handle of the social media post.
	 * @param author The actual name of the author of the post.
	 * @param channel The channel from which this post was retrieved.
	 * @param session The current Hibernate persistence context.
	 * @return A boolean indicating that the news item was added successfully.
	 */
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
	
	/**
	 * Determines if this social media post is spam by retrieving the author's follower count from the social media provider's API. If the number
	 * of followers is less than 15 the post is classified as spam and will not be added to the NEWS_ITEMS table.
	 * 
	 * @param jsonapi Api object retrieved from Social Media provider.
	 * @return Whether or not this post is regarded as spam.
	 */
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
	
	/**
	 * If a social media post was successfully added to the NEWS_ITEMS table, this method adds a record to the SOCIAL_MEDIA_LINKS table.
	 * 
	 * @param text The text of the social media post.
	 * @param channelName The name of the channel from which this post was extracted.
	 * @param author The author of the post.
	 * @param session The current Hibernate persistence context.
	 */
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
					if (urlTitle.length() > 1000) {
						urlTitle = urlTitle.substring(0, 999);
					}

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
								linkRecord.setInfluencerRsn(BigDecimal.valueOf(0));
								linkRecord.setScore(BigDecimal.valueOf(0));
								linkRecord.setDateCreated(new Date());
								linkRecord.setItemRsn(BigDecimal.valueOf(0));
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