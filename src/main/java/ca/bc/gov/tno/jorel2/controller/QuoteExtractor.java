package ca.bc.gov.tno.jorel2.controller;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Component;

import ca.bc.gov.tno.jorel2.Jorel2Process;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.WordsDao;

@Component
public class QuoteExtractor extends Jorel2Root {
	
	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2Process process;
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Inject
	private DataSourceConfig config;
	
	/** Verbs used for quote extraction (e.g. relented, summed, blinked, drawled) currently 1372 instances */
	Set<String> verbs = null;
	
	/** Titles used for quote extraction (e.g. mr., dr., lieut.) currently 10 instances */  
	Set<String> titles = null;
	
	/** Noise words used for quote extraction (e.g. while, by, with, from) currently 28 instances */
	Set<String> noiseWords = null;
	
	/** NoiseNameWords used for quote extraction (e.g. Family School, Langley RCMP) currently 8062 instances */  
	Set<String> noiseNameWords = null;
	
	/** Hibernate persistence context for all quote related activities */
	Session session;
	
	static final char para = (char)10;
	int counter;
	private String g_results;
	private List<Quote> quoteList;
	private Map<String, String> quotedNames;
	private List<String> noiseNameList;
	private List<String> noiseList;
	boolean online=true;
	boolean updateVerbs=false;
	boolean updateNoiseNames=false;
	private List<String> titleList;
	// special global flag if the last name was an unknown alias
	private boolean gunk=true;
	private List<String> verbList;


    public void init() {
    	
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
        
    	if(sessionFactory.isEmpty()) {
    		logger.error("Getting TNO session factory.", new IllegalStateException("No session factory provided."));
    		System.exit(-1);
    	} else {
    		
    		if (verbs == null || titles == null || noiseWords == null || noiseNameWords == null) {
		        session = sessionFactory.get().openSession();
		        
				verbs = loadWords(WordsDao.getWordsByWordType(process, WordType.VERB, session));
				titles = loadWords(WordsDao.getWordsByWordType(process, WordType.TITLE, session));
				noiseWords = loadWords(WordsDao.getWordsByWordType(process, WordType.NOISE, session));
				noiseNameWords = loadWords(WordsDao.getWordsByWordType(process, WordType.NOISENAME, session));
    		}
    	}
    }

    private Set<String> loadWords(List<WordsDao> results) {
    	
    	Set<String> wordSet = new HashSet<>();
    	
    	for (WordsDao word : results) {
    		wordSet.add(word.getWord());
    	}
    	
    	return wordSet;
    }
    
