package ca.bc.gov.tno.jorel2.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import ca.bc.gov.tno.jorel2.Jorel2Root;

public class Jorel2DateUtil extends Jorel2Root {

	public void DateUtil() {
	}
	
	public static java.util.Date convertESTtoPST(java.util.Date date) {

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

	private static java.util.Date convertGMTtoPST(java.util.Date date) {

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
	public
	static java.util.Date GetDate(String articlePage, boolean cpRSS) {
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
	
	private static int monthToInt(String monthCode) {

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
