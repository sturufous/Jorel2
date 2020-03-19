package ca.bc.gov.tno.jorel2.util;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;

import javax.sql.rowset.serial.SerialException;

import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Rss;

/**
 * Provides String processing functionality for use by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 *
 */
public class StringUtil extends Jorel2Root {
	
	/**
	 * This method substitutes various html tags and entities with values that display correctly in news item content in Otis.
	 * removeHTML was inherited from Jorel1.
	 * 
	 * @param in The article text for processing.
	 * @return The sanitized version of the article text.
	 */
	public static String removeHTML(String in) {
		int p, p2;

		String cr = "\r";
		String lf = "\n";

		// replace <P> with |
		p = in.indexOf("<p>");
		while (p >= 0) {
			in = in.substring(0, p) + "|" + in.substring(p + 3);
			p = in.indexOf("<p>");
		}
		p = in.indexOf("<P>");
		while (p >= 0) {
			in = in.substring(0, p) + "|" + in.substring(p + 3);
			p = in.indexOf("<P>");
		}

		in = in.replaceAll("\\<ul\\>", "[ul]");
		in = in.replaceAll("\\</ul\\>", "[/ul]");
		in = in.replaceAll("\\<li\\>", "[li]");
		in = in.replaceAll("\\</li\\>", "[/li]");
		
		// get rid of anything between <> except images
		p = in.indexOf("<");
		while (p >= 0) {
			if (in.indexOf("img") != p+1 ) {
				p2 = in.indexOf(">", p);
				if (p2 >= 0) {
					in = in.substring(0, p) + in.substring(p2 + 1);
					p = in.indexOf("<", p);
				}
				else {
					p = -1;
				}
			} else {
				p = in.indexOf("<", p + 1);
				//p2 = in.indexOf(");
			}
		}

		// get rid of anything between DV.load
		p = in.indexOf("DV.load");
		while (p >= 0) {
			p2 = in.indexOf(");", p);
			if (p2 >= 0) {
				in = in.substring(0, p) + in.substring(p2 + 2);
				p = in.indexOf("DV.load");
			}
			else {
				p = -1;
			}
		}

		in = in.replaceAll("\\[ul\\]","<ul>");
		in = in.replaceAll("\\[/ul\\]","</ul>");
		in = in.replaceAll("\\[li\\]","<li>");
		in = in.replaceAll("\\[/li\\]","</li>");

		in = in.replaceAll("\\r\\r", "|");
		in = in.replaceAll("\\n\\n", "|");
		in = in.replaceAll("\\r\\n", " ");
		in = in.replaceAll("\\r", " ");
		in = in.replaceAll("\\n", " ");

		// fix entities
		in = replaceEntity(in, "``", "\"");
		in = replaceEntity(in, "''", "\"");
		in = replaceEntity(in, "&amp;", "&");
		in = replaceEntity(in, "&rsquo;", "'");
		in = replaceEntity(in, "&lsquo;", "'");
		in = replaceEntity(in, "&rdquo;", "\"");
		in = replaceEntity(in, "&ldquo;", "\"");
		in = replaceEntity(in, "&mdash;", "-");
		in = replaceEntity(in, "&ndash;", "-");
		in = replaceEntity(in, "&bull;", "- ");
		in = replaceEntity(in, "&hellip;", "...");
		in = replaceEntity(in, "&frac12;", " 1/2");
		in = replaceEntity(in, "&eacute;", "e");
		in = replaceEntity(in, "&aacute;", "a");
		in = replaceEntity(in, "&agrave;", "a");
		in = replaceEntity(in, "&egrave;", "e");
		in = replaceEntity(in, "&ccedil;", "c");
		in = replaceEntity(in, "&uuml;", "u");
		in = replaceEntity(in, "&iuml;", "i");
		in = replaceEntity(in, "&icirc;", "i");
		in = replaceEntity(in, "&ecirc;", "e");
		in = replaceEntity(in, "&ntilde;", "n");
		in = replaceEntity(in, "&Eacute;", "E");
		in = replaceEntity(in, "&Aacute;", "A");
		in = replaceEntity(in, "&Agrave;", "A");
		in = replaceEntity(in, "&Egrave;", "E");
		in = replaceEntity(in, "&Ccedil;", "C");
		in = replaceEntity(in, "&Uuml;", "U");
		in = replaceEntity(in, "&Iuml;", "I");
		in = replaceEntity(in, "&Icirc;", "I");
		in = replaceEntity(in, "&Ecirc;", "E");
		in = replaceEntity(in, "&Ntilde;", "N");
		in = replaceEntity(in, "&#8216;", "'");
		in = replaceEntity(in, "&#8217;", "'");
		in = replaceEntity(in, "&#8220;", "\"");
		in = replaceEntity(in, "&#8221;", "\"");
		in = replaceEntity(in, "&#8230;", "...");
		in = replaceEntity(in, "&#8211;", "-");
		in = replaceEntity(in, "&#8212;", "-");

		in = in.replaceAll("\u2018", "'"); //8216
		in = in.replaceAll("\u2019", "'"); //8217
		in = in.replaceAll("\u201C", "\""); //8220
		in = in.replaceAll("\u201D", "\""); //8221
		in = in.replaceAll("\u201D", "\""); //8221
		in = in.replaceAll("\u2026", "..."); //8230
		in = in.replaceAll("\u2013", "-"); //8211
		in = in.replaceAll("\u2014", "-"); //8212
		in = in.replaceAll("\u0009", " ");

		// Get rid of cr+lf after |
		p = in.indexOf("|" + cr + lf);
		while (p >= 0) {
			in = in.substring(0, p) + "|" + in.substring(p + 3);
			p = in.indexOf("|" + cr + lf);
		}

		// replace multiple |
		in = in.replaceAll("\\|+", "|");

		// get rid of junk at start
		in = in.trim();
		boolean cont = true;
		while (cont) {
			cont = false;
			if (in.startsWith(cr)) {
				in = in.substring(1);
				in = in.trim();
				cont = true;
			}
			if (in.startsWith(lf)) {
				in = in.substring(1);
				in = in.trim();
				cont = true;
			}
			if (in.startsWith("|")) {
				in = in.substring(1);
				in = in.trim();
				cont = true;
			}
		}

		return in;
	}
	
