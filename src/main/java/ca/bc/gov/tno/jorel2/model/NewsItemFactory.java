package ca.bc.gov.tno.jorel2.model;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.sun.syndication.feed.synd.SyndEntry;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Nitf;
import ca.bc.gov.tno.jorel2.jaxb.Rss;
import ca.bc.gov.tno.jorel2.util.DateUtil;
import ca.bc.gov.tno.jorel2.util.DbUtil;
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
	
	public static NewsItemsDao createNewsItemTemplate() {
		
		NewsItemsDao newsItem = new NewsItemsDao(
				null,                        // BigDecimal rsn
				new Date(),                  // Date itemDate
				"",                          // String source
				new Date(),                  // Date itemTime
				"",                          // String summary
				"",                          // String title
				"Internet",                  // String type
				false,                       // Boolean frontpagestory
				false,                       // Boolean published
				false,                       // Boolean archived
				"",                          // String archivedTo
				new Date(),                  // Date recordCreated
				new Date(),                  // Date recordModified
				"",                          // String string1
				"",                          // String string2
				"",                          // String string3
				"",                          // String string4
				"",                          // String string5
				"",                          // String string6
				"",                          // String string7
				"",                          // String string8
				"",                          // String string9
				new BigDecimal(0),           // BigDecimal number1
				new BigDecimal(0),           // BigDecimal number1
				null,                        // Date date1
				null,                        // Date date1
				"",                          // String filename
				"",                          // String fullfilepath
				"",                          // String webpath
				false,                       // Boolean thisjustin
				null,                        // String importedfrom
				new BigDecimal(0),           // BigDecimal expireRule
				false,                       // Boolean commentary
				StringUtil.stringToClob(""), // Clob text
				null,                        // Blob binary
				"",                          // String contenttype
				false,                       // Boolean binaryloaded
				false,                       // Boolean loadbinary
				false,                       // Boolean externalbinary
				false,                       // Boolean cbraNonqsm
				"rss",                       // String postedby
				false,                       // Boolean onticker
				false,                       // Boolean waptopstory
				false,                       // Boolean alert
				null,                        // BigDecimal autoTone
				false,                       // Boolean categoriesLocked
				false,                       // Boolean coreAlert
				0D,                          // Double commentaryTimeout
				new BigDecimal(0),           // BigDecimal commentaryExpireTime
				null,                        // Clob transcript
				null,                        // String eodCategory
				null,                        // String eodCategoryGroup
				null                         // String eodDate
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
		Date itemTime = DateUtil.getPubTimeAsDate(item.getPubDate());
		Date itemDate = DateUtil.getDateAtMidnightByDate(itemTime);

		NewsItemsDao newsItem = createNewsItemTemplate();
				
		// Assign content of this Rss.Channel.Item to the NewsItemDao object
		newsItem.setItemDate(itemDate);
		newsItem.setItemTime(itemTime);
		newsItem.setSource(source);
		newsItem.setTitle(title);
		newsItem.setString6(item.getCreator());
		newsItem.setWebpath(item.getLink());
		newsItem.setText(StringUtil.stringToClob(content));
		newsItem.setSummary(summary);
		
		// Saves converting back from Clob to string if you need the article content later
		newsItem.content = content;
		
		return newsItem;
	}
	
	/**
	 * Creates and populates an instance of <code>NewsItemDao</code> containing data from an XML based newspaper import file.
	 * 
	 * @param item The news item to process.
	 * @param source The source of the news item (e.g. The Globe and Mail)
	 * @return A NewsItemsDao object instantiated with the data contained in <code>item</code>
	 */
	public static NewsItemsDao createXmlNewsItem(Nitf item, String source) {
		
		String content = "";
		String summary = "";
		String title = StringUtil.removeHTML(item.getBody().getBodyHead().getHedline().getHl1());
		
		content = StringUtil.removeHTML(item.getBody().getBodyContent());
		summary = item.getBody().getBodyHead().getHedline().getHl2();
		
		content = StringUtil.SubstituteEmojis(content);
		
		// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
		Date pubDate = DateUtil.getDateFromYyyyMmDd(item.getHead().getPubdata().getDatePublication().toString());

		NewsItemsDao newsItem = createNewsItemTemplate();
				
		// Assign content of this Rss.Channel.Item to the NewsItemDao object
		newsItem.setItemDate(pubDate);
		newsItem.setItemTime(pubDate);
		newsItem.setSource(source);
		newsItem.setTitle(title);
		newsItem.setString6(item.getBody().getBodyHead().getByline());
		newsItem.setWebpath(""); // item.getLink());
		newsItem.setText(StringUtil.stringToClob(content));
		newsItem.setSummary(summary);
		newsItem.setType("Newspaper");
		
		// Saves converting back from Clob to string if you need the article content later
		newsItem.content = content;
		
		return newsItem;
	}

	/**
	 * Creates and populates an instance of <code>NewsItemDao</code> containing data from a Non-XML based RSS feed item.
	 * 
	 * @param item The news item to process.
	 * @param source The source of the news item (e.g. CP News)
	 * @param instance Used to track timeout events when retrieving a news item.
	 * @return A NewsItemsDao object instantiated with the data contained in <code>item</code>
	 */
	public static NewsItemsDao createCPNewsItem(SyndEntry item, String source, Jorel2ServerInstance instance) {
		
		String content;
		NewsItemsDao newsItem = createNewsItemTemplate();
		
		try {
			content = UrlUtil.retrieveCPNewsItem(item, source, instance);
			if (content != null) {
			
				// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
				Date itemTime = item.getPublishedDate();
				if (itemTime == null) {
					itemTime = new Date();
				}
				Date itemDate = DateUtil.getDateAtMidnightByDate(itemTime);
				
				content = StringUtil.SubstituteEmojis(content);
		
				// Assign content of this SyndEntry to the NewsItemDao object
				newsItem.setItemDate(itemDate);
				newsItem.setItemTime(new Date());
				newsItem.setType(source);
				newsItem.setSource(source);
				newsItem.setTitle(item.getTitle());
				newsItem.setWebpath(item.getLink());
				newsItem.setText(StringUtil.stringToClob(content));
				
				// Saves converting back from Clob to string if you need the article content later
				newsItem.content = content;
			}
		}
		catch (Exception e) {
			logger.error("Retrieving individual CP News item: " + item.getUri(), e);
			newsItem = null;
		}
		
		return newsItem;
	}
	
	/**
	 * Creates and populates an instance of <code>NewsItemDao</code> containing data from a Non-XML based RSS feed item.
	 * 
	 * @param item The news item to process.
	 * @param source The source of the news item (e.g. CP News)
	 * @param instance Used to track timeout events when retrieving a news item.
	 * @return A NewsItemsDao object instantiated with the data contained in <code>item</code>
	 */
	public static NewsItemsDao createChannelNewsItem(SyndEntry item, String source, String authorHandle, String author, ChannelsDao channel, Jorel2ServerInstance instance) {
		
		String content;
		NewsItemsDao newsItem = createNewsItemTemplate();
		
		// Ensure time portion of Date is 00:00:00. Article won't show in Otis otherwise.
		Date itemTime = item.getPublishedDate();
		String description = StringUtil.nullToEmptyString(item.getDescription().getValue());
		String autoTags = StringUtil.nullToEmptyString(channel.getAutoTags());
		String socialType = "twitter";
		
		if (itemTime == null) {
			itemTime = new Date();
		}
		Date itemDate = DateUtil.getDateAtMidnightByDate(itemTime);
		
		String uri = StringUtil.nullToEmptyString(item.getUri());
		String link = StringUtil.nullToEmptyString(item.getLink());
		if(uri.startsWith("http://"))
			link = uri;
		
		if (!autoTags.equals("")) {
			description = description + "\n\n" + autoTags;
		}
		
		String markupDescription = description;
		if (socialType.equalsIgnoreCase("twitter")) {
			markupDescription = description.replaceAll("(?i)" + VALID_URL_PATTERN_STRING, "$2<a href=\"$3\">$3</a>");
		}
					
		// Assign content of this SyndEntry to the NewsItemDao object
		newsItem.setItemDate(itemDate);
		newsItem.setItemTime(new Date());
		newsItem.setSource(source);
		newsItem.setString1(socialType); // Only twitter supported for now
		newsItem.setString5(channel.getSeries());
		newsItem.setString6(author);
		newsItem.setString2(authorHandle);
		newsItem.setTitle(StringUtil.nullToEmptyString(item.getTitle()));
		newsItem.setType("Social Media");
		//news.setNumber1(Math.round(klout_score));
		//news.setNumber2(reach);
		newsItem.setDate2(new Date()); // goes into the Date2 field
		newsItem.setWebpath(link);
		newsItem.setTranscript(StringUtil.stringToClob(""));
		newsItem.setAlert(channel.getAutoAlert());
		newsItem.setAutoTone(BigDecimal.valueOf(0L));
		newsItem.setPublished(channel.getAutoPublish());
		newsItem.setText(StringUtil.stringToClob(markupDescription));
		
		// Saves converting back from Clob to string if you need the article content later
		newsItem.content = markupDescription;
		
		return newsItem;
	}
	
	public static NewsItemsDao createNewsPaperTemplate(String currentFilePath, String sep) {
		
		NewsItemsDao item = NewsItemFactory.createNewsItemTemplate();
		item.setPostedby("");
		item.setType("Newspaper");
		item.setImportedfrom(currentFilePath.substring(currentFilePath.lastIndexOf(sep) + 1).toUpperCase());

		return item;
	}
	
	public static SourcePaperImagesDao createSourcePaperImage(BigDecimal sourceRsn, String wwwTarget, String fileName, long width, long height) {
		
		SourcePaperImagesDao spiRecord = new SourcePaperImagesDao();
		
		spiRecord.setSourceRsn(sourceRsn);
		spiRecord.setBinaryPath(wwwTarget);
		spiRecord.setFileName(fileName);
		spiRecord.setWidth(BigDecimal.valueOf(width));
		spiRecord.setHeight(BigDecimal.valueOf(height));
		spiRecord.setPaperDate(DateUtil.getDateAtMidnight());
		
		return spiRecord;
	}
}