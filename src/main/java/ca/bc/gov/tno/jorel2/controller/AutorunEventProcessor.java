package ca.bc.gov.tno.jorel2.controller;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.AutoRunDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.FolderItemDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

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
    	
		decoratedTrace(INDENT1, "Starting Autorun event processing");
		
    	try {
    		if (instance.isExclusiveEventActive(EventType.AUTORUN)) {
    			decoratedTrace(INDENT1, "Autorun event processing already active. Skipping.");    			
    		} else {
    			instance.addExclusiveEvent(EventType.AUTORUN);
    			decoratedTrace(INDENT1, "Starting Autorun event processing");
	    		
		        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
		        
		        for (Object[] entityPair : results) {
		        	if (entityPair[0] instanceof EventsDao) {
		        		EventsDao currentEvent = (EventsDao) entityPair[0];
		        		
		        		autorunEvent(currentEvent, session);
		        	}
		        }
		        
		        instance.removeExclusiveEvent(EventType.AUTORUN);
	    	} 
    	}
    	catch (Exception e) {
    		instance.removeExclusiveEvent(EventType.AUTORUN);
    		logger.error("Processing shell command entries.", e);
    	}
		
		decoratedTrace(INDENT1, "Completed Autorun event processing");
     	
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
	
	private void processFilters(Session session)
	{
		ResultSet rs = null;

		/*
		 * Get a vector of Story Line RSNs to process
		 * 	This way, we can lock them so 2 JORELs do not process the same Story Line
		 */
		Vector filterRsns = new Vector(5,2);
		String dateTrigger = AutoRunDao.getDateTrigger(session);

		String sql = "select fi.rsn, fi.name "+
		"from tno.filters fi, tno.folder f "+
		"where fi.folder_name = f.folder_name(+) and fi.user_rsn = f.user_rsn and fi.auto_run = 1 and fi.auto_folder = 1 and f.frozen = 0 and "+
		"(fi.last_run < to_date('" + dateTrigger + "','YYYY-MM-DD HH24:MI:SS') or fi.last_run is null)";
		try {
			rs = DbUtil.runSql(sql, session);
			while (rs.next())
			{
				filterRsns.addElement( Long.toString( rs.getLong(1) ));
			}
		} catch (Exception e) {
			decoratedError(INDENT2, "Filter collect RSNs Error! ", e);
		}
		try {rs.close();} catch (Exception e) {;}

		/*
		 * Now process the vector of Story Line RSNs, locking each record as we go
		 */
		int alertTriggerCount = 0;
		Enumeration vEnum = filterRsns.elements();
		while ((alertTriggerCount == 0) && vEnum.hasMoreElements())
		{
			String fiRsn = (String) vEnum.nextElement();
			String name = "";
			String status = "";

			sql = "select fi.*,f.rsn as \"frsn\",f.days_period,to_char(f.start_date,'YYYY-MM-DD'),to_char(f.end_date,'YYYY-MM-DD'),u.cp,u.scrums,u.social_media "+
			"from tno.filters fi, tno.folder f, tno.users u "+
			"where fi.folder_name = f.folder_name(+) and fi.user_rsn = f.user_rsn and u.rsn = fi.user_rsn and fi.auto_run = 1 and fi.auto_folder = 1 and f.frozen = 0 and "+
			"(fi.last_run < to_date('" + dateTrigger + "','YYYY-MM-DD HH24:MI:SS') or fi.last_run is null) and "+
			"fi.rsn = " + fiRsn + " for update nowait";
			try {
				rs = DbUtil.runSql(sql, session);

				if ((alertTriggerCount == 0) && rs.next())
				{
					long rsn = rs.getLong(1);
					long ursn = rs.getLong(2);
					name = rs.getString(3);

					String otherfiles = rs.getString(6);
					String defaultWhere = rs.getString(7);
					String text = rs.getString(8);
					defaultWhere = StringUtil.replacement(defaultWhere,"c.","n.");

					boolean historyRun = rs.getBoolean(13);
					int itemsForBuzz = rs.getInt(16);

					long frsn = rs.getLong(18);
					long daysPeriod = rs.getLong(19);
					String startDate = rs.getString(20);
					String endDate = rs.getString(21);

					boolean showCP = rs.getBoolean(22);
					boolean showScrums = rs.getBoolean(23);
					boolean showSocialMedia = rs.getBoolean(24);

					decoratedTrace(INDENT2, "Filter " + name + " " + fiRsn, session);

					/*
					 * Clear the auto_run_flag in the folder items, which is used to track which items have just been added
					 */
					FolderItemDao.updateAutoRun(frsn, session);

					/*
					 * Build SQL for the selected Story Line using the themes and other criteria
					 *
					 * Remove any date clause in the query from the filter, auto_run will add it's own date clause
					 */
					int p = defaultWhere.indexOf("n.item_date");
					int e = -1;
					while (p >= 0) {
						e = defaultWhere.indexOf(" and ",p);
						if (e > 0)
							defaultWhere = defaultWhere.substring(0,p) + defaultWhere.substring(e+5);
						else
							defaultWhere = defaultWhere.substring(0,p);
						p = defaultWhere.indexOf("n.item_date");
					}
					defaultWhere = defaultWhere.trim();
					if (defaultWhere.endsWith("and")) defaultWhere = defaultWhere.substring(0,defaultWhere.length()-3);

					// remove item_item references from the where clause
					p = defaultWhere.indexOf("n.item_time");
					e = -1;
					while (p >= 0) {
						e = defaultWhere.indexOf(" and ",p);
						if (e > 0)
							defaultWhere = defaultWhere.substring(0,p) + defaultWhere.substring(e+5);
						else
							defaultWhere = defaultWhere.substring(0,p);
						p = defaultWhere.indexOf("n.item_time");
					}
					defaultWhere = defaultWhere.trim();
					if (defaultWhere.endsWith("and")) defaultWhere = defaultWhere.substring(0,defaultWhere.length()-3);

					/*
					 * Restrict based on types the user has access to
					 */
					// Can the user view CP news stories?
					if (!showCP) {
						defaultWhere=defaultWhere+" and n.type <> 'CP News'";
					}
					// Can the user view Scrum stories?
					if (!showScrums) {
						defaultWhere=defaultWhere+" and n.type <> 'Scrum'";
					}
					// Can the user view Social Media stories?
					if (!showSocialMedia) {
						defaultWhere=defaultWhere+" and n.type <> 'Social Media'";
					}

					/*
					 * Words may have to be cloaked in, so do it now
					 */
					p = defaultWhere.indexOf("#words#");
					if (p > 0) defaultWhere = StringUtil.replacement(defaultWhere, "#words#", text);

					/*
					 * Date clause for the query
					 */
					String dateWhere = "n.item_date = to_date('" + DateUtil.localDateToTnoDateFormat(LocalDate.now()) + "','DD-MON-YY') ";
					if(daysPeriod == 0)
					{
						if ((startDate!=null) && (endDate != null)) {
							// hmm, specific date range, is the current date in that range
							dateWhere = "n.item_date between to_date('" + startDate + "','YYYY-MM-DD') and to_date('" + endDate + "','YYYY-MM-DD')";
						}
					}

					if(historyRun)
					{
						if(daysPeriod > 0)
						{
							dateWhere = "n.item_date >= to_date(sysdate-"+Long.toString(daysPeriod)+") ";
						}
						else
						{
							if ((startDate != null) && (endDate != null)) {
								dateWhere = "n.item_date between to_date('" + startDate + "','YYYY-MM-DD') and to_date('" + endDate + "','YYYY-MM-DD')";
							}
						}
					}

					int itemsInserted = 0;
					/*
					 * Insert the News Items into the Folder for the selected Filter
					 * 	NOTE: this could be a history run, in which case the search needs to cover the entire date range
					 * 		specified for this folder
					 */
					String query = "insert into folder_item ";
					query += "select tno.next_rsn.nextval,"+frsn+",n.rsn,1 from tno.news_items n "+
					"where "+ dateWhere+defaultWhere +
					" and n.rsn not in (select f.item_rsn from folder_item f where f.folder_rsn = " + Long.toString(frsn) + ")";
					try {
						int c = DbUtil.runUpdateSql(query, session);
						itemsInserted += c;
					} catch (Exception e2) {
						decoratedError(INDENT2, "Filter Search Error! ", e2);
					}

					if(historyRun)
					{
						query = "insert into folder_item ";
						query += "select tno.next_rsn.nextval,"+frsn+",n.rsn,1 from tno.hnews_items n "+
						"where " + dateWhere+defaultWhere +
						" and n.rsn not in (select f.item_rsn from folder_item f where f.folder_rsn = " + Long.toString(frsn) + ")";
						try {
							int c = DbUtil.runUpdateSql(query, session);
							itemsInserted += c;
						} catch (Exception e2) {
							decoratedError(INDENT2, "Filter Search Error! ", e2);
						}
					}
					if(itemsInserted > 0)
					{
						status = Integer.toString(itemsInserted) + " item(s) inserted";
						try {
							int c = DbUtil.runUpdateSql("update folder set updated = SYSDATE where rsn = " + frsn, session);
						} catch (Exception e2) {
							decoratedError(INDENT2, "Folder update error! ", e2);
						}
					}
					else
					{
						status = "no items found";
					}
					//try {oracleConn.c.commit();} catch (Exception ec) {;}

					/*
					 * BUZZ, calculate the buzz for this story line, after the items have been added to the folder
					 */
					float buzz=12;
					float buzz10=12;
					int totalItems=12;
					/*try {
						Properties otherValues=new Properties();
						if(itemsForBuzz<0)
							buzz = calculateBuzz(frsn,otherValues,true);
						else
							buzz = calculateBuzz(frsn,otherValues);
						String totalItemsString = (String)otherValues.get("totalItems");
						String buzz10String = (String)otherValues.get("buzz10");
						totalItems = Integer.parseInt(totalItemsString);
						buzz10 = Float.parseFloat(buzz10String);
					} catch (Exception e1) {
						say( "Filter buzz error! " + e1.toString() );
					}*/

					/*
					 * Update the filter so that it does not get picked up again during this cycle.
					 */
					String fiUpdate = "update filters set last_run = sysdate, history_run = 0, buzz = 12, itemsforbuzz = 12, buzz10 = 12 where rsn = " + fiRsn;
					try {
						DbUtil.runUpdateSql(fiUpdate, session);
					} catch (Exception e2) {
						decoratedError(INDENT2, "Filter update error! ", e2);
					}
				}
				else
				{
					if(alertTriggerCount == 0)
					{
						status = "skipped!  Probably processed by a different JOREL";
					}
					else
					{
						status = "skipped!  Alerts ready to be processed by this JOREL.";
					}
				}
			} catch (Exception e) {
				String err = e.toString();
				if(err.indexOf("ORA-00054") > 0)
				{
					status = "skipped!  Filter locked by a different JOREL";
				}
				else
				{
					decoratedError(INDENT2, "Filter " + fiRsn + " Loop Error! ", e);
				}
			}
			try {rs.close();} catch (Exception e) {;}
			//try {oracleConn.c.commit();} catch (Exception e) {;}

			/*
			 * Escape clause for some JORELs
			 *	Does this JOREL have more important events to process?  ALERTS
			 */
			/* if(doesAlerts)
			{
				if(alertObject != null) alertTriggerCount = alertObject.checkTrigger( oracleConn.getOracleConnection() );
			} */
			decoratedTrace(INDENT2, "Filter Done! " + fiRsn + " " + status, session);
		}
		decoratedTrace(INDENT2, "AutoRun Filters done!", session);
	}
}