/*
 *                        AT&T - PROPRIETARY
 *          THIS FILE CONTAINS PROPRIETARY INFORMATION OF
 *        AT&T AND IS NOT TO BE DISCLOSED OR USED EXCEPT IN
 *             ACCORDANCE WITH APPLICABLE AGREEMENTS.
 *
 *          Copyright (c) 2013 AT&T Knowledge Ventures
 *              Unpublished and Not for Publication
 *                     All Rights Reserved
 */

package com.att.research.datarouter.provisioning.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import org.apache.log4j.Logger;

import com.att.research.datarouter.provisioning.beans.DeliveryRecord;
import com.att.research.datarouter.provisioning.beans.ExpiryRecord;
import com.att.research.datarouter.provisioning.beans.Loadable;
import com.att.research.datarouter.provisioning.beans.PublishRecord;

/**
 * Load the DB JDBC driver, and manage a simple pool of connections to the DB.
 *
 * @author Robert Eby
 * @version $Id$
 */
public class DB {
	/** The name of the properties file (in CLASSPATH) */
	public static final String CONFIG_FILE = "provserver.properties";

	private static String DB_DRIVER   = "com.mysql.jdbc.Driver";
	private static String DB_URL      = "jdbc:mysql://127.0.0.1:3306/datarouter";
	private static String DB_LOGIN    = "datarouter";
	private static String DB_PASSWORD = "datarouter";
	private static Properties props;
	private static Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
	private static Queue<Connection> queue = new LinkedList<Connection>();

	/**
	 * Construct a DB object.  If this is the very first creation of this object, it will load a copy
	 * of the properties for the server, and attempt to load the JDBC driver for the database.  If a fatal
	 * error occurs (e.g. either the properties file or the DB driver is missing), the JVM will exit.
	 */
	public DB() {
		if (props == null) {
			props = new Properties();
			InputStream inStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
			try {
				props.load(inStream);
				DB_DRIVER   = (String) props.get("com.att.research.datarouter.db.driver");
				DB_URL      = (String) props.get("com.att.research.datarouter.db.url");
				DB_LOGIN    = (String) props.get("com.att.research.datarouter.db.login");
				DB_PASSWORD = (String) props.get("com.att.research.datarouter.db.password");
				Class.forName(DB_DRIVER);
			} catch (IOException e) {
				intlogger.fatal("PROV9003 Opening properties: "+e.getMessage());
				e.printStackTrace();
				System.exit(1);
			} catch (ClassNotFoundException e) {
				intlogger.fatal("PROV9004 cannot find the DB driver: "+e);
				e.printStackTrace();
				System.exit(1);
			} finally {
				try {
					inStream.close();
				} catch (IOException e) {
				}
			}
		}
	}
	/**
	 * Get the provisioning server properties (loaded from provserver.properties).
	 * @return the Properties object
	 */
	public Properties getProperties() {
		return props;
	}
	/**
	 * Get a JDBC connection to the DB from the pool.  Creates a new one if none are available.
	 * @return the Connection
	 * @throws SQLException
	 */
	@SuppressWarnings("resource")
	public Connection getConnection() throws SQLException {
		Connection c = null;
		while (c == null) {
			synchronized (queue) {
				try {
					c = queue.remove();
				} catch (NoSuchElementException e) {
					int n = 0;
					do {
						// Try up to 3 times to get a connection
						try {
							c = DriverManager.getConnection(DB_URL, DB_LOGIN, DB_PASSWORD);
						} catch (SQLException e1) {
							if (++n >= 3)
								throw e1;
						}
					} while (c == null);
				}
			}
			if (c != null && !c.isValid(1)) {
				c.close();
				c = null;
			}
		}
		return c;
	}
	/**
	 * Returns a JDBC connection to the pool.
	 * @param c the Connection to return
	 * @throws SQLException
	 */
	public void release(Connection c) {
		if (c != null) {
			synchronized (queue) {
				if (!queue.contains(c))
					queue.add(c);
			}
		}
	}

