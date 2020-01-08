package ca.bc.gov.tno.jorel2;

import javax.persistence.MappedSuperclass;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.NamedQuery;

@NamedQuery(name = "Events_FindEventsForProcessing", 
query = "from EventsDao e where e.process=:process and e.lastFtpRun <> :runDate")

/**
 * Maintains a global repository of variable and annotation definitions that are available throughout the system.
 * A primary role of this root superclass is the provision of a global logger.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@MappedSuperclass
public class Jorel2Root {
	
    protected static final Logger logger = LogManager.getLogger(Jorel2Root.class);
    
}
