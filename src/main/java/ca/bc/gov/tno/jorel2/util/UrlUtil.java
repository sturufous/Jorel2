package ca.bc.gov.tno.jorel2.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import com.sun.syndication.feed.synd.SyndEntry;

import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
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
	public static String retrieveCPNewsItem(SyndEntry item, String source, Jorel2ServerInstance instance) {
		
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
	 * @param instance An object representing the current server instance
	 * @return The page content.
	 */
	public static String retrievePageContent(String url, Jorel2ServerInstance instance) {
		
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
	
	public static Map<String, String> resolveURL(String shortURL) {
		
		String title = "";
		String longURL = shortURL;
		String response = "";
		HashMap<String, String> results = new HashMap<>();
		try {
			
			// resolve redirects
			HttpURLConnection connection = (HttpURLConnection) new URL(longURL).openConnection();
			connection.setInstanceFollowRedirects(false);
			int count = 0;
			while ((connection.getResponseCode() / 100 == 3) || (count > 20)) {
				count++;
				longURL = connection.getHeaderField("location");
			    connection = (HttpURLConnection) new URL(longURL).openConnection();
			}
			response = Long.toString(connection.getResponseCode());

			// extract page title
			if (connection.getResponseCode() / 100 == 2) {
				String ct = connection.getContentType();
				if (ct.contains("text/html")) {
		            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		            String line;
		            StringBuilder html = new StringBuilder();
		            while ((line = reader.readLine()) != null) {
		            	html.append(line);
		            	html.append("\n"); 
		            }
		            reader.close();
		            
		            // extract the title
		            Matcher matcher = TITLE_TAG.matcher(html);
		            if (matcher.find()) {
		                // replace any occurrences of whitespace and HTML brackets with a space
		                title = matcher.group(1).replaceAll("[\\s\\<>]+", " ").trim();
		            }

				}
			}
			
			if (title.equalsIgnoreCase("")) title = longURL;

		} catch (Exception ex) {
			;
		}

		results.put("longUrl", longURL);
		results.put("title", title);
		results.put("responseCode", response.toString());
		
		return results;
	}
	
	public static String fixURL(String url) {
		url = url.replaceAll("#.*", "");
		url = removeParam(url, "cmp");
		url = removeParam(url, "utm_medium");
		url = removeParam(url, "utm_source");
		url = removeParam(url, "utm_campaign");
		return url;
	}
	
	private static String removeParam(String url, String param) {
		url = url.replaceAll("\\?"+param+"=([^&]*)&", "?");
		url = url.replaceAll("&"+param+"=([^&]*)", "");
		url = url.replaceAll("\\?"+param+"=([^&]*)", "");
		return url;
	}
}