	// extract quotes from newspaper text, put them in a list
	@SuppressWarnings("deprecation")
	private long extractNewspaper(String text) {
		long count=0;
		int i=0, offset=0;
		char chr;
		boolean lastSpace = true; //was last character a space?
		boolean inQuote = false; //are we inside quotes?
		boolean inUpper = false; //are we inside a sequence of uppercased words (ie proper name)
		String fullQuote = "";
		String offsets = "";
		StringBuilder currentQuote = new StringBuilder("");
		StringBuilder currentPara = new StringBuilder("");
		StringBuilder upperName = new StringBuilder("");
		String upperName2;
		String lastName = "";
		List<String> nameList = new ArrayList<>();
		Map<Integer, String> nameMap;
		boolean skip;

		quoteList.clear();
		counter = -1;
		g_results = "";

		// CAREFUL: must keep text the same length, because we return offsets
		if (text==null) text = "";
		text = text.replace((char)10, para);
		text = text.replace((char)13, para);
		text = text.replace('|', para);
		text = text.replaceAll("\u2018", "'"); //8216
		text = text.replaceAll("\u2019", "'"); //8217
		text = text.replaceAll("\u201C", "\""); //8220
		text = text.replaceAll("\u201D", "\""); //8221
		text = text.replace('{', '(');
		text = text.replace('}', ')');
		text = text+para;

		//System.out.println(text);

		nameMap = markQuotedNames(text); //make note of the places where the names from the quotednames table are

		for (i=0;i<text.length();i++) {

			// cloak names/aliases from the quotednames table
			skip = false;
			if (!inQuote) {
				if (nameMap.containsKey(new Integer(i))) {
					@SuppressWarnings("deprecation")
					String tname = (String)nameMap.get(new Integer(i));
					i = i+tname.length()-1;
					currentPara.append(upperName.toString().toLowerCase());
					currentPara.append("{name:"+tname.toString().trim()+"}");
					nameList = appendNamelist(nameList, tname);
					inUpper = false;
					upperName.setLength(0);
					skip = true;
				}
			}

			if (!skip) {
				chr=text.charAt(i);

				//System.out.println(chr);
				//System.out.println((int)chr);

				switch(chr){


				/***** quote *****/
				case '"':
					if(inQuote){
						if(currentQuote.length()>0){
							char lastc=currentQuote.charAt(currentQuote.length()-1);
							if(!Character.isLetterOrDigit(lastc)){ //string ends in punctuation (probably) - good
								fullQuote=appendQuote(fullQuote,currentQuote);
								offsets=appendOffset(offsets,offset,i-offset);
								currentPara.append("{quote"+lastc+"}");
							} else{ //string ends in a letter or number - probably an inline or "scare" quote
								currentPara.append("{garbage}");
							}
						} else{ //empty quote??
							currentPara.append("{garbage}");
						}
						currentQuote.setLength(0);
					} else{
						if(inUpper){ // we were in an upper case name
							if(!upperName.toString().equals("")){
								// found a name!!
								upperName2=fixName(upperName.toString());
								if(upperName2.equals("")){
									currentPara.append(upperName.toString().trim().toLowerCase()+" ");
								} else{
									currentPara.append("{name:"+upperName2.trim()+"} ");
									nameList=appendNamelist(nameList,upperName2);
								}
								upperName.setLength(0);
							}
							inUpper=false;
						}
						offset=i+1;
					}
					inQuote=!inQuote;
					lastSpace=false;
					break; // quote


					/***** space *****/
				case ' ':
					if(inQuote){
						currentQuote.append(chr);
					} else{
						if(inUpper){
							if (upperName.toString().indexOf(" ")<0) { // first word in the upper name
								if (wordExists(noiseList, upperName.toString())) { // noise word!
									currentPara.append(upperName.toString().toLowerCase());
									currentPara.append(chr);
									upperName.setLength(0);
									inUpper=false;
								} else {
									upperName.append(chr);
								}
							} else {
								upperName.append(chr);
							}
						} else{
							currentPara.append(chr);
						}
						lastSpace=true;
					}
					break; // space


					/***** new paragraph *****/
				case para:
					if(inQuote){ //paragraph ended without encountering a closing quote
						boolean closeit=true;

						//check to see if next paragraph starts with a quote...
						int j=i+1;
						while((j<text.length())&&(text.charAt(j)==para)){
							j++;
						}
						if(j<text.length()){
							if(text.charAt(j)=='\"'){ //next paragraph begins with a quote ... quote continues
								closeit=false;
							}
						}

						fullQuote=appendQuote(fullQuote,currentQuote);
						offsets=appendOffset(offsets,offset,i-offset);
						currentQuote.setLength(0);

						if(closeit){ //next paragraph does NOT begin with a quote ... malformed, close the quote
							inQuote=false;
							lastSpace=true;
							inUpper=false;
							currentPara.append("{quote.}");
						} else{ //next paragraph begins with a quote ... quote continues
							i=j; // skip past quote
							offset=i+1;
						}
					} else{
						if(inUpper){ // we were in an upper case name
							if(!upperName.toString().equals("")){
								// found a name!!
								upperName2=fixName(upperName.toString());
								if(upperName2.equals("")){
									currentPara.append(upperName.toString().trim().toLowerCase());
								} else{
									currentPara.append("{name:"+upperName2.trim()+"}");
									nameList=appendNamelist(nameList,upperName2);
								}
								upperName.setLength(0);
							}
							inUpper=false;
						}
						currentPara.append(Character.toLowerCase(chr));
						lastSpace=true; //paragraph break counts as a space
					}
					if (!inQuote) {
						if (!fullQuote.equals("")){ // paragraph with a quote
							String paragraph = identifyNoise(currentPara.toString());
							//System.out.println(para);
							NameAlias na = determineName(paragraph, nameList, lastName);
							if(na != null)
							{
								Quote q=new Quote(na.name, na.alias, offsets, fullQuote, gunk);
								quoteList.add(q);
								lastName = na.alias;
							}
							fullQuote="";
							offsets="";
						} else { // paragraph without a quote
							if (!currentPara.toString().trim().equals("")) { // current paragraph is not blank
								String paragraph = identify_noise(currentPara.toString());
								String ln = lastSaid(paragraph,nameList,lastName);
								if(ln.equalsIgnoreCase("she") | ln.equalsIgnoreCase("he"))
								{
									;
								}
								else
								{
									lastName = ln;
								}
							}
						}
					}
					if(!inQuote){
						currentPara.setLength(0);
					}
					break; // paragraph


					/***** letter, number, puncuation *****/
				default:
					if(inQuote){
						currentQuote.append(chr);
					} else{
						if(lastSpace){ // new word
							if(Character.isUpperCase(chr)){ //new word is uppercase, name continues to grow
								inUpper=true;
							} else{ //new word is not uppercase, name ends
								if(!upperName.toString().equals("")){
									// found a name!!
									upperName.deleteCharAt(upperName.length()-1); //delete the final space
									upperName2=fixName(upperName.toString());
									if(upperName2.equals("")){
										currentPara.append(upperName.toString().trim().toLowerCase()+" ");
									} else{
										currentPara.append("{name:"+upperName2.trim()+"} ");
										nameList=appendNamelist(nameList,upperName2);
									}
									upperName.setLength(0);
								}
								inUpper=false;
							}
							lastSpace=false;
						}
						if(inUpper){ //we are in the midst of a sequence of uppercase words (ie, a name)

							boolean upperContinues=true; //we probably want to add this character, but let's check...

							if(chr==',') { //a comma signifies a break in the name
								upperContinues=false;
							}

							if(chr=='.') {
								String word = lastWord(upperName.toString()); // get the last word in the name
								if(word.length()>=2){ //one letter word is probably an initial: John A. Macdonald
									if(!wordExists(titleList, word+".")){ //not a title
										//check to see if next word is capitalized
										int j=i+1;
										while ((j<text.length())&&(text.charAt(j)==' ')){
											j++;
										}
										if (j<text.length()){
											if (Character.isUpperCase(text.charAt(j)) || (text.charAt(j)==para) || (text.charAt(j)=='\"')) { //next word is capitalized
												upperContinues=false; //break in the name (probably the end of a sentence)
											}
										}
									}
								}
							}

							if(upperContinues){ //word continues to grow
								upperName.append(chr);
							} else{ //word is done, let's add it
								// found a name!!
								upperName2=fixName(upperName.toString());
								if(upperName2.equals("")){
									currentPara.append(upperName.toString().trim().toLowerCase()+chr);
								} else{
									currentPara.append("{name:"+upperName2.trim()+"}"+chr);
									nameList=appendNamelist(nameList,upperName2);
								}
								upperName.setLength(0);
								inUpper=false;
							}
						} else{
							currentPara.append(Character.toLowerCase(chr));
						}
					}
				}

			}
		}

		return count;
	}
	
