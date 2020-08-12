package ca.bc.gov.tno.jorel2;

import java.time.Instant;

import ca.bc.gov.tno.jorel2.controller.Jorel2Runnable;

public class Jorel2ThreadInstance {
    	
	long startTime = 0;
	Thread thisThread = null;
	Jorel2Runnable runnable = null;
	long timeoutSeconds = 0;

	public Jorel2ThreadInstance(Thread thread, Jorel2Runnable runnable, long dfltTimeout) {
		
		this.thisThread = thread;
		this.runnable = runnable;
		this.timeoutSeconds = dfltTimeout;
	}
	
	public void start() {
		
		startTime = Instant.now().getEpochSecond();
		thisThread.start();
	}
	
	public long getStartTime() {
		
		return startTime;
	}
	
	public void setTimeoutSeconds(long seconds) {
		
		this.timeoutSeconds = seconds;
	}
	
	public boolean hasTimedOut() {
		
		boolean timedOut = false;
		
		long runTime = Instant.now().getEpochSecond() - startTime;
		
		if (timeoutSeconds > 0) {
			timedOut = runTime >= timeoutSeconds;
		}
		
		return timedOut;
	}
	
	public long getDurationSeconds() {
	
		return Instant.now().getEpochSecond() - startTime;
	}
}
