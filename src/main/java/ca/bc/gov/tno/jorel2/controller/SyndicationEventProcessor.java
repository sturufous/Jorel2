package ca.bc.gov.tno.jorel2.controller;

import java.net.SocketTimeoutException;
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
		String address = "";
		
		decoratedTrace(INDENT1, "Starting Syndication event processing");
		
    	try {    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
	        quoteExtractor.init();
    		
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		address = currentEvent.getTitle();
	    			URLConnection feedUrlConnection = new URL(address).openConnection();
	    			feedUrlConnection.setConnectTimeout(URL_CONNECTION_TIMEOUT);
	    			feedUrlConnection.setReadTimeout(URL_READ_TIMEOUT);
	    			String currentSource = currentEvent.getSource();
	    			
	    			feedUrlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
	    			SyndFeedInput input = new SyndFeedInput();
	    			XmlReader xmlReader = new XmlReader(feedUrlConnection);
	    			feed = input.build(xmlReader);
	    				
		    		if (sourcesBeingProcessed.containsKey(currentSource)) {
		    			decoratedTrace(INDENT1, "Two threads attempting to process the " + currentSource + " feed - skipping.");
		    		} else {
		    			processSyndFeed(feed, currentSource, session);
		    		}
		    	} else {
		    		decoratedError(INDENT0, "Looping through Syndication events.", new IllegalArgumentException("Wrong data type in query results, expecting EventsDao."));    		
	        	}
	        } 
    	} 
    	catch (SocketTimeoutException te) {
			instance.addHttpFailure("Timeout at: " + address);
			decoratedError(INDENT2, "Timeout at: " + address, te);
    	}
    	catch (Exception e) {
    		decoratedError(INDENT0, "Retrieving or storing RSS feed.", e);
    	}
    	
    	decoratedTrace(INDENT1, "Completing Syndication event processing");
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
		int articleCount = 0;

		if (!newsItems.isEmpty()) {
			
			for (SyndEntry item : newsItems) {
		    	newsItem = NewsItemFactory.createCPNewsItem(item, source, instance);
						
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
			decoratedTrace(INDENT1, "Added: " + articleCount + " article(s) from " + source);
		}
	}
	
	/**
	 * Filters out articles in <code>feed</code> that already exist in NEWS_ITEMS and inserts any new articles.
	 * 
	 * @param feed The list of URLs returned from the syndication source.
	 * @param currentSource The name of the current syndication source.
	 * @param session The current Hibernate persistence context.
	 */
	
	private void processSyndFeed(SyndFeed feed, String currentSource, Session session) {
		
        List<SyndEntry> newSyndItems;
        
		try {
			if (feed.getEntries().size() == 0) {
				instance.addHttpFailure("No entries returned in Syndication object for " + currentSource);
			} else {
				sourcesBeingProcessed.put(currentSource, "");
				newSyndItems = getNewRssItems(currentSource, feed, session);
				insertNewsItems(currentSource, session, newSyndItems);
				sourcesBeingProcessed.remove(currentSource);
			}
		}
		catch (Exception e) {
			sourcesBeingProcessed.remove(currentSource);
			logger.error("Processing Syndication feed for: " + currentSource, e);
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
	
	private List<SyndEntry> getNewRssItems(String source, SyndFeed feed, Session session) {
		
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
