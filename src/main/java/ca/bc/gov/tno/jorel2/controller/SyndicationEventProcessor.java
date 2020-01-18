package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Optional;
import java.util.TimeZone;

import org.hibernate.Session;
import org.springframework.stereotype.Service;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import ca.bc.gov.tno.jorel2.Jorel2Root;
//import jorel.dbRSS.rssItem;
import ca.bc.gov.tno.jorel2.util.Jorel2StringUtil;

@Service
public class SyndicationEventProcessor extends Jorel2Root implements Jorel2EventProcessor {

	/**
	 * Manages the retrieval and processing of the CP feed using the 
	 * 
	 * @author Stuart Morse
	 * @version 0.0.1
	 */

	SyndicationEventProcessor() {
	}
		
	/**
	 * Process all eligible non-XML syndication event records from the TNO_EVENTS table.
	 * 
	 * @return Optional object containing the results of the action taken.
	 */
	public Optional<String> processEvents(Session session) {
	
		int i = 0;
		SyndFeed feed = null;
		String content;
		String author;
		Date pubdate;
		String title;
		String link;
		String source = "CP News";
		String description;
		String currentURL;
		boolean cpRSS = true;
		
		try {
			URLConnection feedUrlConnection = new URL("http://www.commandnews.com/fpweb/fp.dll/$bc-rss/htm/rss/x_searchlist.htm/_drawerid/!default_bc-rss/_profileid/rss/_iby/daj/_iby/daj/_svc/cp_pub/_k/XQkKHjnAUpumRfdr").openConnection();
			feedUrlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
			i++;
			SyndFeedInput input = new SyndFeedInput();
			i++;
			XmlReader xmlReader = new XmlReader(feedUrlConnection);
			i++;
			feed = input.build(xmlReader);
		}
		catch (Exception ex) {
			logger.error("blah", ex);
		}
		
		for (final Iterator iter = feed.getEntries().iterator(); iter.hasNext(); ) {
			content="";
			author="";
			pubdate=new java.util.Date();
			String pubdate_as_string = "";
			try {
				SyndEntry syndEntry = (SyndEntry) iter.next();
				title = syndEntry.getTitle();
				link = syndEntry.getLink();		

				try {
					String articlePage = new String("");
					java.util.Date articleDate = new java.util.Date();
					String articleTitle = new String("");
					String articleContent = new String("");

					currentURL = ""; //rssUtilLinkandUrl.elementAt(i).toString();
					//URL url = new URL(currentURL);
					URLConnection itemUrlConnection = new URL(currentURL).openConnection();
					itemUrlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
					BufferedReader in = new BufferedReader(new InputStreamReader(itemUrlConnection.getInputStream()));
					String inputLine;

					while ( (inputLine = in.readLine()) != null) {
						//one line at a time at a space since bufferedreader will read until cr/lf but will remove them leading to words running together.
						articlePage += inputLine + " ";
					}
					in.close();

					if (articlePage.length() > 0) {
						try {
							articleDate = GetDate(articlePage, cpRSS);
							articleDate = convertESTtoPST(articleDate);
						} catch (Exception err) {
							skip(); //theFrame.addJLog(eventlog("dbRSS UpdateRSS Error getting date ["+source+"]: " + err.toString()), true);
						}
						try {
							articleTitle = GetTitle(articlePage);
						} catch (Exception err) {
							articleTitle = currentURL;
							skip(); //theFrame.addJLog(eventlog("dbRSS UpdateRSS Error getting title ["+source+"]: " + err.toString()), true);
						}
						try {
							articleContent = GetArticle(articlePage);
						} catch (Exception err) {
							articleContent = currentURL;
							skip(); //theFrame.addJLog(eventlog("dbRSS UpdateRSS Error getting content ["+source+"]: " + err.toString()), true);
						}
						if ( (articleTitle.length() > 0) && (articleContent.length() > 0)) {
							//Insert this into the New_Items table
							skip();
						} else {
								skip(); //theFrame.addJLog(eventlog( "dbRSS Call to DoNews_ItemsInsert Failed for ["+source+"]: " + currentURL), true);
							}
						}
						else {
							skip(); //theFrame.addJLog(eventlog( "dbRSS UpdateRSS Required Field missing for ["+source+"]: " + currentURL), true);
						}
					} 
					catch (Exception err) {
						skip(); //theFrame.addJLog(eventlog("dbRSS UpdateRSS Error ["+source+"]: " + err.toString()), true);
					}
				}
				catch (Exception err) {
					skip(); //theFrame.addJLog(eventlog("dbRSS UpdateRSS Error ["+source+"]: " + err.toString()), true);
				}
			}
		
		return Optional.of("");
	}

