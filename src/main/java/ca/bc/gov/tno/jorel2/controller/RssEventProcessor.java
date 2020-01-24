package ca.bc.gov.tno.jorel2.controller;

import java.lang.reflect.Method;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
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

import ca.bc.gov.tno.jorel2.Jorel2Process;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.IssuesDao;
import ca.bc.gov.tno.jorel2.model.Jorel2ArticleFilter;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemIssuesDao;
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

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Process process;

	Rss rssContent;
	
	/**
	 * Process all eligible RSS event records from the TNO_EVENTS table.
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
    	
    	try {
	        List<Object[]> results = EventsDao.getEventsByEventType(process, eventType, session);
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
			
			// While most feeds are handled in a generic manner, allow for custom handling with a createXXXNewsItem() method if needed.
			for (Rss.Channel.Item item : newsItems) {
		    	newsItem = switch (sourceEnum) {
					case IPOLITICS -> NewsItemFactory.createXmlNewsItem(item, source);
					case DAILYHIVE -> NewsItemFactory.createXmlNewsItem(item, source);
					case BIV -> NewsItemFactory.createXmlNewsItem(item, source);
					default -> null;
		    	};
						
		    	if (newsItem != null) {
		    		session.persist(newsItem);
		    		System.out.println(item.getTitle());
		    		
		    		List<NewsItemIssuesDao> niIssues = NewsItemIssuesDao.getNewsItemIssues(session);
		    		insertfilters(item, IssuesDao.class, NewsItemIssuesDao.class, session);
		    		
		    	}
			}
			
			session.getTransaction().commit();
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void insertfilters(Rss.Channel.Item item, Class filterTable, Class flaggerTable, Session session) {
		
		int hits = 0;
		String content = item.getEncoded() == null ? item.getDescription() : item.getEncoded();
		
		try {
			Method filter = filterTable.getMethod("getEnabledRecordList", Session.class);
			List<Jorel2ArticleFilter> results = (List<Jorel2ArticleFilter>) filter.invoke(null, session);
			
			for (Jorel2ArticleFilter stringList : results) {
				String[] caseInsensitive = stringList.getWords().split(",");
				String[] caseSensitive = stringList.getWordsCaseSensitive().split(",");
				
				for (String phrase : caseInsensitive) {
					phrase = phrase.toLowerCase();
					if (content.indexOf(phrase) >= 0) {
						System.out.println("***** Match for: " + phrase + ", "+ item.getTitle());
						hits++;
					}		
				}
				
				for (String phrase : caseSensitive) {
					if (content.indexOf(phrase) >= 0) {
						System.out.println("***** Match for: " + phrase + ", "+ item.getTitle());
						hits++;
					}		
				}
				
				if (hits > 0) {
				}
			}
		}
		catch (Exception e) {
			logger.error("Running static method getEnabledRecordList() on Method Object.", e);
		}
		
	}
}
