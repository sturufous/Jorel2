package ca.bc.gov.tno.jorel2.controller;

import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import ca.bc.gov.tno.jorel2.Jorel2Instance;
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
 * <p>An entry is added to the <code>threadStartTimestamps</code> map each time a thread is started in the <code>run()</code> method. The key for this
 * map is the thread itself, the value is a <code>java.time Instant</code> representing the time at which the thread was started. When the thread 
 * terminates, and <code>notifyComplete()</code> is called, the start time for the thread is compared with the current time and the run time for
 * the thread is written to the log file.
 * </p>
 * <p><code>notifyComplete()</code> also checks all running threads to make sure no thread's run time has exceeded <code>maxThreadRuntime</code> 
 * seconds. 
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
    private Jorel2Instance instance;
    
	/** Queue that lets Jorel2 push threads in one end and pull them out the other. If there are none the scheduler blocks. */
	ArrayBlockingQueue<Thread> threadQueue = null;
	
	/** Used to cycle through the thread names Jorel2Thread-0, -1 and -2. A maximum of three threads can run concurrently. */
	int threadCounter = 3;
	
	/**
	 * Adds the initial three threads and their associated runnable objects to the <code>threadQueue</code>. This is done <code>PostConstruct</code>
	 * so it is guaranteed to finish prior to the first execution of the <code>@Scheduled run()</code> method.
	 */
	@PostConstruct
	public void init() {
		
		threadQueue = new ArrayBlockingQueue<>(THREAD_POOL_SIZE);
		for (int count=0; count < THREAD_POOL_SIZE; count++) {
			
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			thread.setName("Jorel2Thread-" + count + " [" + count + "]");
			threadQueue.add(thread);
		}
	}
	
	/**
	 * Takes a thread from the threadQueue (if one is available) starts it, and stores it's start time in the <code>threadStartTimestamps</code> map.
	 * If no thread is available this method blocks until <code>notifyThreadComplete()</code> pushes a new thred onto the queue.
	 * 
	 * Waiting for too long for a new thread indicates that a pathological condition exists, so the thread is retrieved from the queue using 
	 * <code>ArrayBlockingQueue</code>'s <code>poll(long timeout, TimeUnit unit)</code> method which will timeout after <code>maxThreadRuntime</code>
	 * seconds. In this case an error is logged and the VM will shut down.
	 */
	@Scheduled(fixedDelay = 30000) //cron = "${cron.expression}")
	public void run() {
		try {
			Thread currentThread = threadQueue.poll(instance.getMaxThreadRuntime(), TimeUnit.SECONDS);
			
			if (currentThread == null) { // Timeout occurred
    			IllegalStateException e = new IllegalStateException("Waited too long to obtain a new thread from the thread queue.");
    			logger.error("Waited to obtain a thread from the thread queue for more than " + (instance.getMaxThreadRuntime()/60) + " minutes.", e);
    			System.exit(FATAL_CONDITION);
    		} else {
    			currentThread.start();
    		   	threadStartTimestamps.put((Thread) currentThread, Instant.now());    			
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
    public void notifyThreadComplete(Thread initiator) {
    	
    	try {
    		// If any thread has been running for more than 30 minutes, shut down this Jorel2 process.
    		if (getMaxRunTime() > instance.getMaxThreadRuntime()) {
    			IllegalStateException e = new IllegalStateException("Maximum thread run time exceeded.");
    			logger.error("A Jorel 2 processing thread ran for more than " + (instance.getMaxThreadRuntime()/60) + " minutes.", e);
    			System.exit(FATAL_CONDITION);
    		}
    		
    		// Create a new runnable and add it to the tail of the thread pool to replace the one that terminated
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			thread.setName("Jorel2Thread-" + (threadCounter % THREAD_POOL_SIZE) + " [" + (threadCounter++) + "]");
			threadQueue.put(thread);
			
			threadStartTimestamps.remove(initiator);
		} catch (InterruptedException e) {
			logger.error("Attempting to store the tail of the thread pool.", e);
		}
    	
    	threadStartTimestamps.remove(initiator);
    }
    
    /**
     * Loops through the <code>threadStartTimestamps</code> map and determines which thread has been running for the longest time. This info is used
     * to determine if any thread has been running for longer than <code>maxThreadRuntime</code> seconds.
     * 
     * @return The run-time of the longest running thread.
     */
    public long getMaxRunTime() {
    	
    	long maxRunTime = 0;
    	
    	for (Entry < Thread, Instant > entry : threadStartTimestamps.entrySet()) {
    		long runTime = Instant.now().getEpochSecond() - entry.getValue().getEpochSecond();
    		if (runTime > maxRunTime) {
    			maxRunTime = runTime;
    		}
    	}
		return maxRunTime;
    }
}
