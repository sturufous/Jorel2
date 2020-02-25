package ca.bc.gov.tno.jorel2.model;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Date;
import javax.sql.rowset.serial.SerialException;
import com.sun.syndication.feed.synd.SyndEntry;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.StringUtil;
import ca.bc.gov.tno.jorel2.util.UrlUtil;

/**
 * Methods in this class create and instantiate new instances of the Hibernate NewsItemsDao object using values in their corresponding
 * <code>item</code> parameters. The new object is passed back to the caller to be persisted.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public class NewsItemFactory extends Jorel2Root {
	
	/**
	 * Creates a new instance of NewsItemDao containing the default values. A subset of these values are overwritten with data
	 * retrieved from the news feed by the corresponding createXXXNewsItem() method.
	 * 
	 * @return The new NewsItemDao template object. This is modified by the caller.
	 */
	
	private static NewsItemsDao createNewsItemTemplate() {
		
		NewsItemsDao newsItem = new NewsItemsDao(
				null,                   // BigDecimal rsn
				new Date(),             // Date itemDate
				"",                     // String source
				new Date(),             // Date itemTime
				"",                     // String summary
				"",                     // String title
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
				"",                     // String string6
				"",                     // String string7
				"",                     // String string8
				"",                     // String string9
				new BigDecimal(0),      // BigDecimal number1
				new BigDecimal(0),      // BigDecimal number1
				null,                   // Date date1
				null,                   // Date date1
				"",                     // String filename
				"",                     // String fullfilepath
				"",                     // String webpath
				false,                  // Boolean thisjustin
				null,                   // String importedfrom
				new BigDecimal(0),      // BigDecimal expireRule
				false,                  // Boolean commentary
				stringToClob(""),       // Clob text
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
	
	/**
	 * Creates and populates an instance of <code>NewsItemDao</code> containing data from an XML based RSS feed item.
	 * 
	 * @param item The news item to process.
	 * @param source The source of the news item (e.g. iPolitics, DailyHive)
	 * @return A NewsItemsDao object instantiated with the data contained in <code>item</code>
	 */
	public static NewsItemsDao createXmlNewsItem(Rss.Channel.Item item, String source) {
		
		String content = "";
		String summary = "";
		String title = StringUtil.removeHTML(item.getTitle());
		
		// Some feeds store the item content in item.encoded, others in item.description.
		if (item.getEncoded() == null) {
			content = StringUtil.removeHTML(item.getDescription());
			summary = "";
		} else {
			content = StringUtil.removeHTML(item.getEncoded());
			summary = StringUtil.removeHTML(item.getDescription());
		}
		
		content = StringUtil.SubstituteEmojis(content);
		
		// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
		Date itemDate = DateUtil.getDateAtMidnight();
		Date itemTime = DateUtil.getPubTimeAsDate(item.getPubDate());

		NewsItemsDao newsItem = createNewsItemTemplate();
				
		// Assign content of this Rss.Channel.Item to the NewsItemDao object
		newsItem.setItemDate(itemDate);
		newsItem.setItemTime(itemTime);
		newsItem.setSource(source);
		newsItem.setTitle(title);
		newsItem.setString6(item.getCreator());
		newsItem.setWebpath(item.getLink());
		newsItem.setText(stringToClob(content));
		newsItem.setSummary(summary);
		
		// Saves converting back from Clob to string
		newsItem.content = content;
		
		return newsItem;
	}
	
	/**
	 * Creates and populates an instance of <code>NewsItemDao</code> containing data from a Non-XML based RSS feed item.
	 * 
	 * @param item The news item to process.
	 * @param source The source of the news item (e.g. CP News)
	 * @return A NewsItemsDao object instantiated with the data contained in <code>item</code>
	 */
	public static NewsItemsDao createCPNewsItem(SyndEntry item, String source) {
		
		String content;
		NewsItemsDao newsItem = createNewsItemTemplate();
		
		try {
			content = UrlUtil.retrieveCPNewsItem(item, source);
			
			// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
			Date itemDate = DateUtil.getDateAtMidnight();
			Date itemTime = item.getPublishedDate();
			
			content = StringUtil.SubstituteEmojis(content);
	
	
			// Assign content of this SyndEntry to the NewsItemDao object
			newsItem.setItemDate(itemDate);
			newsItem.setItemTime(new Date());
			newsItem.setType(source);
			newsItem.setSource(source);
			newsItem.setTitle(item.getTitle());
			newsItem.setWebpath(item.getLink());
			newsItem.setText(stringToClob(content));
			
			// Saves converting back from Clob to string
			newsItem.content = content;
		}
		catch (Exception e) {
			logger.error("Retrieving individual CP News item: " + item.getUri(), e);
			newsItem = null;
		}
		
		return newsItem;
	}

	/**
	 * Converts the article content from a String to the Clob format used by NEWS_ITEMS.TEXT.
	 * 
	 * @param content The String representation of the news item content.
	 * @return Clob version of the content parameter.
	 */
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