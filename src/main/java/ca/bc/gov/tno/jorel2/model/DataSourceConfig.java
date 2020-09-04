package ca.bc.gov.tno.jorel2.model;

import java.util.Optional;
import java.util.Properties;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import ca.bc.gov.tno.jorel2.Jorel2Root;

/**
 * Abstract class allowing the application to leverage Spring's <code>profile</code> infrastructure for Hibernate configuration.
 * 
 * @author Stuart Morse
 * @version 0.0.1
 */

public abstract class DataSourceConfig extends Jorel2Root {
	
	public abstract Optional<SessionFactory> getSessionFactory();
	
	/**
	 * Constructor who's only function is to write a message recording this object's creation to the Jorel2 log file.
	 */
	protected DataSourceConfig() {
		
		logger.debug("Creating: " + this.getClass().toString());
	}
	
	/**
	 * Creates a org.hibernate.cfg.Configuration object and registers all the Hibernate entity classes used by Jorel2 with it.
	 * 
	 * @return A configuration object populated with all the annotated entity classes used by Jorel2.
	 */
	protected Configuration registerEntities() {
        
		Configuration config = new Configuration();

        // Register all Hibernate classes used in Jorel2
        config.addAnnotatedClass(PreferencesDao.class);
        config.addAnnotatedClass(EventsDao.class);
        config.addAnnotatedClass(EventTypesDao.class);
        config.addAnnotatedClass(NewsItemsDao.class);
        config.addAnnotatedClass(WordsDao.class);
        config.addAnnotatedClass(NewsItemQuotesDao.class);
        config.addAnnotatedClass(PagewatchersDao.class);
        config.addAnnotatedClass(FileQueueDao.class);
        config.addAnnotatedClass(NewsItemImagesDao.class);
        config.addAnnotatedClass(PreferencesDao.class);
        config.addAnnotatedClass(FilesImportedDao.class);
        config.addAnnotatedClass(SourcesDao.class);
        config.addAnnotatedClass(SourcePaperImagesDao.class);
        config.addAnnotatedClass(ImportDefinitionsDao.class);
        config.addAnnotatedClass(SyncIndexDao.class);
        config.addAnnotatedClass(HnewsItemsDao.class);
        config.addAnnotatedClass(SourceTypesDao.class);
        config.addAnnotatedClass(JorelDao.class);
        config.addAnnotatedClass(SavedEmailAlertsDao.class);
        config.addAnnotatedClass(AlertTriggerDao.class);
        config.addAnnotatedClass(PublishedPartsDao.class);
        config.addAnnotatedClass(AutoRunDao.class);
        config.addAnnotatedClass(LastDosyncindexDao.class);
        config.addAnnotatedClass(AlertsDao.class);
        config.addAnnotatedClass(EventActivityLogDao.class);
        config.addAnnotatedClass(FolderItemDao.class);
        config.addAnnotatedClass(ReportStoriesDao.class);
        config.addAnnotatedClass(LdapAddressesDao.class);
        config.addAnnotatedClass(EventClipsDao.class);
        config.addAnnotatedClass(ReportForBlackberryDao.class);
        
        return config;
	}
	
	/**
	 * Creates a java.util.Properties object containing all the information necessary to establish a connection with the database.
	 * 
	 * @param systemName The name of the system hosting the TNO database.
	 * @param port The port on which the database is listening for connections.
	 * @param sid The system id of the database.
	 * @param userId The id of the user used to sign in to the database.
	 * @param userPw The password of the user used to sign in to the database.
	 * @param dialect The Hibernate dialect to use in communication with the database.
	 * @return a Properties object containing the data passed in the parameters keyed by org.hibernate.cfg.Enviroment values.
	 */
	
	protected Properties populateSettings(String systemName, String port, String sid, String userId, String userPw, String dialect) {
		
		Properties settings = new Properties();
		
        settings.put(Environment.DRIVER, "oracle.jdbc.OracleDriver");
        settings.put(Environment.URL, "jdbc:oracle:thin:@" + systemName + ":" + port + ":" + sid);
        settings.put(Environment.USER, userId);
        settings.put(Environment.PASS, userPw);
        settings.put(Environment.DIALECT, dialect);
        settings.put("checkoutTimeout", DB_CONNECTION_TIMEOUT);
        //settings.put(Environment.SHOW_SQL, "true");
        
        return settings;
	}
}