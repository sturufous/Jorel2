package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 * Indicates the process we're running on (e.g. "jorel", "jorelMini3")
 * 
 * @author Stuart Morse
 * @version 0.0.1
 *
 */

@ManagedResource(
        objectName="Jorel2Instance:name=jorel2Mbean",
        description="Jorel2 Managed Bean",
        log=true,
        logFile="jmx.log",
        currencyTimeLimit=15,
        persistPolicy="OnUpdate",
        persistPeriod=200,
        persistLocation="foo",
        persistName="bar")
public class Jorel2Process {

	/** The name of the process */
	@Value("${instanceName}")
	public String processName;
	
	Jorel2Process() {
	}
	
	/**
	 * Get the name of the process this execution is running on.
	 * 
	 * @return The name of the current process
	 */
	
	@ManagedAttribute(description="Name of this Jorel instance", currencyTimeLimit=15)
	public String getProcessName() {
		
		return processName;
	}
}
