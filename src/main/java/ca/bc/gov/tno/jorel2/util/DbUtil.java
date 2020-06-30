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
	
	public static List<EventTypesDao> runSql(String query, Session session) {
		
       List<EventTypesDao> eTypes = session.doReturningWork(new ReturningWork<List<EventTypesDao>>() {
            
            public List<EventTypesDao> execute(Connection connection) throws SQLException {
 
                List<EventTypesDao> typeList = new ArrayList<EventTypesDao>();
            	EventTypesDao e = null;
                PreparedStatement cstmt= null;
                try {
                    cstmt = connection.prepareStatement(query);
 
                    ResultSet rs = cstmt.executeQuery();
                    while(rs.next()) {
                        e = new EventTypesDao(rs.getBigDecimal(1), rs.getNString(2), rs.getString(3));
                        typeList.add(e);
                    }
                } finally {
                    cstmt.close();
                }
                return typeList;
            }
        });
       
       return eTypes;
	}
}