	private String GetTitle(String articlePage) {
		String articlePageUpper = articlePage.toUpperCase();
		String titleStr = "";
		titleStr = articlePage.substring(articlePageUpper.indexOf("<TITLE>") + 7,
				articlePageUpper.indexOf("</TITLE>"));
	
		return titleStr;
	}
	
	private String GetArticle(String articlePage) {
		String articlePageUpper = articlePage.toUpperCase();
		String articleStr = "";
		int p=articlePageUpper.indexOf("<ARTICLE>");
		if(p>0)
		{
			articleStr = articlePage.substring(articlePageUpper.indexOf("<ARTICLE>") + 9, articlePageUpper.indexOf("</ARTICLE>"));
		}
		else
		{
			p=articlePageUpper.indexOf("<CONTENT:ENCODED>");
			if(p>0)
			{
				articleStr = articlePage.substring(articlePageUpper.indexOf("<CONTENT:ENCODED>") + 17, articlePageUpper.indexOf("</CONTENT:ENCODED>"));
			}
		}
		articleStr = Jorel2StringUtil.removeHTML(articleStr);
		return articleStr;
	}

	private java.util.Date convertESTtoPST(java.util.Date date) {

		java.util.Date convertedDate = new java.util.Date();

		try {

			SimpleDateFormat genericTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String genericTimeStr = genericTime.format(date);

			SimpleDateFormat estFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			estFormat.setTimeZone(TimeZone.getTimeZone("America/Indianapolis"));

			SimpleDateFormat pstFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			pstFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));

