package ca.bc.gov.tno.jorel2;

import org.springframework.beans.factory.annotation.Value;

/**
 * Indicates the process we're running on (e.g. "jorel", "jorelMini3")
 * 
 * @author Stuart Morse
 * @version 0.0.1
 *
 */
public class Jorel2Process {

	/** The name of the process */
	@Value("${instanceName}")
	private String processName;
	
	Jorel2Process() {
	}
	
	/**
	 * Get the name of the process this execution is running on.
	 * 
	 * @return The name of the current process
	 */
	public String getProcessName() {
		
		return processName;
	}
}