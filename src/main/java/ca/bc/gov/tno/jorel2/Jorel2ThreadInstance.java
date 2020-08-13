package ca.bc.gov.tno.jorel2;

import java.time.Instant;
import java.util.Map;

import ca.bc.gov.tno.jorel2.controller.Jorel2Runnable;

/**
 * Provides a thread instance, house-keeping methods and relevant meta-data that enables the handling of multiple threads in Jorel2. 
 * 
 * @author StuartM
 *
 */
public class Jorel2ThreadInstance extends Jorel2Root {
    	
	/** The UNIX Epoch time in seconds when this thread started */
	long startTime = 0;
	
	/** The thread instance managed by this object */
	Thread thisThread = null;
	
	/** The runnable object being executed in this thread */
	Jorel2Runnable runnable = null;
	
	/** The timeout, in seconds, of this thread (updatable based on the current event) */
	long timeoutSeconds = 0;
	
	long cyclePosition = 0;

	/**
	 * Create a Jorel2ThreadInstance containing a not-yet-started thread, the runnable object it executes and a default
	 * timeout in seconds (which is retrieved from the properties file's maxThreadRuntime attribute).
	 * 
	 * @param thread The thread to manage execution.
	 * @param runnable The runnable object on which execution will take place.
	 * @param dfltTimeout The default timeout in seconds.
	 */
	public Jorel2ThreadInstance(Thread thread, Jorel2Runnable runnable, long dfltTimeout) {
		
		this.thisThread = thread;
		this.runnable = runnable;
		this.timeoutSeconds = dfltTimeout;
	}
	
	/**
	 * Start the thread associated with this object and set the object's <code>startTime</code> to the current UNIX Epoch time.
	 */
	public void start() {
		
		startTime = Instant.now().getEpochSecond();
		thisThread.start();
	}
	
	/**
	 * Return the UNIX Epoch start time of this thread (in seconds).
	 * 
	 * @return The start time of this thread.
	 */
	public long getStartTime() {
		
		return startTime;
	}
	
	/**
	 * Set the timeout of this thread. This is performed in each EventProcessor object based on the timeout value from the EVENTS table.
	 * As each thread can process a number of different events this timeout may change during thread execution.
	 * 
	 * @param seconds The number of seconds this thread can run before it times out.
	 */
	public void setTimeoutSeconds(long seconds) {
		
		this.timeoutSeconds = seconds;
	}
	
	/**
	 * Returns true if the interval between this thread's startTime and the current UNIX Epoch time is greater than the current value
	 * of <code>timeoutSeconds</code> and false otherwise.
	 * 
	 * @return Whether this thread has timed out or not.
	 */
	public boolean hasTimedOut() {
		
		boolean timedOut = false;
		
		long runTime = Instant.now().getEpochSecond() - startTime;
		
		if (timeoutSeconds > 0) {
			timedOut = runTime >= timeoutSeconds;
		}
		
		return timedOut;
	}
	
	/**
	 * Returns the duration of the current thread's execution in seconds.
	 * 
	 * @return How long this thread has been executing.
	 */
	public long getDurationSeconds() {
	
		return Instant.now().getEpochSecond() - startTime;
	}
	
	/**
	 * Sets the name of the thread associated with this object. The format is:
	 * 
	 * "Jorel2Thread-" + threadCounter
	 * 
	 * @param thread The thread to be named.
	 * @param threadCounter The sequence number of this thread.
	 */
	public void setName(Thread thread, long threadCounter) {
				
		thread.setName("Jorel2Thread-" + (threadCounter));
	}
}
