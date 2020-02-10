package ca.bc.gov.tno.jorel2.controller;

import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Manages the execution of Jorel2Runnable objects using a three member ArrayBlockingQueue. Initially the array is populated with three threads
 * and their associated runnable objects. This is done <code>PostConstruct</code> so all injected instance variables are guaranteed to be instantiated.
 * The scheduler is associated with the <code>run()</code> method, which is executed using a schedule defined by the <code>cron.expression</code> in
 * the <code>jorel.properties</code> file. At the time of writing this will spin off a new thread every 30 seconds.
 * 
 * The blocking array insures that there will never be more than three threads executing at the same time. If the array is empty, and the scheduler
 * runs again, it will wait for the <code>notifyComplete()</code> method to push a new thread onto the queue.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public class FifoThreadQueueScheduler extends Jorel2Root {
	
    /** Context from which to extract the Jorel2Thread singleton */
    @Inject
    private ApplicationContext ctx;
    
    /** Map used to record the start times of each thread. This allows the enforcement of MAX_THREAD_RUN_TIME. */
	private Map<Thread, Instant> threadStartTimestamps = new ConcurrentHashMap<>();
	
	/** Queue that lets us push threads in one end and pull them out the other. If there are none the scheduler blocks. */
	ArrayBlockingQueue<Thread> threadPool = null;
	
	/** Used to cycle through the thread names Jorel2Thread-0, -1 and -2. A maximum of three threads can run concurrently. */
	int threadCounter = 0;
	
	@PostConstruct
	public void init() {
		
		threadPool = new ArrayBlockingQueue<>(THREAD_POOL_SIZE);
		for (int count=0; count < THREAD_POOL_SIZE; count++) {
			
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			thread.setName("Jorel2Thread-" + threadCounter++);
			threadPool.add(thread);
		}
	}
	
	@Scheduled(cron = "${cron.expression}")
	public void run() {
		try {
			Thread currentThread = threadPool.take();
			//System.out.println("Thread " + currentThread.getName() + " is alive = " +currentThread.isAlive() + " status = " + currentThread.getState());
			currentThread.start();
		   	threadStartTimestamps.put((Thread) currentThread, Instant.now());
		} catch (InterruptedException e) {
			logger.error("Attempting to get head entry in the thread pool.", e);
		}
	}
	
    public void notifyThreadComplete(Thread initiator) {
    	
    	try {
    		// If any thread has been running for more than 30 minutes, shut down this Jorel2 process.
    		if (getMaxRunTime() > MAX_THREAD_RUN_TIME) {
    			IllegalStateException e = new IllegalStateException("Maximum thread run time exceeded.");
    			logger.error("A Jorel 2 processing thread ran for more than " + (MAX_THREAD_RUN_TIME/60) + " minutes.", e);
    			System.exit(-1);
    		}
    		
    		// Create a new runnable and add it to the tail of the thread pool to replace the one that terminated
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			thread.setName("Jorel2Thread-" + threadCounter++ % THREAD_POOL_SIZE);
			threadPool.put(thread);
			
			if (threadStartTimestamps.size() == THREAD_POOL_SIZE) {
				logger.trace("WARNING: Three threads running concurrently.");
			}
			
			threadStartTimestamps.remove(initiator);
		} catch (InterruptedException e) {
			logger.error("Attempting to store the tail of the thread pool.", e);
		}
    	
    	threadStartTimestamps.remove(initiator);
    }
    
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
