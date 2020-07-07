package ca.bc.gov.tno.jorel2.controller;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;

/**
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class AutorunEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/**
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting Autorun event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
			for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        
	    			autorunEvent(currentEvent, session);
	        		//DbUtil.updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
	        	}
			}
			
    		decoratedTrace(INDENT1, "Completed Autorun event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing user directory entries.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	private void autorunEvent(EventsDao currentEvent, Session session) {
			
		String startTimeStr = currentEvent.getStartTime() == null ? "00:00:00" : currentEvent.getStartTime();
		LocalDateTime now = LocalDateTime.now();
		LocalDate nowDate = LocalDate.now();
		String startHoursMinutes = startTimeStr.substring(0, 5);
		String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());
		
		if ((nowHoursMinutes.compareTo(startHoursMinutes) >= 0)) {
			processFilters(session);
		}
	}
	
	private void processFilters(Session session) {
		//ResultSet res = DbUtil.runSql("select * from EVENT_TYPES", session);
	}
}