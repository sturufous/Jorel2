package ca.bc.gov.tno.jorel2.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.PublishedPartsDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;
import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class HtmlEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2ServerInstance instance;
	
	private static final String mm[] = { "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12" };
	private static final String months[] = { "January", "February", "March", "April", "May", "June","July", "August", "September", "October", "November", "December" };
	private static final String mmm[] = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
	
	/**
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(Jorel2Runnable runnable, Session session) {
    			
    	try {
			decoratedTrace(INDENT1, "Starting Html event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, runnable.getEventTypeName(), session);
	        
	        for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
        			setThreadTimeout(runnable, currentEvent, instance);
	        		
	        		if (DateUtil.runnableToday(currentEvent.getFrequency())) {
	        			htmlEvent(currentEvent, session);
	        		}
	        		
	        		DbUtil.updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);
	        	}
	        }
    	}
    	catch (Exception e) {
    		logger.error("Processing Html entries.", e);
    	}
		
		decoratedTrace(INDENT1, "Completed Html event processing");
     	
    	return Optional.of("complete");
	}
	
	private void htmlEvent(EventsDao currentEvent, Session session) {
		
		String startTimeStr = currentEvent.getStartTime() == null ? "00:00:00" : currentEvent.getStartTime();
		LocalDateTime now = LocalDateTime.now();
		LocalDate nowDate = LocalDate.now();
		String startHoursMinutes = startTimeStr.substring(0, 5);
		String nowHoursMinutes = String.format("%02d:%02d", now.getHour(), now.getMinute());
		
		if ((nowHoursMinutes.compareTo(startHoursMinutes) >= 0)) {
			String part = currentEvent.getFileName();
			
        	switch (part) {
    			case "calendartemplate" -> updateCalendarPart(part.toUpperCase(), currentEvent, session);
        	};
		}
	}
		
	private void updateCalendarPart(String part, EventsDao currentEvent, Session session) {
		
		Calendar calendar = Calendar.getInstance();

		// setup for the current month
		String currentMonth = mm[calendar.get(Calendar.MONTH)]+calendar.get(Calendar.YEAR);     // "051999"
		Calendar ca = Calendar.getInstance();
		int currentDay = ca.get(Calendar.DATE);                                                 // 32
		int day = 1;
		ca.set(Calendar.DATE,day);

		// load the 'servletName'
		String servletName = PublishedPartsDao.getPublishedPartByName("SERVLETNAME", "tno.otis.servlet", session);
		
		// load the 'calendartemplatecell' which is the HTML for days upto and including the current day
		String cell = PublishedPartsDao.getPublishedPartByName(part + "CELL", "?cell", session);
		
		// load the 'calendartemplatenohref' which is the HTML for days after the current day
		String nohref = PublishedPartsDao.getPublishedPartByName(part + "NOHREF", "?nohref", session);

		// load the 'calendartemplate' part and get the content
		String html = PublishedPartsDao.getPublishedPartByName(part, "", session);
		
		if (html.compareTo("") > 0) {
			String dd = "0" + ca.get(Calendar.DATE);
			if (dd.length() == 3) dd = dd.substring(1);
			int previousMonth = ca.get(Calendar.MONTH);
			int previousYear = ca.get(Calendar.YEAR);

			// Cloak the month related stuff in the 'calendartemplate'
			Properties monthName = new Properties();
			monthName.put("month", months[previousMonth] + " " + previousYear);
			monthName.put("currentmonth", " " + mmm[ca.get(Calendar.MONTH)] + " ");
			monthName.put("currentddmmyyyy", ca.get(Calendar.YEAR) + "-" + mm[ca.get(Calendar.MONTH)] + "-" + dd);

			// cloak all the previous months link in the 'calendartemplate'
			Calendar temp_cal = Calendar.getInstance();

			previousMonth = previousMonth - 1;
			previousYear = ca.get(Calendar.YEAR);
			for (int i=1; i < 12; i++) {
				if (previousMonth < 0) {
					previousYear = previousYear - 1;
					previousMonth = 11;
				}
				
				temp_cal.clear();
				if(previousMonth > 11)
					temp_cal.set(previousYear + 1, 0, 1);
				else
					temp_cal.set(previousYear, previousMonth + 1, 1);
				temp_cal.add(Calendar.DATE, -1);

				monthName.put("month" + i, mmm[previousMonth]);
				monthName.put("ddmmyyyy" + i, "" + temp_cal.get(Calendar.YEAR) + "-" + mm[temp_cal.get(Calendar.MONTH)] + "-" + temp_cal.get(Calendar.DATE));
				previousMonth = previousMonth - 1;
			}
			
			html = StringUtil.replace(html, monthName);

			// Make the link for each day of the month up to the current day
			boolean started = false;               // 'started' mean when the 1st day of the month occurs
			for (int i = 1; i <= 37; i++) {        // there are 35 ddmmyyyy to be cloaked

				// need a properties object for the cloaking
				Properties values = new Properties();
				if ((started) | (i == ca.get(Calendar.DAY_OF_WEEK))) {    // depending on which day of the week the month starts at
					if (i == ca.get(Calendar.DAY_OF_WEEK)) started = true;

					// the day must be exactly 2 characters in the ddmmyyyy cloak
					dd = "0"+ca.get(Calendar.DATE);
					if (dd.length() == 3) dd = dd.substring(1);

					// cloak the links for this day
					Properties cellValues = new Properties();
					cellValues.put("ddmmyyyy", ca.get(Calendar.YEAR) + "-" + mm[ca.get(Calendar.MONTH)] + "-" + dd);
					cellValues.put("dd", ("." + ca.get(Calendar.DATE)).substring(1));
					//cellValues.put("servletname",servletName);
					String newCell = StringUtil.replace(cell, cellValues);

					// cloak the cellx link in the calendar template
					values.put("cell"+i,newCell);
					html = StringUtil.replace(html, values);

					// move the calendar to the next day
					day++;                                                  // next day
					if (day > currentDay) cell = nohref;                    // after today, no more HREF links
					ca.set(Calendar.DATE,day);                              // set the day of the month
					if (ca.get(Calendar.DATE) == 1) started = false;        // oops, we went past the end of the month

				} else {  // started is false
					// put in a &nbsp; for cellx
					values.put("cell" + i,"&nbsp;");                          // there is no day for this cell
					html = StringUtil.replace(html, values);                // cloak the &nbsp;
				}
			}
			
			PublishedPartsDao part1 = PublishedPartsDao.getPublishedPartByName(currentMonth, session);
			part1.setContent(StringUtil.stringToClob(html));
			session.beginTransaction();
			session.persist(part1);
			session.getTransaction().commit();

			// create the new part and publish it
			decoratedTrace(INDENT2, "HTML event executed for '" + part + "'", session);

		} else {
			decoratedTrace(INDENT2, "HTML event cannot find the part '" + part + "'", session);
		}
	}
}