package ca.bc.gov.tno.jorel2.controller;


import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.AlertTriggerDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.JorelDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.util.DbUtil;

import static ca.bc.gov.tno.jorel2.model.PublishedPartsDao.getPublishedPartByName;

/**
 * Manages the retrieval and processing of various RSS feeds using JAXB objects in the
 * ca.bc.gov.tno.jorel2.jaxb package and its sub-packages.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class AlertEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	/**
	 * Process all eligible RSS event records from the TNO_EVENTS table.  The goal is that separate threads can process different RSS events. An earlier 
	 * version of this code added the synchronized modifier
	 * 
	 * @param eventType The type of event we're processing (e.g. "RSS", "Monitor")
	 * @param session The current Hibernate persistence context
	 * @return Optional object containing the results of the action taken.
	 */
	
	public Optional<String> processEvents(String eventType, Session session) {
    	
    	try {
    		decoratedTrace(INDENT1, "Starting Alert event processing");
    		
	        List<Object[]> results = EventsDao.getElligibleEventsByEventType(instance, eventType, session);
			for (Object[] entityPair : results) {
	        	if (entityPair[0] instanceof EventsDao) {
	        		EventsDao currentEvent = (EventsDao) entityPair[0];
	        
	    			alertEvent(currentEvent, session);
	        		//DbUtil.updateLastFtpRun(DateUtil.getDateNow(), currentEvent, session);	        		
	        	}
			}
			
    		decoratedTrace(INDENT1, "Completed Alert event processing");
    	} 
    	catch (Exception e) {
    		logger.error("Processing user directory entries.", e);
    	}
    	
    	return Optional.of("complete");
	}
	
	private void alertEvent(EventsDao currentEvent, Session session) {
		
		JorelDao.updateLastAlertRunTime(LocalDateTime.now().toString(), session);
		List<PreferencesDao> prefs = PreferencesDao.getPreferencesByRsn(PREFERENCES_RSN, session);
		int triggerCount = AlertTriggerDao.getTriggerCount(session);
		
		// If there is no preferences object, set showEmoji to true, otherwise get the value from the preferences object
		Boolean showEmoji = prefs.size() == 0 ? Boolean.valueOf(true) : prefs.get(0).getShowEmoji();
		
		
		if(triggerCount > 0) {
			List<BigDecimal> rsnList = NewsItemsDao.getAlertItemRsns(session);
			
			if(rsnList.size() > 0) {
				String partStr = getPublishedPartByName("V35ALERTSEMAIL", "<html>The Following (<**num**>) story(s) were added to TNO: <br><**story_links**></html>", session);
				String partLineStr = getPublishedPartByName("V35ALERTSEMAILLINE", "<A HREF = \"<**httphost**>command=showstory&rsn=<**rsn**>\"><**title**></A><**images**><br>", session);
				String requestTranscriptLink = getPublishedPartByName("REQUEST_TRANSCRIPT_ALERT_LINK", "", session);
				String partSingleStr = getPublishedPartByName("V35ALERTSEMAILSINGLE", "<A HREF = \"<**httphost**>command=showstory&rsn=<**rsn**>\"><**title**></A><br>", session);
				String partSingleSubjStr = getPublishedPartByName("V7ALERTSEMAILSINGLESUBJECT", "<**source**>: <**stitle**><**tone**>", session);
				String transcriptEmoji = getPublishedPartByName("V10ALERTSTRANSCRIPTEMOJI", ":page_facing_up:", session);
				String tonePos = getPublishedPartByName("V61TONEPOSITIVE", " tone5x", session);
				String toneNeu = getPublishedPartByName("V61TONENEUTRAL", " tone0x", session);
				String toneNeg = getPublishedPartByName("V61TONENEGATIVE", " tone-5x", session);
				String tonePosEmoji = getPublishedPartByName("V13TONEPOSEMOJI", ":thumbsup:", session);
				String toneNeuEmoji = getPublishedPartByName("V13TONENEUEMOJI", ":point_right::point_left:", session);
				String toneNegEmoji = getPublishedPartByName("V13TONENEGEMOJI", ":thumbsdown:", session);
				String typeTag = getPublishedPartByName("V61TYPETAG", " typex<**type**>", session);
				String tos = getPublishedPartByName("V61TOS", TOS_MSG, session);
				String tosScrum = getPublishedPartByName("V61TOS_SCRUM", "This e-mail is a service provided by the Public Affairs Bureau and is only intended for the original addressee.", session);
				String visualTone = getPublishedPartByName("V81TONE", "", session);
				String visualToneSmall = getPublishedPartByName("V81TONESMALL", "", session);
				
				if(!showEmoji) {
					tonePosEmoji = "";
					toneNeuEmoji = "";
					toneNegEmoji = "";
				}
				
				//The varaibles used to store the news_item fields
				String rsn, item_date, source="", item_time, summary, title="", type, string1, string2, string3, string4;
				String string5, string6, string7, string8, string9, contenttype, series, common_call, webpath;
				String previousTitle = "~";
				String previousSource = "~";
				int tone = 0;
				boolean null_tone = false;

				int messageCounter = 0;
				String sqlWhere = "";
				String alertName = "";
				long alertRsn = 0;
				
				/* 
				 * Because the SQL_WHERE column of ALERTS uses native PL/SQL statements and not Hibernate HQL syntax, we have to bypass the ORM
				 * and retrieve a java.sql.ResultSet object for each query using the Hibernate session's doReturningWork() method. An alternative 
				 * would be to create an SQL to HQL translator, but that is not within the scope of this rewrite. This approach is used throughout 
				 *  this handler for consistency. 
				 */
				
				String activeAlertQuery = "select u.first_name, u.last_name, u.user_name, u.email_address, a.rsn, a.alert_name, a.sql_where, u.cp, u.core, u.scrums, u.social_media,u.rsn,u.view_tone from users u, alerts a " +
				"where u.rsn = a.user_rsn and u.alerts = 1 and u.alerts_status = 1 and a.status = 1 order by u.core desc, a.alert_name asc ";

				ResultSet alertRS = DbUtil.runSql(activeAlertQuery, session);
				
				try {
					while(alertRS.next()) {
						String first = alertRS.getString(1);
						String last = alertRS.getString(2);
					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
						
				int i = 1;
			}
		}
	}
	
	/* private boolean retry_saved_email_alert(Frame1 f, jorelEmail jMail, dbAlert alertObj, OracleConnection conn) {
		boolean smtp_is_ok = true;

		String rsn_list = "0";
		String readObjSQL = "SELECT * FROM saved_email_alerts";
		try
		{
			int c = 0;
			PreparedStatement stmt = conn.getConnection().prepareStatement(readObjSQL);
			ResultSet rs = stmt.executeQuery();
			while(rs.next())
			{
				if(c == 0)
				{
					f.addJLog("Retry Sending Saved Alerts...");					
				}
				c++;

				long rsn = rs.getLong(1);
				String a = rs.getString(2);
				String u = rs.getString(3);
				String r = rs.getString(4);
				String s = rs.getString(5);
				String b = rs.getString(7);
				String al = rs.getString(8);
				long user_rsn = 0;
				String user_email = "";

				String m = "";
				Clob cl = rs.getClob(6);
				if (cl != null) {
					int l = (int) cl.length();
					m = cl.getSubString(1, l).replace( (char) 0, (char) 32);
				}
				
				String user_sql = "SELECT rsn,email_address FROM users where user_name = '"+u+"'";
				PreparedStatement stmt2 = conn.getConnection().prepareStatement(readObjSQL);
				ResultSet rs2 = stmt2.executeQuery();
				if(rs2.next())
				{
					user_rsn = rs2.getLong(1);
					user_email = rs2.getString(2);
				}
				rs2.close();
				stmt2.close();

				EmailMessage em = new EmailMessage(a,u,r,s,m,"","",false);
				em.bcc_recipients = b;
				em.alertRsnList = al;
				if(em != null)
				{
					String msg = em.send(jMail, alertObj, conn);
					//int pos = msg.indexOf("Could not connect to SMTP host");
					int pos = msg.indexOf("javax.mail.SendFailedException: Sending failed");
					if (pos > 0) 
					{
						// DO NOTHING AND GET OUT!  STILL CANNOT CONNECT
						smtp_is_ok = false;
						break;
					}
					else
					{
						rsn_list = rsn_list + "," + rsn;
					}
				}
			}
			stmt.close();
		} catch(Exception err) {f.addJLog("retry_saved_email_alert: "+err.toString());}

		if(rsn_list != "0")
		{
			try
			{
				String sql = "delete from saved_email_alerts where rsn in ("+rsn_list+")";
				Statement s = conn.getConnection().createStatement();
				ResultSet r = s.executeQuery(sql);
				r.close();
				s.close();
			} catch(Exception err) {;}		    	
		}
		return smtp_is_ok;
	} */
}