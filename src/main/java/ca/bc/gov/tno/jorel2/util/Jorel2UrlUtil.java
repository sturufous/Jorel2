package ca.bc.gov.tno.jorel2.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import com.sun.syndication.feed.synd.SyndEntry;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;

public class Jorel2UrlUtil extends Jorel2Root {

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
				content = Jorel2StringUtil.GetArticle(articlePage);
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
