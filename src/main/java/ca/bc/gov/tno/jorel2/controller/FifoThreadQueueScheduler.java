package ca.bc.gov.tno.jorel2.controller;

import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import ca.bc.gov.tno.jorel2.Jorel2ServerInstance;
import ca.bc.gov.tno.jorel2.Jorel2ThreadInstance;
import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * <p>Manages the execution of Jorel2Runnable objects using a three member ArrayBlockingQueue. Initially the array is populated with three threads
 * and their associated runnable objects. This is done <code>PostConstruct</code> so all injected instance variables are guaranteed to be instantiated.
 * The scheduler is associated with the <code>run()</code> method, which is executed using a schedule defined by the <code>cron.expression</code> in
 * the <code>jorel.properties</code> file. Scheduled execution of this method is guaranteed to remain dormant until all PostConstruc methods have completed.
 * At the time of writing the <code>run()</code> method will spin off a new thread every 30 seconds. There is no explicit call from any of the Jorel2 code 
 * that starts this process, Spring handles everything.
 * </p>
 * <p>The blocking array insures that there will never be more than three threads executing at the same time. If the array is empty, and the scheduler
 * runs again, it will wait for the <code>notifyComplete()</code> method to push a new thread onto the queue. This happens when a previously executed
 * thread completes. It is the last call from the Jorel2Runnable <code>run()</code> method.
 * </p>
 * <p>An entry is added to the <code>activeThreads</code> map each time a thread is started in the <code>run()</code> method. This manages the
 * <code>Thread</code> object that is currently running and allows a separate timeout to be defined for each thread. This is necessary due to the
 * wide range of execution times expected for individual events. For instance, it might be perfectly reasonable for the LDAP event to complete in
 * over 45 minutes, while an execution time of five minutes might be excessive for the RSS event.
 * </p>
 * <p><code>notifyComplete()</code> also checks all running threads to make sure no thread's run time has exceeded it's configured timeout period.
 * </p>
 * <p>The blocking function of the threadQueue ensures that the scheduled process waits until one of the three threads completes, thus avoiding the 
 * proliferation of blocked threads.
 * </p>
 * @author Stuart Morse
 * @version 0.0.1
 */

public class FifoThreadQueueScheduler extends Jorel2Root {
	
    /** Context from which to extract the Jorel2Thread Prototype */
    @Inject
    private ApplicationContext ctx;
    
    @Inject
    private Jorel2ServerInstance instance;
    
	/** Queue that lets Jorel2 push threads in one end and pull them out the other. If there are none the scheduler blocks. */
	private ArrayBlockingQueue<Jorel2ThreadInstance> threadQueue = null;
	
	/** Used to cycle through the thread names Jorel2Thread-0, -1 and -2. A maximum of three threads can run concurrently. */
	private volatile int threadCounter = 3;
	
	/** Apache commons object that loads the contents of jorel.properties and watches it for changes */
	@Inject
	public PropertiesConfiguration config;
	
	/** The default thread timeout as defined in the properties file */
	private long dfltTimeout = 0;

	/**
	 * Adds the initial three threads and their associated runnable objects to the <code>threadQueue</code>. This is done <code>PostConstruct</code>
	 * so it is guaranteed to finish prior to the first execution of the <code>@Scheduled run()</code> method.
	 */
	@PostConstruct
	public void init() {
		
		threadQueue = new ArrayBlockingQueue<>(THREAD_POOL_SIZE);
		dfltTimeout = instance.config.getLong("maxThreadRuntime");
		for (int count=0; count < THREAD_POOL_SIZE; count++) {
			
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			Jorel2ThreadInstance jorelThread = new Jorel2ThreadInstance(thread, runnable, dfltTimeout);
			jorelThread.setName(thread, count);
			runnable.setJorel2ThreadInstance(jorelThread);
			threadQueue.add(jorelThread);
		}
	}
	
