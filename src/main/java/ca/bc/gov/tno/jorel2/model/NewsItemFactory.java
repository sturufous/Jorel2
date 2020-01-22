package ca.bc.gov.tno.jorel2.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import javax.sql.rowset.serial.SerialException;
import com.sun.syndication.feed.synd.SyndEntry;
import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.util.Jorel2DateUtil;
import ca.bc.gov.tno.jorel2.util.Jorel2StringUtil;
import ca.bc.gov.tno.jorel2.util.Jorel2UrlUtil;

public class NewsItemFactory extends Jorel2Root {
	
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
	
	public static NewsItemsDao createGenericNewsItem(Rss rss, Rss.Channel.Item item, String source) {
		
		String content = "";
		
		if (item.getEncoded() == null) {
			content = Jorel2StringUtil.removeHTML(item.getDescription());
		} else {
			content = Jorel2StringUtil.removeHTML(item.getEncoded());
		}
		
		content = Jorel2StringUtil.SubstituteEmojis(content);
		
		String title = Jorel2StringUtil.removeHTML(rss.getChannel().getTitle());
		
		// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
		Date itemDate = Jorel2DateUtil.getDateAtMidnight();
		Date itemTime = Jorel2DateUtil.getPubTimeAsDate(item.getPubDate());

		NewsItemsDao newsItem = createNewsItemTemplate();
				
		// Assign content of this Rss.Channel.Item to the NewsItemDao object
		newsItem.setItemDate(itemDate);
		newsItem.setItemTime(itemTime);
		newsItem.setSource(source);
		newsItem.setTitle(item.getTitle());
		newsItem.setString6(item.getCreator());
		newsItem.setWebpath(item.getLink());
		newsItem.setText(stringToClob(content));
		
		return newsItem;
	}
	
	public static NewsItemsDao createCPNewsItem(SyndEntry item, String source) {
		
		String currentUrl = item.getLink(); //rssUtilLinkandUrl.elementAt(i).toString();
		String articlePage = new String("");
		java.util.Date articleDate = new java.util.Date();
		String articleTitle = new String("");
		String content = new String("");

		content = Jorel2UrlUtil.retrieveCPNewsItem(item, source);
		
		// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
		LocalDate itemLocalDate = LocalDate.now();
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant()); 
		articleTitle = item.getTitle();
		
		Boolean containsEmoji = EmojiManager.containsEmoji(content);
		
		// Transform unicode emojis into their HTML encoded representations
		if (containsEmoji) {
			content = EmojiParser.parseToHtmlDecimal(content);
		}

		NewsItemsDao newsItem = new NewsItemsDao(
				null,                   // BigDecimal rsn
				itemDate,               // Date itemDate
				source,                 // String source
				new Date(),             // Date itemTime. TODO not sure how to save the actual time value in the db
				"",                     // String summary
				articleTitle,           // String title
				"CP News",              // String type
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