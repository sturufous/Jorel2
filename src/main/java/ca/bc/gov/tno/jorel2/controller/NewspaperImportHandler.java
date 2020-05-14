package ca.bc.gov.tno.jorel2.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.StringTokenizer;

import javax.inject.Inject;
import javax.xml.bind.Unmarshaller;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.JaxbUnmarshallerFactory;
import ca.bc.gov.tno.jorel2.jaxb.Nitf;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.ImportDefinitionsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemQuotesDao;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;
import ca.bc.gov.tno.jorel2.util.StringUtil;

/**
 * Spring service that provides methods to extract the content of newspaper import files of various formats (currently xml
 * and freeform). The articles are persisted to the NEWS_ITEMS table, and any quotes in the article are extracted.
 * 
 * @author StuartM
 */

@Service
public class NewspaperImportHandler extends Jorel2Root {

	/** Object containing a set of JAXB Unmarshaller objects, one for each xml format supported (e.g. Rss and Nitf)  */
	@Inject
	JaxbUnmarshallerFactory unmarshallerFactory;
	
	/** Quote extractor for processing article text  */
	@Inject
	QuoteExtractor quoteExtractor;
	
	private ArrayList sofMarkers = new ArrayList();
	private ArrayList eofMarkers = new ArrayList();
	private ArrayList fieldNumbers = new ArrayList();
	private String newrecord_sof;
	
	private int currentFieldMarker = -1;

	/**
	 * Imports the Nitf file at the location indicated by xmlFilePath using JAXB unmarshalling, extracts the quotes
	 * from the article and creates the NEWS_ITEMS record.
	 * 
	 * @param currentEvent The current event record, used to extract the source name.
	 * @param xmlFilePath The full path name of the file to import.
	 * @param session The current Hibernate persistence context.
	 * @return Whether the operation was successful.
	 */
	
	public boolean doXmlImport(EventsDao currentEvent, String xmlFilePath, Session session) {
		
		boolean success = true;
		Nitf item = null;
		
		try {
		    Unmarshaller unmarshaller = unmarshallerFactory.getNitfUnmarshaller();
		    
			// The JAXB unmarshaller is not thread safe, so synchronize unmarshalling
			synchronized(unmarshaller) {
			    item = (Nitf) unmarshaller.unmarshal (new File(xmlFilePath));
				unmarshaller.notify();
			}

		    NewsItemsDao newsItem = NewsItemFactory.createXmlNewsItem(item, currentEvent.getName());
			session.beginTransaction();
    		session.persist(newsItem);
    		
    		// Extract all quotes, and who made them, from the news item.
    		quoteExtractor.extract(newsItem.content);
    		NewsItemQuotesDao.saveQuotes(quoteExtractor, newsItem, session);
    		
			session.getTransaction().commit();
			success = true;
		} catch (Exception e) {
			decoratedError(INDENT1, "Unmarshalling/persisting xml newspaper article: " + xmlFilePath, e);
			success = false;
		}

		return success;
	} 
	
