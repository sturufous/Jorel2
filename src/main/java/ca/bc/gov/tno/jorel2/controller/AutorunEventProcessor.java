package ca.bc.gov.tno.jorel2.controller;

import java.io.StringReader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Vector;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.AutoRunDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.FolderItemDao;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;
import oracle.sql.CLOB;

/**
 * As of mid-July 2020 the focus switched from re-factoring Jorel1 code to meeting the current budgetary restrictions of the project.
 * Consequently this event processor largely foregoes the use of Hibernate and runs the same code as Jorel1. SQL statemements are processed
 * using the methods DbUtil.runSql() and and DbUtil.runUpdatedSql() which employ Hibernate's doReturningWork() method to execute native 
 * PL/SQL code via the connection associated with the Hibernate session object. 
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
			String dateTrigger = AutoRunDao.getDateTrigger(session);

			processFilters(dateTrigger, session);
			processReports(dateTrigger, session);
		}
	}
	
	private void processFilters(String dateTrigger, Session session)
	{
		ResultSet rs = null;

		/*
		 * Get a vector of Story Line RSNs to process
		 * 	This way, we can lock them so 2 JORELs do not process the same Story Line
		 */
		Vector filterRsns = new Vector(5,2);

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
	
	private void processReports(String dateTrigger, Session session)
	{
		String httpHost = instance.getAppHttpHost();

		/*
		 * 1) Which reports are auto run
		 */
		decoratedTrace(INDENT2, "AutoRun Reports starting...", session);
		ResultSet rs = null;
		ResultSet rs2 = null;

		/*
		 * Is it the weekend?
		 */
		String runTimeField = "run_time";
		Calendar cal = Calendar.getInstance();
		if ((cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) || (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)) {
			runTimeField = "run_time_wknd";
		}
		int day_of_week = cal.get(Calendar.DAY_OF_WEEK);

		/*
		 * Get a vector of Report RSNs to process
		 * 	This way, we can lock them so 2 JORELs do not process the same Report
		 */
		Vector<String> report_rsns = new Vector<String>(5,2);;

		boolean triggered = false;
		String sql = "select r.rsn from tno.reports r where r.auto_run = 1 and "+
		"(r.freq_daily = 1 or "+
		"(r.freq_weekly = 1 and freq_weekly_day = "+day_of_week+")"+
		") and "+
		"to_char(r."+runTimeField+",'HH24:MI') <> '00:00' and "+
		"to_char(r."+runTimeField+",'HH24:MI') < to_char(sysdate,'HH24:MI') and "+
		"(to_char(r.last_run,'YYYY-MM-DD') < substr('" + dateTrigger + "',1,10) or r.last_run is null)";
		try {
			decoratedTrace(INDENT2, "Reports sql... " + sql, session);
			rs = DbUtil.runSql(sql, session);
			while (rs.next())
			{
				if(! triggered)
				{
					triggered = true;
					decoratedTrace(INDENT2, "Auto Run Report triggered! " + sql, session);
				}
				report_rsns.addElement( Long.toString( rs.getLong(1) ));
			}
		} catch (Exception e) {
			decoratedError(INDENT2, "Report collect RSNs Error! " + e.toString(), e);
		}
		try {rs.close();} catch (Exception e) {;}

		/*
		 * Now process the vector of Reports RSNs, locking each record as we go
		 */
		int alertTriggerCount = 0;
		Enumeration<String> vEnum = report_rsns.elements();
		while ((alertTriggerCount == 0) && vEnum.hasMoreElements())
		{	
			/*
			 * 2) Get report and lock it up so other JORELs will not process this same report
			 */
			String rRsn = (String) vEnum.nextElement();
			String status = "";
			decoratedTrace(INDENT2, "Looking for Report " + rRsn, session);

			sql = "select r.rsn,r.user_rsn,r.auto_run_dups,r.auto_run_send,to_char(r.last_run,'YYYY-MM-DD HH24:MI:SS'),r.report_group,r.name,r.auto_bc_update,u.cp,u.scrums,u.social_media,r.pref_sort,r.auto_run_dups_section,to_char(r." + runTimeField + ",'HH24:MI') "+
			" from tno.reports r, tno.users u where r.rsn = " + rRsn+" and u.rsn = r.user_rsn and (r.last_run < to_date('" + dateTrigger + "','YYYY-MM-DD HH24:MI:SS') or r.last_run is null) order by r." + runTimeField +" for update nowait";
			try {
				rs = DbUtil.runSql(sql, session);

				if ((alertTriggerCount == 0) && rs.next())
				{
					long rsn = rs.getLong(1);
					long userRsn = rs.getLong(2);
					boolean autoRunDups = rs.getBoolean(3);
					boolean autoRunSend = rs.getBoolean(4);
					String lastRun = rs.getString(5);
					if(lastRun == null) lastRun = aktivDate.yyyymmdd()+" 00:00:05";
					String reportGroup = rs.getString(6);
					if(reportGroup == null) reportGroup = "";
					String reportName = rs.getString(7);
					if(reportName == null) reportName = "un-named";
					boolean autoBcUpdate = rs.getBoolean(8);
					boolean showCP = rs.getBoolean(9);
					boolean showScrums = rs.getBoolean(10);
					boolean showSocialMedia = rs.getBoolean(11);
					String prefSort = rs.getString(12);
					boolean autoRunDupsSection = rs.getBoolean(13);
					String runTime = rs.getString(14);

					String startTime = new SimpleDateFormat("HH:mm").format(Calendar.getInstance().getTime());
					String timeStamp = new SimpleDateFormat("HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
					long s = (new java.util.Date()).getTime();

					decoratedTrace(INDENT2, "Populate Report " + reportName + " at " + timeStamp + " [" + prefSort + "]", session);

					/*
					 * 3) Update the Report so that it does not get picked up again during this cycle.
					 */
					String slUpdate = "update reports set last_run = sysdate + INTERVAL '5' minute where rsn = " + rsn;
					try {
						DbUtil.runUpdateSql(slUpdate, session);
					} catch (Exception e2) {
						decoratedError(INDENT2, "Report update error! ", e2);
					}
					//try {oracleConn.c.commit();} catch (Exception e) {;}

					/*
					 * 4) Clear existing stories
					 */
					try {
						DbUtil.runSql("delete from tno.report_stories where report_rsn = " + rRsn, session);
					} catch (Exception e) {
						decoratedError(INDENT2, "Report data cleared Error! ", e);
					}

					/*
					 * 5) Re-populate report stories for each section using the filter or story line assigned to the section
					 */
					reportsRepopulate(rsn, autoRunDups, autoRunDupsSection, lastRun, reportGroup, showCP, showScrums, showSocialMedia, autoBcUpdate, session);

					/*
					 * 5.5) Analysis graphs for this report (after the report has been populated)
					 * No plan to use doesAlerts for Jorel2 yet, so "false" is passed through 
					 */
					analysisForReportData(rsn, false, dateTrigger, session);

					/*
					 * 6) Create AND send the report
					 */
					if(autoRunSend)
					{
						decoratedTrace(INDENT2, "Report to email report " + reportName, session);

						dbParts y = new dbParts();
						//dbCloaker x = new dbCloaker(oracleConn.db,y,"<**","**>");

						//
						//	Need some users fields (email, view_tone)
						//
						String userSql = "select u.email_address,u.view_tone from users u where u.rsn = " + userRsn;
						rs2 = DbUtil.runSql(userSql, session);
						if(rs2.next())
						{
							String userEmail = rs2.getString(1);
							boolean viewTone = rs2.getBoolean(2);
							String tpu = getTonePoolUsers(userRsn, session);
							String mailHost = instance.getMailHostAddress();
							httpHost = instance.getAppHttpHost();
							long currentPeriod = frame.getPrefs().getCurrentPeriod();
							String avHost = instance.getStorageAvHost();

							decoratedTrace(INDENT2, "Report create Report object and report.send() it...", session);
							String msg= "";
							try
							{
								String sqlUpdate = "update tno.report_stories set sort_position = ? where rsn = ?";
								PreparedStatement ps = oracleConn.c.prepareStatement(sqlUpdate);

								Report report = new Report(oracleConn, x, rsn, userRsn, userEmail, viewTone, tpu, mailHost, httpHost, currentPeriod, avHost);
								String sortMsg = report.sortStories(prefSort, Long.toString(rsn), ps);
								msg = report.send();
							} catch (Exception e)
							{
								decoratedError(INDENT2, "Report send error: " + userEmail, e);
							}
							msg = msg.trim();
							if(msg.equalsIgnoreCase("Email sent!"))
							{
								decoratedTrace(INDENT2, "Report send message: " + msg + " " + userEmail, session);
							}
							else
							{
								decoratedTrace(INDENT2, "Report send failed! " + userEmail, session);
								decoratedTrace(INDENT2, "Report send failed! " + msg, session);
								/*
								 * 	An error occurred while sending the email, so at this point, we assume the report did not get emailed
								 */
								if ( (msg.indexOf("Could not connect to SMTP host") > 0) || (msg.indexOf("Unknown SMTP host") > 0) )
								{
									String revertUpdate = "update reports set last_run = to_date('" + lastRun + "','YYYY-MM-DD HH24:MI:SS') where rsn = " + rsn;
									try {
										DbUtil.runUpdateSql(revertUpdate, session);
									} catch (Exception e4)
									{
										decoratedError(INDENT2, "Report could not reset report for re-run after SMTP host error! " + rsn + " " + userEmail, e4);
									}
								}
							}
						}
					}
					long e1 = (new java.util.Date()).getTime()-s;                      // end the query timer
					String end_time = new SimpleDateFormat("HH:mm").format(Calendar.getInstance().getTime());
					status = " ** timing ** " + reportName + ", start time: " + runTime + ", started: " + startTime+", ended: " + end_time + ", elapsed: " + e1;

					/*
					 * 7) Final commit
					 */
					//try {oracleConn.c.commit();} catch (Exception e) {;}
				} else {
					status = "skipped!  Report last run date modified by a different JOREL";
				}
			} catch (Exception e) {
				String err = e.toString();
				if(err.indexOf("ORA-00054") > 0)
				{
					status = "skipped!  Report locked by a different JOREL";
				}
				else
				{
					decoratedError(INDENT2, "Report " + rRsn + " Loop Error!", e);
				}
			}
			if (rs != null) try {rs.close();} catch (Exception e) {;}
			if (rs2 != null) try {rs2.close();} catch (Exception e) {;}
			//try {oracleConn.c.commit();} catch (Exception e) {;}

			/*
			 * Escape clause for some JORELs
			 *	Does this JOREL have more important events to process?  ALERTS
			 */
			/* if(doesAlerts)
			{
				if(alertObject != null) alertTriggerCount = alertObject.checkTrigger( oracleConn.getOracleConnection() );
			} */
			decoratedTrace(INDENT2, "Report Done! " + rRsn + " " + status, session);
		}
		decoratedTrace(INDENT2, "AutoRun Reports done!", session);
	}
	
	/*
	 * 4) Re-populate report stories for each section using the filter or story line assigned to the section
	 */
	public void reportsRepopulate(long reportRsn, boolean autoRunDups, boolean autoRunDupsSection, String lastRun, String reportGroup, boolean showCP, boolean showScrums, boolean showSocialMedia, boolean autoBcUpdate, Session session)
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "select s.rsn,s.filter_rsn,s.story_line_rsn,s.folder_rsn from tno.report_sections s where s.report_rsn = " + reportRsn + " order by s.sort_position";
		try {
			rs = DbUtil.runSql(sql, session);
			while (rs.next())
			{
				long sectionRsn = rs.getLong(1);
				long filterRsn = rs.getLong(2);
				long storylineRsn = rs.getLong(3);
				long folderRsn = rs.getLong(4);

				if(filterRsn > 0) reportsByFilter(reportRsn, sectionRsn, filterRsn, autoRunDups, autoRunDupsSection, lastRun, reportGroup, showCP, showScrums, showSocialMedia, autoBcUpdate, session);
				if(folderRsn > 0) reportsByFolder(reportRsn, sectionRsn, folderRsn, autoRunDups, autoRunDupsSection, lastRun, reportGroup, showCP, showScrums, showSocialMedia, autoBcUpdate, session);
				if(storylineRsn > 0) reportsByStoryline(reportRsn, sectionRsn, storylineRsn, autoRunDups, autoRunDupsSection, lastRun, reportGroup, showCP, showScrums, showSocialMedia, autoBcUpdate, session);
			}
		} catch (Exception e) {
			decoratedError(INDENT2, "Report repopulate " + reportRsn + " Error! ", e);
		}
		try { if (rs != null) rs.close(); } catch (SQLException err) {;}
		try { if (stmt != null) stmt.close(); } catch (SQLException err) {;}
	}
	
	private void analysisForReportData(Long reportRsn, boolean doesAlerts, String dateTrigger, Session session)
	{
		decoratedTrace(INDENT2, "AutoRun Analysis for Report data starting...", session);
		ResultSet rs = null;
		ResultSet rs2 = null;

		/*
		 * Get a vector of Story Line RSNs to process
		 * 	This way, we can lock them so 2 JORELs do not process the same Story Line
		 */
		Vector<String> analysis_rsns = new Vector<String>(5,2);;

		/*
		 * Only select analysis graphs for the current report
		 */
		String sql = "select a.rsn from tno.analysis a "+
		"where a.auto_run = 1 and "+
		"a.rsn in (select rg.analysis_rsn from report_graphs rg where rg.report_rsn = " + Long.toString(reportRsn) + ") and " +
		"(a.last_run < to_date('" + dateTrigger + "','YYYY-MM-DD HH24:MI:SS') or a.last_run is null)";
		try {
			rs = DbUtil.runSql(sql, session);
			while (rs.next())
			{
				analysis_rsns.addElement( Long.toString( rs.getLong(1) ));
			}
		} catch (Exception e) {
			decoratedError(INDENT2, "Analysis collect RSNs Error! ", e);
		}
		try {rs.close();} catch (Exception e) {;}

		/*
		 * Now process the vector of Story Line RSNs, locking each record as we go
		 */
		int alertTriggerCount = 0;
		Enumeration<String> vEnum = analysis_rsns.elements();
		while ((alertTriggerCount == 0) && vEnum.hasMoreElements())
		{
			String aRsn = (String) vEnum.nextElement();
			String status = "";
			decoratedTrace(INDENT2, "Analysis " + aRsn, session);

			sql = "select a.rsn, a.user_rsn, a.name, a.data, to_char(a.last_run,'YYYY-MM-DD HH24:MI:SS') from tno.analysis a where a.rsn = " + aRsn + " and (a.last_run < to_date('" + dateTrigger + "','YYYY-MM-DD HH24:MI:SS') or a.last_run is null) for update nowait";
			try {
				rs = DbUtil.runSql(sql, session);

				if ((alertTriggerCount == 0) && rs.next())
				{

					long rsn = rs.getLong(1);
					long userRsn = rs.getLong(2);
					String name = rs.getString(3);
					String lastRun = rs.getString(5);
					if (lastRun==null) lastRun = "";

					// For some of the form data (most actually), the form data is
					//    in an XML string
					Clob cl=rs.getClob(4);
					long l=cl.length();
					Document doc=null;
					String xml="";
					if(l>0){
						xml=cl.getSubString(1,(int)l); // scares me here too
						doc=parseXML(xml);
					}

					String searchType=getValueByTagName(doc, "a_searchtype");

					/*
					 * This Analysis graph is for the search type of report
					 */
					if (searchType.equalsIgnoreCase("report")) {
						try {

							updateLastRunDate(rsn, session);	// so it will not be picked up during this cycle twice

							/*
							 * Draw the analysis
							 */
							Analysis a = new Analysis(oracleConn, rsn, user_rsn, daily.prefs.getCurrentPeriod(), true);
							a.draw(false, true);

							/*
							 * Draw any analysis appearing in reports
							 */
							sql = "select rg.image_size, rg.font_size from tno.report_graphs rg where rg.analysis_rsn = " + aRsn;
							rs2 = DbUtil.runSql(sql, session);
							while (rs2.next())
							{
								long imageSize = rs2.getLong(1);
								long fontSize = rs2.getLong(2);
								a = new Analysis(oracleConn, rsn, userRsn, daily.prefs.getCurrentPeriod(), (int) imageSize, (int) fontSize, true);
								a.draw(false, true);
							}
							rs2.close();
							
							status = "drawn";

						} catch (Exception e2) {
							decoratedError(INDENT0, "Analysis update error!", e2);
						}
					}
				}
				else
				{
					if(alertTriggerCount == 0)
					{
						status = "skipped! Probably processed by a different JOREL";
					}
					else
					{
						status = "skipped! Alerts ready to be processed by this JOREL.";
					}
				}
			} catch (Exception e) {
				String err = e.toString();
				if(err.indexOf("ORA-00054") > 0)
				{
					status = "skipped!  Analysis locked by a different JOREL";
				}
				else
				{
					decoratedError(INDENT0, "Analysis " + aRsn + " Loop Error!", e);
				}
			}
			if (rs!=null) try {rs.close();} catch (Exception e) {;}
			if (rs2!=null) try {rs2.close();} catch (Exception e) {;}
			//try {oracleConn.c.commit();} catch (Exception e) {;}

			/*
			 * Escape clause for some JORELs
			 *	Does this JOREL have more important events to process?  ALERTS
			 */
			/* if(doesAlerts)
			{
				if(alertObject != null) alertTriggerCount = alertObject.checkTrigger( oracleConn.getOracleConnection() );
			} */
			decoratedTrace(INDENT2, "Analysis " + aRsn + " " + status, session);
		}
		decoratedTrace(INDENT2, "AutoRun Analysis done!", session);
	}
	
	/*
	 * 4) Re-populate report stories by filter
	 */
	public void reportsByFilter(long reportRsn, long sectionRsn, long filterRsn, boolean autoRunDups, boolean autoRunDupsSection, String lastRun, String reportGroup, boolean showCP, boolean showScrums, boolean showSocialMedia, boolean autoBcUpdate, Session session)
	{
		ResultSet rs = null;

		/*
		 * Get the filter
		 */
		String sql = "select fi.* from tno.filters fi where fi.rsn = " + filterRsn;
		try {
			rs = DbUtil.runSql(sql, session);
			if (rs.next())
			{
				long userRsn = rs.getLong(2);

				String defaultWhere = rs.getString(7);
				String text = rs.getString(8);
				defaultWhere = StringUtil.replacement(defaultWhere,"c.","n.");

				/*
				 * Build SQL for the selected filter
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
				// Omit BC update
				if (autoBcUpdate) {
					defaultWhere=defaultWhere+" and (CONTAINS(n.title,'BC Update',8)=0) and (CONTAINS(n.title,'BC Calendar',9)=0)";
				}

				/*
				 * Words may have to be cloaked in, so do it now
				 */
				p = defaultWhere.indexOf("#words#");
				if (p > 0) defaultWhere = StringUtil.replacement(defaultWhere,"#words#",text);  			

				/*
				 * Date clause for the query
				 */
				//String dateWhere = "n.item_date = to_date('"+aktivDate.today()+"','DD-MON-YYYY') ";
				//String dateWhere = "to_char(n.record_created,'YYYY-MM-DD HH24:MI:SS') >= '"+last_run+"' ";
				String dateWhere = "n.record_created >= to_date('" + lastRun + "','YYYY-MM-DD HH24:MI:SS') ";

				/*
				 * Find all the news items for today for the filter
				 */
				String newsSql = "select n.rsn from tno.news_items n where " + dateWhere + defaultWhere;
				if(! autoRunDups)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn <> " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn)+" and r.report_group = '" + reportGroup + "')";
				}
				if(!autoRunDupsSection)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn = " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn)+" and r.report_group = '" + reportGroup + "')";
				}
				try {
					decoratedTrace(INDENT2, "Autorun Report: filter sql: " + newsSql, session);

					ResultSet rs2 = DbUtil.runSql(newsSql, session);
					long counter = 0;
					while (rs2.next())
					{
						counter++;
						long newsItemRsn = rs2.getLong(1);
						reportsByItemRsn(userRsn, reportRsn, sectionRsn, newsItemRsn, text, counter, session);
					}
					rs2.close();
				} catch (Exception e2) {
					decoratedError(INDENT0, "Report repopulate filter search Error! ", e2);
				}
			}
		} catch (Exception e) {
			decoratedError(INDENT0, "Report repopulate by filter " + reportRsn + " Error!", e);
		}
		try { if (rs != null) rs.close(); } catch (SQLException err) {;}
	}
	
	/*
	 * 4) Re-populate report stories by story line
	 */
	public void reportsByFolder(long reportRsn, long sectionRsn, long folderRsn, boolean autoRunDups, boolean autoRunDupsSection, String lastRun, String reportGroup, boolean showCP, boolean showScrums, boolean showSocialMedia, boolean autoBcUpdate, Session session)
	{
		ResultSet rs = null;
		String text = null;

		String sql = "select fo.* from tno.folder fo where fo.rsn = " + folderRsn;
		try {
			rs = DbUtil.runSql(sql, session);
			if (rs.next())
			{
				long userRsn = rs.getLong(2);

				String newsSql = "select n.rsn from tno.news_items n where n.rsn in (select fi.item_rsn from folder_item fi where fi.folder_rsn = "+folderRsn+")";
				if(! autoRunDups)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn <> " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn)+" and r.report_group = '" + reportGroup+"')";
				}
				if(! autoRunDupsSection)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn = " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn)+" and r.report_group = '" + reportGroup + "')";
				}
				newsSql = newsSql + " union all ";
				newsSql = newsSql + "select n.rsn from tno.hnews_items n where n.rsn in (select fi.item_rsn from folder_item fi where fi.folder_rsn = " + folderRsn+")";
				if(! autoRunDups)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn <> " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn) + " and r.report_group = '" + reportGroup + "')";
				}
				if(! autoRunDupsSection)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s " +
					"where r.rsn = s.report_rsn and r.rsn = " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn)+" and r.report_group = '" + reportGroup + "')";
				}
				try {
					decoratedTrace(INDENT2, "Autorun Report: folder sql: " + newsSql, session);

					ResultSet rs2 = DbUtil.runSql(newsSql, session);
					long counter = 0;
					while (rs2.next())
					{
						counter++;
						long newsItemRsn = rs2.getLong(1);
						reportsByItemRsn(userRsn, reportRsn, sectionRsn, newsItemRsn, text, counter, session);
					}
					decoratedTrace(INDENT2, "Added " + counter + " items to report [" + reportRsn + "] section " + sectionRsn, session);
					rs2.close();
				} catch (Exception e2) {
					decoratedError(INDENT0, "Report repopulate folder search Error! ", e2);
				}
			}
		} catch (Exception e) {
			decoratedError(INDENT0, "Report repopulate by folder " + reportRsn + " Error! ", e);
		}
		try { if (rs != null) rs.close(); } catch (SQLException err) {;}
	}
	
	/*
	 * 4) Re-populate report stories by story line
	 */
	public void reportsByStoryline(long reportRsn, long sectionRsn, long storylineRsn, boolean autoRunDups, boolean autoRunDupsSection, String lastRun, String reportGroup, boolean showCP, boolean showScrums, boolean showSocialMedia, boolean autoBcUpdate, Session session)
	{
		ResultSet rs = null;

		/*
		 * Get the story line
		 */
		String sql = "select s.* from tno.story_lines s where s.rsn = " + storylineRsn;
		try {
			rs = DbUtil.runSql(sql, session);
			if (rs.next())
			{
				long userRsn = rs.getLong(2);
				String name = rs.getString(3);
				String themes = rs.getString(4);
				long score = rs.getLong(5);
				boolean published = rs.getBoolean(6);
				String queryText = "";

				/*
				 * Build SQL for the selected Story Line using the themes and other criteria
				 */
				String themeWhere = "";
				String scoreWhere = " and ((";
				String[] temp_t = themes.split(",");
				int themesCount = temp_t.length;
				for(int j=0; j < themesCount; j++)
				{
					themeWhere += " and CONTAINS(n.text, 'about("+temp_t[j] + ")'," + Integer.toString(j+1) + ")>0 ";
					scoreWhere += " score(" + Integer.toString(j+1) + ")+";

					queryText += "about(" + temp_t[j] + ") ";
				}
				if(score > 0)
				{
					scoreWhere += "0) > " + Long.toString(score) + ")";
					themeWhere += scoreWhere;
				}	
				//String dateWhere = "n.item_date = to_date('"+aktivDate.today()+"','DD-MON-YYYY') ";
				//String dateWhere = "to_char(n.record_created,'YYYY-MM-DD HH24:MI:SS') >= '"+last_run+"' ";
				String dateWhere = "n.record_created >= to_date('" + lastRun + "','YYYY-MM-DD HH24:MI:SS') ";
				if(published)
				{
					themeWhere += " and n.published = 1";
				}

				/*
				 * Restrict based on types the user has access to
				 */
				// Can the user view CP news stories?
				if (!showCP) {
					themeWhere = themeWhere + " and n.type <> 'CP News'";
				}
				// Can the user view Scrum stories?
				if (!showScrums) {
					themeWhere = themeWhere + " and n.type <> 'Scrum'";
				}
				// Can the user view Social Media stories?
				if (!showSocialMedia) {
					themeWhere = themeWhere + " and n.type <> 'Social Media'";
				}
				// Omit BC update
				if (autoBcUpdate) {
					themeWhere=themeWhere+" and (CONTAINS(n.title,'BC Update',8)=0) and (CONTAINS(n.title,'BC Calendar',9)=0)";
				}

				/*
				 * Find all the news items for today for the story line
				 */
				String newsSql = "select n.rsn from tno.news_items n where "+dateWhere+themeWhere;
				if(!autoRunDups)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn <> " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn) + " and r.report_group = '" + reportGroup + "')";
				}
				if(! autoRunDupsSection)
				{
					newsSql = newsSql + " and n.rsn not in (select s.item_rsn from reports r, report_stories s "+
					"where r.rsn = s.report_rsn and r.rsn = " + Long.toString(reportRsn) + " and r.auto_run = 1 and r.user_rsn = " + Long.toString(userRsn) + " and r.report_group = '" + reportGroup + "')";
				}
				try {
					ResultSet rs2 = DbUtil.runSql(newsSql, session);
					long counter = 0;
					while (rs2.next())
					{
						counter++;
						long news_item_rsn = rs2.getLong(1);
						reportsByItemRsn(userRsn, reportRsn, sectionRsn, news_item_rsn, queryText, counter, session);
					}
					rs2.close();
				} catch (Exception e2) {
					decoratedError(INDENT0, "Report repopulate story line search Error!", e2);
				}
			}
		} catch (Exception e) {
			decoratedError(INDENT0, "Report repopulate by story line " + reportRsn + " Error!", e);
		}

		try { if (rs != null) rs.close(); } catch (SQLException err) {;}
	}

	/*
	 * 4) Re-populate report stories by news item rsn
	 */
	public void reportsByItemRsn(long userRsn, long reportRsn, long sectionRsn, long itemRsn, String queryText, long counter, Session session)
	{
		//say("reports_by_item_rsn() add item #"+counter);

		Statement stmt = null;
		ResultSet rs = null;

		String storyrsn = "";
		String sort = "-1";
		String tocsort = "99";
		String summary = "";
		String headline = "";
		String content = "";

		boolean pref3 = false;
		boolean pref8 = false;
		boolean pref11 = false;
		boolean pref_inc_images = false;
		boolean pref_use_thumb = false;
		boolean pref_inc_frontpage = false;
		boolean show_tone = false;
		boolean pref_cloak_byline = false; // include the byline in the TOC
		boolean pref_add_frontpage = false; // include [FRONTPAGE] in the TOC
		boolean hilite = false;
		long unixTime = 0;
		String typeRsn = "0";

		PreparedStatement newsps = null;
		PreparedStatement ps = null;

		String tonePoolUserRSNs = getTonePoolUsers(userRsn, session);

		//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
		// Prepare a JDBC statement for repeated use
		String where = " n.source = s.source(+) and n.type = st.type and n.rsn = ? and n.rsn = t.item_rsn(+) and t.user_rsn(+) = 0";
		String newssql = "select n.rsn, n.item_date, n.source, n.type, n.title, " +
		"n.string1, n.string2, n.string3, n.string4, n.string5, n.string6, n.string7, " +
		"n.text, n.contenttype, n.binary, n.binaryloaded, n.webpath, s.abbr, n.archived, " +
		"n.summary, n.archived_to, n.date1, n.number1, n.number2, string8, to_char(n.item_time,'hh24:mi'), "+
		"n.importedfrom, n.filename, n.externalbinary, "+
		"(select avg (tn.tone) from users_tones tn where tn.item_rsn = n.rsn and tn.user_rsn in (" + tonePoolUserRSNs + ")), "+
		"n.transcript, s.common_call, 'current' as item_table, st.rsn "+
		" from tno.news_items n, tno.sources s, tno.source_types st, tno.users_tones t where " + where;
		
		newssql = newssql + " union all ";
		newssql = newssql + "select n.rsn, n.item_date, n.source, n.type, n.title, " +
		"n.string1, n.string2, n.string3, n.string4, n.string5, n.string6, n.string7, " +
		"n.text, n.contenttype, n.binary, n.binaryloaded, n.webpath, s.abbr, n.archived, " +
		"n.summary, n.archived_to, n.date1, n.number1, n.number2, string8, to_char(n.item_time,'hh24:mi'), "+
		"n.importedfrom, n.filename, n.externalbinary, "+
		"(select avg (tn.tone) from users_tones tn where tn.item_rsn = n.rsn and tn.user_rsn in (" + tonePoolUserRSNs + ")), "+
		"n.transcript, s.common_call, 'history' as item_table, st.rsn "+
		" from tno.hnews_items n, tno.sources s, tno.source_types st, tno.users_tones t where " + where;

		try {
			ResultSet rs2 = DbUtil.runSql("select r.pref3,r.pref8,r.pref11,r.pref_inc_images,r.pref_use_thumb,r.pref_inc_frontpage,r.show_tone,r.pref_cloak_byline,r.pref_add_frontpage,r.hilite from reports r where r.rsn = " + reportRsn, session);
			if (rs2.next()) {
				pref3 = rs2.getBoolean(1);
				pref8 = rs2.getBoolean(2);
				pref11 = rs2.getBoolean(3);
				pref_inc_images = rs2.getBoolean(4);
				pref_use_thumb = rs2.getBoolean(5);
				pref_inc_frontpage = rs2.getBoolean(6);
				show_tone = rs2.getBoolean(7);
				pref_cloak_byline = rs2.getBoolean(8);
				pref_add_frontpage = rs2.getBoolean(9);
				hilite = rs2.getBoolean(10);
			}
			rs2.close();
		} catch (Exception err) { 
			decoratedError(INDENT0, "Reports add item to report error (part 1)", err); 
		}

		try {
			newsps = oracleConn.c.prepareStatement(newssql);
		} catch (Exception err) { 
			decoratedError(INDENT0, "Reports add item to report error (part 2)", err); 
		}

		String w = queryText;
		boolean isQueryWords=false;
		if (w != null) {
			if (hilite) isQueryWords=true;
		}

		String prevType = "~";
		String storyFormat = "";

		String progress = "a";

		try {
			boolean itemFound = true;

			progress = "b";

			newsps.setLong(1, itemRsn);
			newsps.setLong(2, itemRsn);
			rs = newsps.executeQuery();
			if (rs.next()) {
				long rsn = rs.getLong(1);
				java.sql.Date itemDate = rs.getDate(2);
				String source = rs.getString(3);
				if (source == null) source="";
				String commonCall = rs.getString(32);
				if (commonCall == null) commonCall="";
				String type = rs.getString(4);
				if (type == null) type="";
				String title = rs.getString(5);
				String string1 = rs.getString(6);
				if (string1 == null) string1="";
				String string2 = rs.getString(7);
				if (string2 == null) string2="";
				String string3 = rs.getString(8);
				if (string3 == null) string3="";
				String string4 = rs.getString(9);
				if (string4 == null) string4="";
				String string5 = rs.getString(10);
				if (string5 == null) string5="";
				String string6 = rs.getString(11);
				if (string6 == null) string6="";
				String string7 = rs.getString(12);
				if (string7 == null) string7="";
				Clob cl = rs.getClob(13);
				String t="";
				if (cl == null) {
					t = "No story";
				} else {
					int l = (int)cl.length();
					t = cl.getSubString(1,l).replace((char)0,(char)32);
				}
				String abbr = rs.getString(18);
				if (abbr == null) abbr = aktivString.removeSpaces(source);
				String time = rs.getString(26);
				if (time == null) time="";
				if (time.startsWith("00:00")) time="";

				String story_summary = rs.getString(20);
				if (story_summary == null) story_summary="";

				String item_table = rs.getString(33);
				typeRsn = rs.getString(34);
				if(typeRsn == null) typeRsn = "0";

				if (!commonCall.equals("")) {
					commonCall = " (" + commonCall + ")";
				}

				headline = title.trim();
				if (pref_cloak_byline) {
					if (!string6.equals("")) {
						headline = headline + " - "+string6;
					}
				}
				if (pref3) headline = headline + " - " + source;
				if (pref11) headline = headline + commonCall;
				if (pref8) headline = headline + " - " + aktivDate.searchDate(itemDate);
				if (pref_add_frontpage) {
					if (string3.equalsIgnoreCase("a01")) {
						headline = "[FRONT PAGE] "+headline;
					}
				}

				progress = "c";

				String page = "";
				if (!string3.equals(""))
					page = "Page "+string3;

				if (!type.equals(prevType)) storyFormat = getItemTypeFormat(type, session);
				prevType = type;

				String searchDate = aktivDate.searchDate(itemDate);
				String d = aktivDate.fullDate( itemDate.toString() );

				unixTime = aktivTime.unixTime(searchDate, time);

				int tone = 0;
				Object tone_test = rs.getObject(30);
				boolean null_tone = false;
				if (tone_test == null) {
					null_tone = true;
				} else {
					tone = rs.getInt(30);
				}
				Clob transcriptClob = rs.getClob(31);
				String transcript="";
				if (transcriptClob == null) {
					transcript = "";
				} else {
					int l = (int)transcriptClob.length();
					transcript = transcriptClob.getSubString(1,l).replace((char)0,(char)32);
				}

				progress = "d";

				// Bold the query words
				if ((isQueryWords) & (w != null) & hilite) {
					// fudge an id
					long range = 2147483647;
					Random r = new Random();
					long q_id = (long)(r.nextDouble()*range);

					StringBuilder text = new StringBuilder(t);
					StringBuilder trans = new StringBuilder(transcript);
					aktiv.Common.markup_content(text, trans, w, itemRsn, item_table, q_id, oracleConn, false);
					t = text.toString();
					transcript = trans.toString();
				}

				if (transcript.length() > 10) {
					story_summary = transcript;
				}

				progress = "e";

				String line = StringUtil.replace(storyFormat, "<**item_date**>", d );
				line = StringUtil.replace(line, "<**itemrsn**>", Long.toString(itemRsn) );
				line = StringUtil.replace(line, "<**source**>", source ) ;
				line = StringUtil.replace(line, "<**sourceabbr**>", abbr ) ;
				line = StringUtil.replace(line, "<**title**>", title ) ;
				line = StringUtil.replace(line, "<**type**>", type ) ;
				line = StringUtil.replace(line, "<**string1**>", string1 ) ;
				line = StringUtil.replace(line, "<**string2**>", string2 ) ;
				line = StringUtil.replace(line, "<**string3**>", string3 ) ;
				line = StringUtil.replace(line, "<**string4**>", string4 ) ;
				line = StringUtil.replace(line, "<**string5**>", string5 ) ;
				line = StringUtil.replace(line, "<**string6**>", string6 ) ;
				line = StringUtil.replace(line, "<**string7**>", string7 ) ;
				if (string6.length() > 0)
					line = StringUtil.replace(line, "<**byline**>", "By "+string6 ) ;
				else
					line = StringUtil.replace(line, "<**byline**>", "" ) ;
				line = StringUtil.replace(line, "<**item_time**>", time ) ;
				line = StringUtil.replace(line, "<**summary**>", StringUtil.replace(story_summary,"|","<p>") ) ;
				line = StringUtil.replace(line, "<**content**>", StringUtil.replace(t,"|","<p>") );
				line = StringUtil.replace(line, "<**transcript**>", "" );
				String url = instance.getAppHttpHost() + "command=showstory&rsn=" + itemRsn;
				String href = "<a href="+url+">Link to Story</a>";
				line = StringUtil.replace(line, "<**href**>", href ) ;
				line = StringUtil.replace(line, "<**page**>", page ) ;
				line = StringUtil.replace(line, "<**tone**>", "" ) ;
				line = StringUtil.replace(line, "<**typetag**>", "" ) ;
				line = StringUtil.replace(line, "<**visualtone**>", "<!--visualtone-->"); // cloak it in later
				if(pref_inc_images)
					line = StringUtil.replace(line, "<**images**>", news_item_images(oracleConn.c,itemRsn,source,searchDate,pref_use_thumb,pref_inc_frontpage) ) ;
				else
					line = StringUtil.replace(line, "<**images**>", "") ;

				progress = "f";

				content = line;

			} else {
				itemFound = false;
			}
			rs.close();

			progress = "g";

			if (itemFound) {
				long r = aktiv.Common.nextrsn(oracleConn);

				if (r > 0) {
					String sqlString = "insert into tno.report_stories values (?,?,?,?,?,?,?,'a',?,?,?)";
					ps = oracleConn.c.prepareStatement(sqlString);
					ps.setLong(1, r );
					ps.setLong(2, reportRsn );
					ps.setString(3, headline );
					ps.setLong(4, sectionRsn );
					ps.setLong(5, Long.parseLong(sort) );
					ps.setLong(6, Long.parseLong(tocsort) );
					ps.setString(7, summary );
					ps.setLong(8, itemRsn );
					ps.setLong(9, unixTime );
					ps.setString(10, typeRsn );
					ps.executeUpdate();
					ps.close();

					progress = "h";

					sqlString = "select content from tno.report_stories where rsn = ? for update";
					ps = oracleConn.c.prepareStatement(sqlString);
					ps.setLong(1,r);
					rs = ps.executeQuery();
					if (rs.next()) {
						CLOB cl = (CLOB)rs.getClob(1);
						Writer cos = cl.getCharacterOutputStream();
						cos.write(content);
						cos.close();
					}
					rs.close();
					ps.close();
					if (storyrsn.length() < 1) storyrsn = Long.toString(r);

					progress = "i";
				}
			}
		} catch (Exception err) { 
			decoratedError(INDENT0, "Reports add item to report error: " + progress, err); 
		}

		try { if (newsps != null) newsps.close(); } catch (SQLException err) {;}
		try { if (rs != null) rs.close(); } catch (SQLException err) {;}
		try { if (ps != null) ps.close(); } catch (SQLException err) {;}
		try { if (stmt != null) stmt.close(); } catch (SQLException err) {;}
	}
	
	public String getTonePoolUsers(long userRsn, Session session)
	{
		Statement stmt = null;
		ResultSet rs = null;
		Statement stmt2 = null;
		ResultSet rs2 = null;
		String tonePoolUsers="0";	// just TNO editors

		String sql = "select tone_pool_rsn from tno.users where rsn = " + userRsn;
		try {
			rs = DbUtil.runSql(sql, session);
			if (rs.next())
			{
				sql = "select users from tone_pool where rsn = " + rs.getLong(1);
				try{
					rs2 = DbUtil.runSql(sql, session);
					if(rs2.next()){
						Clob cl=rs2.getClob(1);
						long l=cl.length();
						if(l > 0){
							tonePoolUsers = cl.getSubString(1,(int)l);
						}
					}
				} catch(Exception err){ ;}
			}
		} catch (Exception err) { ; }
		try{ if(rs!=null) rs.close(); } catch(SQLException err){ ; }
		try{ if(rs2!=null) rs2.close(); } catch(SQLException err){ ; }

		return tonePoolUsers;
	}
	
	private Document parseXML(String xml){
		Document doc=null;
		try{
			DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
			DocumentBuilder db=dbf.newDocumentBuilder();
			doc=db.parse(new InputSource(new StringReader(xml)));
		} catch(Exception err){
			;}
		return doc;
	}
	
	private String getValueByTagName(Document doc,String tag){
		String value="";
		if(doc==null) return value;
		try{
			NodeList tagNodeList=doc.getElementsByTagName(tag);
			if(tagNodeList!=null){
				if(tagNodeList.getLength()>0){
					Element tagElement=(Element)tagNodeList.item(0);
					if(tagElement!=null){
						NodeList textTagList=tagElement.getChildNodes();
						if(textTagList!=null){
							if(textTagList.getLength()>0)
								value=((Node)textTagList.item(0)).getNodeValue();
						}
					}
				}
			}
		} catch(Exception err){
			value="";}
		return value;
	}
	
	/*
	 * Update the analysis so that it does not get picked up again during this cycle.
	 */
	private void updateLastRunDate(Long rsn, Session session)
	{
		String slUpdate = "update analysis set last_run = sysdate + INTERVAL '1' minute where rsn = " + Long.toString(rsn);
		try {
			DbUtil.runUpdateSql(slUpdate, session);
		} catch (Exception e2) {
			decoratedError(INDENT2, "Analysis update error!", e2);
		}
		//try {oracleConn.c.commit();} catch (Exception e) {;}
	}
	
	public String getItemTypeFormat(String type, Session session) {
		String formatPart = "V35ADD2FORMAT";
		if (type.equalsIgnoreCase("tv news")) formatPart = "V35ADD2FORMATAV";
		if (type.equalsIgnoreCase("talk radio")) formatPart = "V35ADD2FORMATAV";
		if (type.equalsIgnoreCase("radio news")) formatPart = "V35ADD2FORMATAV";
		String storyFormat = getPart(formatPart, session);
		return storyFormat;
	}
	
	private String getPart(String partName, Session session)
	{
		String part = "";
		ResultSet prs = part_object.select(partName);
		if(part_object.next(prs)) part = part_object.getContent();
		try { prs.close(); } catch (Exception ex) {;}
		return part;
	}
}