package ca.bc.gov.tno.jorel2.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
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

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.util.Jorel2DateUtil;
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
		String source = rss.getChannel().getTitle().replaceAll("\\s+","");
		LocalDate itemLocalDate = LocalDate.now();
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
		
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
				summary,                // String summary
				title,                  // String title
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
	
	public static NewsItemsDao createCPNewsItem(SyndEntry item) {
		
		String currentUrl = item.getLink(); //rssUtilLinkandUrl.elementAt(i).toString();
		String articlePage = new String("");
		java.util.Date articleDate = new java.util.Date();
		String articleTitle = new String("");
		String content = new String("");

		try {
			URLConnection itemUrlConnection = new URL(currentUrl).openConnection();
			itemUrlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
			BufferedReader in = new BufferedReader(new InputStreamReader(itemUrlConnection.getInputStream()));
			String inputLine;

			// Get the article content one line at a time
			while ( (inputLine = in.readLine()) != null) {
				articlePage += inputLine + " ";
			}
			in.close();

			if (articlePage.length() > 0) {
				articleDate = Jorel2DateUtil.GetDate(articlePage, true);
				articleDate = Jorel2DateUtil.convertESTtoPST(articleDate);
				articleTitle = Jorel2StringUtil.GetTitle(articlePage);
				content = Jorel2StringUtil.GetArticle(articlePage);
			} else {
				content = "No Content";
			}
		}
		catch (Exception err) {
			logger.error("Retrieving article at link: " + currentUrl, err);
		}
		
		// Pre-process any problematic content
		String source = "CP News";
		
		// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
		LocalDate itemLocalDate = LocalDate.now();
		Date itemDate = Date.from(itemLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant()); 
		
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
