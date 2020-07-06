package ca.bc.gov.tno.jorel2.controller;


import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;

import javax.inject.Inject;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import com.vdurmont.emoji.EmojiParser;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.AlertTriggerDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.JorelDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.model.PreferencesDao;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

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
				Vector myEmailVector = new Vector(5,10);
				Vector singletonEmailVector = new Vector(5,10);
				String emailMessage = null;
				
				Map<String, String> parts = loadPublishedParts(session);
				
				
				/*if(!showEmoji) {
					tonePosEmoji = "";
					toneNeuEmoji = "";
					toneNegEmoji = "";
				}*/
				
				//The variables used to store the news_item fields
				String rsn, itemDate, source="", itemTime, summary, title="", type, string1, string2, string3, string4;
				String string5, string6, string7, string8, string9, contenttype, series, common_call, webpath;
				String previousTitle = "~";
				String previousSource = "~";
				int tone = 0;
				boolean nullTone = false;

				int messageCounter = 0;
				String sqlWhere = "";
				String alertName = "";
				long alertRsn = 0;
				
				/* 
				 * Because the SQL_WHERE column of ALERTS uses native PL/SQL statements and not Hibernate HQL syntax, we have to bypass the ORM
				 * and retrieve a java.sql.ResultSet object for each query using the Hibernate session's doReturningWork() method. An alternative 
				 * would be to create an SQL to HQL translator, but that is not within the scope of this rewrite. This approach is used throughout 
				 * this handler for consistency. 
				 */
				
				String activeAlertQuery = "select u.first_name, u.last_name, u.user_name, u.email_address, a.rsn, a.alert_name, a.sql_where, u.cp, u.core, u.scrums, u.social_media,u.rsn,u.view_tone from users u, alerts a " +
				"where u.rsn = a.user_rsn and u.alerts = 1 and u.alerts_status = 1 and a.status = 1 order by u.core desc, a.alert_name asc ";

				ResultSet alertRS = DbUtil.runSql(activeAlertQuery, session);
				
				try {
					String previousSqlWhere = ".?.";

					while(alertRS.next()) {
						String userName = alertRS.getString(3);
						String userEmail = alertRS.getString(4);
						alertRsn = alertRS.getLong(5);
						alertName = alertRS.getString(6);
						if (alertName == null) alertName = "?";
						sqlWhere = alertRS.getString(7);
						if (sqlWhere == null) sqlWhere = "";
						boolean cp = alertRS.getBoolean(8);
						boolean core = alertRS.getBoolean(9);
						boolean scrums = alertRS.getBoolean(10);
						boolean socialMedia = alertRS.getBoolean(11);
						long userRsn = alertRS.getLong(12);
						boolean view_tone = alertRS.getBoolean(13);
						
						sqlWhere = appendSqlWhereForSource(cp, scrums, socialMedia, sqlWhere);
						
						if (sqlWhere.length() != 0) sqlWhere = sqlWhere + " and ";
						sqlWhere = sqlWhere + "n.rsn = t.item_rsn(+) and t.user_rsn(+) = 0 and n.source = s.source(+)";

						emailMessage = "News Records found for alert: " + alertRS.getString(6) + " [ ";

						// is it the very same sql where clause as the previous alert
						// if so, use the same result as the previous alert....
						if (sqlWhere.equals(previousSqlWhere)) {
							emailMessage = emailMessage + " ditto ";

							// Deal with any 'single' emails for CP News and Transcripts
							for (int kk=0; kk < singletonEmailVector.size(); kk++) {
								EmailMessage em = (EmailMessage) singletonEmailVector.elementAt(kk);
								if (em != null) {
									String alertRSN = alertRS.getString(5);
									String u = alertRS.getString(3);                     	// user name
									String r = userEmail;              			       	// recipients
									if (r==null) r = "";

									myEmailVector.addElement( new EmailMessage(alertRSN,u,r,em.getSubject(),em.getMessage(),em.getRSNList(),em.getToneEmoji(),view_tone) );
								}
							}
						}
					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
						
				int i = 1;
			}
		}
	}
	
	/**
	 * Appends a conditional statement to SqlWhere that filters out records from 'CP News', 'Scrum' and 'Social Media' if their
	 * corresponding boolean variables are set to false.
	 * 
	 * @param cp Should this alert report on CP News items
	 * @param scrums Should this alert report on Scrums
	 * @param socialMedia Should this alert report on Social Media
	 * @param sqlWhere SQL statement to append to
	 * @return SQL statement with filter conditions appended
	 */
	private String appendSqlWhereForSource(boolean cp, boolean scrums, boolean socialMedia, String sqlWhere) {
				
		if (!cp) {
			if (sqlWhere.length() != 0) sqlWhere = sqlWhere + " and ";
			sqlWhere = sqlWhere + " n.type <> 'CP News'";
		}
		if (!scrums) {
			if (sqlWhere.length() != 0) sqlWhere = sqlWhere + " and ";
			sqlWhere = sqlWhere + " n.type <> 'Scrum'";
		}
		if (!(socialMedia)) {
			if (sqlWhere.length() != 0) sqlWhere = sqlWhere + " and ";
			sqlWhere = sqlWhere + " n.type <> 'Social Media'";
		}
		
		return sqlWhere;
	}
	
	private Map<String, String> loadPublishedParts(Session session) {
		
		Map<String, String> parts = new HashMap<>();
		
		getPublishedPartByName("V35ALERTSEMAIL", V35ALERTSEMAIL_DFLT, "partStr", parts, session);
		getPublishedPartByName("V35ALERTSEMAILLINE", V35ALERTSEMAILLINE_DFLT, "partLineStr", parts, session);
		getPublishedPartByName("REQUEST_TRANSCRIPT_ALERT_LINK", REQUEST_TRANSCRIPT_DFLT, "requestTranscriptLink", parts, session);
		getPublishedPartByName("V35ALERTSEMAILSINGLE", V35ALERTSEMAILSINGLE_DFLT, "partSingleStr", parts, session);
		getPublishedPartByName("V7ALERTSEMAILSINGLESUBJECT", "<**source**>: <**stitle**><**tone**>", "partSingleSubjStr", parts, session);
		getPublishedPartByName("V10ALERTSTRANSCRIPTEMOJI", ":page_facing_up:", "transcriptEmoji", parts, session);
		getPublishedPartByName("V61TONEPOSITIVE", " tone5x",  "tonePos", parts, session);
		getPublishedPartByName("V61TONENEUTRAL", " tone0x", "toneNeu", parts, session);
		getPublishedPartByName("V61TONENEGATIVE", " tone-5x", "toneNeg", parts, session);
		getPublishedPartByName("V13TONEPOSEMOJI", ":thumbsup:", "tonePosEmoji", parts, session);
		getPublishedPartByName("V13TONENEUEMOJI", ":point_right::point_left:", "toneNeuEmoji", parts, session);
		getPublishedPartByName("V13TONENEGEMOJI", ":thumbsdown:", "toneNegEmoji", parts, session);
		getPublishedPartByName("V61TYPETAG", " typex<**type**>", "typeTag", parts, session);
		getPublishedPartByName("V61TOS", TOS_MSG_DFLT, "tos", parts, session);
		getPublishedPartByName("V61TOS_SCRUM", V61TOS_SCRUM_DFLT, "tosScrum", parts, session);
		getPublishedPartByName("V81TONE", "", "visualTone", parts, session);
		getPublishedPartByName("V81TONESMALL", "", "visualToneSmall", parts, session);
		
		return parts;
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
	
	private class EmailMessage {
		String alertRSN;
		String username;
		String recipients;
		String bccRecipients;
		String subject;
		String message;
		String itemRsnList; // string list of news item RSNs - used to compare alerts to see if they are equal
		String alertRsnList; // string list of alert RSNs - used to update the last run date in the alerts
		String tone_emoji;
		boolean view_tone;
		
		private EmailMessage(String rsn, String u, String r, String s, String m, String rl, String te, boolean vt) {
			alertRSN=rsn;
			username=u;
			recipients=r;
			subject = StringUtil.removeCRLF(s);
			message=m;
			itemRsnList=rl;
			bccRecipients="";
			alertRsnList="";
			tone_emoji=te;
			view_tone=vt;
			addBCC(r);
			addAlertRSN(rsn);
		}
		
		private String getAlertRSN() { return alertRSN; }
		private String getUsername() { return username; }
		private String getRecipients() { return recipients; }
		private String getSubject() { return subject; }
		private String getToneEmoji() { return tone_emoji; }
		private boolean getViewTone() { return view_tone; }
		private String getMessage() { return message; }
		private String getBCCRecipients() { return bccRecipients; }
		private String getRSNList() { return itemRsnList; }
		
		private String getAlertRSNList() { return alertRsnList; }
	
		private void addBCC(String r) {
			if (bccRecipients.length() == 0) {
				bccRecipients = r;
			} else {
				bccRecipients = bccRecipients+"~"+r;
			}
		}
		
		private void addAlertRSN(String rsn) {
			if (alertRsnList.length()==0) {
				alertRsnList = rsn;
			} else {
				alertRsnList = alertRsnList+","+rsn;
			}
		}
	}
}