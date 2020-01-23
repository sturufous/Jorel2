package ca.bc.gov.tno.jorel2;

/**
 * Indicates the process we're running on (e.g. "jorel", "jorelMini3")
 * 
 * @author Stuart Morse
 * @version 0.0.1
 *
 */
public class Jorel2Process {

	/** The name of the process */
	private String processName;
	
	/**
	 * Takes the process name as the single argument with no setter method, making this an immutable object.
	 * 
	 * @param processName
	 */
	Jorel2Process(String processName) {
		
		this.processName = processName;
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
