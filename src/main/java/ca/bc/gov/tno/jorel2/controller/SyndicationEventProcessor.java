package ca.bc.gov.tno.jorel2.controller;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemQuotesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;

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
	Jorel2Instance instance;
	
	/** Quote extractor for processing article text  */
	@Inject
	QuoteExtractor quoteExtractor;

	/** Contains a concurrent map of all RSS sources currently being processed. Restricts each source to one thread. */
	Map<String, String> sourcesBeingProcessed = new ConcurrentHashMap<>();
	
	/**
	 * Process all eligible non-XML syndication event records from the TNO_EVENTS table. This method is synchronized to prevent 
	 * two threads from processing the same event type at the same time.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	public Optional<String> processEvents(String eventType, Session session) {
	
		SyndFeed feed = null;
		
    	try {
    		logger.trace(StringUtil.getLogMarker(INDENT1) + "Starting Syndication event processing" + StringUtil.getThreadNumber());
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        List<SyndEntry> newSyndItems;
	        quoteExtractor.init();
    		
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	    			URLConnection feedUrlConnection = new URL(currentEvent.getTitle()).openConnection();
	    			String currentSource = currentEvent.getSource();
	    			
	    			feedUrlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
	    			SyndFeedInput input = new SyndFeedInput();
	    			XmlReader xmlReader = new XmlReader(feedUrlConnection);
	    			feed = input.build(xmlReader);

		    		if (sourcesBeingProcessed.containsKey(currentSource)) {
		    			logger.trace(StringUtil.getLogMarker(INDENT1) + "Two threads attempting to process the " + currentSource + " feed - skipping." + StringUtil.getThreadNumber());
		    		} else {
		    			try {
			    			sourcesBeingProcessed.put(currentSource, "");
				    		newSyndItems = getNewRssItems(currentSource, session, feed);
				    		insertNewsItems(currentSource, session, newSyndItems);
				    		sourcesBeingProcessed.remove(currentSource);
		    			}
		    			catch (Exception e) {
				    		sourcesBeingProcessed.remove(currentSource);
		    				logger.error("Processing Syndication feed for: " + currentSource, e);
		    			}
		    		}
		    	} else {
		    		logger.error("Looping through Syndication events.", new IllegalArgumentException("Wrong data type in query results, expecting EventsDao."));    		
	        	}
	        } 
    	} 
    	catch (Exception e) {
    		logger.error("Retrieving or storing RSS feed.", e);
    	}
    	
		logger.trace(StringUtil.getLogMarker(INDENT1) + "Completing Syndication event processing" + StringUtil.getThreadNumber());
    	return Optional.of("empty");
	}
	
	/**
	 * Create a record in the NEWS_ITEMS table that corresponds with each rss news item in the newsItems ArrayList.
	 * 
	 * @param source The source from which this feed was retrieved (e.g. 'CP News')
	 * @param session The current Hibernate persistence context
	 * @param newsItems The list of news items retrieved from the publisher
	 */
	@SuppressWarnings("preview")
	private void insertNewsItems(String source, Session session, List<SyndEntry> newsItems) {
		
		NewsItemsDao newsItem = null;
		String enumKey = source.toUpperCase().replaceAll("\\s+","");
		int articleCount = 0;

		RssSource sourceEnum = RssSource.valueOf(enumKey);
		
		if (!newsItems.isEmpty()) {
			
			for (SyndEntry item : newsItems) {
		    	newsItem = switch (sourceEnum) {
					case CPNEWS -> NewsItemFactory.createCPNewsItem(item, source);
					default -> null;
		    	};
						
		    	// Persist the news item and perform post-processing				
		    	if (newsItem != null) {
					session.beginTransaction();
					session.persist(newsItem);
					
		    		// Extract all quotes, and who made them, from the news item.
		    		quoteExtractor.extract(newsItem.content);
		    		NewsItemQuotesDao.saveQuotes(quoteExtractor, newsItem, session);
		    		
					session.getTransaction().commit();
		    	}
		    	
		    	articleCount++;
			}
			
			instance.incrementArticleCount(source, articleCount);
			logger.trace(StringUtil.getLogMarker(INDENT1) + "Added: " + articleCount + " article(s) from " + source + StringUtil.getThreadNumber());
		}
	}
	
	/**
	 * Filters out objects that correspond with existing entries in the NEWS_ITEMS table. This prevents the creation of duplicate records.
	 * 
	 * @param source The name of the publisher of this rss feed (e.g. iPolitics, Daily Hive)
	 * @param session The active Hibernate persistence context
	 * @param feed The feed retrieved from the publisher
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
		for (Iterator<?> iter = feed.getEntries().iterator(); iter.hasNext(); ) {
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
