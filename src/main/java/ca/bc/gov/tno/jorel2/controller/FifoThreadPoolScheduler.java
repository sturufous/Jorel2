package ca.bc.gov.tno.jorel2.controller;

import java.time.Instant;
import java.util.AbstractQueue;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import ca.bc.gov.tno.jorel2.Jorel2Root;

public class FifoThreadPoolScheduler extends Jorel2Root {
	
    /** Context from which to extract the Jorel2Thread singleton */
    @Inject
    private ApplicationContext ctx;
    
	private Map<Thread, Instant> threadStartTimestamps = new ConcurrentHashMap<>();
	
	ArrayBlockingQueue<Thread> threadPool = null;
	
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
	
	@Scheduled(fixedRate = 5000)
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
			Jorel2Runnable runnable = ctx.getBean(Jorel2Runnable.class);
			Thread thread = new Thread(runnable);
			thread.setName("Jorel2Thread-" + threadCounter++ % THREAD_POOL_SIZE);
			threadPool.put(thread);
			
			Instant runTime = threadStartTimestamps.get(initiator);
			long runtimeSecond = Instant.now().getEpochSecond() - runTime.getEpochSecond();
			System.out.println("Thread " + initiator.getName() + " took " + runtimeSecond + " to complete.");
			threadStartTimestamps.remove(initiator);
		} catch (InterruptedException e) {
			logger.error("Attempting to store the tail of the thread pool.", e);
		}
    	threadStartTimestamps.remove(initiator);
    	
    }
    
    public long getMaxRunTime() {
    	
    	long max = 0;
    	
    	for (Entry<Thread, Instant> entry : threadStartTimestamps.entrySet()) {
    		long i = Instant.now().getEpochSecond() - entry.getValue().getEpochSecond();
    	}
		return threadCounter;
    }
}
