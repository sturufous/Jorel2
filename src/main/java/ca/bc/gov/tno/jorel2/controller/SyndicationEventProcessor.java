package ca.bc.gov.tno.jorel2.controller;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import ca.bc.gov.tno.jorel2.Jorel2Process;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;

@Service
public class SyndicationEventProcessor extends Jorel2Root implements EventProcessor {

	/**
	 * Manages the retrieval and processing of the CP feed using the 'Page full of links' format.
	 * 
	 * @author Stuart Morse
	 * @version 0.0.1
	 */

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Process process;
	
	/**
	 * Process all eligible non-XML syndication event records from the TNO_EVENTS table.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	public Optional<String> processEvents(String eventType, Session session) {
	
		SyndFeed feed = null;
		
    	try {
	        List<Object[]> results = EventsDao.getEventsByEventType(process, eventType, session);
	        List<SyndEntry> newSyndItems;
    		
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	    			URLConnection feedUrlConnection = new URL(currentEvent.getTitle()).openConnection();
	    			
	    			feedUrlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
	    			SyndFeedInput input = new SyndFeedInput();
	    			XmlReader xmlReader = new XmlReader(feedUrlConnection);
	    			feed = input.build(xmlReader);

		    		newSyndItems = getNewRssItems(currentEvent.getSource(), session, feed);
		    		
		    		insertNewsItems(currentEvent.getSource(), session, newSyndItems);
	        	} else {
		    		throw new IllegalArgumentException("Wrong data type in query results, expecting EventsDao.");    		
	        	}
	        } 
    	} 
    	catch (Exception e) {
    		logger.error("Retrieving or storing RSS feed.", e);
    	}
    	
    	return Optional.of("empty");
	}
	
	/**
	 * Create a record in the NEWS_ITEMS table that corresponds with each rss news item in the newsItems ArrayList.
	 * 
	 * @param newsItems The list of news items retrieved from the publisher
	 * @param session The current Hibernate persistence context
	 * @param rss The entire rss feed retrieved from the publisher
	 */
	@SuppressWarnings("preview")
	private void insertNewsItems(String source, Session session, List<SyndEntry> newsItems) {
		
		NewsItemsDao newsItem = null;
		String enumKey = source.toUpperCase().replaceAll("\\s+","");

		RssSource sourceEnum = RssSource.valueOf(enumKey);
		
		if (!newsItems.isEmpty()) {
			session.beginTransaction();
			
			for (SyndEntry item : newsItems) {
		    	newsItem = switch (sourceEnum) {
					case CPNEWS -> NewsItemFactory.createCPNewsItem(item, source);
					default -> null;
		    	};
						
		    	if (newsItem != null) {
		    		session.persist(newsItem);
		    		System.out.println(item.getTitle());
		    	}
			}
			
			session.getTransaction().commit();
		}
	}
	
	/**
	 * Filters out objects that correspond with existing entries in the NEWS_ITEMS table. This prevents the creation of duplicate records.
	 * 
	 * @param source The name of the publisher of this rss feed (e.g. iPolitics, Daily Hive)
	 * @param session The active Hibernate persistence context
	 * @param rss The feed retrieved from the publisher
	 * @return News items that are not currently in the NEWS_ITEMS table
	 */
	private List<SyndEntry> getNewRssItems(String source, Session session, SyndFeed feed) {
		
		List<SyndEntry> newRssItems = new ArrayList<>();
		List<NewsItemsDao> existingItems = NewsItemsDao.getItemsAddedSinceYesterday(source, session);
		Set<String> existingNewsItems = new HashSet<>();
		
		// Create a list of articles that are already in the NEWS_ITEMS table
		for (NewsItemsDao newsItem : existingItems) {
			existingNewsItems.add(newsItem.getWebpath());
		}
		
		// Create a list of items in the current feed that are not yet in the NEWS_ITEMS table
		for (Iterator iter = feed.getEntries().iterator(); iter.hasNext(); ) {
			SyndEntry item = (SyndEntry) iter.next();
			if (existingNewsItems.contains(item.getLink())) {
				skip();
			} else {
				newRssItems.add(item);
			}
		}
		
		return newRssItems;
	}
}