	/**
	 * Remember all the places where names/aliases (from the quotednames table) are.
	 */
	@SuppressWarnings("deprecation")
	private Map<Integer, String> markQuotedNames(String t) {
		Map<Integer, String> m = new HashMap<>();
		// check the quoted names table to see if it resolves to something else
		int pos;
		String name;
		Set<String> set = quotedNames.keySet();
		Iterator<String> itr = set.iterator();
		while (itr.hasNext()) {
			name = (String)itr.next();
			pos = 0;
			while (pos>=0) {
				pos=t.indexOf(name,pos+1);
				if (pos>=0) {
					// remember
					m.put(new Integer(pos), name);
				}
			}
		}
		return m;
	}
	
	// append to the current quote
	private String appendQuote(String q, StringBuilder a) {
		if (!q.equals("")) q = q+" ";
		q = q+a.toString().trim();
		return q;
	}
	
	// append offset and length for this quote
	private String appendOffset(String ol, int o, int len) {
		if (!ol.equals("")) ol = ol+";";
		ol = ol+Integer.toString(o)+","+Integer.toString(len);
		return ol;
	}


	// maintain a list of names that appear in the text
	private List<String> appendNamelist(List<String> nl, String name) {
		if (!nl.contains(name)) {
			nl.add(name);
		}
		return nl;
	}

	/*
	  Input: a name (sequence of uppercase words)
	   Remove possessive
	   Remove trailing period
	   Delete name if it is a noise name
		 */
	private String fixName(String n) {
		String name = n.trim();

		// if the last character is punctuation, delete it
		if (!name.equals("")) {
			if (!Character.isLetterOrDigit(name.charAt(name.length()-1))){
				name=name.substring(0,name.length()-1);
			}
		}

		// if the name is possessive (eg Mike's) remove the 's
		if (name.length()>2) {
			if ((name.charAt(name.length()-2)=='\'') && (name.charAt(name.length()-1)=='s')){
				name=name.substring(0,name.length()-2);
			}
		}

		// if the name is all capitals, clear it
		if (name.equals(name.toUpperCase())) {
			name = "";
		}

		// if the name is only one character long, clear it
		if (name.length()<=1) {
			name = "";
		}

		// clear the name if it is a noise name
		if (wordExists(noiseNameList, name)) {
			name = "";
		}

		return name;
	}
	