	/**
	 * Used by removeHTML() to substitute a unicode character or HTML entity with a value that will display properly in Otis.
	 * replaceEntity() was inherited from Jorel1.
	 * 
	 * @param in The input string.
	 * @param entity The entity to search for in the <code>in</code> parameter.
	 * @param replace The string that is to be inserted in place of <code>entity</code>
	 * @return The sanitized string.
	 */
	private static String replaceEntity(String in, String entity, String replace) {
		int p, length;

		length = entity.length();
		p = in.indexOf(entity);
		while (p >= 0) {
			in = in.substring(0, p) + replace + in.substring(p + length);
			p = in.indexOf(entity);
		}

		return in;
	}
	
	/**
	 * Strips tags from CP News articles. getArticle() was inherited from Jorel1.
	 * 
	 * @param articlePage The article content.
	 * @return The sanitized article content.
	 */
	public static String getArticle(String articlePage) {
		
		String articlePageUpper = articlePage.toUpperCase();
		String articleStr = "";
		int p=articlePageUpper.indexOf("<ARTICLE>");
		if(p>0)
		{
			articleStr = articlePage.substring(articlePageUpper.indexOf("<ARTICLE>") + 9, articlePageUpper.indexOf("</ARTICLE>"));
		}
		else
		{
			p=articlePageUpper.indexOf("<CONTENT:ENCODED>");
			if(p>0)
			{
				articleStr = articlePage.substring(articlePageUpper.indexOf("<CONTENT:ENCODED>") + 17, articlePageUpper.indexOf("</CONTENT:ENCODED>"));
			}
		}
		articleStr = StringUtil.removeHTML(articleStr);
		return articleStr;
	}

	/**
	 * Replaces Unicode emoji characters with their decimal representations which display correctly in Otis.
	 * 
	 * @param content The article content.
	 * @return The content with Unicode characters substituted with their decimal representations.
	 */
	public static String SubstituteEmojis(String content) {
		
		Boolean containsEmoji = EmojiManager.containsEmoji(content);
		
		// Transform unicode emojis into their HTML encoded representations
		if (containsEmoji) {
			content = EmojiParser.parseToHtmlDecimal(content);
		}
	
		return content;
	}
	
	/**
	 * Generate a marker "*****", "+++++" or "!!!!!" to prepend to the log file message. This indicates how many threads are running concurrently,
	 * one, two or three respectively. 
	 * 
	 * @param indent The depth the message should be indented following the marker.
	 * @return The marker followed by the indent.
	 */
	public static String getLogMarker(String indent) {
		
		if (threadStartTimestamps.size() == 1) {
			return "***** " + indent;
		} 
		else 
		if (threadStartTimestamps.size() == 2) {
			return "+++++ " + indent;
		}
		else
		if (threadStartTimestamps.size() == 3) {
			return "!!!!! " + indent;
		}
		else {
			return "^^^^^ ";
		}
	}
	
	/**
	 * Generates a thread number to append to a log file message, e.g. " [0]". This identifies which thread performed a particular task,
	 * such as performing RSS processing or adding articles to the NEWS_ITEMS table...useful if multiple threads are running concurrently.
	 * 
	 * @return A string consisting of a space, left square bracket, the thread number and right square bracket.
	 */
	public static String getThreadNumber() {
		
		String threadName = Thread.currentThread().getName();
		String number = threadName.substring(13, 14);
		
		return " [" + number + "]";
	}
	
