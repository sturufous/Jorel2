package ca.bc.gov.tno.jorel2.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Component;
import ca.bc.gov.tno.jorel2.Jorel2Process;
import ca.bc.gov.tno.jorel2.Jorel2Root;
import ca.bc.gov.tno.jorel2.model.DataSourceConfig;
import ca.bc.gov.tno.jorel2.model.WordsDao;

@Component
public class QuoteExtractor extends Jorel2Root {
	
	/** Process we're running as (e.g. "jorel", "jorelMini3") */
	@Inject
	private Jorel2Process process;
	
	/** Configuration object for the active data source. Contains system_name, port etc. */
	@Inject
	private DataSourceConfig config;
	
	/** Verbs used for quote extraction (e.g. relented, summed, blinked, drawled) currently 1372 instances */
	Set<String> verbs = null;
	
	/** Titles used for quote extraction (e.g. mr., dr., lieut.) currently 10 instances */  
	Set<String> titles = null;
	
	/** Noise words used for quote extraction (e.g. while, by, with, from) currently 28 instances */
	Set<String> noiseWords = null;
	
	/** NoiseNameWords used for quote extraction (e.g. Family School, Langley RCMP) currently 8062 instances */  
	Set<String> noiseNameWords = null;
	
	/** Hibernate persistence context for all quote related activities */
	Session session;
	
    public void init() {
    	
    	Optional<SessionFactory> sessionFactory = config.getSessionFactory();
        
    	if(sessionFactory.isEmpty()) {
    		logger.error("Getting TNO session factory.", new IllegalStateException("No session factory provided."));
    		System.exit(-1);
    	} else {
    		
    		if (verbs == null || titles == null || noiseWords == null || noiseNameWords == null) {
		        session = sessionFactory.get().openSession();
		        
				verbs = loadWords(WordsDao.getWordsByWordType(process, WordType.VERB, session));
				titles = loadWords(WordsDao.getWordsByWordType(process, WordType.TITLE, session));
				noiseWords = loadWords(WordsDao.getWordsByWordType(process, WordType.NOISE, session));
				noiseNameWords = loadWords(WordsDao.getWordsByWordType(process, WordType.NOISENAME, session));
    		}
    	}
    }

    private Set<String> loadWords(List<WordsDao> results) {
    	
    	Set<String> wordSet = new HashSet<>();
    	
    	for (WordsDao word : results) {
    		wordSet.add(word.getWord());
    	}
    	
    	return wordSet;
    }
}