	// does word exist in this list?
	private boolean wordExists(List<String> wl, String w) {
		return wl.contains(w.toLowerCase());
	}

	/*
	  Given a paragraph, identify noise names and put them into the noise names table,
	  and return the fixed paragraph
		 */
	private String identify_noise(String para) {
		String noiseName, noiseName2;

		// match patterns like "of the Victoria School Board."
		Pattern pattern=Pattern.compile("(of|to|at|by|for|from|with|in|about) the \\{name:([^\\}]*)\\}[,;\\.]");
		Matcher matcher=pattern.matcher(para);
		while (matcher.find()) {
			noiseName = matcher.group(2);
			noiseName2 = fixName(noiseName);
			if (!noiseName2.equals("")) {
				if (!quotedNames.containsKey(noiseName2)) { //not in the quoted names table
					para = replacement(para, "{name:"+noiseName+"}", noiseName.toLowerCase());
					noiseNameList.add(noiseName2.toLowerCase());
					insertWord(noiseName2, WordType.NOISENAME);
				}
			}
		}

		return para;
	}

	// insert into the words table
	private void insertWord(String word, WordType type) {
		if (online) {
			boolean cont=true;
			if ((!updateVerbs) && (type == WordType.VERB)) cont=false;
			if ((!updateNoiseNames) && (type == WordType.NOISENAME)) cont=false;
			if (cont) {
				String sqlString="insert into tno.words values (tno.QUOTED_RSN.nextval, ?, ?)";
				PreparedStatement ps=null;
				try{
					ps=c.prepareStatement(sqlString);
					ps.setString(1,word);
					ps.setLong(2,type);
					ps.executeUpdate();
					c.commit();

					if (type == WordType.VERB) appendResults("verb '"+word+"' added");

				} catch (SQLException err) {;}
				try { if (ps != null) ps.close(); } catch (SQLException err) {;}
			}
		}
	}

	// append to the results
	private void appendResults(String r) {
		if (!g_results.equals("")) g_results = g_results+", ";
		g_results = g_results+r;
	}
	
