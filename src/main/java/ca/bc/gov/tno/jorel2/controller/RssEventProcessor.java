package ca.bc.gov.tno.jorel2.controller;

import java.net.URL;
import java.util.Optional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.dailyhive.DailyHiveRss;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class RssEventProcessor extends Jorel2Root implements Jorel2EventProcessor {

	DailyHiveRss rssContent;
	
	RssEventProcessor() {
		
	}
	
	/**
	 * Process all eligible RSS event records from the TNO_EVENTS table.
	 * 
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents() {
		
    	try {
    		JAXBContext context = JAXBContext.newInstance(DailyHiveRss.class);
    		Unmarshaller unmarshaller = context.createUnmarshaller();
    		rssContent = (DailyHiveRss) unmarshaller.unmarshal(new URL("http://dailyhive.com/feed/vancouver"));
    	} 
    	catch (Exception e) {
    		logger.error("Retrieving RSS feed", e);
    	}
    	
    	return Optional.of(rssContent.toString());
	}
}
