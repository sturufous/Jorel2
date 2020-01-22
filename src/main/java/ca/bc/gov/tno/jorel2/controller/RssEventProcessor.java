package ca.bc.gov.tno.jorel2.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.Jorel2StringUtil;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class RssEventProcessor extends Jorel2Root implements Jorel2EventProcessor {

	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Inject
	private DataSourceConfig config;

	Rss rssContent;
	
	RssEventProcessor() {
		
	}
	
	/**
	 * Process all eligible RSS event records from the TNO_EVENTS table.
	 * 
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Session session) {
    	
    	try {
	        List<Object[]> results = EventsDao.getRssEvents(session);
	        List<Rss.Channel.Item> newRssItems;
    		
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
		    		JAXBContext context = JAXBContext.newInstance(Rss.class);
		    		Unmarshaller unmarshaller = context.createUnmarshaller();
		    		rssContent = (Rss) unmarshaller.unmarshal(new URL(currentEvent.getTitle()));
		    		
		    		newRssItems = getNewRssItems(currentEvent.getSource(), session, rssContent);
		    		
		    		insertNewsItems(currentEvent.getSource(), newRssItems, session, rssContent);
	        	} else {
		    		throw new IllegalArgumentException("Wrong data type in query results, expecting EventsDao.");    		
	        	}
	        	
	        } 
    	} 
    	catch (Exception e) {
    		logger.error("Retrieving or storing RSS feed.", e);
    	}
    	
    	return Optional.of(rssContent.toString());
	}
	
	/**
	 * Filters out Rss.Channel.Items objects that correspond with existing entries in the NEWS_ITEMS table. This prevents the creation of duplicate records. 
	 * While the RSS XML DTD is a fixed standard, different publications may choose to interpret fields within the standard differently. For example, the 
	 * Daily Hive feed stores the article body text in the <code>description</code> field, while iPolitics stores it in the <code>content:encoded</code> field. 
	 * Before a new item is added to the newRssItems List it is processed by Jorel2StringUtil.cleanUpItem() to address these disparities.
	 * 
	 * @param source The name of the publisher of this rss feed (e.g. iPolitics, Daily Hive)
	 * @param session The active Hibernate persistence context
	 * @param rss The feed retrieved from the publisher
	 * @return News items that are not currently in the NEWS_ITEMS table
	 */
	private List<Rss.Channel.Item> getNewRssItems(String source, Session session, Rss rss) {
		
		List<Rss.Channel.Item> newRssItems = new ArrayList<>();
		List<NewsItemsDao> existingItems = NewsItemsDao.getItemsAddedSinceYesterday(source, session);
		Set<String> existingNewsItems = new HashSet<>();
		
		// Create a list of articles that are already in the NEWS_ITEMS table
		for (NewsItemsDao newsItem : existingItems) {
			existingNewsItems.add(newsItem.getWebpath());
		}
		
		// Create a list of items in the current feed that are not yet in the NEWS_ITEMS table
		for (Rss.Channel.Item rssItem : rss.getChannel().getItem()) {
			if (existingNewsItems.contains(rssItem.getLink())) {
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
	 * @param newsItems The list of news items retrieved from the publisher
	 * @param session The current Hibernate persistence context
	 * @param rss The entire rss feed retrieved from the publisher
	 */
	@SuppressWarnings("preview")
	private void insertNewsItems(String source, List<Rss.Channel.Item> newsItems, Session session, Rss rss) {
		
		NewsItemsDao newsItem = null;
		String enumKey = source.toUpperCase().replaceAll("\\s+","");
		RssSource sourceEnum = RssSource.valueOf(enumKey);
		
		if (!newsItems.isEmpty()) {
			session.beginTransaction();
			
			// While most feeds are handled in a generic manner, allow for custom handling with a createXXXXNewsItem() method if needed.
			for (Rss.Channel.Item item : newsItems) {
		    	newsItem = switch (sourceEnum) {
					case IPOLITICS -> NewsItemFactory.createGenericNewsItem(rss, item, source);
					case DAILYHIVE -> NewsItemFactory.createGenericNewsItem(rss, item, source);
					//case CBC -> NewsItemFactory.createGenericNewsItem(rss, item, source);
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
}