	public boolean doFreeFormImport(EventsDao currentEvent, ImportDefinitionsDao importMeta, String currentFile, BufferedReader in, Session session) {
		
		String line="";
		String buffer="";
		String sof="";                                                              // start of field marker (like !@AUTHOR=)
		String eof="";                                                              // end of field marker (like !@)
		String field="";                                                            // field number (like 3 or 4 or C or newrecord)
		String fieldValue="";
		String lastfield="";
		String theByline=" ";

		boolean endOfFile=false;
		boolean inField=false;
		boolean inRecord=false;
		boolean readMore=true;                                                      // read more data
		boolean done=false;
		
		Clob posListClob = importMeta.getPositionList();
		String posListString = StringUtil.clobToString(posListClob);
		populatePositionList(posListString);

		// Do this until someone says we are done (likely end of file or read error)
		while (!done) {

			// Only read more data when told to do so
			if (readMore) {
				try {
					line = in.readLine();                                                 // read a line from the file
					if (line == null) {
						endOfFile=true;                                                     // at the end of the file
						done=true;                                                          // we are done!
					}
				} catch (IOException err) {
					endOfFile=true;                                                       // errors cause us to be done
					done=true;
				}

				buffer=buffer+" "+line;                                                 // append the line to the buffer
				readMore=false;                                                         // turn off reading data
			}

			// Have we found a sof (start of field marker) and we are waiting for the end of field marker
			if ((endOfFile) | (inField)) {

				// is the eof (end of field marker) in the buffer yet
				if ((endOfFile) | (findEofMarker(buffer))) {

					// get the markers for this field, start marker and end marker
					sof = getSofMarkers();                                          // start of field marker
					eof = getEofMarkers();                                          // end of field marker
					field = getFieldNumbers();                                      // field number

					// the field number is the indicator for the start of a new record
					if ((endOfFile) | (field.equalsIgnoreCase("newrecord"))) {

						if (endOfFile) {
							int posStart = buffer.indexOf(sof);
							if(posStart >= 0) buffer=buffer.substring(posStart+sof.length());

							//buffer = aktivString.removeEntities(buffer);

							//newsItem.addToField( field, buffer.trim() );
						}

						// if we are inside a record, dump what we have to the database
						//if (inRecord) {
						//	saveItem(newsItem, imports);
						//	newsItem.initialize();            // init for a new record
						//}
						inRecord=true;

						// 'newrecord' field hit
					} // if ((endOfFile) | (field.equalsIgnoreCase("newrecord")))

					// extract the value for this field using the sof and the eof
					int posStart = buffer.indexOf(sof);
					if(posStart >= 0) {

						buffer=buffer.substring(posStart+sof.length());

						int posEnd = buffer.indexOf(eof);

						// make sure the "newrecord" marker doesn't occur before the eof
						int posEnd2 = buffer.indexOf(getNewRecordSof());
						if ((posEnd2 > 0) && (posEnd > 0)) {
							posEnd = Math.min(posEnd, posEnd2);
						} else {
							posEnd = Math.max(posEnd, posEnd2);
						}

						if (posEnd >= 0) {
							fieldValue = buffer.substring(0,posEnd);                       // get the value

							/***Informart fudges ***/
							if (importMeta.getName().toLowerCase().startsWith("infomart")) {
								if (field.equals("3")) {
									if (fieldValue.trim().equalsIgnoreCase("24 Hours Vancouver"))
										fieldValue = "Vancouver 24 hrs";
								}
							}
							else {
								if ( (field.equals("C")) || (field.equals("6")) || (field.equals("19")) || (field.equals("21")) ) {
									//fieldValue = aktivString.removeEntities(fieldValue);									
								}
							}
							
							/***fudges for all***/
							if (field.equals("16")) { // page must not have 
								if (fieldValue.toLowerCase().startsWith("a1/")) {
									fieldValue = "A01";
								}
							}

							addToField( field, fieldValue.trim() );                  // set the field in the News Item record
							buffer=buffer.substring(posEnd);
							inField=false;                                                    // no longer positioned in a field
						} else {
							readMore=true;                                                    // read more data
						}
					}
				} else {
					// no eof markers, so read more data
					readMore=true;
				} //if ((endOfFile) | (imports.findEofMarker(buffer)))
			} else {

				// we are not inside a field, can we find the start of a field
				if (findSofMarker(buffer)) {
					inField=true;                                                         // Yes, start of a field found
				} else {
					readMore=true;                                                        // No, read more data

					// clear the buffer since we are not in a field and not inside a record
					buffer="";
					lastfield=field;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Only format we have to worry about is freeform.
	 * 
	 * @param posList
	 */
	
	private void populatePositionList(String list) {
		
		String posList = "";
		
		if (list == null)
			posList = "";
		else
			posList = list;

		sofMarkers.clear();
		eofMarkers.clear();
		fieldNumbers.clear();
		newrecord_sof = "(notset)";

		StringTokenizer st1 = new StringTokenizer(posList, "," );
		int tokens = st1.countTokens();
		for (int i=0; i<tokens; i++) {
			String line = st1.nextToken();
			StringTokenizer st2 = new StringTokenizer( line, "^" );
			if (st2.countTokens() == 3) {
				sofMarkers.add( st2.nextToken() );
				eofMarkers.add( st2.nextToken() );
				fieldNumbers.add( st2.nextToken() );
			}
		}
	}
	
	//******************************* From here on down, code extracted from Jorel1 **************************************/
	
	public int getCurrentFieldMarker() { return currentFieldMarker; }
	public void setCurrentFieldMarker( int v ) { currentFieldMarker = v; return; }

	public int getSize() { return sofMarkers.size(); }

	public String getSofMarkers() {
		int index = getCurrentFieldMarker();
		if (index == -1) return "";
		if (index >= sofMarkers.size()) return "";

		return (String) sofMarkers.get(index);
	}

	public String getEofMarkers() {
		int index = getCurrentFieldMarker();
		if (index == -1) return "";
		if (index >= eofMarkers.size()) return "";

		return (String) eofMarkers.get(index);
	}

	public String getFieldNumbers() {
		int index = getCurrentFieldMarker();
		if (index == -1) return "";
		if (index >= fieldNumbers.size()) return "";

		return (String) fieldNumbers.get(index);
	}

	public String getNewRecordSof() {
		return newrecord_sof;
	}

	public boolean findSofMarker(String buffer) {
		setCurrentFieldMarker(-1);
		boolean done=false;
		int i=0;
		while (!done) {
			String sof = (String) sofMarkers.get(i);
			int pos = buffer.indexOf(sof);
			if (pos >= 0 ) {
				setCurrentFieldMarker(i);
				done = true;
			} else {
				i++;
				if (i >= sofMarkers.size()) done=true;
			}
		}
		return (getCurrentFieldMarker() != -1);
	}
	
	public boolean findEofMarker(String buffer) {
		int index = getCurrentFieldMarker();
		if (index == -1) return false;

		String sof = getSofMarkers();
		int pos = buffer.indexOf(sof);
		if (pos < 0) return false;

		String temp = buffer.substring(pos+sof.length());

		String eof = (String) eofMarkers.get(index);
		pos = temp.indexOf(eof);
		
		// make sure the "newrecord" marker doesn't occur before the eof
		int pos2 = temp.indexOf(getNewRecordSof());
		if ((pos2 > 0) && (pos > 0)) {
			pos = Math.min(pos, pos2);
		} else {
			pos = Math.max(pos, pos2);
		}

		if (pos >= 0) return true;
		return false;
	}

	public void addToField( String num, String value ) {

		// Content is a special field
		if (num.equalsIgnoreCase("C")) {
			int npGarbage = value.indexOf("!@LKW=");
			if (npGarbage > 10) value = value.substring(0,npGarbage);
			//setContent((getContent()+value).trim());
			return;
		}
		
		// THIS IS WHERE YOU WOULD POKE A VALUE INTO A NewsItemsDao RECORD

		int fieldNumber = 0;
		try {
			fieldNumber = Integer.parseInt(num);
		} catch (Exception err) {
			fieldNumber = 0;
		}
		if (fieldNumber == 0) return;


	}
}
