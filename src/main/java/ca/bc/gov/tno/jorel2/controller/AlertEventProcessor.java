package ca.bc.gov.tno.jorel2.controller;


import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
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
import ca.bc.gov.tno.jorel2.util.Base64Util;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.EmailUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

import static ca.bc.gov.tno.jorel2.model.PublishedPartsDao.getPublishedPartByName;

/**
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class AlertEventProcessor extends Jorel2Root implements EventProcessor {

	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	Jorel2Instance instance;
	
	static final String excludeTranscriptRequest = "chmb, chkg, cjvb, chnm, cftv, ckye, krpi, cjrj";
	
	/**
	 * Process all eligible Alert event records from the TNO_EVENTS table.
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
	
	/**
	 * Because the SQL_WHERE column of ALERTS uses native PL/SQL statements and not Hibernate HQL syntax, we have to bypass the ORM
	 * and retrieve a java.sql.ResultSet object for each query using the Hibernate session's doReturningWork() method. An alternative 
	 * would be to create an SQL to HQL translator, but that is not within the scope of this rewrite. This approach is used throughout 
	 * this handler for consistency. 
	 */
	
	private void alertEvent(EventsDao currentEvent, Session session) {
		
		JorelDao.updateLastAlertRunTime(LocalDateTime.now().toString(), session);
		List<PreferencesDao> prefs = PreferencesDao.getPreferencesByRsn(PREFERENCES_RSN, session);
		int triggerCount = AlertTriggerDao.getTriggerCount(session);
		
		// If there is no preferences object, set showEmoji to true, otherwise get the value from the preferences object
		Boolean showEmoji = prefs.size() == 0 ? Boolean.valueOf(true) : prefs.get(0).getShowEmoji();
		
		
		if(triggerCount > 0) {
			String rsnList = NewsItemsDao.getAlertItemRsns(session);
			
			if(rsnList.length() > 0) {
				boolean stopAlertProcessing = false;
				Vector myEmailVector = new Vector(5,10);
				Vector singletonEmailVector = new Vector(5,10);
				String emailMessage = null;
				
				Map<String, String> parts = loadPublishedParts(showEmoji, session);
				
				//The variables used to store the news_item fields
				String rsn, itemDate, source="", itemTime, summary, title="", type, string1, string2, string3, string4;
				String string5, string6, string7, string8, string9, contenttype, series, commonCall, webpath;
				String previousTitle = "~";
				String previousSource = "~";
				int tone = 0;
				boolean nullTone = false;

				int messageCounter = 0;
				String sqlWhere = "";
				String alertName = "";
				long alertRsn = 0;
				String emailLine = null;
				String emailLines = null;
				String rsnInThisAlert = null;
				String emailAddress = null;
								
				try {
					String previousSqlWhere = ".?.";

					String activeAlertQuery = "select u.first_name, u.last_name, u.user_name, u.email_address, a.rsn, a.alert_name, a.sql_where, u.cp, u.core, u.scrums, u.social_media,u.rsn,u.view_tone from users u, alerts a " +
							"where u.rsn = a.user_rsn and u.alerts = 1 and u.alerts_status = 1 and a.status = 1 order by u.core desc, a.alert_name asc ";

					ResultSet alertRS = DbUtil.runSql(activeAlertQuery, session);
					
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
						boolean sqlAlertError = false;
						String exceptionString = "";
						String httpHost = instance.getAppHttpHost();
						
						sqlWhere = filterBySource(cp, scrums, socialMedia, sqlWhere);
						
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
									String u = alertRS.getString(3);        // user name
									String r = userEmail;              		// recipients
									if (r==null) r = "";

									myEmailVector.addElement( new EmailMessage(alertRSN,u,r,em.getSubject(),em.getMessage(),em.getRSNList(),em.getToneEmoji(),view_tone) );
								}
							}
						} else {
							previousSqlWhere = sqlWhere;

							messageCounter = 0;
							emailLines = "";
							rsnInThisAlert = "";
							singletonEmailVector.clear();
							String errorLineStr = "ERROR: There is an error in your alert named: <**alert_name**><br><**sql_where**><br><**exception**><br>";
							String rsnAlertedList = "";
							
							String itemQuery = "select n.rsn,n.source,n.title,n.string5,n.type,n.text,n.string6,n.item_date,to_char(n.item_time,'hh24:mi:ss'),n.transcript,t.tone,s.common_call,n.webpath " +
							"from news_items n, users_tones t, sources s where n.rsn in (" + rsnList + ") " + sqlWhere + " order by n.rsn";

							ResultSet newsRecords = null;
							try {
								newsRecords = DbUtil.runSql(itemQuery, session);
							} catch (SQLException e) {
								exceptionString = e.toString().toLowerCase();
								decoratedError(INDENT2, "Reading list of matching news items for alert.", e);
								sqlAlertError = true;
							}
							
							if (sqlAlertError) {
								int pse = exceptionString.indexOf("parser syntax error");
								int pos = exceptionString.indexOf("oracle text");
								if ((pos > 0) & (pse <= 0)) stopAlertProcessing=true;

								previousSqlWhere = ".?.";
								emailLine = errorLineStr;
								emailLine = StringUtil.exchange(emailLine,"<**alert_name**>", alertName);
								emailLine = StringUtil.exchange(emailLine,"<**sql_where**>", sqlWhere);
								emailLine = StringUtil.exchange(emailLine,"<**exception**>", exceptionString);

								emailLines = emailLines + emailLine;
								emailLine = "";

							} else {
								while (newsRecords.next()){
									rsn = newsRecords.getString(1);

									if (rsnAlertedList.length() == 0) {
										rsnAlertedList = rsnAlertedList + rsn;
									} else {
										if (rsnAlertedList.indexOf(rsn) < 0) rsnAlertedList = rsnAlertedList + "," + rsn;
									}

									emailMessage = emailMessage + rsn + " ";

									tone = 0;
									nullTone = false;

									//populate the variables they may use
									if(newsRecords.getString(2) != null) {source       = newsRecords.getString(2);} else {source = "";}
									if(newsRecords.getString(3) != null) {title        = newsRecords.getString(3);} else {title = "";}
									if(newsRecords.getString(4) != null) {string5      = newsRecords.getString(4);} else {string5 = "";}
									if(newsRecords.getString(5) != null) {type         = newsRecords.getString(5);} else {type = "";}
									if(newsRecords.getString(7) != null) {string6      = newsRecords.getString(7);} else {string6 = "";}
									if(newsRecords.getString(8) != null) {itemDate     = newsRecords.getDate(8).toString();} else {itemDate = "";}
									if(newsRecords.getString(9) != null) {itemTime     = newsRecords.getString(9);} else {itemTime = "";}
									if(newsRecords.getObject(11) != null) {tone        = newsRecords.getInt(11);} else {nullTone = true;}
									if(newsRecords.getString(12) != null) {commonCall = newsRecords.getString(12);} else {commonCall = "";}
									if(newsRecords.getString(13) != null) {webpath     = newsRecords.getString(13);} else {webpath = "";}

									if (itemTime.startsWith("00:00")) itemTime="";
									if (itemTime.length() > 6) itemTime = itemTime.substring(0,5);

									String sourceEncoded = source;
									String typeEncoded = type;
									String string6Encoded = string6;
									try {
										sourceEncoded = java.net.URLEncoder.encode(sourceEncoded, "ISO-8859-1");
										typeEncoded = java.net.URLEncoder.encode(typeEncoded, "ISO-8859-1");
										string6Encoded = java.net.URLEncoder.encode(string6Encoded, "ISO-8859-1");
									} catch (Exception ex) { ; }

									if (!commonCall.equals("")) {
										commonCall = "("+commonCall+")";
									}
									
									if ((type.equals("Transcript")) | (type.equals("Scrum")) | (type.equals("CP News")) | (type.equals("Internet")) | (type.equals("TV News")) | 
											(type.equals("Radio News")) | (type.equals("Talk Radio")) | (type.equals("Social Media"))) {
										
										String quotedNames = ""; //quotes.getNames(rsn);
										
										String text = "";
										Clob cl = newsRecords.getClob(6);
										if (cl != null) {
											int l = (int) cl.length();
											text = cl.getSubString(1, l).replace( (char) 0,
													(char) 32);
											text = StringUtil.exchange(text, "|", "<p>");
										}

										String transcript = "";
										Clob cl2 = newsRecords.getClob(10);
										if (cl2 != null) {
											int l = (int) cl2.length();
											transcript = cl2.getSubString(1, l).replace( (char) 0,
													(char) 32);
											transcript = StringUtil.exchange(transcript, "|", "<p>");
										}

										if (string5.length() <= 0) string5 = string6;

										// cloak email - populate all the possible values they may want displayed in each email line
										emailLine = parts.get("partSingleStr");
										emailLine = StringUtil.exchange(emailLine, "<**httphost**>", httpHost); //Always Present
										emailLine = StringUtil.exchange(emailLine, "<**rsn**>", rsn); //Always Present
										emailLine = StringUtil.exchange(emailLine, "<**source**>", source);
										if (title.indexOf(";") > 0) {
											emailLine = StringUtil.exchange(emailLine, "<**title**>", StringUtil.firstreplacement(title, ";", "<br>"));
										}
										else {
											emailLine = StringUtil.exchange(emailLine, "<**title**>", title);
										}
										emailLine = StringUtil.exchange(emailLine, "<**string5**>", string5);
										emailLine = StringUtil.exchange(emailLine, "<**item_date**>", itemDate);
										emailLine = StringUtil.exchange(emailLine, "<**item_time**>", itemTime);
										if (string5.length() > 0) {
											if (string5.equals(source)) {
												emailLine = StringUtil.exchange(emailLine, "<**series**>", ""); // same as source - surpress
											} else {
												emailLine = StringUtil.exchange(emailLine, "<**series**>", string5);
											}
										}
										else {
											emailLine = StringUtil.exchange(emailLine, "<**series**>", ""); // no series
										}
										emailLine = StringUtil.exchange(emailLine, "<**quoted**>", quotedNames);

										/*
										 * Transcript stuff
										 */
										String rtUrl = "";
										String transcript_icon = "";
										emailLine = StringUtil.exchange(emailLine, "<**content**>", text);
										emailLine = StringUtil.exchange(emailLine, "<**transcript**>", transcript);
										if (transcript.trim().equals("")) {
											emailLine = StringUtil.exchange(emailLine, "<**transcript/content**>", text);
											
											// no transcript, put request transcript link onto alert message
											if ( (type.equals("TV News")) | (type.equals("Radio News")) | (type.equals("Talk Radio")) )
											{
												rtUrl = StringUtil.exchange(parts.get("requestTranscriptLink"), "<**httphost**>", httpHost);

												String key = rsn+","+Long.toString(userRsn)+","+instance.getMailHostAddress() + "," + userEmail + "," + userName;
												key = Base64Util.encode(key);

												rtUrl = StringUtil.exchange(rtUrl, "<**key**>", key);
												
												String exclTheseSources = excludeTranscriptRequest;
												if(exclTheseSources.indexOf(source.toLowerCase()) > -1) rtUrl = "";
										
											}
										
										} else {
											emailLine = StringUtil.exchange(emailLine, "<**transcript/content**>", transcript);
											transcript_icon = parts.get("transcript_emoji");
										}
										emailLine = StringUtil.exchange(emailLine, "<**request_transcript**>", rtUrl);

										// tone		
										String subjecttone = ""; // tone as it appears in the subject line
										String toneIcon = "";
										if (!nullTone) {
											String vt = StringUtil.exchange(parts.get("visualTone"), "<**number**>", ""+Math.round(tone));
											String vts = parts.get("visualToneSmall");
											if (tone<=-1) {
												emailLine = StringUtil.exchange(emailLine, "<**tone**>", parts.get("toneNeg"));
												vt = StringUtil.exchange(vt, "<**color**>", "red");
												vts = StringUtil.exchange(vts, "<**color**>", "red");
												subjecttone = "      "+Math.round(tone);
												toneIcon = parts.get("toneNegEmoji");
											} else if (tone>=1) {
												emailLine = StringUtil.exchange(emailLine, "<**tone**>", parts.get("tonePos"));
												vt = StringUtil.exchange(vt, "<**color**>", "green");
												vts = StringUtil.exchange(vts, "<**color**>", "green");
												subjecttone = "       +"+Math.round(tone);
												toneIcon = parts.get("tonePosEmoji");
											} else {
												emailLine = StringUtil.exchange(emailLine, "<**tone**>", parts.get("toneNeu"));
												vt = StringUtil.exchange(vt, "<**color**>", "gray");
												vts = StringUtil.exchange(vts, "<**color**>", "gray");
												subjecttone = "       0";
												toneIcon = parts.get("toneNeuEmoji");
											}
											emailLine = StringUtil.exchange(emailLine, "<**visualtone**>", vt);
											emailLine = StringUtil.exchange(emailLine, "<**visualtonesmall**>", vts);
										} else {
											emailLine = StringUtil.exchange(emailLine, "<**tone**>", "");
											emailLine = StringUtil.exchange(emailLine, "<**visualtone**>", "");
											emailLine = StringUtil.exchange(emailLine, "<**visualtonesmall**>", "");
										}

										// transcript flag
										if (type.equalsIgnoreCase("transcript")) {
											emailLine = StringUtil.exchange(emailLine, "<**typetag**>", StringUtil.exchange(parts.get("typeTag"), "<**type**>", type.toLowerCase().replace(' ', '_')));
											transcript_icon = parts.get("transcriptEmoji");
										} else {
											emailLine = StringUtil.exchange(emailLine, "<**typetag**>", "");
										}

										// terms of service
										if (type.equalsIgnoreCase("scrum")) {
											emailLine = StringUtil.exchange(emailLine, "<**terms_of_service**>", parts.get("tosScrum"));
										} else {
											emailLine = StringUtil.exchange(emailLine, "<**terms_of_service**>", parts.get("tos"));
										}

										emailLine = StringUtil.exchange(emailLine, "<**source_encoded**>", sourceEncoded ) ;
										emailLine = StringUtil.exchange(emailLine, "<**type_encoded**>", typeEncoded ) ;
										emailLine = StringUtil.exchange(emailLine, "<**string6_encoded**>", string6Encoded ) ;

										// common call
										emailLine = StringUtil.exchange(emailLine, "<**commoncall**>", commonCall);
										
										emailLine = StringUtil.exchange(emailLine, "<**webpath**>", webpath);

										// save new EmailMessage object
										String alertRSN = alertRS.getString(5);
										String u = alertRS.getString(3); // user name
										String r = alertRS.getString(4); // recipients
										if (r==null) r = "";

										String stitle = title; // title as it appears in the subject line
										if (stitle.length() > 100) stitle = stitle.substring(0, 95) + "...";

										String s = parts.get("partSingleSubjStr");
										s = StringUtil.exchange(s, "<**title**>", title ) ;
										s = StringUtil.exchange(s, "<**stitle**>", stitle ) ;
										s = StringUtil.exchange(s, "<**source**>", source ) ;
										s = StringUtil.exchange(s, "<**tone**>", subjecttone ) ;
										s = StringUtil.exchange(s, "<**transcript_emoji**>", transcript_icon ) ;
										String new_subject = EmojiParser.parseToUnicode(s);

										myEmailVector.addElement(new EmailMessage(alertRSN, u, r, new_subject, emailLine, rsn, toneIcon, view_tone));

										// save email to vector for later
										singletonEmailVector.addElement(new EmailMessage(alertRSN, u, r, new_subject, emailLine, rsn, toneIcon, view_tone));
									} else {

										String text = "";
										if (type.equals("Social Media")) {
											Clob cl = newsRecords.getClob(6);
											if (cl != null) {
												int l = (int) cl.length();
												text = cl.getSubString(1, l).replace( (char) 0,
														(char) 32);
												text = StringUtil.exchange(text, "|", "<p>");
												text = "<br>"+text+"<br>";
											}
										}

										//populate all the possible values they may want displayed in each email line
										emailLine = parts.get("partLineStr");
										emailLine = StringUtil.exchange(emailLine, "<**httphost**>", httpHost); //Always Present
										emailLine = StringUtil.exchange(emailLine, "<**rsn**>", rsn); //Always Present
										emailLine = StringUtil.exchange(emailLine, "<**source**>", source);
										if (title.indexOf(";") > 0) {
											emailLine = StringUtil.exchange(emailLine, "<**title**>", StringUtil.firstreplacement(title, ";", "<br>"));
										}
										else {
											emailLine = StringUtil.exchange(emailLine, "<**title**>", title);
										}
										emailLine = StringUtil.exchange(emailLine, "<**string5**>", string5);
										emailLine = StringUtil.exchange(emailLine, "<**item_date**>", itemDate);
										emailLine = StringUtil.exchange(emailLine, "<**item_time**>", itemTime);
										if (string5.length() > 0) {
											emailLine = StringUtil.exchange(emailLine, "<**series**>", string5);
										}
										else {
											emailLine = StringUtil.exchange(emailLine, "<**series**>", source);
										}

										// social media items only
										emailLine = StringUtil.exchange(emailLine, "<**content**>", text);

										// tone		
										String tone_icon = "";
										if (!nullTone) {
											String vt = StringUtil.exchange(parts.get("visualTone"), "<**number**>", ""+Math.round(tone));
											String vts = parts.get("visualToneSmall");
											if (tone<=-1) {
												emailLine = StringUtil.exchange(emailLine, "<**tone**>", parts.get("toneNeg"));
												vt = StringUtil.exchange(vt, "<**color**>", "red");
												vts = StringUtil.exchange(vts, "<**color**>", "red");
											} else if (tone>=1) {
												emailLine = StringUtil.exchange(emailLine, "<**tone**>", parts.get("tonePos"));
												vt = StringUtil.exchange(vt, "<**color**>", "green");
												vts = StringUtil.exchange(vts, "<**color**>", "green");
											} else {
												emailLine = StringUtil.exchange(emailLine, "<**tone**>", parts.get("toneNeu"));
												vt = StringUtil.exchange(vt, "<**color**>", "gray");
												vts = StringUtil.exchange(vts, "<**color**>", "gray");
											}
											emailLine = StringUtil.exchange(emailLine, "<**visualtone**>", vt);
											emailLine = StringUtil.exchange(emailLine, "<**visualtonesmall**>", vts);
											emailLine = StringUtil.exchange(emailLine, "<**tone_emoji**>", tone_icon);
										} else {
											emailLine = StringUtil.exchange(emailLine, "<**tone**>", "");
											emailLine = StringUtil.exchange(emailLine, "<**visualtone**>", "");
											emailLine = StringUtil.exchange(emailLine, "<**visualtonesmall**>", "");
											emailLine = StringUtil.exchange(emailLine, "<**tone_emoji**>", "");
										}

										// transcript flag
										if (type.equalsIgnoreCase("transcript")) {
											emailLine = StringUtil.exchange(emailLine, "<**typetag**>", StringUtil.exchange(parts.get("typeTag"), "<**type**>", type.toLowerCase().replace(' ', '_')));
										} else {
											emailLine = StringUtil.exchange(emailLine, "<**typetag**>", "");
										}

										emailLine = StringUtil.exchange(emailLine, "<**source_encoded**>", sourceEncoded ) ;
										emailLine = StringUtil.exchange(emailLine, "<**type_encoded**>", typeEncoded ) ;
										emailLine = StringUtil.exchange(emailLine, "<**string6_encoded**>", string6Encoded ) ;

										// common call
										emailLine = StringUtil.exchange(emailLine, "<**commoncall**>", commonCall);

										emailLine = StringUtil.exchange(emailLine, "<**webpath**>", webpath);

										// images
										String imgs = "";
										/*if(isAlertImages)
											imgs = nii.getImages4Alerts(frame.getAVHost(),Long.parseLong(rsn));
										emailLine = StringUtil.exchange(emailLine, "<**images**>", imgs);*/

										emailLine = EmojiParser.parseToUnicode(emailLine);

										emailLines = emailLines + emailLine;
										rsnInThisAlert = rsnInThisAlert+" "+rsn;

										emailLine = "";

										messageCounter++;
										previousTitle = title;
										previousSource = source;
									}
								} // while (newsRecords.next())
	
								try { newsRecords.close(); } catch (Exception e) {;}
							}
							
							if(emailLines != ""){
								//Set the email message
								emailMessage = emailMessage + "] | Saving alert for user: " + alertRS.getString(3);
								String email = StringUtil.exchange(parts.get("partStr"),"<**num**>",String.valueOf(messageCounter));
								email = StringUtil.exchange(email,"<**story_links**>",emailLines);

								String alertRSN = alertRS.getString(5);
								String u = alertRS.getString(3);                     // user name
								String r = alertRS.getString(4);                     // recipients
								if (r==null) r = "";
								String s = "";      // subject

								// set subject
								if (messageCounter>1) {
									s = "TNO: " + messageCounter + " new stories for alert "+alertRS.getString(6);     // subject
								} else {
									String sPreviousTitle = previousTitle;  // title as it appears in the subject line
									if (sPreviousTitle.length()>100) sPreviousTitle = sPreviousTitle.substring(0, 95)+"...";

									s = parts.get("partSingleSubjStr");
									s = StringUtil.exchange(s, "<**title**>", previousTitle ) ;
									s = StringUtil.exchange(s, "<**stitle**>", sPreviousTitle ) ;
									s = StringUtil.exchange(s, "<**source**>", previousSource ) ;
									s = StringUtil.exchange(s, "<**transcript_emoji**>", "" ) ;
									s = StringUtil.exchange(s, "<**tone_emoji**>", "" ) ;
								}

								myEmailVector.addElement( new EmailMessage(alertRSN,u,r,s,email,rsnInThisAlert,"",view_tone) );

								emailMessage = "";
							} // sqlWhere and previousSqlWhere do not match
						}
					}
					
					try { alertRS.close(); } catch (Exception e) {;}
					
					decoratedTrace(INDENT2, "Combining alerts...");

					// Coalesce emails - send identical emails to multiple recipients
					if(!stopAlertProcessing) {
						// sort emails by email body
						Collections.sort(myEmailVector, new Comparator<EmailMessage>() {
							public int compare(EmailMessage one, EmailMessage two) {
								int scompare = one.getSubject().compareTo(two.getSubject());
								if (scompare==0)
									return one.getMessage().compareTo(two.getMessage());
								else
									return scompare;
							}
						});
						String lastSubject = "";
						String lastRSNList = "";
						int ii = 0;
						// loop through emails
						while (ii < myEmailVector.size()) {

							EmailMessage em = (EmailMessage) myEmailVector.elementAt(ii);

							// compare current message to previous message
							if ( (em.getSubject().equals(lastSubject)) && (em.getRSNList().equals(lastRSNList)) ) {
								// subject and message are the same

								// is there room to add another BCC recipient? 4000 chars max
								if (((EmailMessage) myEmailVector.elementAt(ii-1)).getBCCRecipients().length()<3500) {
									// add BCC recipient to previous message
									((EmailMessage) myEmailVector.elementAt(ii-1)).addBCC(em.getRecipients());
									((EmailMessage) myEmailVector.elementAt(ii-1)).addAlertRSN(em.getAlertRSN());
									// delete current message
									myEmailVector.remove(ii);
								} else {
									ii++; // no room, move on
								}

							} else {
								// not identical emails, move on
								lastSubject = em.getSubject();
								lastRSNList = em.getRSNList();
								ii++;
							}
						}
					}
					
					decoratedTrace(INDENT2, "Sending alerts...");

					// Send the emails
					Vector msgs = new Vector(5,10);
					String msg = "";
					if(! stopAlertProcessing) {
						for (int ii=0; ii<myEmailVector.size(); ii++) {
							EmailMessage em = (EmailMessage) myEmailVector.elementAt(ii);
							if (em != null) {
								msg = em.send();

								//int pos = msg.indexOf("Could not connect to SMTP host");
								int pos = msg.indexOf("javax.mail.SendFailedException: Sending failed");
								if (pos > 0)
								{
									// save this EmailMessage for later
									//save_email_alert(frame, o, em);
								}

								StringTokenizer st1 = new StringTokenizer(msg, "\n");
								int tokens = st1.countTokens();
								for (int j = 0; j < tokens; j++) {
									String tmsg = st1.nextToken();
									if (tmsg.length() !=0 ) {
										//msgs.addElement(new LogMessage(tmsg));
									}
								}

							}
						}
					}

					// multi-line message is possible
					//eventLogMultiple(msgs);
					//frame.addJLogMultiple(msgs);

					msgs.removeAllElements();
					myEmailVector.removeAllElements();

					//update news_items set alert = 0 where rsn in (" + rsn_list + ")"
				} catch (SQLException e) {
					decoratedError(INDENT2, "Processing Alert records.", e);
				}
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
	private String filterBySource(boolean cp, boolean scrums, boolean socialMedia, String sqlWhere) {
				
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
	
	/**
	 * Load the <code>parts</code> map with values from the PUBLISHED_PARTS table, keyed by PUBLISHED_PARTS.NAME. If the key is missing from
	 * the table, a default value is associated with the given key. These values are included in a map rather than in method-global variables
	 * to allow a more modular approach.
	 * 
	 * @param showEmoji Whether emoji characters should be shown in the alerts generated.
	 * @param session The current Hibernate persistence context.
	 * @return Map containing the 
	 */
	private Map<String, String> loadPublishedParts(boolean showEmoji, Session session) {
		
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
		
		if (!showEmoji) {
			parts.put("tonePosEmoji", "");
			parts.put("toneNeuEmoji", "");
			parts.put("toneNegEmoji", "");
		} else {
			getPublishedPartByName("V13TONEPOSEMOJI", ":thumbsup:", "tonePosEmoji", parts, session);
			getPublishedPartByName("V13TONENEUEMOJI", ":point_right::point_left:", "toneNeuEmoji", parts, session);
			getPublishedPartByName("V13TONENEGEMOJI", ":thumbsdown:", "toneNegEmoji", parts, session);
		}
		
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
		String hostAddress;
		String mailPort;
		
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
			hostAddress = instance.getMailHostAddress();
			mailPort = instance.getMailPortNumber();
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
		
		private String send() {
			if (username == null) return "username is null";
			if (bccRecipients == null) return "recipients is null";
			if (subject == null) return "subject is null";
			if (message == null) return "message is null";

			if(!view_tone) tone_emoji = "";
			subject = StringUtil.exchange(subject, "<**tone_emoji**>", tone_emoji ) ;
			subject = EmojiParser.parseToUnicode(subject);
			
			String msg = "";
			try {
				msg = EmailUtil.sendAlertEmail(hostAddress, mailPort, username, recipients, subject, message);
			} catch (Exception ex) {
				msg =  "Exception sendEmail: "+ex.toString()+" bcc: " + bccRecipients+", re: "+subject;
			}
			/* try {
				if (alertRsnList != null) alertObj.updateAlertDates( o, alertRsnList );
			} catch (Exception ex) {
				msg =  "Exception updateAlertDates: "+ex.toString()+" '"+alertRsnList+"'";
			}*/
			return msg;
		}
		
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