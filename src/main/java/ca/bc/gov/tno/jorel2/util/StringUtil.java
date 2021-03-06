package ca.bc.gov.tno.jorel2.util;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.persistence.ParameterMode;
import javax.persistence.StoredProcedureQuery;

import org.hibernate.Session;
import org.hibernate.procedure.ProcedureOutputs;

import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;

import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Provides String processing functionality for use by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 *
 */
public class StringUtil extends Jorel2Root {
	
	static final char cr = '\015';
	static final char lf = '\012';
	
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
		
		if (activeThreads.size() == 1) {
			return "***** " + indent;
		} 
		else 
		if (activeThreads.size() == 2) {
			return "+++++ " + indent;
		}
		else
		if (activeThreads.size() == 3) {
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
		String number = threadName.substring(13, threadName.length());
		
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
			int ch;
			while ((ch = r.read()) != -1) {
			   buffer.append("" + (char) ch);
			}
		} catch (IOException | SQLException e) {
			logger.error("Translating Clob content to String.", e);
		}
		
		return buffer.toString();
	}
	
	// Methods from the Aktiv package
	
	public static String removeCRLF(String s) {
		StringBuilder sb = new StringBuilder(s);
		int i = 0;
		while (i < sb.length()) {
			if (sb.charAt(i) == cr) {
				sb.deleteCharAt(i);
			} else {
				if (sb.charAt(i) == lf)
					sb.deleteCharAt(i);
				else
					i++;
			}
		}
		return sb.toString();
	}
	
	public static String replace(String src, String chrs, String data) {
		StringBuilder sb = new StringBuilder();
		int p = 0;
		int i = 0;
		int startTag = 0;
		String srcLC = src.toLowerCase();

		while ((startTag = srcLC.indexOf(chrs, p)) != -1) {
			p = startTag;
			sb.append(src.substring(i, p));
			p += chrs.length();
			sb.append(data);
			i = p;
		}
		sb.append(src.substring(p));
		return sb.toString();
	}
	
	public static void replace(StringBuilder src, String chrs, String data) {
		
		if (data==null) data = "";
		int dl = chrs.length();
		int il = data.length();
		int p = 0;

		while ((p = src.indexOf(chrs, p)) != -1) {
			if (dl < il) {
				src.delete(p,p+dl);
				src.insert(p,data);
			} else if (dl > il) {
				src.delete(p,p+(dl-il));
				src.replace(p,p+il,data);
			} else {
				src.replace(p,p+il,data);
			}
			p += il;
		}
		return;
	}
	
	public static String replace(String src, Properties values, String sTag, String eTag) {
		StringBuilder sb = new StringBuilder();
		String tag = "";
		int p = 0;
		int i = 0;
		int startTag = 0;
		int endTag = 0;

		while ((startTag = src.indexOf(sTag, p)) != -1) {
			p = startTag;
			sb.append(src.substring(i, p));
			p += sTag.length();
			endTag = src.indexOf(eTag, p);
			if (endTag == -1)
				; // what to do?
			tag = src.substring(p, endTag);
			if (tag.length() > 0)
				sb.append(values.getProperty(tag.toLowerCase(),sTag+tag+eTag));
			p = endTag + eTag.length();
			i = p;
		}
		sb.append(src.substring(p));
		return sb.toString();
	}

	public static String replacement(String s, String o, String n) {

		String org = s;
		String result = s;
		int i;
		do {
			i = org.indexOf(o);
			if (i != -1) {
				result = org.substring(0,i);
				result = result + n;
				result = result + org.substring(i + o.length());
				org = result;
			}
		} while (i != -1);
		return result;
	}

	public static String firstreplacement(String s, String o, String n) {

		String org = s;
		String result = s;

		int i = org.indexOf(o);
		if (i != -1) {
			result = org.substring(0,i);
			result = result + n;
			result = result + org.substring(i + o.length());
			org = result;
		}
		return result;
	}
	
	public static String removeSpaces(String s) {
		StringBuilder sb = new StringBuilder(s);
		int i = 0;
		while (i < sb.length()) {
			if (sb.charAt(i) == ' ') {
				sb.deleteCharAt(i);
			} else {
				i++;
			}
		}
		return sb.toString();
	}
	
	public static String markup(String pIndex, long pRsn, String pQuery, long pQueryId, boolean stemSearch, Session session) {
		boolean ok=true;
		String t="";

		try {
			StoredProcedureQuery query = session.createStoredProcedureQuery("HILITE");
			
			query.registerStoredProcedureParameter("pIndex", String.class, ParameterMode.IN).setParameter("pIndex", pIndex);
			query.registerStoredProcedureParameter("pRsn", String.class, ParameterMode.IN).setParameter("pRsn", pRsn);
			query.registerStoredProcedureParameter("pQuery", String.class, ParameterMode.IN).setParameter("pQuery", pQuery);
			query.registerStoredProcedureParameter("pQueryId", String.class, ParameterMode.IN).setParameter("pQueryId", pQueryId);
			query.execute();
		    query.unwrap(ProcedureOutputs.class).release();
			
		} catch (Exception err) { ok=false; }
		if (!ok) return "1";

		ResultSet rs = null;
		try {
			String sql = "select document from RESULT_MARKUP where query_id = " + pQueryId ;
			rs = DbUtil.runSql(sql, session);
			if (rs.next()) {
				Clob cl = rs.getClob(1);
				if (cl == null) {
					t = "";
				} else {
					int l = (int)cl.length();
					t = cl.getSubString(1,l);
				}
			}
		} catch (Exception err) { ok=false; }
		try { if (rs != null) rs.close(); } catch (Exception err) {;}
		if (! ok) return "2";

		rs = null;
		try {
			String sql = "delete from RESULT_MARKUP where query_id = " + pQueryId;
			rs = DbUtil.runSql(sql, session);
		} catch (Exception err) {;}
		try { if (rs != null) rs.close(); } catch (Exception err) {;}
		return t;
	}
	
	public static void markupContent(StringBuilder text, StringBuilder transcript, String w, long itemrsn, String item_table, long q_id, boolean stem_search, Session session) {
		// count the number of line feeds in content
		int lastIndex = 0;
		int lfCount = 0;
		String t = text.toString();
		
		while (lastIndex != -1) {
			lastIndex = t.indexOf((char)10, lastIndex);
			if (lastIndex != -1) {
				lfCount++;
				lastIndex++;
			}
		}					

		String q_index = "TEXT_INDEX3";
		if (item_table.equalsIgnoreCase("current")) q_index = "CONTENT_INDEX3";

		String markedUp = markup(q_index, itemrsn, w, q_id, stem_search, session);
		if (markedUp.length() > 1) {
			// markedUp will be the text concatenated to the transcript -- need to split them
			lfCount=lfCount+2; // there is a life feed at the beginning and a line feed separating the text and transcript
			lastIndex = 0;
			int testIndex = 0;
			while ((lfCount > 0) && (testIndex != -1)) {
				testIndex = markedUp.indexOf((char)10, lastIndex);
				if (testIndex != -1)
					lastIndex = testIndex+1;
				else
					lastIndex = markedUp.length();
				lfCount--;
			}
			
			// replace the StringBuilders - must use the same ones so we can pass back by reference
			transcript = transcript.replace(0, transcript.length(), markedUp.substring(lastIndex));
			text = text.replace(0, text.length(), markedUp.substring(0, lastIndex));
		} else { // error?
			text = text.replace(0, text.length(), t + " [" + markedUp + "]");
		}
	}
	
	public static String replace(String src, Properties values) {
		StringBuilder sb = new StringBuilder();
		String tag = "";
		int p = 0;
		int i = 0;
		int startTag = 0;
		int endTag = 0;

		while ((startTag = src.indexOf("<**", p)) != -1) {
			p = startTag;
			sb.append(src.substring(i, p));
			p += 3;
			endTag = src.indexOf("**>", p);
			if (endTag == -1)
				; // what to do?
			tag = src.substring(p, endTag);
			if (tag.length() > 0)
				sb.append(values.getProperty(tag.toLowerCase(),"<**"+tag+"**>"));
			p = endTag + 3;
			i = p;
		}
		sb.append(src.substring(p));
		return sb.toString();
	}
	
	public static String nullToEmptyString(String value) {
		
		String result = "";
		
		if(value != null) {
			result = value;
		}
		
		return result;
	}
}
