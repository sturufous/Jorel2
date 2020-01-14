package ca.bc.gov.tno.jorel2.controller;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;

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
    		
	        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
		    		JAXBContext context = JAXBContext.newInstance(Rss.class);
		    		Unmarshaller unmarshaller = context.createUnmarshaller();
		    		rssContent = (Rss) unmarshaller.unmarshal(new URL(currentEvent.getTitle()));
		    		
		    		insertNewsItems(rssContent, session);
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
	
	@SuppressWarnings("preview")
	private void insertNewsItems(Rss rss, Session session) {
		
		List<Rss.Channel.Item> items = rss.getChannel().getItem();
		NewsItemsDao newsItem;
		RssSource rssSource = RssSource.valueOf(rss.getChannel().getTitle().toUpperCase());
		
		session.beginTransaction();
		
		for (Rss.Channel.Item item : items) {
	    	newsItem = switch (rssSource) {
				case IPOLITICS -> NewsItemFactory.createIpoliticsNewsItem(rss, item);
				default -> new NewsItemsDao();
	    	};
					
			session.persist(newsItem);
			System.out.println(item.getTitle());
		}
		
		session.getTransaction().commit();
	}
}
