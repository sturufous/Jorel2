package ca.bc.gov.tno.jorel2.model;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import javax.sql.rowset.serial.SerialException;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.util.Jorel2StringUtil;

public class NewsItemFactory extends Jorel2Root {
	
	public static NewsItemsDao createIpoliticsNewsItem(Rss rss, Rss.Channel.Item item) {
		
		// Pre-process any problematic content
		String content = Jorel2StringUtil.removeHTML(item.getEncoded());
		String title = Jorel2StringUtil.removeHTML(rss.getChannel().getTitle());
		String summary = Jorel2StringUtil.removeHTML(item.getDescription());
		LocalDate itemLocalDate = LocalDate.now();
		
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

		if (item.getEncoded() == null) {
			logger.error("RSS Processing error: null value in rss content field.");
			item.setEncoded("No content");
		}
		
		NewsItemsDao newsItem = new NewsItemsDao(
				null,                   // BigDecimal rsn
				itemDate,               // Date itemDate
				title,                  // String source
				new Date(),             // Date itemTime. TODO not sure how to save the actual time value in the db
				summary,                // String summary
				item.getTitle(),        // String title
				"Internet",             // String type
				true,                   // Boolean frontpagestory
				false,                  // Boolean published
				false,                  // Boolean archived
				"",                     // String archivedTo
				new Date(),             // Date recordCreated
				new Date(),             // Date recordModified
				"",                     // String string1
				"",                     // String string2
				"",                     // String string3
				"",                     // String string4
				"",                     // String string5
				item.getCreator(),      // String string6
				"",                     // String string7
				"",                     // String string8
				"",                     // String string9
				new BigDecimal(0),      // BigDecimal number1
				new BigDecimal(0),      // BigDecimal number1
				null,                   // Date date1
				null,                   // Date date1
				"",                     // String filename
				"",                     // String fullfilepath
				item.getLink(),         // String webpath
				false,                  // Boolean thisjustin
				null,                   // String importedfrom
				new BigDecimal(0),      // BigDecimal expireRule
				false,                  // Boolean commentary
				stringToClob(content),  // Clob text
				null,                   // Blob binary
				"",                     // String contenttype
				false,                  // Boolean binaryloaded
				false,                  // Boolean loadbinary
				false,                  // Boolean externalbinary
				false,                  // Boolean cbraNonqsm
				"rss",                  // String postedby
				false,                  // Boolean onticker
				false,                  // Boolean waptopstory
				false,                  // Boolean alert
				null,                   // BigDecimal autoTone
				false,                  // Boolean categoriesLocked
				false,                  // Boolean coreAlert
				0D,                     // Double commentaryTimeout
				new BigDecimal(0),      // BigDecimal commentaryExpireTime
				null,                   // Clob transcript
				null,                   // String eodCategory
				null,                   // String eodCategoryGroup
				null                    // String eodDate
			);
		
		return newsItem;
	}
	
	public static NewsItemsDao createDailyHiveNewsItem(Rss rss, Rss.Channel.Item item) {
		
		if (item.getDescription() == null) {
			logger.error("RSS Processing error: null value in rss description field.");
			item.setDescription("No content");
		}
		
		// Pre-process any problematic content
		String content = Jorel2StringUtil.removeHTML(item.getDescription());
		String title = Jorel2StringUtil.removeHTML(item.getTitle());
		String summary = Jorel2StringUtil.removeHTML(item.getTitle());
		String source = rss.getChannel().getTitle();
		LocalDate itemLocalDate = LocalDate.now();
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

		NewsItemsDao newsItem = new NewsItemsDao(
				null,                   // BigDecimal rsn
				itemDate,               // Date itemDate
				source,                 // String source
				new Date(),             // Date itemTime. TODO not sure how to save the actual time value in the db
				summary,                // String summary
				item.getTitle(),        // String title
				"Internet",             // String type
				true,                   // Boolean frontpagestory
				false,                  // Boolean published
				false,                  // Boolean archived
				"",                     // String archivedTo
				new Date(),             // Date recordCreated
				new Date(),             // Date recordModified
				"",                     // String string1
				"",                     // String string2
				"",                     // String string3
				"",                     // String string4
				"",                     // String string5
				item.getCreator(),      // String string6
				"",                     // String string7
				"",                     // String string8
				"",                     // String string9
				new BigDecimal(0),      // BigDecimal number1
				new BigDecimal(0),      // BigDecimal number1
				null,                   // Date date1
				null,                   // Date date1
				"",                     // String filename
				"",                     // String fullfilepath
				item.getLink(),         // String webpath
				false,                  // Boolean thisjustin
				null,                   // String importedfrom
				new BigDecimal(0),      // BigDecimal expireRule
				false,                  // Boolean commentary
				stringToClob(content),  // Clob text
				null,                   // Blob binary
				"",                     // String contenttype
				false,                  // Boolean binaryloaded
				false,                  // Boolean loadbinary
				false,                  // Boolean externalbinary
				false,                  // Boolean cbraNonqsm
				"rss",                  // String postedby
				false,                  // Boolean onticker
				false,                  // Boolean waptopstory
				false,                  // Boolean alert
				null,                   // BigDecimal autoTone
				false,                  // Boolean categoriesLocked
				false,                  // Boolean coreAlert
				0D,                     // Double commentaryTimeout
				new BigDecimal(0),      // BigDecimal commentaryExpireTime
				null,                   // Clob transcript
				null,                   // String eodCategory
				null,                   // String eodCategoryGroup
				null                    // String eodDate
			);
		
		return newsItem;
	}

	private static Clob stringToClob(String content) {
		
		Clob contentClob = null;
		
		try {
			contentClob = new javax.sql.rowset.serial.SerialClob(content.toCharArray());
		} catch (SerialException e) {
			logger.error("Translating rss content to clob. Content = " + content, e);
		} catch (SQLException e) {
			logger.error("Translating rss content to clob. Content = " + content, e);
			e.printStackTrace();
		}
		
		return contentClob;
	}
}