			java.util.Date dbDate = estFormat.parse(genericTimeStr);
			String pstStrDatTime = pstFormat.format(dbDate); //Convert to PST date
			convertedDate = pstFormat.parse(pstStrDatTime);

		}
		catch (Exception err) {
		}

		return convertedDate;

	}

	private java.util.Date convertGMTtoPST(java.util.Date date) {

		java.util.Date convertedDate = new java.util.Date();

		try {
			SimpleDateFormat genericTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String genericTimeStr = genericTime.format(date);

			SimpleDateFormat gmtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			gmtFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

			SimpleDateFormat pstFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			pstFormat.setTimeZone(TimeZone.getTimeZone("PST"));

			java.util.Date dbDate = gmtFormat.parse(genericTimeStr);
			String pstStrDatTime = pstFormat.format(dbDate); //Convert to PST date
			convertedDate = pstFormat.parse(pstStrDatTime);
		}
		catch (Exception err) {
		}
		return convertedDate;
	}
	
	@SuppressWarnings("deprecation")
	private java.util.Date GetDate(String articlePage, boolean cpRSS) {
		String articlePageUpper = articlePage.toUpperCase();
		String dateStr = "";
		String month = "";
		String day = "";
		String time = "";
		int year = 0;
		java.util.Date currDate = new java.util.Date();
		java.util.Date dateProperUtil = new java.util.Date();
		java.util.Date dateProper = new java.util.Date();
		java.util.Date parsedDate = new java.util.Date();

		Calendar calendar = GregorianCalendar.getInstance();
		int p=articlePageUpper.indexOf("<DATE>");
		if(p>0)
		{
			dateStr = articlePage.substring(articlePageUpper.indexOf("<DATE>") + 6,
					articlePageUpper.indexOf("</DATE>"));
			month = dateStr.substring(0, 3);
			day = dateStr.substring(4, 6);
			time = dateStr.substring(7, 12);

			year = calendar.get(Calendar.YEAR);
		}
		else
		{
			p=articlePageUpper.indexOf("<PUBDATE>");
			if(p>0)
			{
				dateStr = articlePage.substring(articlePageUpper.indexOf("<PUBDATE>") + 9,
						articlePageUpper.indexOf("</PUBDATE>"));

				//Fri, 25 Oct 2013 13:59:27 +0000
				SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
				try
				{
					parsedDate = formatter.parse(dateStr);
				} catch (Exception de) {;}
				month = new SimpleDateFormat("MMM").format(parsedDate);
				day = String.format("%02d", parsedDate.getDate());	//dateStr.substring(4, 6);
				time = String.format("%02d", parsedDate.getHours())+":"+String.format("%02d", parsedDate.getMinutes());

				year = parsedDate.getYear()+1900;	//calendar.get(Calendar.YEAR);
			}
			else
			{
				year = calendar.get(Calendar.YEAR);
				month = String.format("%02d", Calendar.MONTH+1);
				day = String.format("%02d", Calendar.DAY_OF_MONTH);
				time = "12:01";
			}
		}

		try {

			if (cpRSS) {
				//The CPRSS time stamp does not include a year so we have to code some fudges here
				int articleMonth = monthToInt(month.toUpperCase());
				int currentMonth = calendar.MONTH + 1; //+1 compensate for 0 index

				//if the current month is Dec and the article month is Jan then set the year to next year (this could occur if the server times are out of sync with TNO being behind and an article is posted close to NewYears)
				//if the current month is Jan or Feb and the article month is Dec, Nov, Oct, Sept, Aug then set the year to last year
				// else leave it at the current year.

				//currentMonth is Dec and articleMonth is Jan
				if ( (currentMonth == 12) && (articleMonth == 1)) {
					year = calendar.get(Calendar.YEAR) + 1;
				}

				//currentMonth is Jan or Feb and ArticleMonth is Dec, Nov, Oct, Sept, or Aug
				if ( (currentMonth >= 1) && (currentMonth <= 2) && (articleMonth >= 8) &&
						(articleMonth <= 12)) {
					year = calendar.get(Calendar.YEAR) - 1;
				}

			}
			//Fri Oct 25 09:28:00 PDT 2013: dbRSS.GetDate(): java.text.ParseException: Unparseable date: "25/10/113 6:59"
			dateProper = new SimpleDateFormat("dd/MMM/yyyy HH:mm").parse(day + "/" + month + "/" + Integer.toString(year) + " " + time);

		}
		catch (Exception err) {
			skip(); //theFrame.addJLog(eventlog("dbRSS.GetDate(): " + err), true);
		}

		return dateProper;
	}

	private int monthToInt(String monthCode) {

		monthCode = monthCode.toUpperCase();
		if (monthCode.compareTo("JAN") == 0)
			return 1;
		if (monthCode.compareTo("FEB") == 0)
			return 2;
		if (monthCode.compareTo("MAR") == 0)
			return 3;
		if (monthCode.compareTo("APR") == 0)
			return 4;
		if (monthCode.compareTo("MAY") == 0)
			return 5;
		if (monthCode.compareTo("JUN") == 0)
			return 6;
		if (monthCode.compareTo("JUL") == 0)
			return 7;
		if (monthCode.compareTo("AUG") == 0)
			return 8;
		if (monthCode.compareTo("SEP") == 0)
			return 9;
		if (monthCode.compareTo("OCT") == 0)
			return 10;
		if (monthCode.compareTo("NOV") == 0)
			return 11;
		if (monthCode.compareTo("DEC") == 0)
			return 12;
		return 0;
	}
}
