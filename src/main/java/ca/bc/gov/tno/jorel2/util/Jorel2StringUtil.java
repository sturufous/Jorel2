package ca.bc.gov.tno.jorel2.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Jorel2StringUtil {
	
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

		// get rid of anything between <>
		p = in.indexOf("<");
		while (p >= 0) {
			p2 = in.indexOf(">", p);
			if (p2 >= 0) {
				in = in.substring(0, p) + in.substring(p2 + 1);
				p = in.indexOf("<");
			}
			else {
				p = -1;
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
	
	public static String GetTitle(String articlePage) {
		String articlePageUpper = articlePage.toUpperCase();
		String titleStr = "";
		titleStr = articlePage.substring(articlePageUpper.indexOf("<TITLE>") + 7,
				articlePageUpper.indexOf("</TITLE>"));
	
		return titleStr;
	}
	
	public static String GetArticle(String articlePage) {
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
		articleStr = Jorel2StringUtil.removeHTML(articleStr);
		return articleStr;
	}

	
	/* public long getAutoToneVal(String content){

	     double dblVal = 0;
	     long toneVal = 0; //neutral by default
	     boolean atLeastOneHit = false;

	     if ((phrases.length == 0) | (content == null)) return toneVal;
	        try {

	          //loop through each item in the phrase array accumilating the phVal total

	          int counter = 0;
	          Pattern pattern;
	          Matcher matcher;
	          content = content.toLowerCase();

	          while (counter < phrases.length){

	           pattern=Pattern.compile("\\b"+phrases[counter].toLowerCase()+"\\b");
	           matcher=pattern.matcher(content);

	           while (matcher.find()) {
	                dblVal = dblVal + phraseVals[counter];
	                atLeastOneHit = true;
	                }
	            counter++;

	          }


	        } catch (Exception err) {
	          setLastError("dbAutoTone.getAutoToneVal(): Error "+err);
	          return 0;
	        }

	        // round toneVal to an long number
	        toneVal = Math.round(dblVal);

	        // truncate to max range of -5 to +5
	        if (toneVal > 5) toneVal = 5;
	        if (toneVal < -5) toneVal = -5;

	if(atLeastOneHit)
	        {
	          return toneVal;}
	        else{
	          // a return value of -99 means there were no hits so no hits
	          return -99;}

	 } */
}
