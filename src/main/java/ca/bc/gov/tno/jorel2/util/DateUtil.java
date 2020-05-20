package ca.bc.gov.tno.jorel2.util;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Provides date manipulation utility methods for Jorel2. Preference is given to the Java 1.8 <code>time</code> package but older packages
 * like java.util.Date are also used when required by external components like jdbc drivers. The need to handle java.util.Date objects
 * can lead to a proliferation of utility methods because we may need to perform similar translations for LocalDate and Date.
 * 
 * @author StuartM
 * @version 0.0.1
 */
public class DateUtil extends Jorel2Root {

	/**
	 * Takes the RSS formatted published date and converts it into java.util.Date object for storage in the TNO database.
	 * 
	 * @param pubDate RSS string representation of published date.
	 * @return java.util.Date representation of the input date.
	 */
	public static Date getPubTimeAsDate(String pubDate) {
	
		int lastSpacePos = pubDate.lastIndexOf(" ");
		String zone = pubDate.substring(lastSpacePos+1, pubDate.length());
		String zoneSymbol;
		
		if (zone.length() > 3) { // offset format (e.g. +0000 [iPolitics])
			zoneSymbol = "Z";
		} else {
			zoneSymbol = "z"; // Zone name (e.g. GMT [DailyHive])
		}
		
		String formatPattern = "E, d LLL yyyy HH:mm:ss " + zoneSymbol;
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern, Locale.UK);
		ZoneOffset offset = ZoneOffset.ofHours(0) ;
		LocalDateTime dateTime = LocalDateTime.parse(pubDate, formatter);
		Date itemTime = Date.from(dateTime.toInstant(offset));

