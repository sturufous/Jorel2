package ca.bc.gov.tno.jorel2.controller;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import ca.bc.gov.tno.jorel2.Jorel2Root;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Spring framework service that executes all tasks performed by Jorel2.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

@Service
public class Jorel2Service extends Jorel2Root {
	
    @Autowired
    private TaskExecutor taskExecutor;
    
    @Autowired
    private ApplicationContext ctx;

	/**
	 * Starts all tasks performed by Jorel2.
	 */
	
    @PostConstruct
    public void init(){
        Jorel2Thread myThread = ctx.getBean(Jorel2Thread.class);
        taskExecutor.execute(myThread);
    }
}