	/**
	 * Takes a <code>Jorel2ThreadInstance</code> from the threadQueue (if one is available) starts it, and stores it's start time in the object.
	 * If no thread is available this method blocks until <code>notifyThreadComplete()</code> pushes a new thread onto the queue.
	 * 
	 * Waiting for too long for a new thread indicates that a pathological condition exists, so the thread is retrieved from the queue using 
	 * <code>ArrayBlockingQueue</code>'s <code>poll(long timeout, TimeUnit unit)</code> method which will timeout after the global property
	 * <code>maxThreadRuntime</code> seconds. In this case an error is logged and the VM will shut down.
	 */
	@Scheduled(cron = "0/30 * * * * ?") // When testing is finished, uncomment @PropertySource(value = "file:properties/jorel.properties")
	public void run() {
		try {
			Jorel2ThreadInstance currentThread = threadQueue.poll(instance.getMaxThreadRuntime(), TimeUnit.SECONDS);
			
			if (currentThread == null) { // Timeout occurred
    			IllegalStateException e = new IllegalStateException("Waited too long to obtain a new thread from the thread queue.");
    			logger.error("Waited to obtain a thread from the thread queue for more than " + (instance.getMaxThreadRuntime()/60) + " minutes.", e);
    			System.exit(FATAL_CONDITION);
    		} else {
    			currentThread.start();
    		   	activeThreads.put(currentThread, "");    			
    		}
		} catch (InterruptedException e) {
			logger.error("Attempting to get head entry in the thread pool.", e);
		}
	}
	
	/**
	 * Invokes the <code>getBean()</code> method of Spring's <code>ApplicationContext</code> to create a new instance of Jorel2Runnable. As the
	 * <code>Jorel2Runnable</code> bean is annotated with <code>@Prototype</code> this method creates a new thread instance every time it is called. 
	 * All other beans used by Jorel2 are singletons. This approach gives Spring control to instantiate all injected instance variables in 
	 * <code>Jorel2Runnable</code>, of which there are six at this time of writing. 
	 * 
	 * This method also monitors all running threads to ensure their run-times do not exceed <code>maxThreadRuntime</code> seconds. If this
	 * condition is violated a message is written to the log and Jorel2 will shut down. A warning is also written to the log if all three threads were
	 * running when this message was called. This might indicate a problem if it continues to occur.
	 * 
	 * @param initiator The thread who's <code>run()</code> method just completed.
	 */
    public void notifyThreadComplete(Jorel2ThreadInstance initiator) {
    	
    	try {
    		// If any thread has been running for more than 30 minutes (the max runtime property), shut down this Jorel2 process.
    		if (threadTimeoutOccurred()) {
    			IllegalStateException e = new IllegalStateException("Maximum thread run time exceeded.");
    			logger.error("A Jorel 2 processing thread ran for more than its defined timeout period.", e);
    			System.exit(FATAL_CONDITION);
    		}
    		
    		// Create a new runnable and add it to the tail of the thread pool to replace the one that terminated
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			Jorel2ThreadInstance jorelThread = new Jorel2ThreadInstance(thread, runnable, dfltTimeout);
			jorelThread.setName(thread, threadCounter++);
			runnable.setJorel2ThreadInstance(jorelThread);
			threadQueue.put(jorelThread);
			
			activeThreads.remove(initiator);
		} catch (InterruptedException e) {
			logger.error("Attempting to store the tail of the thread pool.", e);
		}
    }
    
    /**
     * Loops through the <code>activeThreads</code> map and determines if any active threads have exceeded their maximum runTimes.
     * 
     * @return Whether any thread in the activeThreads map has timed out.
     */
    public boolean threadTimeoutOccurred() {
    	
    	boolean timedOut = false;
    	
    	for (Entry <Jorel2ThreadInstance, String> entry : activeThreads.entrySet()) {
    		Jorel2ThreadInstance currentThread = entry.getKey();
    		if (currentThread.hasTimedOut()) {
    			timedOut = true;
    		}
    	}
    	
		return timedOut;
    }
}
