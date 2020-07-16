package ca.bc.gov.tno.jorel2.controller;

import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import static ca.bc.gov.tno.jorel2.model.PublishedPartsDao.getPublishedPartByName;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;

public class ReportHandler {
	
	static final String UPARROW = "&#x25B2;";	
	static final String DOWNARROW = "&#x25BC;";	
	static final String NEUTRALARROW = "&#x25C4;&#x25BA;";

	static final String pref_sort_1 = "Section, Alphabetic";
	static final String pref_sort_2 = "Date, Time";
	static final String pref_sort_3 = "Media Type";
	static final String pref_sort_4 = "etaD, emiT";
	
	long rsn;
	long user_rsn;
	String user_email;
	String mail_host;
	boolean user_view_tone;
	String tonePoolUserRSNs;
	String servlet_url;
	long current_period;
	String av_host;

	public ReportHandler()
	{
		
	}
	public ReportHandler(long rsn, long user_rsn, String user_email, boolean user_view_tone, String tonePoolUserRSNs, String mail_host, String servlet_url, long current_period, String av_host) {
		this.rsn = rsn;
		this.user_rsn = user_rsn;
		this.user_email = user_email;
		this.mail_host = mail_host;
		this.user_view_tone = user_view_tone;
		this.tonePoolUserRSNs = tonePoolUserRSNs;
		if (servlet_url.endsWith("?"))
			servlet_url = servlet_url.substring(0, servlet_url.length()-1);
		this.servlet_url = servlet_url;
		this.current_period = current_period;
		this.av_host = av_host;
	}
	
	@SuppressWarnings("unused")
	public String send(org.hibernate.Session hSession) {

		StringBuilder sb = new StringBuilder();

		String usermsg = "Email sent!";

		String subject = "";
		String bbSubject = "";
		String from = "";
		String to = "";
		String bcc = "";
		String bb = "";
		boolean ccSelf = false;
		boolean pagebreak = false;
		boolean include_analysis = false;
		boolean ok = true;

		try {
			String select = "select r.email_subject, r.email_from, r.email_recipients, r.pref5, r.pref10, r.email_bcc, r.email_blackberry, " +
			"r.include_analysis " +
			"from tno.reports r where r.rsn = " + rsn + " and r.user_rsn = " + user_rsn;
			ResultSet rs = DbUtil.runSql(select, hSession);
			if (rs.next()) {
				subject = rs.getString(1);
				if (subject == null) subject = ""; else subject = subject.trim();
				if (subject.length() < 1) subject = "TNO Report";
				bbSubject = subject;
				subject = subject + " - " + DateUtil.fullDate(DateUtil.yyyymmdd());

				from = rs.getString(2);
				if (from == null) from = ""; else from = from.trim();
				if (from.length() < 1) from = user_email.trim();
				if (from.length() < 1) {
					ok = false;
					usermsg = "ERROR: The report has no from email address defined";
				}

				to = rs.getString(3);
				if (to == null) to = ""; else to = to.trim();

				ccSelf = rs.getBoolean(4);
				pagebreak = rs.getBoolean(5);

				bcc = rs.getString(6);
				if (bcc == null) bcc = ""; else bcc = bcc.trim();

				bb = rs.getString(7);
				if (bb == null) bb = ""; else bb = bb.trim();

				include_analysis = rs.getBoolean(8);

				if ((to.length() < 1) & (bb.length() < 1)) {
					ok = false;
					usermsg = "ERROR: At least 1 To address or Blackberry/iPhone address is required.";
				}

			} else {
				ok = false;
				usermsg = "ERROR: Missing report "+rsn;
			}
			rs.close();
		} catch (Exception mex) {
			ok=false;
			usermsg = "Exception: "+mex.toString();
		}

		if (ok) {
			
			Random rnd=new Random();
			String randomStr=Integer.toString(Math.abs(rnd.nextInt()));
			
			// format the report and save it later for use on a blackberry
			format(sb, pagebreak, false, randomStr, hSession);

			/* if (bb.length() > 2) {
				key = saveForBlackbery( bb, sb );
				if (key > 0) bbflag = true;
			} */

			boolean debug = false;
			// create some properties and get the default Session
			Properties props = System.getProperties();
			props.put("mail.host", mail_host);
			javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);
			session.setDebug(debug);

			MimeMessage msg = null;
			boolean sendMsg = true;
			if (to.length() < 1) {
				sendMsg = false;

			} else {
				try {
					// multiple recipients is possible
					StringTokenizer st1 = new StringTokenizer( to, "\n" );
					int tokens = st1.countTokens();
					InternetAddress[] address = new InternetAddress[tokens];
					for (int j=0; j<tokens; j++) {
						String emailAddress = st1.nextToken();
						address[j] = new InternetAddress(emailAddress);
					}

					InternetAddress fromAddress = new InternetAddress(from);

					msg = new MimeMessage(session);
					msg.setFrom(fromAddress);
					msg.setRecipients(Message.RecipientType.TO, address);
					if (ccSelf) msg.setRecipient(Message.RecipientType.CC, fromAddress);
					if (bcc.length() > 0) {
						StringTokenizer bccst1 = new StringTokenizer( bcc, "\n" );
						int bcctokens = bccst1.countTokens();
						InternetAddress[] bccaddress = new InternetAddress[bcctokens];
						for (int j=0; j<bcctokens; j++) {
							String emailAddress = bccst1.nextToken();
							bccaddress[j] = new InternetAddress(emailAddress);
						}
						msg.setRecipients(Message.RecipientType.BCC, bccaddress);
					}
					msg.setSubject(subject);
					
					MimeMultipart mp = new MimeMultipart();
					
					// mime part 1 - the report
					MimeBodyPart mbp1 = new MimeBodyPart();
					mbp1.setText(sb.toString());
					mbp1.setHeader("Content-Type","text/html; charset=\"UTF-8\"");
					mp.addBodyPart(mbp1);

					// mime part 2 - the graph
					if (include_analysis) {	
						int count = 1;
						String count_suffix = "";
						ResultSet rs = DbUtil.runSql("select analysis_rsn, image_size, font_size from report_graphs where report_rsn = " + rsn + " order by sort, analysis_rsn", hSession);
						while (rs.next()) {

							long analysis_rsn = rs.getLong(1);
							long image_size = rs.getLong(2);
							long font_size = rs.getLong(3);
							
							if (count>1)
								count_suffix = ""+count;

							MimeBodyPart mbp2 = new MimeBodyPart();
							AnalysisHandler analysis = new AnalysisHandler(analysis_rsn, user_rsn, current_period, (int)image_size, (int)font_size);
							analysis.report_write_file(randomStr, hSession);
	
							String tempdir = System.getProperty("java.io.tmpdir");
							if ( !(tempdir.endsWith("/") || tempdir.endsWith("\\")) )
								   tempdir = tempdir + System.getProperty("file.separator");
	
							DataSource fds = new FileDataSource(tempdir+analysis_rsn+"_"+image_size+"_"+font_size+"_"+randomStr+".gif");
							mbp2.setDataHandler(new DataHandler(fds));
							mbp2.setHeader("Content-ID","<analysis_"+analysis_rsn+"_"+image_size+"_"+font_size+"_"+randomStr+">");
							mbp2.setHeader("Content-Disposition", "attachment; filename=analysis"+count_suffix+".gif");
							mp.addBodyPart(mbp2);
							
						}
						rs.close();
					}
				      
					msg.setContent(mp);			      
					//msg.setHeader("MIME-Version", "1.0");
					//msg.setHeader("Content-Type", "multipart/mixed");
					//msg.setHeader("X-MS-Has-Attach", "yes");

				} catch (Exception mex) {
					usermsg = "Sending Report Failed!";
					usermsg = usermsg + "\n<!-- " + mex.toString() + " -->";
					sendMsg = false;
				}
			}

			// Send the message; try a maximum of 3 times spaced 3 seconds apart
			if (sendMsg) {
				int tryCount = 0;
				boolean sendFailed = true;

				// loop a maximum of 3 times
				while (sendFailed) {
					String failMessage = "";
					try {
						Transport.send(msg);
						sendFailed = false;
					} catch (Exception mex) {
						sendFailed = true;
						tryCount = tryCount + 1;
						failMessage = mex.getMessage();     //mex.toString();
						failMessage = failMessage.replaceAll("javax.mail.SendFailedException: ","");
						if (failMessage.indexOf("User unknown") > 0){
							tryCount = 99;
							failMessage = failMessage.replaceAll("nested exception is: ","");
							failMessage = failMessage.replaceAll("<","");
							failMessage = failMessage.replaceAll(">","");
							failMessage = failMessage.replaceAll("550 5.1.1","");
						}
					}

					// if the send has failed, try again....
					if (sendFailed) {
						if (tryCount > 1) {
							sendFailed = false;
							usermsg = "Sending Report Failed! The mail server is unavailable, please try later again in a few minutes.<!-- "+failMessage+" -->";
							usermsg = "Sending Report Failed! Details: "+failMessage+"";
						} else {
							try { Thread.sleep(1000*3); } catch (InterruptedException e) {;}
						}
					}
				}
			}
		}
		sb.setLength(0);

