package ca.bc.gov.tno.jorel2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
	
	/**
	 * Takes an sql statement and uses Hibernate's <code>doReturningWork()</code> method to execute the statement and
	 * return a result set containing the list of rows matched by the statement.
	 * 
	 * @param query The query to run
	 * @param session The current Hibernate presistence context
	 * @return The result set containing the list of rows matched by the statement.
	 * @throws SQLException
	 */
	public static ResultSet runSql(String query, Session session) throws SQLException {
		
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

	/**
	 * Takes an sql statement and uses Hibernate's <code>doReturningWork()</code> method to execute and update using the statement and
	 * return the number of records affected by the update.
	 * 
	 * @param query The update query to run.
	 * @param session The current Hibernate presistence context
	 * @return The number of records affected by the update statement.
	 * @throws SQLException
	 */
	public static Integer runUpdateSql(String query, Session session) throws SQLException {
		
       Integer result = session.doReturningWork(new ReturningWork<Integer>() {
            
            public Integer execute(Connection connection) throws SQLException {
 
                Statement cstmt = connection.createStatement();
                cstmt = connection.prepareStatement(query);
				int c = cstmt.executeUpdate(query);
                cstmt.close();
                
                return Integer.valueOf(c);
            }
        });
       
       return result;
	}

}
