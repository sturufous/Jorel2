package ca.bc.gov.tno.jorel2.model;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.SQLException;

import javax.sql.rowset.serial.SerialException;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;

public class NewsItemFactory extends Jorel2Root {
	
	public static NewsItemsDao createIpoliticsNewsItem(Rss rss, Rss.Channel.Item item) {
		
		NewsItemsDao newsItem = new NewsItemsDao(
				null,                                   // BigDecimal rsn
				new Date(2020, 6, 10),                  // Date itemDate
				rss.getChannel().getTitle(),            // String source
				new Date(2020, 6, 10),                  // Date itemTime
				item.getDescription(),                  // String summary
				item.getTitle(),                        // String title
				"News",                                 // String type
				false,                                  // Boolean frontpagestory
				false,                                  // Boolean published
				false,                                  // Boolean archived
				"Archived to",                          // String archivedTo
				new Date(2020, 6, 10),                  // Date recordCreated
				new Date(2020, 6, 10),                  // Date recordModified
				"",                                     // String string1
				"",                                     // String string2
				"",                                     // String string3
				"",                                     // String string4
				"",                                     // String string5
				"",                                     // String string6
				"",                                     // String string7
				"",                                     // String string8
				"",                                     // String string9
				new BigDecimal(10),                     // BigDecimal number1
				new BigDecimal(10),                     // BigDecimal number1
				new Date(2020, 6, 10),                  // Date date1
				new Date(2020, 6, 10),                  // Date date1
				"Filename",                             // String filename
				"Fullpath",                             // String fullfilepath
				item.getLink(),                         // String webpath
				false,                                  // Boolean thisjustin
				rss.getChannel().getTitle(),            // String importedfrom
				new BigDecimal(10),                     // BigDecimal expireRule
				false,                                  // Boolean commentary
				stringToClob("This is the content."),   // Clob text
				null,                                   // Blob binary
				"application/rss+xml",                  // String contenttype
				false,                                  // Boolean binaryloaded
				false,                                  // Boolean loadbinary
				false,                                  // Boolean externalbinary
				false,                                  // Boolean cbraNonqsm
				"Posted by",                            // String postedby
				false,                                  // Boolean onticker
				false,                                  // Boolean waptopstory
				false,                                  // Boolean alert
				new BigDecimal(10),                     // BigDecimal autoTone
				false,                                  // Boolean categoriesLocked
				false,                                  // Boolean coreAlert
				1000D,                                  // Double commentaryTimeout
				new BigDecimal(10),                     // BigDecimal commentaryExpireTime
				null,                                   // Clob transcript
				"eodCategory",                          // String eodCategory
				"eodCategoryGroup",                     // String eodCategoryGroup
				"eodDate"                               // String eodDate
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
