package ca.bc.gov.tno.jorel2.controller;

import java.util.Optional;

import org.hibernate.Session;

import ca.bc.gov.tno.jorel2.Jorel2Process;

/**
 * Interface for Jorel2 even processor services.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public interface Jorel2EventProcessor {

	Optional<String> processEvents(String eventType, Session session);
}
