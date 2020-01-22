package ca.bc.gov.tno.jorel2.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class Jorel2DateUtil extends Jorel2Root {

	/**
	 * Takes the RSS formatted published date and converts it into java.util.Date object for storage in the TNO database.
	 * 
	 * @param pubDate RSS string representation of published date.
	 * @return java.util.Date representation of the input date.
	 */
	public static Date getPubTimeAsDate(String pubDate) {
	
		String zone = pubDate.substring(26, pubDate.length());
		String zoneSymbol;
		
		if (zone.length() > 3) { // offset format (e.g. +0000 [iPolitics])
			zoneSymbol = "Z";
		} else {
			zoneSymbol = "z"; // Zone name (e.g. GMT [DailyHive])
		}
		
		String formatPattern = "E, dd LLL yyyy HH:mm:ss " + zoneSymbol;
		
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
}