	/**
	 * Truncate the page content received by a pagewatcher to the section between the start and end strings and
	 * process html tags.
	 * 
	 * @param s The page content
	 * @param start Ignore page content before this string
	 * @param end Ignore page content after this string
	 * @return The processed string
	 */
	public static String fix(String s, String start, String end) {

	    int p;

	    // if start tag is set, trim to the first occurence of it
	    if (start!=null) {
	      if (!start.equals("")) {
	        p = s.indexOf(start);
	        if (p >= 0) {
	          s = s.substring(p + start.length());
	        }
	      }
	    }

	    // if end tag is set, go to the last occurence of it
	    if (end!=null) {
	      if (!end.equals("")) {
	        p = s.lastIndexOf(end);
	        if (p >= 0) {
	          s = s.substring(0, p);
	        }
	      }
	    }

	    s = s.replaceAll("(?i)\\<p\\>","\n");              // replace <p> tags with new lines
	    s = s.replaceAll("(?i)\\<br\\>","\n");             // replace <br> tags with new lines
	    s = s.replaceAll("(?i)\\<div[\\S\\s]*?\\>","\n");  // replace <div> tags with new lines
	    s = s.replaceAll("(?i)\\<td[\\S\\s]*?\\>","\n");   // replace <td> tags with new lines
	    s = s.replace('\r','\n');
	    s = s.replaceAll("[^\\p{ASCII}]", "");             // remove non-Ascii text

	    s = s.replaceAll("(?i)\\<script[\\S\\s]*?\\<\\/script\\>","");  // strip anything between script tags
	    s = s.replaceAll("\\<[\\S\\s]*?\\>","");          // strip html tags

	    s = s.replaceAll("[ \t]+"," ");                   // replace multiple spaces with one space
	    s = s.replaceAll("\n[ \t]","\n");                 // replace new line+space with new line
	    s = s.replaceAll("\n+","\n");                     // replace multiple new lines with one new line

	    return s;
	}
	
	/**
	 * Calculates the difference between two web pages as a subroutine of the pagewatcher process.
	 * 
	 * @param a Web page 1 content
	 * @param b Web page 2 content
	 * @return Summary of the differences between web page 1 and web page 2
	 */
    public static String diff(String a, String b) {
	  
        int MAX = 10000;
        String[] x = new String[MAX];   // lines in first string
        String[] y = new String[MAX];   // lines in second string
        int M = 0;                      // number of lines of first string
        int N = 0;                      // number of lines of second string
        StringBuffer add=new StringBuffer("");
        StringBuffer del=new StringBuffer("");

        x = a.split("\\s*\n\\s*");
        y = b.split("\\s*\n\\s*");

        M = x.length;
        N = y.length;

        int[][] opt = new int[M+1][N+1];

        // compute length of LCS and all subproblems via dynamic programming
        for (int i = M-1; i >= 0; i--) {
          for (int j = N-1; j >= 0; j--) {
            if (x[i].trim().equalsIgnoreCase(y[j].trim()))
              opt[i][j] = opt[i+1][j+1] + 1;
            else
              opt[i][j] = Math.max(opt[i+1][j], opt[i][j+1]);
          }
        }

        // recover LCS itself and print out non-matching lines
        int i = 0, j = 0;
        while(i < M && j < N) {
          if (x[i].trim().equalsIgnoreCase(y[j].trim())) {
            i++;
            j++;
          } else if (opt[i+1][j] >= opt[i][j+1]) {
            del.append("Line removed: " + x[i] + "<br>\n<br>\n");
            i++;
          } else {
            add.append("Line altered: " + y[j] + "<br>\n<br>\n");
            j++;
          }
        }

        // dump out one remainder of one string if the other is exhausted
        while(i < M || j < N) {
          if (i == M) {
            add.append("Line altered: " + y[j] + "<br>\n<br>\n");
            j++;
          } else if (j == N) {
            del.append("Line removed: " + x[i] + "<br>\n<br>\n");
            i++;
          }
        }

        if (add.toString().equals("")) {
          return del.toString();
        } else {
          return add.toString();
        }
    }
    
	/**
	 * Converts the article content from a String to the Clob format used by NEWS_ITEMS.TEXT.
	 * 
	 * @param content The String representation of the news item content.
	 * @return Clob version of the content parameter.
	 */
	public static Clob stringToClob(String content) {
		
		Clob contentClob = null;
		
		try {
			contentClob = new javax.sql.rowset.serial.SerialClob(content.toCharArray());
		} catch (SQLException e) {
			logger.error("Translating rss content to clob. Content = " + content, e);
		} 
		
		return contentClob;
	}
	
	/**
	 * Converts the clob content to a string.
	 * 
	 * @param content The Clob to process.
	 * @return String version of the Clob.
	 */
	public static String clobToString(Clob content) {

		StringBuffer buffer = new StringBuffer();
		
		try {
			Reader r = content.getCharacterStream();
			int j = 0;
			int ch;
			while ((ch = r.read()) != -1) {
			   buffer.append("" + (char) ch);
			}
		} catch (IOException | SQLException e) {
			logger.error("Translating Clob content to String.", e);
		}
		
		return buffer.toString();
	}
}
