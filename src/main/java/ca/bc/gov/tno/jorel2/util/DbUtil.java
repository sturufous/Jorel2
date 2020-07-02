package ca.bc.gov.tno.jorel2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;

import ca.bc.gov.tno.jorel2.model.EventTypesDao;
import ca.bc.gov.tno.jorel2.model.EventsDao;

public class DbUtil {
	
	/**
	 * Updates lastFtpRun of the current event to the value provided.
	 * 
	 * @param value The value to store in lastFtpRun field of currentEvent.
	 * @param currentEvent The monitor event currently being processed.
	 * @param session The current Hibernate persistence context.
	 */
	public static void updateLastFtpRun(String value, EventsDao currentEvent, Session session) {
	
		//Update this record to reflect that it has run and can now be run again
		currentEvent.setLastFtpRun(value);
		session.beginTransaction();
		session.persist(currentEvent);
		session.getTransaction().commit();
	}
	
	public static ResultSet runSql(String query, Session session) {
		
       ResultSet results = session.doReturningWork(new ReturningWork<ResultSet>() {
            
            public ResultSet execute(Connection connection) throws SQLException {
 
            	ResultSet rs = null;
            	
                PreparedStatement cstmt= null;
                cstmt = connection.prepareStatement(query);
                rs = cstmt.executeQuery();
                
                return rs;
            }
        });
       
       return results;
	}
}