		//++++++++++++ BLACKBERRY EMAIL +++++++++++++++
		/*if (bbflag) {
			String moreMsg = sendToBlackberry(bbSubject, key, bb, from );
			usermsg = usermsg + " " + moreMsg;
		} else {
			if (bb.length() > 2) usermsg = usermsg + " " + bbMsg;
		}*/
		//--------------------------------------------------------------------------

		return usermsg;
		
	}





	public void format(StringBuilder sb, boolean pageBreak, org.hibernate.Session hSession) {
		format(sb, pageBreak, false, "", hSession);
	}
	
	@SuppressWarnings("unused")
	public void format(StringBuilder sb, boolean pageBreak, boolean sendButton, String randomStr, org.hibernate.Session hSession) {
		String select = "";
		boolean ok = true;

		String name = "";
		String date = DateUtil.fullDate(DateUtil.yyyymmdd() );
		String exec = "";
		String exec2= "";
		boolean p1 = false;
		boolean p2 = false;
		boolean p3 = false;
		boolean p4 = false;
		boolean p7 = false;
		boolean include_analysis = false;
		boolean show_tone = false;
		boolean include_stories = true;
		boolean pref_frontpage_section = true;
		boolean pref_top_bc = true;
		boolean pref_trending = true;
		boolean pref_most_shared = true;
		String pref_sort = pref_sort_1;
		String primarySort = "";

		sb.append(getPublishedPartByName("V35REPORTPREVIEWH", "", hSession));

		if (sendButton) {
			Properties pm = new Properties();
			pm.put("rsn",rsn+"");
			String sendButtonFormat = StringUtil.replace(getPublishedPartByName("V35REPORTPREVIEWSEND", "", hSession), pm, "[[[","]]]");
			sb.append( sendButtonFormat );
		}
		
		String previewFormat="";
		String execFormat="";
		String toc="";
		String sectionFormatH="";
		String sectionFormatD="";
		String sectionFormatF="";
		String newPage="";
		String anchors="";
		String storyFormatDivide="";
		String storyFormatH="";
		String storyFormatD="";
		String storyFormatF="";
		String summaryFormatD="";
		String analysisFormat="";
		String visualTone="";
		String visualToneSmall="";
		String analysisGroup="";
		try
		{
			previewFormat = getPublishedPartByName("V35REPORTPREVIEW", "", hSession);
			execFormat = getPublishedPartByName("V35REPORTPREVIEWEXEC", "", hSession);
			toc = getPublishedPartByName("V35REPORTPREVIEWTOC", "", hSession);
			sectionFormatH = getPublishedPartByName("V35REPORTPREVIEWSECTIONH", "", hSession);
			sectionFormatD = getPublishedPartByName("V35REPORTPREVIEWSECTIOND", "", hSession);
			sectionFormatF = getPublishedPartByName("V35REPORTPREVIEWSECTIONF", "", hSession);
			newPage = getPublishedPartByName("V35REPORTPAGEBREAK", "", hSession);
			anchors = getPublishedPartByName("V35REPORTANCHOR", "", hSession);
			storyFormatDivide = getPublishedPartByName("V35REPORTPREVIEWSTORYDIVIDE", "", hSession);
			storyFormatH = getPublishedPartByName("V35REPORTPREVIEWSTORYH", "", hSession);
			storyFormatD = getPublishedPartByName("V35REPORTPREVIEWSTORYD", "", hSession);
			storyFormatF = getPublishedPartByName("V35REPORTPREVIEWSTORYF", "", hSession);
			summaryFormatD = getPublishedPartByName("V35REPORTPREVIEWSUMMARYD", "", hSession);
			analysisFormat = getPublishedPartByName("V61REPORTANALYSIS", "", hSession);
			visualTone = getPublishedPartByName("V81TONE", "", hSession);
			visualToneSmall = getPublishedPartByName("V81TONESMALL", "", hSession);
			analysisGroup = getPublishedPartByName("V9REPORTANALYSISGROUP", "", hSession);
		} catch (Exception e)
		{
			ok=false;
			sb.append("Problems getting PARTS: "+e.toString());
		}
		if(! ok) return;
	
		boolean has_front_page_images = false;
		boolean hasSections = reportHasSections(Long.toString(rsn), hSession);


		//String analysis_summary = "";
		//String analysis_toc = "";
		//String analysis_bottom = "";
		StringBuilder analysis_summary = new StringBuilder();
		StringBuilder analysis_toc = new StringBuilder();
		StringBuilder analysis_bottom = new StringBuilder();
		
		// Get the user report
		ResultSet rs=null;
		try {
			select = "select r.name,r.pref1,r.pref2,r.pref3,r.pref4,r.pref7," +
			"r.include_analysis,r.analysis_rsn,r.analysis_location,r.analysis_size,r.analysis_font_size,r.show_tone,e.summary,e.summary2,r.pref_add_frontpage,r.pref_inc_stories,"+
			"r.pref_frontpage_section,r.pref_top_bc,r.pref_trending,r.pref_most_shared,r.pref_sort "+
			"from tno.reports r, report_exec_summary e "+
			"where r.rsn = e.report_rsn(+) and r.rsn = "+rsn+" and r.user_rsn = "+user_rsn;
			rs = DbUtil.runSql(select, hSession);
			if (rs.next()) {
				name = rs.getString(1);
				p1 = rs.getBoolean(2);
				p2 = rs.getBoolean(3);
				p3 = rs.getBoolean(4);
				p4 = rs.getBoolean(5);
				p7 = rs.getBoolean(6);
				include_analysis = rs.getBoolean(7);
				show_tone = rs.getBoolean(12);
				exec = rs.getString(13);
				exec2 = rs.getString(14);
				include_stories = rs.getBoolean(16);
				pref_frontpage_section = rs.getBoolean(17);
				pref_top_bc = rs.getBoolean(18);
				pref_trending = rs.getBoolean(19);
				pref_most_shared = rs.getBoolean(20);
				pref_sort = rs.getString(21);
				if (pref_sort == null) pref_sort = pref_sort_1;
				if (exec == null) exec = "";
				if (exec2 == null) exec2 = "";
				exec = exec+exec2;
				
				if(pref_sort.equals(pref_sort_1))
				{
					primarySort = "s.sort_position,";
				}
				else if(pref_sort.equals(pref_sort_2) | pref_sort.equals(pref_sort_4))
				{
					;
				}
				else
				{
					primarySort = "";
				}
				
				if (include_analysis) {
					boolean analysis_ok = false;

					//Statement analysis_stmt=null;
					ResultSet analysis_rs=null;
					ResultSet rg_rs = DbUtil.runSql("select * from report_graphs where report_rsn = " + rsn + " order by sort, analysis_rsn", hSession);
					while (rg_rs.next()) {

						long a_rsn = rg_rs.getLong(3);
						String a_location = rg_rs.getString(4);
						long a_imagesize = rg_rs.getLong(5);
						long a_fontsize = rg_rs.getLong(6);

						analysis_rs = DbUtil.runSql("select * from analysis where rsn = " + a_rsn + " and user_rsn = " + user_rsn, hSession);
						if(analysis_rs.next())
						{
							analysis_ok = true;
						}

						StringBuilder analysis_graph = new StringBuilder(analysisFormat);
						if(analysis_ok)
						{
							AnalysisHandler analysis = new AnalysisHandler(a_rsn, user_rsn, current_period, (int)a_imagesize, (int)a_fontsize);
							analysis.draw(true, false, hSession);
							Random rnd=new Random();
							String rndNumber=Integer.toString(rnd.nextInt());
							if (sendButton) { //preview
								StringUtil.replace(analysis_graph, "<**analysisimage**>", "<img src=\"tno.otis.servlet?command=analysis&subcommand=display_graph&rsn="+Long.toString(a_rsn) + "&imagesize=" + Long.toString(a_imagesize) + "&fontsize=" + Long.toString(a_fontsize) + "&rnd="+rndNumber + "\">");
							} else { //email
								StringUtil.replace(analysis_graph, "<**analysisimage**>", "<img src=\"cid:analysis_" + a_rsn + "_" + a_imagesize + "_" + a_fontsize + "_" + randomStr + "\">");
							}
							StringUtil.replace(analysis_graph, "<**imagelink**>", servlet_url+"?command=analysis&subcommand=display_graph&rsn="+Long.toString(a_rsn)+"&user_rsn="+user_rsn+"&rnd="+rndNumber);
							StringUtil.replace(analysis_graph, "<**textlink**>", servlet_url+"?command=analysis&subcommand=download&rsn="+Long.toString(a_rsn)+"&rnd="+rndNumber);
						}
						else
						{
							StringUtil.replace(analysis_graph, "<**analysisimage**>", "Analysis Graph cannot be found!");
							StringUtil.replace(analysis_graph, "<**imagelink**>", "");
							StringUtil.replace(analysis_graph, "<**textlink**>", "");
						}
						
						if (a_location.equalsIgnoreCase("summary")) {
							analysis_summary.append(analysis_graph);
						} else if (a_location.equalsIgnoreCase("toc")) {
							analysis_toc.append(analysis_graph);
						} else {
							analysis_bottom.append(analysis_graph);
						}
												
					}
					try { if (analysis_rs != null) analysis_rs.close(); } catch (SQLException err) {;}
					try { if (rg_rs != null) rg_rs.close(); } catch (SQLException err) {;}
				}

				StringBuilder f = new StringBuilder(previewFormat);
				StringUtil.replace(f, "<**name**>", name ) ;
				StringUtil.replace(f, "<**date**>", date ) ;
				sb.append(f);

				if (exec.length() > 0) {
					sb.append(StringUtil.replace(execFormat, "<**executive_summary**>", exec ));
				}

				if (analysis_summary.length()!=0) {
					sb.append(StringUtil.replace(analysisGroup, "<**analysis**>", analysis_summary.toString()));
				}

			} else {
				sb.append("Report "+rsn+" not found!");
				ok = false;
			}
		} catch (Exception err) {
			sb.append("Report error "+err.toString()+"<br>"+select);
			ok = false;
		}
		try { if (rs != null) rs.close(); } catch (SQLException err) {;}

		// Buffer for formatted data
		StringBuilder hsb = new StringBuilder();         // headline string buffer
		StringBuilder ssb = new StringBuilder();         // story string buffer
		StringBuilder bsb = new StringBuilder();         // blank section buffer
		StringBuilder smsb = new StringBuilder();		   // social media buffer
		
		Map<String, String> toneMap = new HashMap<String, String>();
		
		if (ok) {
			
			// ====== FRONT PAGE IMAGES
			if (pref_frontpage_section) {
				ResultSet fp_rs=null;
				StringBuilder imgs = new StringBuilder();
				try {
					String sql = "select * from source_paper_images i, sources s where i.source_rsn = s.rsn and i.paper_date = to_date(sysdate) order by s.rsn";
					fp_rs = DbUtil.runSql(sql, hSession);
					while(fp_rs.next()) {
						imgs.append(" <img src=\"" + av_host + fp_rs.getString(6) + fp_rs.getString(7) + "\" style=\"vertical-align:top\" />");						
					}
				} catch (Exception err) {
					sb.append("Front Page Images format error " + err.toString() + "<br>");
				}

				if (imgs.length()>0) {
					StringBuilder frontPagesSection = new StringBuilder(getPublishedPartByName("V9REPORTFRONTPAGES", "", hSession));
					StringUtil.replace(frontPagesSection,"<**images**>",imgs.toString());
					if (pageBreak) ssb.append(newPage);
					ssb.append(StringUtil.replace(storyFormatDivide, "<**sectionname**>", "" ) );
					ssb.append(frontPagesSection);
					has_front_page_images = true;
				}
				try { if (fp_rs != null) fp_rs.close(); } catch (SQLException err) {;}
			}

			// ====== SOCIAL MEDIA
			
			// 1. top BC
			if (pref_top_bc) {
				ResultSet sm_rs=null;
				StringBuilder sm = new StringBuilder();
				Calendar cal = Calendar.getInstance();
				
				// NO WEEKEND CHECK - code left just in case
				Boolean weekend_check = false;
				if (weekend_check && ((cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) || (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)))
				{
					String period = "1.042";	// 25 hours
					period = "2.0";				// 48 hours
					
					try {
						String sql="select z.* from "+
						"	( "+
						"		select url as \"URL\", url_title as \"Title\", count(rsn) as \"Count\""+
						"		from social_media_links s "+
						"		where deleted = 0 and to_char(date_created,'yyyy-mm-dd hh24:mi') >= to_char(sysdate-"+period+",'yyyy-mm-dd hh24:mi') and "+
						"			replace(upper(s.author),' ','%') not in (select upper(replace(se.name,' ','')) from series se ) and "+
						"			(select count(*) from sources so where (so.sm_exception_string <> '' or so.sm_exception_string is not null) and lower(s.url) like '%'||so.sm_exception_string||'%') = 0 "+
						"		group by s.url, url_title "+
						"		order by 3 desc,2 desc"+
						"	) z "+
						"where rownum <= 10";
						sm_rs = DbUtil.runSql(sql, hSession);
						while(sm_rs.next()) {
							sm.append(" <li><a href=\"" + sm_rs.getString(1) + "\">" + sm_rs.getString(2) + "</a>");						
						}
					} catch (Exception err) {
						sb.append("Social Media TopBC format error " + err.toString() + "<br>");
					}
				}
				else
				{
					sm = new StringBuilder(getPublishedPartByName(("_AUTO_TRENDING_BC"), "", hSession));
				}

				if (sm.length()>0) {
					StringBuilder topBCSection = new StringBuilder(getPublishedPartByName("V9REPORTTOPBC", "", hSession));
					StringUtil.replace(topBCSection,"<**data**>", sm.toString());
					smsb.append(topBCSection);
				}
				try { if (sm_rs != null) sm_rs.close(); } catch (SQLException err) {;}
			}
			
			// 2. trending in vancouver
			if (pref_trending) {
				String data = getPublishedPartByName("_TRENDING_VANCOUVER", "", hSession);

				if (data.length()>0) {
					StringBuilder trendingSection = new StringBuilder(getPublishedPartByName("V9REPORTTRENDING", "", hSession));
					StringUtil.replace(trendingSection,"<**data**>",data);
					smsb.append(trendingSection);
				}
			}
			
			// 3. most shared
			if (pref_most_shared) {
				ResultSet sm_rs=null;
				StringBuilder sm = new StringBuilder();
				try {
					String sql = "select * from " +
					"(select url as \"URL\", url_title as \"Title\", count(rsn) as \"Count\"" +
					"from social_media_links where deleted = 0 and date_created > (SYSDATE - 7) " +
					"group by url, url_title " +
					"order by 3 desc,2 desc) " +
					"where rownum <= 10";
					sm_rs = DbUtil.runSql(sql, hSession);
					while(sm_rs.next()) {
						sm.append(" <li><a href=\"" + sm_rs.getString(1) + "\">" + sm_rs.getString(2) + "</a>");						
					}
				} catch (Exception err) {
					sb.append("Social Media MostShared format error "+err.toString()+"<br>");
				}

				if (sm.length()>0) {
					StringBuilder mostSharedSection = new StringBuilder(getPublishedPartByName("V9REPORTMOSTSHARED", "", hSession));
					StringUtil.replace(mostSharedSection,"<**data**>", sm.toString());
					smsb.append(mostSharedSection);
				}
				try { if (sm_rs != null) sm_rs.close(); } catch (SQLException err) {;}
			}
			
			// wrap social media in a section
			if (smsb.length()>0) {
				StringBuilder sm = new StringBuilder(getPublishedPartByName("V9REPORTSOCIALMEDIA", "", hSession));
				StringUtil.replace(sm,"<**data**>",smsb.toString());
				if (pageBreak) ssb.append(newPage);
				ssb.append(StringUtil.replace(storyFormatDivide, "<**sectionname**>", "" ));
				ssb.append(sm);
			}

			// ====== HEADLINES
			if (p1) {                                    // Show headlines at top
				hsb.append(toc);
				try {

					// add the front page image section to the TOC
					if (has_front_page_images) {
						StringBuilder f = new StringBuilder(storyFormatD);
						StringUtil.replace(f, "<**headline**>", "Front pages" ) ;
						StringUtil.replace(f, "<**section_name**>", "" ) ;
						StringUtil.replace(f, "<**count**>", "1" ) ;
						StringUtil.replace(f, "<**summary**>", "" ) ;
						StringUtil.replace(f, "<**visualtonesmall**>", "");
						hsb.append(f);
					}

					// social media
					if (smsb.length()>0) {
						StringBuilder f = new StringBuilder(storyFormatD);
						StringUtil.replace(f, "<**headline**>", "Social media" ) ;
						StringUtil.replace(f, "<**section_name**>", "" ) ;
						StringUtil.replace(f, "<**count**>", "2" ) ;
						StringUtil.replace(f, "<**summary**>", "" ) ;
						StringUtil.replace(f, "<**visualtonesmall**>", "");
						hsb.append(f);
					}

					select = "select s.*,h.*, st.tab, " +
					"(select avg (tn.tone) from users_tones tn where tn.item_rsn = h.item_rsn and tn.user_rsn in (" + tonePoolUserRSNs + ")) " +
					"from report_stories h, report_sections s, source_types st "+
					"where h.report_rsn = "+rsn+" and h.report_section_rsn = s.rsn(+) and h.source_type_rsn = st.rsn(+) and h.toc_position > 0 " +
					"order by " + primarySort + "h.sort_position";

					rs = DbUtil.runSql(select, hSession);
					String prevSection = "~";
					while (rs.next()) {
						String storyrsn = rs.getString(8);
						String sectionName = rs.getString(3);
						if (sectionName == null) sectionName = "";
						String headline = rs.getString(10);
						if (headline == null) headline = "";
						String summary = rs.getString(14);
						if (summary == null) summary = ""; else summary = summary.trim();
						long item_rsn = rs.getLong(16);
						
						String tab = rs.getString(19);
						if (tab == null) tab = "External";
						
						if(! hasSections)
						{
							if(pref_sort.equals(pref_sort_3))	// Media type sort
							{
								sectionName = tab;
							}
							else if(pref_sort.equals(pref_sort_2) | pref_sort.equals(pref_sort_4))	// date time sort
							{
								sectionName = "";
							}
						}

						int tone = 0;
						boolean null_tone = false;
						if(rs.getObject(20) != null){ tone = rs.getInt(20);} else {null_tone = true;}
						
						if (! sectionName.equalsIgnoreCase(prevSection)) {
							if (prevSection.equals("~")) {
								hsb.append(sectionFormatH);
							} else {
								hsb.append(storyFormatF);
								hsb.append(sectionFormatF);
							}
							hsb.append( StringUtil.replace(sectionFormatD, "<**section_name**>", sectionName ) );
							hsb.append(storyFormatH);
							prevSection = sectionName;
						} else {
							sectionName = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
						}						
						
						if (headline.length() > 0) {
							StringBuilder f = new StringBuilder(storyFormatD);
							StringUtil.replace(f, "<**headline**>", headline ) ;
							StringUtil.replace(f, "<**section_name**>", sectionName ) ;
							StringUtil.replace(f, "<**count**>", storyrsn ) ;
							if (p2) {
								if (summary.length() > 0)
									StringUtil.replace(f, "<**summary**>", StringUtil.replace(summaryFormatD, "<**summary**>", summary ) ) ;
								else
									StringUtil.replace(f, "<**summary**>", "" ) ;
							} else {
								StringUtil.replace(f, "<**summary**>", "" ) ;
							}
							StringUtil.replace(f, "<**visualtonesmall**>", visualTone(tone, null_tone, show_tone, visualToneSmall, hSession));
							hsb.append(f);
						} else {
							if (! p7) bsb.append(sectionName+"<br>");
						}
						
						toneMap.put(storyrsn, visualTone(tone, null_tone, show_tone, visualTone, hSession)); // save to cloak into the story content
						
					}					
					if (! prevSection.equals("~")) {
						hsb.append(storyFormatF);
						hsb.append(sectionFormatF);
					}
					rs.close();
					
				} catch (Exception err) {
					sb.append("TOC format error "+err.toString()+"<br>");
				}
			}

			if (analysis_toc.length()!=0) {
				hsb.append(StringUtil.replace(analysisGroup, "<**analysis**>", analysis_toc.toString()));
			}

			// ====== STORY CONTENT
			if (include_stories) {
				try {
					//select = "select s.*,h.* from report_sections s, report_stories h"+
					//" where s.rsn(+) = h.report_section_rsn and h.report_rsn = "+rsn+
					//" order by s.sort_position, s.name, h.sort_position, h.headline";

					select = "select s.*,h.*, st.tab "+
					"from report_stories h, report_sections s, source_types st "+
					"where h.report_rsn = "+rsn+" and h.report_section_rsn = s.rsn(+) and h.source_type_rsn = st.rsn(+) "+
					"order by "+primarySort+"h.sort_position";

					String previous_content = "";
					String previousStoryrsn = "~";
					
					rs = DbUtil.runSql(select, hSession);
					while (rs.next()) {
						String storyrsn = rs.getString(8);
						String sectionName = rs.getString(3);
						if (sectionName == null) sectionName = "";
						String tab = rs.getString(19);
						if (tab == null) tab = "External";
						String headline = rs.getString(10);
						if (headline == null) headline = "";
						if (headline.length() > 0) {
							
							if(! hasSections)
							{
								if(pref_sort.equals(pref_sort_3))	// Media type sort
								{
									sectionName = tab;
								}
								else if(pref_sort.equals(pref_sort_2) | pref_sort.equals(pref_sort_4))	// date time sort
								{
									sectionName = "";
								}
							}
							
							// Keep stories in a separate string buffer
							Clob cl = rs.getClob(15);
							String content="";
							if (cl == null) {
								content = "No story";
							} else {
								int l = (int)cl.length();
								content = cl.getSubString(1,l).replace((char)0,(char)32);
							}
							
							/*
							 *  Once the 'next' link is set, the story content is complete and can be appended to the story content buffer  
							 */
							if(! previousStoryrsn.equals("~"))
							{
								previous_content = StringUtil.replace(previous_content, "<**next**>", "<a href=\"#A"+storyrsn+"\">next</a>" );
								
								ssb.append(previous_content);
								previous_content = "";
							}
							
							/*
							 *	Prepare the current story, do not append the story to the buffer, as we have not set the 'next' link yet
							 *		Once we get the next story record, we can do all that
							 */
							String full_content = "";
							if (pageBreak) full_content = full_content + newPage;
							full_content = full_content + StringUtil.replace(storyFormatDivide, "<**sectionname**>", sectionName );
							
							String the_anchors = StringUtil.replace(anchors, "<**rsn**>", storyrsn );
							if(! previousStoryrsn.equals("~"))
								the_anchors = StringUtil.replace(the_anchors, "<**previous**>", "<a href=\"#A"+previousStoryrsn+"\">previous</a>" );
							else
								the_anchors = StringUtil.replace(the_anchors, "<**previous**>", "" );
							full_content = full_content + the_anchors;
							
							// tone was saved when creating the TOC. Get it from the hashmap and cloak it.
							String visualToneSaved = toneMap.get(storyrsn);
							if (visualToneSaved==null) {
								content = StringUtil.replace(content, "<!--visualtone-->", "" );
							} else {
								content = StringUtil.replace(content, "<!--visualtone-->", visualToneSaved );
							}
							full_content = full_content + content;
							
							previous_content = full_content;
							previousStoryrsn = storyrsn;
						}
					}
					if(! previousStoryrsn.equals("~"))
					{
						previous_content = StringUtil.replace(previous_content, "<**next**>", "" );
						ssb.append(previous_content);
						previous_content = "";
					}

					rs.close();
				} catch (Exception err) {
					sb.append("Story format error: "+err.toString()+"<br>");
				}
			}
			
			if (analysis_bottom.length()!=0) {
				ssb.append(StringUtil.replace(analysisGroup, "<**analysis**>", analysis_bottom.toString()));
			}

			// ====== EMPTY SECTIONS
			if (! p4) {                     // Suppress sections with no stories
				try {
					select = "select s.name from report_sections s where s.report_rsn = "+rsn+
					" and (select count(*) from report_stories r where r.report_section_rsn = s.rsn) = 0"+
					" order by s.sort_position, s.name";
					rs = DbUtil.runSql(select, hSession);
					while (rs.next()) {
						String sectionName = rs.getString(1);
						if (sectionName == null) sectionName = "";
						if (sectionName.length() > 0) bsb.append(sectionName+"<br>");
					}
					rs.close();
				} catch (Exception err) {;}
			}

			// Put together all the pieces (hsb, ssb, bsb)
			if (p1) {                       // Include headlines at top
				sb.append(hsb);
				if (! p4) {                   // DO not suppress blank sections
					if (p7) {                   // show blank sections at top
						sb.append("<ul><li><b>Blank Sections</b><br>");
						sb.append(bsb);
						sb.append("</ul>");
					}
				}
			}
			sb.append(ssb);
			if (p1) {                       // show headlines
				if (! p4) {                   // DO not suppress blank sections
					if (! p7) {                 // show blank sections at bottom
						sb.append("<p><hr><b>Blank Sections</b><br>");
						sb.append(bsb);
					}
				}
			}
			hsb.setLength(0);
			ssb.setLength(0);
			bsb.setLength(0);
		}

		try { if (rs != null) rs.close(); } catch (SQLException err) {;}

		sb.append(getPublishedPartByName("V35REPORTPREVIEWF", "", hSession));
		return;
	}

	public String visualTone(int tone, boolean null_tone, boolean show_tone, org.hibernate.Session hSession) {
		// tone flag
		if (show_tone && user_view_tone) {
			if (!null_tone) {
				String vt = getPublishedPartByName("V81TONE", "", hSession);
				return visualTone(tone, null_tone, show_tone, vt, hSession);
			} else {
				return "";
			}
		} else {
			return "";
		}
	}

	public String visualTone(int tone, boolean null_tone, boolean show_tone, String vt, org.hibernate.Session hSession) {
		// tone flag
		if (show_tone && user_view_tone) {
			if (!null_tone) {
				StringBuilder t = new StringBuilder(vt);
				StringUtil.replace(t, "<**number**>", ""+Math.round(tone));
				if (tone<=-1) {
					StringUtil.replace(t, "<**color**>", "red");
					StringUtil.replace(t, "<**arrow**>", DOWNARROW);
					StringUtil.replace(t, "<**word**>", "Negative");
				} else if (tone>=1) {
					StringUtil.replace(t, "<**color**>", "green");
					StringUtil.replace(t, "<**arrow**>", UPARROW);
					StringUtil.replace(t, "<**word**>", "Positve");
				} else {
					StringUtil.replace(t, "<**color**>", "gray");
					StringUtil.replace(t, "<**arrow**>", NEUTRALARROW);
					StringUtil.replace(t, "<**word**>", "Neutral");
				}
				return t.toString();
			} else {
				return "";
			}
		} else {
			return "";
		}
	}
	
	@SuppressWarnings("resource")
	public String sortStories(String sort_method, String report_rsn, String updateSql, org.hibernate.Session hSession)
	{
		ResultSet rs = null;
		String msg = "";
		boolean hasSections = reportHasSections(report_rsn, hSession);
		try
		{
			// Get list of unassign sort position (-1)
			List<Long> listRSNs = new ArrayList<Long>();
			String select = "select r.rsn from report_stories r where r.sort_position = -1 and r.report_rsn = " + report_rsn + " order by r.rsn";
			rs = DbUtil.runSql(select, hSession);
			while (rs.next())
			{
				long storylinersn = rs.getLong(1);
				listRSNs.add(storylinersn);
			}
			rs.close();
	
			// Any new sort position to assign
			if(listRSNs.size() > 0)
			{
				if(sort_method.equals(pref_sort_1))
				{
					// assign sort position based on user selected report sort order
					long last_sort = 0;
					long sort_inc = 1;
					select = "select r.rsn from report_stories r, report_sections s " +
					"where r.report_section_rsn = s.rsn(+) and r.report_rsn = " + report_rsn + " " +
					"order by s.sort_position, r.headline";
					rs = DbUtil.runSql(select, hSession);
					while (rs.next())
					{
						/*
						 * Only change the stories that are new, ie have a sort_positin of -1 (see above)
						 * 	Leave all the rest as is (the user may have changed some)
						 */
						long storylinersn = rs.getLong(1);
						if(listRSNs.contains(storylinersn))
						{
							// update this story with a sort position
							updateSql = updateSql.replaceAll("?1", Long.toString(last_sort + sort_inc));
							updateSql = updateSql.replaceAll("?2", Long.toBinaryString(storylinersn));
							DbUtil.runUpdateSql(updateSql, hSession);
							
							sort_inc++;
						}
						else
						{
							last_sort = last_sort + 100;
							sort_inc = 1;
						}
					}
					rs.close();
				}
				else if(sort_method.equals(pref_sort_2) | sort_method.equals(pref_sort_4))	// Date Time sort
				{
					String direction = "";
					if(sort_method.equals(pref_sort_4)) direction = " desc";
					
					// assign sort position based on user selected report sort order
					long last_sort = 0;
					long sort_inc = 1;
					select = "select r.rsn from report_stories r where r.report_rsn = "+report_rsn+" order by r.date_time"+direction;
					if(hasSections)
					{
						select = "select r.rsn from report_stories r, report_sections s "+
						"where r.report_rsn = "+report_rsn+" and r.report_section_rsn = s.rsn(+) order by s.sort_position, r.date_time"+direction;
					}
					rs = DbUtil.runSql(select, hSession);
					while (rs.next())
					{
						/*
						 * Only change the stories that are new, ie have a sort_positin of -1 (see above)
						 * 	Leave all the rest as is (the user may have changed some)
						 */
						long storylinersn = rs.getLong(1);
						if(listRSNs.contains(storylinersn))
						{
							// update this story with a sort position
							updateSql = updateSql.replaceAll("?1", Long.toString(last_sort + sort_inc));
							updateSql = updateSql.replaceAll("?2", Long.toBinaryString(storylinersn));
							DbUtil.runUpdateSql(updateSql, hSession);
							
							sort_inc++;
						}
						else
						{
							last_sort = last_sort + 100;
							sort_inc = 1;
						}
					}
					rs.close();
				}
				else if(sort_method.equals(pref_sort_3))	// Media Type
				{
					// assign sort position based on user selected report sort order
					long last_sort = 0;
					long sort_inc = 1;
					select = "select r.rsn,"+
						"(select min(stx.sort_order) from source_types stx where stx.tab in (select sty.tab from source_types sty where sty.type = n.type) group by tab)"+
						",s.sort_order,n.item_date,n.item_time,n.string3,r.item_rsn,n.rsn"+
						" from report_stories r, news_items n, sources s, source_types t"+
						" where r.report_rsn = "+report_rsn+" and r.item_rsn = n.rsn(+) and n.source = s.source(+) and n.type = t.type(+) "+
						"UNION "+
						"select r.rsn,"+
						"(select min(stx.sort_order) from source_types stx where stx.tab in (select sty.tab from source_types sty where sty.type = h.type) group by tab)"+
						",s.sort_order,h.item_date,h.item_time,h.string3,r.item_rsn,h.rsn"+
						" from report_stories r, hnews_items h, sources s, source_types t"+
						" where r.report_rsn = "+report_rsn+" and r.item_rsn = h.rsn(+) and h.source = s.source(+) and h.type = t.type(+) "+
						" order by 2,3,4,5,6";
					if(hasSections)
					{
						select = "select r.rsn,"+
						"(select min(stx.sort_order) from source_types stx where stx.tab in (select sty.tab from source_types sty where sty.type = n.type) group by tab)"+
						",s.sort_order,n.item_date,n.item_time,n.string3,r.item_rsn,n.rsn,rs.sort_position"+
						" from report_stories r, report_sections rs, news_items n, sources s, source_types t"+
						" where r.report_rsn = "+report_rsn+" and r.report_section_rsn = rs.rsn(+) and r.item_rsn = n.rsn(+) and n.source = s.source(+) and n.type = t.type(+) "+
						"UNION "+
						"select r.rsn,"+
						"(select min(stx.sort_order) from source_types stx where stx.tab in (select sty.tab from source_types sty where sty.type = h.type) group by tab)"+
						",s.sort_order,h.item_date,h.item_time,h.string3,r.item_rsn,h.rsn,rs.sort_position"+
						" from report_stories r, report_sections rs, hnews_items h, sources s, source_types t"+
						" where r.report_rsn = "+report_rsn+" and r.report_section_rsn = rs.rsn(+) and r.item_rsn = h.rsn(+) and h.source = s.source(+) and h.type = t.type(+) "+
						" order by 9,2,3,4,5,6";
					}
					
					rs = DbUtil.runSql(select, hSession);
					while (rs.next())
					{
						long storylinersn = rs.getLong(1);
						long rnews_item_rsn = rs.getLong(7);
						long news_item_rsn = 0;
						String rsn_object = rs.getString(8);
						if(rsn_object != null)
						{
							news_item_rsn = Long.parseLong(rsn_object);
						}
						/*
						 * Only change the stories that are new, ie have a sort_positin of -1 (see above)
						 * 	Leave all the rest as is (the user may have changed some)
						 */
						if(listRSNs.contains(storylinersn))
						{
							// update this story with a sort position
							updateSql = updateSql.replaceAll("?1", Long.toString(last_sort + sort_inc));
							updateSql = updateSql.replaceAll("?2", Long.toBinaryString(storylinersn));
							DbUtil.runUpdateSql(updateSql, hSession);

							sort_inc++;
							
							/*
							 * The UNION above causes duplicates, so in some cases we can remove the current story as it has
							 * 	been dealt with
							 */
							if((rnews_item_rsn == 0) || (news_item_rsn > 0))
							{
								listRSNs.remove(storylinersn);
							}
						}
						else
						{
							last_sort = last_sort + 100;
							sort_inc = 1;
						}
					}
					rs.close();
				}
			}
		}
		catch (Exception err)
		{
			msg = err.toString();
		}
		try { rs.close(); } catch (Exception err) {;}
		return msg;
	}

	public boolean reportHasSections(String report_rsn, org.hibernate.Session hSession)
	{
		boolean hasSections = false;
		try
		{
			// Does report have sections
			String select = "select count(*) from report_sections s where s.report_rsn = "+report_rsn;
			ResultSet rs = DbUtil.runSql(select, hSession);
			if (rs.next())
			{
				if(rs.getInt(1) > 0) hasSections = true;
			}
			rs.close();
		}
		catch(Exception err)
		{ ; }
		return hasSections;
	}
}
