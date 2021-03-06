package ca.bc.gov.tno.jorel2.controller;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.xml.bind.Unmarshaller;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.JaxbUnmarshallerFactory;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemQuotesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class RssEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	/** Quote extractor for processing article text  */
	@Inject
	QuoteExtractor quoteExtractor;
	
	/** Object containing a set of JAXB Unmarshaller objects, one for each xml format supported (e.g. Rss and Nitf)  */
	@Inject
	JaxbUnmarshallerFactory unmarshallerFactory;
	
	/** Contains a concurrent map of all RSS sources currently being processed. Restricts each source to one thread. */
	Map<String, String> sourcesBeingProcessed = new ConcurrentHashMap<>();
	
	/**
	 * Process all eligible RSS event records from the TNO_EVENTS table.  The goal is that separate threads can process different RSS events. An earlier 
	 * version of this code added the synchronized modifier
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    	
		Rss rssContent = null;
		
    	try {
    		logger.trace(StringUtil.getLogMarker(INDENT1) + "Starting RSS event processing" + StringUtil.getThreadNumber());
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        List<Rss.Channel.Item> newRssItems;
	        quoteExtractor.init();
	        
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        		String currentSource = currentEvent.getSource();
        			setThreadTimeout(runnable, currentEvent, instance);
		    		
		    		if (sourcesBeingProcessed.containsKey(currentSource)) {
		    			decoratedTrace(INDENT1, "Two (or more) threads attempting to process the " + currentSource + " feed - skipping.");
		    		} else {
		    			try {
			        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
				    			sourcesBeingProcessed.put(currentSource, "");
				    			rssContent = getRssContent(currentEvent.getTitle());
				    			
				    			if(rssContent != null) {
						    		newRssItems = getNewRssItems(currentSource, rssContent, session);
						    		insertNewsItems(currentSource, newRssItems, rssContent, session);
				    			}
				    			
					    		sourcesBeingProcessed.remove(currentSource);
			        		}
		    			}
		    			catch (Exception e) {
				    		sourcesBeingProcessed.remove(currentSource);
		    				logger.error("Processing RSS feed for: " + currentSource, e);
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
    	
		decoratedTrace(INDENT1, "Completing RSS event processing");
    	return Optional.of(rssContent != null ? rssContent.toString() : "No results.");
	}
	
	/**
	 * Filters out Rss.Channel.Items objects that correspond with existing entries in the NEWS_ITEMS table. This prevents the creation of duplicate records. 
	 * 
	 * @param source The name of the publisher of this rss feed (e.g. iPolitics, Daily Hive)
	 * @param rss The feed retrieved from the publisher
	 * @param session The active Hibernate persistence context
	 * @return News items that are not currently in the NEWS_ITEMS table
	 */
	private List<Rss.Channel.Item> getNewRssItems(String source, Rss rss, Session session) {
		
		List<Rss.Channel.Item> newRssItems = new ArrayList<>();
		List<NewsItemsDao> existingItems = NewsItemsDao.getItemsAddedSinceYesterday(source, session);
		Set<String> existingNewsItems = new HashSet<>();
		
		// Create a list of articles that are already in the NEWS_ITEMS table
		for (NewsItemsDao newsItem : existingItems) {
			existingNewsItems.add(newsItem.getWebpath());
		}
		
		// Create a list of items in the current feed that are not yet in the NEWS_ITEMS table
		for (Rss.Channel.Item rssItem : rss.getChannel().getItem()) {
			Date pubDate = DateUtil.getPubTimeAsDate(rssItem.getPubDate());
			Date filterDate = DateUtil.getDateMinusDays(new Date(), 2L);
			int comparator = pubDate.compareTo(filterDate);
			
			// Ignore any items already in the database, or published more than two days ago
			if (existingNewsItems.contains(rssItem.getLink()) || comparator <= 0) {
				skip();
			} else {
				newRssItems.add(rssItem);
			}
		}
		
		return newRssItems;
	}
	
	/**
	 * Create a record in the NEWS_ITEMS table that corresponds with each rss news item in
	 * the newsItems ArrayList.
	 * 
	 * @param source The source from which this feed was retrieved (e.g. iPolitics, DailyHive)
	 * @param newsItems The list of news items retrieved from the publisher
	 * @param session The current Hibernate persistence context
	 * @param rss The entire rss feed retrieved from the publisher
	 */
	private void insertNewsItems(String source, List<Rss.Channel.Item> newsItems, Rss rss, Session session) {
		
		NewsItemsDao newsItem = null;
		int articleCount = 0;
		
		if (!newsItems.isEmpty()) {
			
			// While most feeds are handled in a generic manner, allow for custom handling with a createXXXNewsItem() method if needed.
			for (Rss.Channel.Item item : newsItems) {
		    	newsItem = NewsItemFactory.createXmlNewsItem(item, source);
						
		    	// Persist the news item and perform post-processing				
		    	if (newsItem != null) {
					session.beginTransaction();
		    		session.persist(newsItem);
		    		
		    		// Extract all quotes, and who made them, from the news item.
		    		quoteExtractor.extract(newsItem.content);
		    		NewsItemQuotesDao.saveQuotes(quoteExtractor, newsItem, session);
		    		
					session.getTransaction().commit();
					articleCount++;
		    	}
			}
			
			instance.incrementArticleCount(source, articleCount);
			decoratedTrace(INDENT2, "Added: " + articleCount + " article(s) from " + source);
		}
	}
	
	/**
	 * Returns a JAXB Rss object containing all rss items at the url <code>address</code> or null if an error occurs.
	 * 
	 * @param address The web address of the RSS source.
	 * @return All articles at the url indicated in the <code>address</code> parameter.
	 */
	
	private Rss getRssContent(String address) {
		
		Rss rssContent = null;
		Unmarshaller unmarshaller = unmarshallerFactory.getRssUnmarshaller();
		
		try {
			// The JAXB unmarshaller is not thread safe, so synchronize unmarshalling
			synchronized(unmarshaller) {
				URL url = new URL(address);
				URLConnection connection = url.openConnection();
				connection.setConnectTimeout(URL_CONNECTION_TIMEOUT);
				connection.setReadTimeout(URL_READ_TIMEOUT);
				InputStream inputStream = connection.getInputStream();
				rssContent = (Rss) unmarshaller.unmarshal(inputStream);
				unmarshaller.notify();
			}
		} catch (SocketTimeoutException se) {
			instance.addHttpFailure("Timeout at: " + address);
			decoratedError(INDENT0, "Timeout at: " + address, se);
		} catch (Exception me) {
			decoratedError(INDENT0, "Retrieving RSS content from " + address, me);
		}
		
		return rssContent;
	}
}
