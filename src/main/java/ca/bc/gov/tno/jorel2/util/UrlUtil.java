package ca.bc.gov.tno.jorel2.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

import com.sun.syndication.feed.synd.SyndEntry;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
import ca.bc.gov.tno.jorel2.Jorel2Root;

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
	 * @param source The source of the current news feed (e.g. 'CP News').
	 * @param instance Used to track timeouts when retrieving page content.
	 * @return The complete article text retrieved from the item's link.
	 */
	public static String retrieveCPNewsItem(SyndEntry item, String source, Jorel2Instance instance) {
		
		String currentUrl = item.getLink();
		String articlePage = new String("");
		String content = new String("");
		
		articlePage = retrievePageContent(currentUrl, instance);

		if (articlePage != null && articlePage.length() > 0) {
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
	public static String retrievePageContent(String url, Jorel2Instance instance) {
		
		String inputLine;
		String articlePage = new String("");

		// Get the article content one line at a time
		try {
			HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
			urlConnection.setUseCaches(false);
			urlConnection.setConnectTimeout(URL_CONNECTION_TIMEOUT);
			urlConnection.setReadTimeout(URL_READ_TIMEOUT);
			urlConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
			BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			
			while ((inputLine = in.readLine()) != null) {
				articlePage += inputLine + " ";
			}
			
			in.close();
			urlConnection.disconnect();
		} catch (SocketTimeoutException te) {
			instance.addHttpFailure("Timeout at: " + url);			
			decoratedError(INDENT0, "Timeout at: " + url, te);
		} catch (Exception e) {
			logger.error("Error retrieving page at: {}", url, e);
			articlePage = null;
		}

		return articlePage;
	}
}
