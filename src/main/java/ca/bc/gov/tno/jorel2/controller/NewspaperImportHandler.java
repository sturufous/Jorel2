package ca.bc.gov.tno.jorel2.controller;

import java.io.File;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.jaxb.Nitf;
import ca.bc.gov.tno.jorel2.model.EventsDao;
import ca.bc.gov.tno.jorel2.model.NewsItemFactory;
import ca.bc.gov.tno.jorel2.model.NewsItemsDao;

@Service
public class NewspaperImportHandler extends Jorel2Root {

	public boolean doXmlImport(EventsDao currentEvent, String xmlFilePath, Session session) {
		
		try {
			JAXBContext jc = JAXBContext.newInstance (Nitf.class);
		    Unmarshaller u = jc.createUnmarshaller ();
		    Nitf item = (Nitf) u.unmarshal (new File(xmlFilePath));
		    
		    NewsItemsDao newsItem = NewsItemFactory.createXmlNewsItem(item, currentEvent.getName());
		} catch (Exception e) {
			decoratedError(INDENT1, "Unmarshalling xml newspaper article: " + xmlFilePath, e);
		}
		//JAXBContext context = JAXBContext.newInstance(Rss.class);
    	//unmarshaller = context.createUnmarshaller();

		return true;
		
/*		try {
	    	 
	    	File fXmlFile = new File(xmlFileName);
	    	DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	    	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	    	Document doc = dBuilder.parse(fXmlFile);
	     
	    	//optional, but recommended
	    	//read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
	    	doc.getDocumentElement().normalize();
	     
	    	NodeList nList = doc.getElementsByTagName(imports.getNewRecordSof());
	     	     
	    	for (int j = 0; j < nList.getLength(); j++) {
	     
	    		Node nNode = nList.item(j);
	     	     
	    		if (nNode.getNodeType() == Node.ELEMENT_NODE)
	    		{
	    			Element eElement = (Element) nNode;
	    			
	    			// Loop thru import definition
	    			for(int i = 0; i < imports.getSize(); i++)
	    			{
		    			imports.setCurrentFieldMarker(i);
		    			String nodeName = imports.getSofMarkers().trim();
		    			String attrName = imports.getEofMarkers().trim();
		    			String field = imports.getFieldNumbers();

						if (field.equalsIgnoreCase("newrecord")) { // ignore this
						} else {

							String fieldValue = "";
			    			if(attrName.equalsIgnoreCase(""))
			    			{
			    				//fieldValue = eElement.getElementsByTagName(nodeName).item(0).getTextContent();
			    				//fieldValue = eElement.getElementsByTagName(nodeName).item(0).getNodeValue();
			    				fieldValue = eElement.getElementsByTagName(nodeName).item(0).getFirstChild().getNodeValue();
			    				//frame.addJLog(nodeName+": " + eElement.getElementsByTagName(nodeName).item(0).getTextContent());
			    			}
			    			else
			    			{
			    				NamedNodeMap attributes = eElement.getElementsByTagName(nodeName).item(0).getAttributes();
			    				fieldValue = attributes.getNamedItem(attrName).getNodeValue();
			    				//frame.addJLog(nodeName+" "+attrName+": " + attributes.getNamedItem(attrName).getNodeValue());
			    			}

			    			if ((imports.getName().toLowerCase().startsWith("globe")) | 
			    					(imports.getName().toLowerCase().startsWith("star"))){
								// Remove junk from story content, headline, byline, notes
								if ( (field.equals("C")) || (field.equals("6")) || (field.equals("19")) || (field.equals("21")) ) {
									fieldValue = removeHTML(fieldValue);
								}
								// Capitalize the first of each byline
								if (field.equals("19")) {
									fieldValue = capFirst(fieldValue);
									if (fieldValue.startsWith("By "))
										fieldValue = fieldValue.substring(3);
								}
								// Replace new lines with ;
								if (field.equals("6")) {
									fieldValue = fieldValue.replaceAll("[\n\r]+", "; ");
								}
			    			}
			    			if(fieldValue == null) fieldValue = "?fieldValue?";
			    			
			    			newsItem.addToField( field, fieldValue );
						}
	    			}   
					saveItem(newsItem, imports);
					newsItem.initialize();            // init for a new record
	    		}
	    	}
        } catch (Exception e) {
    		frame.addJLog("doXMLImport error: "+e.toString());
        } */
	} 
}