		return itemTime;
	}
	
	/**
	 * Otis expects the time portion of the NEWS_ITEMS.ITEM_DATE to be '00:00:00', otherwise the article will not be displayed. This 
	 * method ensures that this is the case.
	 * 
	 * @return Today's date with a time portion of '00:00:00'.
	 */
	public static Date getDateAtMidnight() {
		
		LocalDate itemLocalDate = LocalDate.now();
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
		
		return itemDate;
	}
	
	/**
	 * Otis expects the time portion of the NEWS_ITEMS.ITEM_DATE to be '00:00:00', otherwise the article will not be displayed. This 
	 * method ensures that this is the case.
	 * 
	 * @param itemTime The date/time on which the date at midnight is based.
	 * @return Today's date with a time portion of '00:00:00'.
	 */
	public static Date getDateAtMidnightByDate(Date itemTime) {
		
		LocalDate itemLocalDate = itemTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
		
		return itemDate;
	}
	
	/**
	 * Formats a date for use in comparisons (or instantiations) of dates in standard TNO format.
	 * 
	 * @return The current date in "Feb 5 2020" format.
	 */
	
	public static String getDateNow() {
		
		// Process the current date using the JDK 1.8 Time API
		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy");
		
		// Format the current date to match values in LAST_FTP_RUN
		String dateMatch = now.format(formatter);
		
		return dateMatch;
	}
	
	/**
	 * Takes a date as a yyyyMMdd string and converts it into a java.util.Date object.
	 * 
	 * @param dateStr The string representation of the date to be converted.
	 * @return The date corresponding to the input string dateStr
	 */
	
	public static Date getDateFromYyyyMmDd(String dateStr) {
		
		// Process the current date using the JDK 1.8 Time API
		Date utilDate = null;
		
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
			utilDate = formatter.parse(dateStr);
		} catch (ParseException e) {
			e.printStackTrace();
		}
 
		return utilDate;
	}
	
	public static String getTimeNow() {
		
		// Process the current date using the JDK 1.8 Time API
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy, hh:mm:ss");
		String dateMatch = now.format(formatter);
		
		return dateMatch;		
	}
	
	/**
	 * Determines whether an event should be run today based on the frequency string provided. The format of this string is
	 * "mtwtfss". The string is indexed by the day of the week as retrieved by LocalDate.getDayOfWeek(). This assigns the 
	 * ordinal value '0' to Monday and '6' to Sunday. If the character at the position corresponding to the current day of
	 * the week is a dash ('-') this method will return <code>false</code>. False is also returned if the <code>frequency</code>
	 * parameter is null or if its length is anything other than 7.
	 * 
	 * @param frequency String indicating on which days of the week a task should be run.
	 * @return True if the task should be run today, false otherwise.
	 */
	public static boolean runnableToday(String frequency) {
		
		boolean runnable = false;
		
		if (frequency != null && frequency.length() == 7) {
			LocalDate dateNow = LocalDate.now();
			DayOfWeek localDay = dateNow.getDayOfWeek();
			int intDay = localDay.ordinal();
			char charDay = frequency.charAt(intDay);
			runnable = !(charDay == '-');
		}
		
		return runnable;
	}
	
	public static String unixTimestampToDate(BigDecimal timestamp) {
		
		Instant lastModified = new Date(timestamp.longValue()).toInstant();
		LocalDateTime localTime = LocalDateTime.ofInstant(lastModified, ZoneId.systemDefault());
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy, hh:mm:ss");
		String dateMatch = localTime.format(formatter);
		
		return dateMatch;
	}
	
	public static Date getDateMinusDays(Date sourceDate, long days) {
		
		LocalDateTime dateTime = LocalDateTime.ofInstant(sourceDate.toInstant(), ZoneId.systemDefault());
		dateTime = dateTime.minusDays(days);
		Date itemTime = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
		
		return itemTime;
	}
	
	public static LocalDate localDateFromDdMmYyyy(String dateString) {
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
		LocalDate localDate = LocalDate.parse(dateString, formatter);
		
		return localDate;
	}
	
	public static LocalDate localDateFromYyyyMmDd(String dateString) {
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		LocalDate localDate = LocalDate.parse(dateString, formatter);
		
		return localDate;
	}
	
	public static LocalDate localDateFromMmDdYYYY(String dateString) {
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMddyyyy");
		LocalDate localDate = LocalDate.parse(dateString, formatter);
		
		return localDate;
	}
	
	public static String localDateToTnoDateFormat(LocalDate date) {
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yy");
		String strDate = date.format(formatter);
		
		return strDate.toUpperCase();
	}
	
	public static Date dateFromLocalDate(LocalDate date) {
		
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}
	
	// TODO Convert this to use java.time
	// Creates a calendar object from a time string
	public static Calendar createTime(String time, String frequency) {
		if (time == null) time="00:00:00";
		if (frequency == null) frequency="-------";

		// if an improper frequency given, then assume all days
		if (frequency.length() != 7) frequency = "mtwtfss";

		int startHour=0;
		int startMinute=0;
		int startSecond=0;
		try {
			StringTokenizer st1 = new StringTokenizer( time, ":" );
			String hour=st1.nextToken();
			startHour = Integer.parseInt(hour);
			String minute=st1.nextToken();
			startMinute = Integer.parseInt(minute);
			if (st1.hasMoreTokens()) {
				String second=st1.nextToken();
				startSecond = Integer.parseInt(second);
			} else {
				startSecond = 0;
			}
		} catch (Exception err) {
	    	logger.trace(StringUtil.getLogMarker(INDENT1) + "Error parsing time ["+time+"] ", err);
			startHour = 0; startMinute = 0; startSecond = 0;
		}

		Calendar cal = Calendar.getInstance();
		cal.setTime(new java.util.Date());
		cal.set(Calendar.HOUR_OF_DAY, startHour);
		cal.set(Calendar.MINUTE, startMinute);
		cal.set(Calendar.SECOND, startSecond);
		cal.set(Calendar.MILLISECOND, 0);

		// if all dashes, then no days scheduled
		if (frequency.equals("-------")) {
			cal.add(Calendar.YEAR, 1); // add a year
		} else {
			int daysToAdd = 0;
			int testday = (7 + cal.get(Calendar.DAY_OF_WEEK) - 2) % 7;
			while (frequency.substring(testday, testday+1).equals("-")) {
				testday = (testday + 1) % 7;
				daysToAdd++;
			}
			cal.add(Calendar.DAY_OF_MONTH, daysToAdd);
		}

		return cal;
	}
}