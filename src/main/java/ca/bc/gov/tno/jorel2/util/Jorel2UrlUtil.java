package ca.bc.gov.tno.jorel2.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import com.sun.syndication.feed.synd.SyndEntry;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;

/**
 * Provides utilities that process URL based content.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public class Jorel2UrlUtil extends Jorel2Root {

	/**
	 * Takes the link contained in the SyndEntry object <code>item</code>, retrieves the article text (pretending to be a web browser)
	 * a line at a time, and builds the article text for display in Otis.
	 * 
	 * @param item The current news item.
	 * @param source The source of the current news feed (e.g. 'CP News')
	 * @return The complete article text retrieved from the item's link.
	 */
	public static String retrieveCPNewsItem(SyndEntry item, String source) {
		
		String currentUrl = item.getLink();
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
				content = Jorel2StringUtil.getArticle(articlePage);
			} else {
				content = "No Content";
			}
		}
		catch (Exception err) {
			logger.error("Retrieving article at link: " + currentUrl, err);
		}
		
		return content;
	}
}