	/**
	 * Run all necessary retrofits required to bring the database up to the level required for this version
	 * of the provisioning server.  This should be run before the server itself is started.
	 * @return true if all retrofits worked, false otherwise
	 */
	public boolean runRetroFits() {
		return retroFit1()
			&& retroFit2()
			&& retroFit3()
			&& retroFit4()
			&& retroFit5()
			&& retroFit6()
			&& retroFit7()
			&& retroFit8()
			&& retroFit9()  //New retroFit call to add CREATED_DATE column Rally:US674199 - 1610
			&& retroFit10() //New retroFit call to add BUSINESS_DESCRIPTION column Rally:US708102 - 1610
			&& retroFit11() //New retroFit call for groups feature Rally:US708115 - 1610	
			;
	}
	/**
	 * Retrofit 1 - Make sure the expected tables are in MySQL and are initialized.
	 * Uses mysql_init_0000 and mysql_init_0001 to setup the DB.
	 * @return true if the retrofit worked, false otherwise
	 */
	private boolean retroFit1() {
		final String[] expected_tables = {
			"FEEDS", "FEED_ENDPOINT_ADDRS", "FEED_ENDPOINT_IDS", "PARAMETERS", "SUBSCRIPTIONS"
		};
		Connection c = null;
		try {
			c = getConnection();
			Set<String> tables = getTableSet(c);
			boolean initialize = false;
			for (String s : expected_tables) {
				initialize |= !tables.contains(s);
			}
			if (initialize) {
				intlogger.info("PROV9001: First time startup; The database is being initialized.");
				runInitScript(c, 0);		// script 0 creates the provisioning tables
				runInitScript(c, 1);		// script 1 initializes PARAMETERS
			}
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	/**
	 * Retrofit 2 - if the LOG_RECORDS table is missing, add it.
	 * Uses mysql_init_0002 to create this table.
	 * @return true if the retrofit worked, false otherwise
	 */
	private boolean retroFit2() {
		Connection c = null;
		try {
			// If LOG_RECORDS table is missing, add it
			c = getConnection();
			Set<String> tables = getTableSet(c);
			if (!tables.contains("LOG_RECORDS")) {
				intlogger.info("PROV9002: Creating LOG_RECORDS table.");
				runInitScript(c, 2);		// script 2 creates the LOG_RECORDS table
			}
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	/**
	 * Retrofit 3 - if the FEEDS_UNIQUEID table (from release 1.0.*) exists, drop it.
	 * If SUBSCRIPTIONS.SUBID still has the auto_increment attribute, remove it.
	 * @return true if the retrofit worked, false otherwise
	 */
	@SuppressWarnings("resource")
	private boolean retroFit3() {
		Connection c = null;
		try {
			// if SUBSCRIPTIONS.SUBID still has auto_increment, remove it
			boolean doremove = false;
			c = getConnection();
			DatabaseMetaData md = c.getMetaData();
			ResultSet rs = md.getColumns("datarouter", "", "SUBSCRIPTIONS", "SUBID");
			if (rs != null) {
				while (rs.next()) {
					doremove = rs.getString("IS_AUTOINCREMENT").equals("YES");
				}
				rs.close();
				rs = null;
			}
			if (doremove) {
				intlogger.info("PROV9002: Modifying SUBSCRIPTIONS SUBID column to remove auto increment.");
				Statement s = c.createStatement();
				s.execute("ALTER TABLE SUBSCRIPTIONS MODIFY COLUMN SUBID INT UNSIGNED NOT NULL");
				s.close();
			}

			// Remove the FEEDS_UNIQUEID table, if it exists
			Set<String> tables = getTableSet(c);
			if (tables.contains("FEEDS_UNIQUEID")) {
				intlogger.info("PROV9002: Dropping FEEDS_UNIQUEID table.");
				Statement s = c.createStatement();
				s.execute("DROP TABLE FEEDS_UNIQUEID");
				s.close();
			}
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	private long nextid = 0;	// used for initial creation of LOG_RECORDS table.
	/**
	 * Retrofit 4 - if old log tables exist (from release 1.0.*), copy them to LOG_RECORDS, then drop them.
	 * @return true if the retrofit worked, false otherwise
	 */
	@SuppressWarnings("resource")
	private boolean retroFit4() {
		Connection c = null;
		try {
			c = getConnection();
			Set<String> tables = getTableSet(c);
			if (tables.contains("PUBLISH_RECORDS")) {
				intlogger.info("PROV9002: Copying PUBLISH_RECORDS to LOG_RECORDS table.");
				copyLogTable("PUBLISH_RECORDS", PublishRecord.class);
				intlogger.info("PROV9002: Dropping PUBLISH_RECORDS table.");
				Statement s = c.createStatement();
				s.execute("DROP TABLE PUBLISH_RECORDS");
				s.close();
			}
			if (tables.contains("DELIVERY_RECORDS")) {
				intlogger.info("PROV9002: Copying DELIVERY_RECORDS to LOG_RECORDS table.");
				copyLogTable("DELIVERY_RECORDS", DeliveryRecord.class);
				intlogger.info("PROV9002: Dropping DELIVERY_RECORDS table.");
				Statement s = c.createStatement();
				s.execute("DROP TABLE DELIVERY_RECORDS");
				s.close();
			}
			if (tables.contains("EXPIRY_RECORDS")) {
				intlogger.info("PROV9002: Copying EXPIRY_RECORDS to LOG_RECORDS table.");
				copyLogTable("EXPIRY_RECORDS", ExpiryRecord.class);
				intlogger.info("PROV9002: Dropping EXPIRY_RECORDS table.");
				Statement s = c.createStatement();
				s.execute("DROP TABLE EXPIRY_RECORDS");
				s.close();
			}
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	/**
	 * Retrofit 5 - Create the new routing tables required for Release 2.
	 * Adds a new "SUSPENDED" column to FEEDS and SUBSCRIPTIONS.
	 * Modifies the LOG_RECORDS table to handle new R2 records.
	 * @return true if the retrofit worked, false otherwise
	 */
	@SuppressWarnings("resource")
	private boolean retroFit5() {
		final String[] expected_tables = {
			"INGRESS_ROUTES", "EGRESS_ROUTES", "NETWORK_ROUTES", "NODESETS", "NODES"
		};
		Connection c = null;
		try {
			// If expected tables are not present, then add new routing tables
			c = getConnection();
			Set<String> tables = getTableSet(c);
			boolean initialize = false;
			for (String s : expected_tables) {
				initialize |= !tables.contains(s);
			}
			if (initialize) {
				intlogger.info("PROV9002: Adding routing tables for Release 2.0.");
				runInitScript(c, 3);		// script 3 creates the routing tables
			}

			// Add SUSPENDED column to FEEDS/SUBSCRIPTIONS
			DatabaseMetaData md = c.getMetaData();
			for (String tbl : new String[] {"FEEDS", "SUBSCRIPTIONS" }) {
				boolean add_col = true;
				ResultSet rs = md.getColumns("datarouter", "", tbl, "SUSPENDED");
				if (rs != null) {
					add_col = !rs.next();
					rs.close();
					rs = null;
				}
				if (add_col) {
					intlogger.info("PROV9002: Adding SUSPENDED column to "+tbl+" table.");
					Statement s = c.createStatement();
					s.execute("ALTER TABLE "+tbl+" ADD COLUMN SUSPENDED BOOLEAN DEFAULT FALSE");
					s.close();
				}
			}

			// Modify LOG_RECORDS for R2
			intlogger.info("PROV9002: Modifying LOG_RECORDS table.");
			Statement s = c.createStatement();
			s.execute("ALTER TABLE LOG_RECORDS MODIFY COLUMN TYPE ENUM('pub', 'del', 'exp', 'pbf', 'dlx') NOT NULL");
			s.close();
			s = c.createStatement();
			s.execute("ALTER TABLE LOG_RECORDS MODIFY COLUMN REASON ENUM('notRetryable', 'retriesExhausted', 'diskFull', 'other')");
			s.close();
			boolean add_col = true;
			ResultSet rs = md.getColumns("datarouter", "", "LOG_RECORDS", "CONTENT_LENGTH_2");
			if (rs != null) {
				add_col = !rs.next();
				rs.close();
				rs = null;
			}
			if (add_col) {
				intlogger.info("PROV9002: Fixing two columns in LOG_RECORDS table (this may take some time).");
				s = c.createStatement();
				s.execute("ALTER TABLE LOG_RECORDS MODIFY COLUMN CONTENT_LENGTH BIGINT NOT NULL, ADD COLUMN CONTENT_LENGTH_2 BIGINT AFTER RECORD_ID");
				s.close();
			}
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	/**
	 * Retrofit 6 - Adjust LOG_RECORDS.USER to be 50 chars (MR #74).
	 * @return true if the retrofit worked, false otherwise
	 */
	@SuppressWarnings("resource")
	private boolean retroFit6() {
		Connection c = null;
		try {
			c = getConnection();
			// Modify LOG_RECORDS for R2
			intlogger.info("PROV9002: Modifying LOG_RECORDS.USER length.");
			Statement s = c.createStatement();
			s.execute("ALTER TABLE LOG_RECORDS MODIFY COLUMN USER VARCHAR(50)");
			s.close();
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	/**
	 * Retrofit 7 - Adjust LOG_RECORDS.FEED_FILEID and LOG_RECORDS.DELIVERY_FILEID to be 256 chars.
	 * @return true if the retrofit worked, false otherwise
	 */
	@SuppressWarnings("resource")
	private boolean retroFit7() {
		Connection c = null;
		try {
			c = getConnection();
			// Modify LOG_RECORDS for long (>128) FILEIDs
			intlogger.info("PROV9002: Modifying LOG_RECORDS.USER length.");
			Statement s = c.createStatement();
			s.execute("ALTER TABLE LOG_RECORDS MODIFY COLUMN FEED_FILEID VARCHAR(256), MODIFY COLUMN DELIVERY_FILEID VARCHAR(256)");
			s.close();
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	/**
	 * Retrofit 8 - Adjust FEEDS.NAME to be 255 chars (MR #74).
	 * @return true if the retrofit worked, false otherwise
	 */
	@SuppressWarnings("resource")
	private boolean retroFit8() {
		Connection c = null;
		try {
			c = getConnection();
			intlogger.info("PROV9002: Modifying FEEDS.NAME length.");
			Statement s = c.createStatement();
			s.execute("ALTER TABLE FEEDS MODIFY COLUMN NAME VARCHAR(255)");
			s.close();
		} catch (SQLException e) {
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}
	
	/**
	 * Retrofit 9 - Add column FEEDS.CREATED_DATE and SUBSCRIPTIONS.CREATED_DATE, 1610 release user story US674199.
	 * @return true if the retrofit worked, false otherwise
	 */

	@SuppressWarnings("resource")		
	private boolean retroFit9() {		
		Connection c = null;		
		try {		
			c = getConnection();		
			// Add CREATED_DATE column to FEEDS/SUBSCRIPTIONS tables
			DatabaseMetaData md = c.getMetaData();		
			for (String tbl : new String[] {"FEEDS", "SUBSCRIPTIONS" }) {		
				boolean add_col = true;		
				ResultSet rs = md.getColumns("datarouter", "", tbl, "CREATED_DATE");		
				if (rs != null) {		
					add_col = !rs.next();		
					rs.close();		
					rs = null;		
				}		
				if (add_col) {		
					intlogger.info("PROV9002: Adding CREATED_DATE column to "+tbl+" table.");		
					Statement s = c.createStatement();
					s.execute("ALTER TABLE "+tbl+" ADD COLUMN CREATED_DATE timestamp DEFAULT CURRENT_TIMESTAMP");		
					s.close();		
				}		
			}						
		} catch (SQLException e) {		
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());		
			return false;		
		} finally {		
			if (c != null)		
				release(c);		
		}		
		return true;		
	}

	/**
	 * Retrofit 10 -Adding business BUSINESS_DESCRIPTION to FEEDS table (Rally
	 * US708102).
	 * 
	 * @return true if the retrofit worked, false otherwise
	 */

	@SuppressWarnings("resource")
	private boolean retroFit10() {
		Connection c = null;
		boolean addColumn = true;
		
		try {

			c = getConnection();		
			// Add BUSINESS_DESCRIPTION column to FEEDS table
			DatabaseMetaData md = c.getMetaData();		
				boolean add_col = true;		
				ResultSet rs = md.getColumns("datarouter", "", "FEEDS", "BUSINESS_DESCRIPTION");		
				if (rs != null) {		
					add_col = !rs.next();		
					rs.close();		
					rs = null;		
				}	
		if(add_col) {
			intlogger
					.info("PROV9002: Adding BUSINESS_DESCRIPTION column to FEEDS table.");
			Statement s = c.createStatement();
			s.execute("ALTER TABLE FEEDS ADD COLUMN BUSINESS_DESCRIPTION varchar(1000) DEFAULT NULL AFTER DESCRIPTION, MODIFY COLUMN DESCRIPTION VARCHAR(1000)");
			s.close();
			}
		}
		catch (SQLException e) {
			intlogger
					.fatal("PROV9000: The database credentials are not working: "
							+ e.getMessage());
			return false;
		} finally {
			if (c != null)
				release(c);
		}
		return true;
	}


	/*New retroFit method is added for groups feature Rally:US708115 - 1610	
	* @retroFit11()
	* @parmas: none
	* @return - boolean if table and fields are created (Group table, group id in FEEDS, SUBSCRIPTION TABLES)
	*/
	@SuppressWarnings("resource")	
	private boolean retroFit11() {		
		final String[] expected_tables = {		
			"GROUPS"		
		};		
		Connection c = null;		
			
		try {		
			// If expected tables are not present, then add new routing tables		
			c = getConnection();		
			Set<String> tables = getTableSet(c);		
			boolean initialize = false;		
			for (String s : expected_tables) {		
				initialize |= !tables.contains(s);		
			}		
			if (initialize) {		
				intlogger.info("PROV9002: Adding GROUPS table for Release 1610.");		
				runInitScript(c, 4);		// script 4 creates the routing tables		
			}		
					
			// Add GROUPID column to FEEDS/SUBSCRIPTIONS		
			DatabaseMetaData md = c.getMetaData();		
			for (String tbl : new String[] {"FEEDS", "SUBSCRIPTIONS" }) {		
				boolean add_col = true;		
				ResultSet rs = md.getColumns("datarouter", "", tbl, "GROUPID");		
				if (rs != null) {		
					add_col = !rs.next();		
					rs.close();		
					rs = null;		
				}		
				if (add_col) {		
					intlogger.info("PROV9002: Adding GROUPID column to "+tbl+" table.");		
					Statement s = c.createStatement();		
					s.execute("ALTER TABLE "+tbl+" ADD COLUMN GROUPID INT(10) UNSIGNED NOT NULL DEFAULT 0 AFTER FEEDID");		
					s.close();		
				}		
			}						
		} catch (SQLException e) {		
			intlogger.fatal("PROV9000: The database credentials are not working: "+e.getMessage());		
			return false;		
		} finally {		
			if (c != null)		
				release(c);		
		}		
		return true;		
	}


	/**
	 * Copy the log table <i>table_name</i> to LOG_RECORDS;
	 * @param table_name the name of the old (1.0.*) table to copy
	 * @param table_class the class used to instantiate a record from the table
	 * @throws SQLException if there is a problem getting a MySQL connection
	 */
	@SuppressWarnings("resource")
	private void copyLogTable(String table_name, Class<? extends Loadable> table_class) throws SQLException {
		long start = System.currentTimeMillis();
		int n = 0;
		Connection c1 = getConnection();
		Connection c2 = getConnection();

		try {
			Constructor<? extends Loadable> cnst = table_class.getConstructor(ResultSet.class);
			PreparedStatement ps = c2.prepareStatement(LogfileLoader.INSERT_SQL);
			Statement stmt = c1.createStatement();
			ResultSet rs = stmt.executeQuery("select * from "+table_name);
			while (rs.next()) {
				Loadable rec = cnst.newInstance(rs);
				rec.load(ps);
				ps.setLong(18, ++nextid);
				ps.executeUpdate();
				if ((++n % 10000) == 0)
					intlogger.debug("  "+n+" records done.");
			}
			stmt.close();
			ps.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}

		release(c1);
		release(c2);
		long x = (System.currentTimeMillis() - start);
		intlogger.debug("  "+n+" records done in "+x+" ms.");
	}

	/**
	 * Get a set of all table names in the DB.
	 * @param c a DB connection
	 * @return the set of table names
	 */
	private Set<String> getTableSet(Connection c) {
		Set<String> tables = new HashSet<String>();
		try {
			DatabaseMetaData md = c.getMetaData();
			ResultSet rs = md.getTables("datarouter", "", "", null);
			if (rs != null) {
				while (rs.next()) {
					tables.add(rs.getString("TABLE_NAME"));
				}
				rs.close();
			}
		} catch (SQLException e) {
		}
		return tables;
	}
	/**
	 * Initialize the tables by running the initialization scripts located in the directory specified
	 * by the property <i>com.att.research.datarouter.provserver.dbscripts</i>.  Scripts have names of
	 * the form mysql_init_NNNN.
	 * @param c a DB connection
	 * @param n the number of the mysql_init_NNNN script to run
	 */
	private void runInitScript(Connection c, int n) {
		String scriptdir = (String) props.get("com.att.research.datarouter.provserver.dbscripts");
		StringBuilder sb = new StringBuilder();
		try {
			String scriptfile = String.format("%s/mysql_init_%04d", scriptdir, n);
			if (!(new File(scriptfile)).exists())
				return;

			LineNumberReader in = new LineNumberReader(new FileReader(scriptfile));
			String line;
			while ((line = in.readLine()) != null) {
				if (!line.startsWith("--")) {
					line = line.trim();
					sb.append(line);
					if (line.endsWith(";")) {
						// Execute one DDL statement
						String sql = sb.toString();
						sb.setLength(0);
						Statement s = c.createStatement();
						s.execute(sql);
						s.close();
					}
				}
			}
			in.close();
			sb.setLength(0);
		} catch (Exception e) {
			intlogger.fatal("PROV9002 Error when initializing table: "+e.getMessage());
			System.exit(1);
		}
	}
}
