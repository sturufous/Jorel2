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
	
	public Optional<String> processEvents() {
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
    	
    	try {
	    	if(sessionFactory.isEmpty()) {
	    		IllegalStateException e = new IllegalStateException("No session factory provided.");
	    		logger.error("Getting TNO session factory.", e);
	    		throw e;
	    	} else {
		        Session session = sessionFactory.get().openSession();

		        List<Object[]> results = EventsDao.getRssEvents(session);
	    		
		        // Because the getRssEvents method executes a join query it returns an array containing EventsDao and EventTypesDao objects
		        for (Object[] entityPair : results) {
		        	if (entityPair[0] instanceof EventsDao) {
		        		EventsDao currentEvent = (EventsDao) entityPair[0];
			    		JAXBContext context = JAXBContext.newInstance(Rss.class);
			    		Unmarshaller unmarshaller = context.createUnmarshaller();
			    		rssContent = (Rss) unmarshaller.unmarshal(new URL(currentEvent.getTitle()));
			    		
			    		insertItems(rssContent);
		        	} else {
			    		IllegalArgumentException e = new IllegalArgumentException("Wrong data type in query results.");
			    		logger.error("Expecting EventsDao object at position [0].", e);
			    		throw e;		        		
		        	}
		        	
		        } 
	    	}
    	} 
    	catch (Exception e) {
    		logger.error("Retrieving RSS feed", e);
    	}
    	
    	return Optional.of(rssContent.toString());
	}
	
	private void insertItems(Rss rss) {
		
		List<Rss.Channel.Item> items = rss.getChannel().getItem();
		
		for (Rss.Channel.Item item : items) {
			NewsItemsDao rssItem = new NewsItemsDao(
				//this.rsn = rsn;
				new BigDecimal(10),
				//this.itemDate = itemDate;
				new Date(2020, 6, 10),
				//this.source = source;
				rss.getChannel().getTitle(),
				//this.itemTime = itemTime
				new Date(2020, 6, 10),
				//this.summary = summary;
				item.getDescription(),
				//this.title = title;
				item.getTitle(),
				//this.type = type;
				"News",
				//this.frontpagestory = frontpagestory;
				false,
				//this.published = published;
				false,
				//this.archived = archived;
				false,
				//this.archivedTo = archivedTo;
				"Archived to",
				//this.recordCreated = recordCreated;
				new Date(2020, 6, 10),
				//this.recordModified = recordModified;
				new Date(2020, 6, 10),
				//this.string1 = string1;
				"",
				//this.string2 = string2;
				"",
				//this.string3 = string3;
				"",
				//this.string4 = string4;
				"",
				//this.string5 = string5;
				"",
				//this.string6 = string6;
				"",
				//this.string7 = string7;
				"",
				//this.string8 = string8;
				"",
				//this.string9 = string9;
				"",
				//this.number1 = number1;
				new BigDecimal(10),
				//this.number2 = number2;
				new BigDecimal(10),
				//this.date1 = date1;
				new Date(2020, 6, 10),
				//this.date2 = date2;
				new Date(2020, 6, 10),
				//this.filename = filename;
				"Filename",
				//this.fullfilepath = fullfilepath;
				"Fullpath",
				//this.webpath = webpath;
				item.getLink(),
				//this.thisjustin = thisjustin;
				false,
				//this.importedfrom = importedfrom;
				rss.getChannel().getTitle(),
				//this.expireRule = expireRule;
				new BigDecimal(10),
				//this.commentary = commentary;
				false,
				//this.text = text;
				null,
				//this.binary = binary;
				null,
				//this.contenttype = contenttype;
				"application/rss+xml",
				//this.binaryloaded = binaryloaded;
				false,
				//this.loadbinary = loadbinary;
				false,
				//this.externalbinary = externalbinary;
				false,
				//this.cbraNonqsm = cbraNonqsm;
				false,
				//this.postedby = postedby;
				"Posted by",
				//this.onticker = onticker;
				false,
				//this.waptopstory = waptopstory;
				false,
				//this.alert = alert;
				false,
				//this.autoTone = autoTone;
				new BigDecimal(10),
				//this.categoriesLocked = categoriesLocked;
				false,
				//this.coreAlert = coreAlert;
				false,
				//this.commentaryTimeout = commentaryTimeout;
				1000D,
				//this.commentaryExpireTime = commentaryExpireTime;
				new BigDecimal(10),
				//this.transcript = transcript;
				null,
				//this.eodCategory = eodCategory;
				"eodCategory",
				//this.eodCategoryGroup = eodCategoryGroup;
				"eodCategoryGroup",
				//this.eodDate = eodDate;
				"eodDate"
			);
					
			System.out.println(item.getTitle());
		}
		
	}
}
