package ca.bc.gov.tno.jorel2.controller;

import java.util.Optional;

import org.hibernate.Session;

/**
 * Interface for Jorel2 even processor services.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public interface Jorel2EventProcessor {

	Optional<String> processEvents(Session session);
}