	/*
	  Given a paragraph, identify noise names and put them into the noise names table,
	  and return the fixed paragraph
		 */
	private String identifyNoise(String para) {
		String noiseName, noiseName2;

		// match patterns like "of the Victoria School Board."
		Pattern pattern=Pattern.compile("(of|to|at|by|for|from|with|in|about) the \\{name:([^\\}]*)\\}[,;\\.]");
		Matcher matcher=pattern.matcher(para);
		while (matcher.find()) {
			noiseName = matcher.group(2);
			noiseName2 = fixName(noiseName);
			if (!noiseName2.equals("")) {
				if (!quotedNames.containsKey(noiseName2)) { //not in the quoted names table
					para = replacement(para, "{name:"+noiseName+"}", noiseName.toLowerCase());
					noiseNameList.add(noiseName2.toLowerCase());
					insertWord(noiseName2, WordType.NOISENAME);
				}
			}
		}

		return para;
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

	// what is the last word in this sequence?
	private String lastWord(String s) {
		int pos = s.lastIndexOf(" ");
		int pos2 = s.lastIndexOf(".");
		if ((pos>0) || (pos2>0)) {
			return s.substring(Math.max(pos, pos2)+1);
		} else {
			return s;
		}
	}
	
	/*
	  Given a paragraph (without quotes), determine who the latest speaker is
		 */
	private String lastSaid(String currPara, List<String> nl, String last) {
		int i;
		String said = "";

		// match patterns like "John said"
		//                  or "John, descriptor, said"
		for (i=0;i<verbList.size();i++) {
			said = patternMatch(currPara, "\\{name:([^\\}]*)\\}(, ([a-z\\s']|\\{name:([^\\}]*)\\})+,)? "+(String)verbList.get(i));
			if (!said.equals("")) break;
		}

		// match patterns like ", said John"
		//                  or ", said descriptor John"
		if (said.equals("")) {
			for(i=0;i<verbList.size();i++){
				said=patternMatch(currPara,", "+(String)verbList.get(i)+" [a-z\\s']*\\{name:([^\\}]*)\\}");
				if(!said.equals("")) break;
			}
		}

		// otherwise, just use the first name (with spaces, or resolvable to a name with spaces) in the paragraph
		if (said.equals("")) {
			Pattern pattern=Pattern.compile("\\{name:([^\\}]*)\\}");
			Matcher matcher=pattern.matcher(currPara);
			while (matcher.find()) {
				String temp_said = matcher.group(1);
				if (temp_said.indexOf(" ")>=0) {
					said = temp_said;
				} else {
					temp_said = aliasToName(temp_said, nl);
					if (temp_said.indexOf(" ")>=0) {
						said = temp_said;
					}
				}
				if(!said.equals("")) break;
			}
		}

		// person is still unknown, just use last name
		if (said.equals("")) {
			said=last;
		}

		return said;
	}
	
	/*
	  Convert an alias to a name, found either higher in the story or in the quoted names table
		 */
	private String aliasToName(String name, List<String> nl) {
		name = removeTitles(name);
		gunk = true; // global boolean - unknown alias
		int i;
		// see if there is a longer form of this name higher in the story
		for (i=0;i<nl.size();i++) {
			if (((String)nl.get(i)).indexOf(name)>=0) {
				name = removeTitles((String)nl.get(i));
				i = nl.size()+1;
			}
		}
		if (quotedNames.containsKey(name)) {
			name = (String)quotedNames.get(name);
			gunk = false; // global boolean - unknown alias
		}
		return name;
	}

	// remove words from the front of a name if they are titles
	private String removeTitles(String name) {
		String word;
		int pos;
		do {
			pos=name.indexOf(" ");
			if(pos>0){
				word=name.substring(0,pos);
			} else{
				word=name;
			}
			if (wordExists(titleList,word)){
				if(pos>0){
					name=name.substring(pos+1);
				} else{
					name="";
				}
			} else {
				pos=-1;
			}

		} while (pos>0);

		return name;
	}

	private String patternMatch(String text, String p) {
		return patternMatch(text, p, 1);
	}

	// helper method for simple (one group) regular expressions
	private String patternMatch(String text, String p, int num) {
		String result="";
		Pattern pattern=Pattern.compile(p);
		Matcher matcher=pattern.matcher(text);
		if (matcher.find()) {
			result = matcher.group(num);
		}
		return result;
	}

	/*
	  Given a paragraph containing a quote, a list of names that have so far appeared
	  in the story, and the last name from the previous paragraph, determine who said the quote
		 */
	private NameAlias determineName(String currPara, List<String> nl, String last) {
		int i;
		String alias = "";
		// match patterns like ""blah blah," John said"
		//                  or ""blah blah," John, descriptor, said"
		for (i=0;i<verbList.size();i++) {
			alias = patternMatch(currPara, "\\{quote,\\} \\{name:([^\\}]*)\\}(, ([a-z\\s']|\\{name:([^\\}]*)\\})+,)? "+(String)verbList.get(i));
			if (!alias.equals("")) break;
		}
		// match patterns like ""blah blah," said John"
		//                  or ""blah blah," said descriptor John"
		if (alias.equals("")) {
			for (i=0;i<verbList.size();i++) {
				alias = patternMatch(currPara, "\\{quote,\\} "+(String)verbList.get(i)+" [a-z\\s']*\\{name:([^\\}]*)\\}");
				if (!alias.equals("")) break;
			}
		}
		// match patterns like "said John, "blah blah""
		//                  or "said descriptor John, "blah blah""
		if (alias.equals("")) {
			for (i=0;i<verbList.size();i++) {
				alias = patternMatch(currPara, (String)verbList.get(i)+" [a-z\\s']*\\{name:([^\\}]*)\\}[,:] \\{quote.\\}");
				if (!alias.equals("")) break;
			}
		}
		// match patterns like "John said, "blah blah""
		//                  or "John, descriptor, said, "blah blah""
		if (alias.equals("")) {
			for (i=0;i<verbList.size();i++) {
				alias = patternMatch(currPara, "\\{name:([^\\}]*)\\}(, ([a-z\\s']|\\{name:([^\\}]*)\\})+,)? "+(String)verbList.get(i)+"[,:]? \\{quote.\\}");
				if (!alias.equals("")) break;
			}
		}

		// match patterns like ""blah blah," action John."
		if (alias.equals("")) {
			Pattern pattern=Pattern.compile(" \\{quote,\\} ([a-z]+) \\{name:([^\\}]*)\\}[,;\\.]");
			Matcher matcher=pattern.matcher(currPara);
			if (matcher.find()) {
				String verb = matcher.group(1);
				if (!wordExists(noiseList, verb)) {
					alias = matcher.group(2);
					if (!wordExists(verbList, verb)) {
						insertWord(verb, WordType.VERB);
						verbList.add(verb.toLowerCase());
					}
				}
			}
		}

		// match patterns like ""blah blah," John action."
		if (alias.equals("")) {
			Pattern pattern=Pattern.compile(" \\{quote,\\} \\{name:([^\\}]*)\\} ([a-z]+)[,;\\.]");
			Matcher matcher=pattern.matcher(currPara);
			if (matcher.find()) {
				String verb = matcher.group(2);
				if (!wordExists(noiseList, verb)) {
					alias = matcher.group(1);
					if (!wordExists(verbList, verb)) {
						insertWord(verb, WordType.VERB);
						verbList.add(verb.toLowerCase());
					}
				}
			}
		}

		// match patterns like "John said"
		//                  or "John, descriptor, said"
		if (alias.equals("")) {
			for(i=0;i<verbList.size();i++){
				alias=patternMatch(currPara, "\\{name:([^\\}]*)\\}(, ([a-z\\s']|\\{name:([^\\}]*)\\})+,)? "+(String)verbList.get(i));
				if (!alias.equals("")) break;
			}
		}

		// match patterns like ", said John"
		//                  or ", said descriptor John"
		if (alias.equals("")) {
			for(i=0;i<verbList.size();i++){
				alias=patternMatch(currPara,", "+(String)verbList.get(i)+" [a-z\\s']*\\{name:([^\\}]*)\\}");
				if(!alias.equals("")) break;
			}
		}

		// otherwise, just find the first name (with spaces, or resolvable to a name with spaces) in the paragraph
		if (alias.equals("")) {
			Pattern pattern=Pattern.compile("\\{name:([^\\}]*)\\}");
			Matcher matcher=pattern.matcher(currPara);
			while (matcher.find()) {
				String temp_alias = matcher.group(1);
				if (temp_alias.indexOf(" ")>=0) {
					alias = temp_alias;
				} else {
					temp_alias = aliasToName(temp_alias, nl);
					if (temp_alias.indexOf(" ")>=0) {
						alias = temp_alias;
					}
				}
				if(!alias.equals("")) break;
			}
		}

		// Otherwise again...still no alias
		if (alias.equals(""))
		{
			// Determine if the quote is a full paragraph quote 
			//	(remove email addresses, some stories end the last paragraph with email address of the story writer)
			String EMAIL_PATTERN = "([^.@\\s]+)(\\.[^.@\\s]+)*@([^.@\\s]+\\.)+([^.@\\s]+)";
			String paragraph = currPara.replaceAll(EMAIL_PATTERN, "").trim();
			if(paragraph.equalsIgnoreCase("{quote.}") | paragraph.equalsIgnoreCase("{quote,}") | paragraph.equalsIgnoreCase("{quote,} she said.") | paragraph.equalsIgnoreCase("{quote,} he said."))
			{
				// It is a full paragraph quote,
				//	person is still unknown, just use last name, probably from the previous paragraph
				if (alias.equals("")) {
					alias=last;
				}

				// last resort, use any found name
				if (alias.equals("")) {
					if (nl.size()>0) alias=(String)nl.get(nl.size()-1);
				}
			}
		}

		// If STILL no name...
		//  Quote was in a paragraph, but did NOT find one of the patterns above
		//	so probably an ironic phrase or maybe sarcasm
		if(alias.equals("")) return null;

		String name = aliasToName(alias, nl);
		return new NameAlias(name, alias);
	}
	
	/*
	This class stores information about a name / alias.
	 */
	class NameAlias {
		String  name;
		String  alias;

		public NameAlias(String n, String a) {
			if (n==null) n = "";
			if (a==null) a = "";
			name=n;
			alias=a;
		}
	}
	
	/*
	This class stores information about a single quote.
	 */
	class Quote {
		String  name;
		String  alias;
		String  offset_lengths;
		String  quote_text;
		boolean unknown_alias;

		public Quote(String n, String a, String o, String q, boolean u) {
			name=n;
			alias=a;
			offset_lengths=o;
			quote_text=q;
			unknown_alias=u;
		}
	}

}
