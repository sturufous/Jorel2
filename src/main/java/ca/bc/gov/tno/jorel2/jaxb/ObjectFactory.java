//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.0 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2020.05.12 at 03:12:28 PM PDT 
//


package ca.bc.gov.tno.jorel2.jaxb;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the ca.bc.gov.tno.jorel2.jaxb package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: ca.bc.gov.tno.jorel2.jaxb
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Nitf }
     * 
     * @return a new Nift object.
     */
    public Nitf createNitf() {
        return new Nitf();
    }

    /**
     * Create an instance of {@link Nitf.Body }
     * 
     * @return the Body object for this Nitf instance.
     */
    public Nitf.Body createNitfBody() {
        return new Nitf.Body();
    }

    /**
     * Create an instance of {@link Nitf.Body.BodyHead }
     * 
     * @return the BodyHead object for this Nitf instance.
     */
    public Nitf.Body.BodyHead createNitfBodyBodyHead() {
        return new Nitf.Body.BodyHead();
    }

    /**
     * Create an instance of {@link Nitf.Head }
     * 
     * @return the Head object for this Nitf instance.
     */
    public Nitf.Head createNitfHead() {
        return new Nitf.Head();
    }

    /**
     * Create an instance of {@link Nitf.Body.BodyHead.Hedline }
     * 
     * @return The Hedline object for this nitf instance
     */
    public Nitf.Body.BodyHead.Hedline createNitfBodyBodyHeadHedline() {
    	
        return new Nitf.Body.BodyHead.Hedline();
    }

    /**
     * Create an instance of {@link Nitf.Body.BodyHead.Dateline }
     * 
     * @return The Dateline object for this Nitf instance.
     */
    public Nitf.Body.BodyHead.Dateline createNitfBodyBodyHeadDateline() {
        return new Nitf.Body.BodyHead.Dateline();
    }

    /**
     * Create an instance of {@link Nitf.Head.Pubdata }
     * 
     * @return The pubdata object for this Nitf instance.
     */
    public Nitf.Head.Pubdata createNitfHeadPubdata() {
    	
        return new Nitf.Head.Pubdata();
    }

}
