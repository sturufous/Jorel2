package ca.bc.gov.tno.jorel2.jaxb;

import org.xml.sax.InputSource;

import com.sun.istack.NotNull;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
 
public final class JaxBWrapper<T> {
 
    private final Unmarshaller unmarshaller;
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private static final Map<ContextDescriptor, Unmarshaller> cache = new HashMap();
 
    /**
     * Creating the JAXB unmashaller via :
     * <p/>
     * javax.xml.bind.JAXBContext.newInstance() is a very slow operation, you can greating improve your performance if you create a cache of these instances, we do this by wrapping the call to newInstance with the following code
     */
    private static class ContextDescriptor {
 
        private final String jaxbContext;
        private final String schemaLocation;
 
        public boolean equals(Object o) {
            if (o instanceof ContextDescriptor) {
                final ContextDescriptor un = ((ContextDescriptor) o);
                return jaxbContext.equals(un.jaxbContext) && schemaLocation.equals(un.schemaLocation);
            }
            return false;
        }
 
        public int hashCode() {
            return (jaxbContext + schemaLocation).hashCode();
        }
 
        ContextDescriptor(String jaxbContext, String schemaLocation) {
            this.jaxbContext = jaxbContext.trim();
            this.schemaLocation = schemaLocation.trim();
        }
    }
 
    public JaxBWrapper(final String jaxbContext, final String schemaLocation) {
        unmarshaller = getInstance(jaxbContext, schemaLocation);
    }
 
    public JaxBWrapper(final String jaxbContex) {
        this(jaxbContex, "your root xsd file path");
    }
 
    private static Unmarshaller getInstance(final String jaxbContext, final String schemaLocation) {
 
        final ContextDescriptor contextDescriptor = new ContextDescriptor(jaxbContext, schemaLocation);
        final Unmarshaller unmarshaller = cache.get(contextDescriptor);
 
        if (unmarshaller != null)
            return unmarshaller;
 
        final Unmarshaller result = newInstance(jaxbContext, schemaLocation);
        cache.put(contextDescriptor, result);
        return result;
    }
 
    private static Unmarshaller newInstance(final String jaxbContext, final String schemaLocation) {
        try {
            final JAXBContext context = JAXBContext.newInstance(jaxbContext);
            final Unmarshaller result = context.createUnmarshaller();
 
            final SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
 
            try {
                URI uri = Thread.currentThread().getContextClassLoader().getResource(schemaLocation).toURI();
                final Schema s = schemaFactory.newSchema(toStreamSources(uri));
                result.setSchema(s);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return result;
        }
        catch (JAXBException e) {
            throw new RuntimeException("Exception occured creating JAXB unmarshaller for context=" + jaxbContext, e);
        }
 
    }
 
    @SuppressWarnings("unchecked")
	public T unmarshallXMLString(final String xmlString) throws Exception {
        final StringReader sr = new StringReader(xmlString.trim());
        final InputSource is = new InputSource(sr);
        return (T) unmarshaller.unmarshal(is);
    }
 
    static Source[] toStreamSources(@NotNull final URI stream) {
        return new Source[]{new StreamSource(stream.toString())};
    }
}