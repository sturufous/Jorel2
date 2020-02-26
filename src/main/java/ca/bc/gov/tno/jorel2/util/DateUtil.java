package ca.bc.gov.tno.jorel2.util;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Provides date manipulation utility methods for Jorel2. Preference is given to the Java 1.8 <code>time</code> package but older packages
 * like java.util.Date are also used when required by external components like jdbc drivers.
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
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);
		ZoneOffset offset = ZoneOffset.ofHours(-8) ;
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
}