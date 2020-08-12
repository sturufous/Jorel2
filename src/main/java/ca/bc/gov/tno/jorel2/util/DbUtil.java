package ca.bc.gov.tno.jorel2.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;

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
            	
                Statement stmt = null;
                stmt = connection.createStatement();
                connection.beginRequest();
                rs = stmt.executeQuery(query);
                connection.endRequest();
                
                return rs;
            }
        });
       
       return results;
	}

	/**
	 * Takes an sql statement and uses Hibernate's <code>doReturningWork()</code> method to execute the statement and
	 * return a result set containing the list of rows matched by the statement. Uses the TYPE_SCROLL_INSENSITIVE and
	 * CONCUR_READ_ONLY flags when creating the Statement object.
	 * 
	 * @param query The query to run
	 * @param session The current Hibernate presistence context
	 * @return The result set containing the list of rows matched by the statement.
	 * @throws SQLException
	 */
	public static ResultSet runSqlFlags(String query, Session session) throws SQLException {
		
       ResultSet results = session.doReturningWork(new ReturningWork<ResultSet>() {
            
            public ResultSet execute(Connection connection) throws SQLException {
 
            	ResultSet rs = null;
            	
                Statement cstmt = null;
                cstmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                rs = cstmt.executeQuery(query);
                
                return rs;
            }
        });
       
       return results;
	}
	
	/**
	 * Takes an update sql statement and uses Hibernate's <code>doReturningWork()</code> method to execute and update using the statement and
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
 
           		boolean autoCommit = connection.getAutoCommit();
           		int count = 0;
           		
           		connection.setAutoCommit(true);
                
            	try {
	                Statement cstmt = connection.createStatement();
	                cstmt = connection.prepareStatement(query);
					count = cstmt.executeUpdate(query);
	                cstmt.close();
            	}
                catch (SQLException e) {
                	connection.setAutoCommit(autoCommit);
                	throw e;
                }
                
            	connection.setAutoCommit(autoCommit);
                return Integer.valueOf(count);
            }
        });
       
       return result;
	}

	static public void filterSQLWhere(String rsn, String defaultOrder, long currentPeriod, Properties filterProperties, Session session) {
		String defaultWhere="";
		String orderBy=defaultOrder;
		String defaultOtherFiles="";
		String dateWhere="";
		String dateClause="";
		String text="";
		String name="";
		String whichTables="";
		long fi_rsn = 0;
		long user_rsn = 0;
		boolean is_auto_run = false;
		boolean is_auto_folder = false;
		String folder_name = "";
		float buzz = 0;
		long itemsforbuzz = 0;
		
		text = "Only Front Page Stories<br>";
		
		// load the filter, the SQL where clause, SQL for other files required and the text description of the where clause
		ResultSet rs=null;
		try {
			String sqlString = "select otherfiles, whereclause, wheretext, orderby, name, rsn, auto_run, auto_folder, folder_name, user_rsn, buzz, itemsforbuzz from tno.filters where rsn = " + rsn;
			rs = DbUtil.runSql(sqlString, session);
			if ( rs.next()) {
				defaultOtherFiles = rs.getString(1);
				defaultWhere = rs.getString(2);
				text = rs.getString(3) ;
				orderBy = rs.getString(4);
				name = rs.getString(5);
				fi_rsn = rs.getLong(6);
				is_auto_run = rs.getBoolean(7);
				is_auto_folder = rs.getBoolean(8);
				folder_name = rs.getString(9);
				user_rsn = rs.getLong(10);
				buzz = rs.getFloat(11);
				itemsforbuzz = rs.getLong(12);

				if (text == null) text = "";
				if (orderBy == null) orderBy = "";
				if (orderBy == null) orderBy = defaultOrder; else orderBy = orderBy.trim();
				if (folder_name == null) folder_name = "";
			}
		} catch (SQLException err) {;}
		try{if(rs != null) rs.close();} catch(SQLException err){;}
		if (defaultWhere.length() == 0) return;

		// check where clause for SYSDATE in which case, no need to tweak the date
		// If SYSDATE not in the where clause, use the current date for the filter
		String whereLC = defaultWhere.toLowerCase();
		int p = whereLC.indexOf("sysdate");
		if (p < 0) {
			// Use today's date for the filter to be triggered, here is the date part of the where clause
			dateWhere = "n.item_date = to_date('" + DateUtil.today() + "','DD-MON-YYYY') ";
			dateClause = "Item Date is " + DateUtil.today();

			// find and remove any date clause in the current whereclause stored in the database
			p = defaultWhere.indexOf("n.item_date");
			int e = -1;
			while (p >= 0) {
				e = defaultWhere.indexOf(" and ",p);
				if (e > 0)
					defaultWhere = defaultWhere.substring(0,p) + defaultWhere.substring(e+5);
				else
					defaultWhere = defaultWhere.substring(0,p);
				p = defaultWhere.indexOf("n.item_date");
			}
			if (e < 0)
				defaultWhere = defaultWhere + dateWhere;
			else
				defaultWhere = defaultWhere + " and " + dateWhere;

		} else {

			// SYSDATE is in the where clause
			dateClause = whereLC.substring(p + 6);
			p = dateClause.indexOf(" and ");
			if (p > 0) dateClause = dateClause.substring(0,p);

			String period = "";
			for (int i = 0; i < dateClause.length(); i++) {
				String x = dateClause.substring(i, i + 1);
				if ((x.compareTo("0") >= 0) & (x.compareTo("9") <= 0)) period = period + x;
			}
			int d = 0;
			try {
				d = Integer.parseInt(period);
			} catch (NumberFormatException err) {d = 0;}
			if (d > currentPeriod) whichTables = "both";
		}
		p = whereLC.indexOf("#words#");
		if (p > 0) defaultWhere = StringUtil.replacement(defaultWhere,"#words#",text);

		filterProperties.put("defaultWhere", defaultWhere);
		filterProperties.put("whereLC", whereLC);
		filterProperties.put("orderBy", defaultOrder);
		filterProperties.put("defaultOtherFiles", defaultOtherFiles);
		filterProperties.put("dateWhere", dateWhere);
		filterProperties.put("dateClause", dateClause);
		filterProperties.put("text", text);
		filterProperties.put("name", name);
		filterProperties.put("whichTables", whichTables);
		
		filterProperties.put("fi_rsn", Long.toString(fi_rsn));
		filterProperties.put("user_rsn", Long.toString(user_rsn));
		filterProperties.put("is_auto_run", Boolean.toString(is_auto_run));
		filterProperties.put("is_auto_folder", Boolean.toString(is_auto_folder));
		filterProperties.put("folder_name", folder_name);
		filterProperties.put("buzz", Float.toString(buzz));
		filterProperties.put("itemsforbuzz", Long.toString(itemsforbuzz));
	}
}
