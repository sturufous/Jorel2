package ca.bc.gov.tno.jorel2.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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

public class UrlUtil extends Jorel2Root {

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
		String articleTitle = new String("");
		String content = new String("");
		
		articlePage = retrievePageContent(currentUrl);

		if (articlePage.length() > 0) {
			content = StringUtil.getArticle(articlePage);
		} else {
			content = "No Content";
		}
		
		return content;
	}
	
	/**
	 * Retrieves page content from the url indicated. The method's output contains all the bytes returned from the url including
	 * html tags, style sheets and scripts.
	 * 
	 * @param url The url from which content should be retrieved.
	 * @return The page content.
	 */
	public static String retrievePageContent(String url) {
		
		String inputLine;
		String articlePage = new String("");

		// Get the article content one line at a time
		try {
			HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
			urlConnection.setUseCaches(false);
			urlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
			BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			
			while ((inputLine = in.readLine()) != null) {
				articlePage += inputLine + " ";
			}
			
			in.close();
		} catch (IOException e) {
			logger.error("Error retrieving page at: {}", url, e);
		}

		return articlePage;
	}
}
