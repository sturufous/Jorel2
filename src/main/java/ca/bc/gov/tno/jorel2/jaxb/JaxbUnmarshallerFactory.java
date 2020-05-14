package ca.bc.gov.tno.jorel2.jaxb;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 * Object that consolidates multiple types of JAXB unmarshaller into a singleton that can be placed into the Spring application
 * context. As calls to JAXBContext.createUnmarshaller() are expensive, this object ensures that this creation process is only
 * executed once for each type of unmarshaller used by Jorel2.
 * 
 * @author StuartM
 */

public class JaxbUnmarshallerFactory {
	
	private Unmarshaller rssUnmarshaller = null;
	private Unmarshaller nitfUnmarshaller = null;
	
	/**
	 * Create a JAXB Unmarshaller object for each type of JAXB class used by Jorel2, and store them in their respective
	 * instance variables.
	 */
	public JaxbUnmarshallerFactory () {
		
		try {
			
			// Create unmarshaller for de-serializing RSS feeds
			JAXBContext context;
			context = JAXBContext.newInstance(Rss.class);
	    	rssUnmarshaller = context.createUnmarshaller();
	    	
	    	// Create unmarshaller for de-serializing Nitf (newspaper article) xml files
			context = JAXBContext.newInstance(Nitf.class);
	    	nitfUnmarshaller = context.createUnmarshaller();

		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Get the Unmarshaller for RSS objects.
	 * 
	 * @return The unmarshaller for RSS objects.
	 */
	
	public Unmarshaller getRssUnmarshaller() {
		
		return rssUnmarshaller;
	}

	/**
	 * Get the Unmarshaller for Nitf objects (newspaper article file format [e.g. Globe and Mail]).
	 * 
	 * @return The unmarshaller for Nitf objects.
	 */
	
	public Unmarshaller getNitfUnmarshaller() {
		
		return nitfUnmarshaller;
	}
}
