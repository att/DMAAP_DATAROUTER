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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import org.apache.log4j.Logger;

import com.att.research.datarouter.provisioning.BaseServlet;
import com.att.research.datarouter.provisioning.beans.DeliveryExtraRecord;
import com.att.research.datarouter.provisioning.beans.DeliveryRecord;
import com.att.research.datarouter.provisioning.beans.ExpiryRecord;
import com.att.research.datarouter.provisioning.beans.Loadable;
import com.att.research.datarouter.provisioning.beans.LogRecord;
import com.att.research.datarouter.provisioning.beans.Parameters;
import com.att.research.datarouter.provisioning.beans.PubFailRecord;
import com.att.research.datarouter.provisioning.beans.PublishRecord;

/**
 * This class provides methods that run in a separate thread, in order to process logfiles uploaded into the spooldir.
 * These logfiles are loaded into the MySQL LOG_RECORDS table. In a running provisioning server, there should only be
 * two places where records can be loaded into this table; here, and in the method DB.retroFit4() which may be run at
 * startup to load the old (1.0) style log tables into LOG_RECORDS;
 * <p>This method maintains an {@link RLEBitSet} which can be used to easily see what records are presently in the
 * database.
 * This bit set is used to synchronize between provisioning servers.</p>
 *
 * @author Robert Eby
 * @version $Id: LogfileLoader.java,v 1.22 2014/03/12 19:45:41 eby Exp $
 */
public class LogfileLoader extends Thread {
	/** Default number of log records to keep when pruning.  Keep 10M by default. */
	public static final long DEFAULT_LOG_RETENTION = 10000000L;
	/** NOT USED: Percentage of free space required before old records are removed. */
	public static final int REQUIRED_FREE_PCT = 20;

	/** This is a singleton -- there is only one LogfileLoader object in the server */
	private static LogfileLoader p;

	/**
	 * Get the singleton LogfileLoader object, and start it if it is not running.
	 * @return the LogfileLoader
	 */
	public static synchronized LogfileLoader getLoader() {
		if (p == null)
			p = new LogfileLoader();
		if (!p.isAlive())
			p.start();
		return p;
	}

	/** The PreparedStatement which is loaded by a <i>Loadable</i>. */
	public static final String INSERT_SQL = "insert into LOG_RECORDS values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	/** Each server can assign this many IDs */
	private static final long SET_SIZE = (1L << 56);

	private final Logger logger;
	private final DB db;
	private final String spooldir;
	private final long set_start;
	private final long set_end;
	private RLEBitSet seq_set;
	private long nextid;
	private boolean idle;

	private LogfileLoader() {
		this.logger   = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
		this.db       = new DB();
		this.spooldir = db.getProperties().getProperty("com.att.research.datarouter.provserver.spooldir");
		this.set_start = getIdRange();
		this.set_end   = set_start + SET_SIZE - 1;
		this.seq_set  = new RLEBitSet();
		this.nextid   = 0;
		this.idle     = false;

		// This is a potentially lengthy operation, so has been moved to run()
		//initializeNextid();
		this.setDaemon(true);
		this.setName("LogfileLoader");
	}

	private long getIdRange() {
		long n;
		if (BaseServlet.isInitialActivePOD())
			n = 0;
		else if (BaseServlet.isInitialStandbyPOD())
			n = SET_SIZE;
		else
			n = SET_SIZE * 2;
		String r = String.format("[%X .. %X]", n, n+SET_SIZE-1);
		logger.debug("This server shall assign RECORD_IDs in the range "+r);
		return n;
	}
	/**
	 * Return the bit set representing the record ID's that are loaded in this database.
	 * @return the bit set
	 */
	public RLEBitSet getBitSet() {
		return seq_set;
	}
	/**
	 * True if the LogfileLoader is currently waiting for work.
	 * @return true if idle
	 */
	public boolean isIdle() {
		return idle;
	}
	/**
	 * Run continuously to look for new logfiles in the spool directory and import them into the DB.
	 * The spool is checked once per second.  If free space on the MySQL filesystem falls below
	 * REQUIRED_FREE_PCT (normally 20%) then the oldest logfile entries are removed and the LOG_RECORDS
	 * table is compacted until free space rises above the threshold.
	 */
	@Override
	public void run() {
		initializeNextid();	// moved from the constructor
		while (true) {
			try {
				File dirfile = new File(spooldir);
				while (true) {
					// process IN files
					File[] infiles = dirfile.listFiles(new FilenameFilter() {
						@Override
						public boolean accept(File dir, String name) {
							return name.startsWith("IN.");
						}
					});

					if (infiles.length == 0) {
						idle = true;
						try {
							Thread.sleep(1000L);
						} catch (InterruptedException e) {
						}
						idle = false;
					} else {
						// Remove old rows
						if (pruneRecords()) {
							// Removed at least some entries, recompute the bit map
							initializeNextid();
						}

						// Process incoming logfiles
						for (File f : infiles) {
							if (logger.isDebugEnabled())
								logger.debug("PROV8001 Starting " + f + " ...");
							long time = System.currentTimeMillis();
							int[] n = process(f);
							time = System.currentTimeMillis() - time;
							logger.info(String
									.format("PROV8000 Processed %s in %d ms; %d of %d records.",
											f.toString(), time, n[0], n[1]));
							f.delete();
						}
					}
				}
			} catch (Exception e) {
				logger.warn("PROV0020: Caught exception in LogfileLoader: " + e);
				e.printStackTrace();
			}
		}
	}
	private boolean pruneRecords() {
		boolean did1 = false;
		long count = countRecords();
		long threshold = DEFAULT_LOG_RETENTION;
		Parameters param = Parameters.getParameter(Parameters.PROV_LOG_RETENTION);
		if (param != null) {
			try {
				long n = Long.parseLong(param.getValue());
				// This check is to prevent inadvertent errors from wiping the table out
				if (n > 1000000L)
					threshold = n;
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		logger.debug("Pruning LOG_RECORD table: records in DB="+count+", threshold="+threshold);
		if (count > threshold) {
			count -= threshold;						// we need to remove this many records;
			Map<Long,Long> hist = getHistogram();	// histogram of records per day
			// Determine the cutoff point to remove the needed number of records
			long sum = 0;
			long cutoff = 0;
			for (Long day : new TreeSet<Long>(hist.keySet())) {
				sum += hist.get(day);
				cutoff = day;
				if (sum >= count)
					break;
			}
			cutoff++;
			cutoff *= 86400000L;		// convert day to ms
			logger.debug("  Pruning records older than="+(cutoff/86400000L)+" ("+new Date(cutoff)+")");

			Connection conn = null;
			try {
				// Limit to a million at a time to avoid typing up the DB for too long.
				conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("DELETE from LOG_RECORDS where EVENT_TIME < ? limit 1000000");
				ps.setLong(1, cutoff);
				while (count > 0) {
					if (!ps.execute()) {
						int dcount = ps.getUpdateCount();
						count -= dcount;
						logger.debug("  "+dcount+" rows deleted.");
						did1 |= (dcount!=0);
						if (dcount == 0)
							count = 0;	// prevent inf. loops
					} else {
						count = 0;	// shouldn't happen!
					}
				}
				ps.close();
				Statement stmt = conn.createStatement();
				stmt.execute("OPTIMIZE TABLE LOG_RECORDS");
				stmt.close();
			} catch (SQLException e) {
				System.err.println(e);
				e.printStackTrace();
			} finally {
				db.release(conn);
			}
		}
		return did1;
	}
	private long countRecords() {
		long count = 0;
		Connection conn = null;
		try {
			conn = db.getConnection();
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as COUNT from LOG_RECORDS");
			if (rs.next()) {
				count = rs.getLong("COUNT");
			}
			rs.close();
			stmt.close();
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
		} finally {
			db.release(conn);
		}
		return count;
	}
	private Map<Long,Long> getHistogram() {
		Map<Long,Long> map = new HashMap<Long,Long>();
		Connection conn = null;
		try {
			logger.debug("  LOG_RECORD table histogram...");
			conn = db.getConnection();
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT FLOOR(EVENT_TIME/86400000) AS DAY, COUNT(*) AS COUNT FROM LOG_RECORDS GROUP BY DAY");
			while (rs.next()) {
				long day = rs.getLong("DAY");
				long cnt = rs.getLong("COUNT");
				map.put(day, cnt);
				logger.debug("  "+day + "  "+cnt);
			}
			rs.close();
			stmt.close();
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
		} finally {
			db.release(conn);
		}
		return map;
	}
	private void initializeNextid() {
		Connection conn = null;
		try {
			conn = db.getConnection();
			Statement stmt = conn.createStatement();
			// Build a bitset of all records in the LOG_RECORDS table
			// We need to run this SELECT in stages, because otherwise we run out of memory!
			RLEBitSet nbs = new RLEBitSet();
			final long stepsize = 6000000L;
			boolean go_again = true;
			for (long i = 0; go_again; i += stepsize) {
				String sql = String.format("select RECORD_ID from LOG_RECORDS LIMIT %d,%d", i, stepsize);
				ResultSet rs = stmt.executeQuery(sql);
				go_again = false;
				while (rs.next()) {
					long n = rs.getLong("RECORD_ID");
					nbs.set(n);
					go_again = true;
				}
				rs.close();
			}
			stmt.close();
			seq_set = nbs;

			// Compare with the range for this server
			// Determine the next ID for this set of record IDs
			RLEBitSet tbs = (RLEBitSet) nbs.clone();
			RLEBitSet idset = new RLEBitSet();
			idset.set(set_start, set_start+SET_SIZE);
			tbs.and(idset);
			long t = tbs.length();
			nextid = (t == 0) ? set_start : (t - 1);
			if (nextid >= set_start+SET_SIZE) {
				// Handle wraparound, when the IDs reach the end of our "range"
				Long[] last = null;
				Iterator<Long[]> li = tbs.getRangeIterator();
				while (li.hasNext()) {
					last = li.next();
				}
				if (last != null) {
					tbs.clear(last[0], last[1]+1);
					t = tbs.length();
					nextid = (t == 0) ? set_start : (t - 1);
				}
			}
			logger.debug(String.format("initializeNextid, next ID is %d (%x)", nextid, nextid));
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
		} finally {
			db.release(conn);
		}
	}
// OLD CODE - commented here for historical purposes
//
//	private boolean pruneRecordsOldAlgorithm() {
//		// Determine space available -- available space must be at least 20% under /opt/app/mysql
//		int pct = getFreePercentage();
//		boolean did1 = false;
//		while (pct < REQUIRED_FREE_PCT) {
//			logger.info("PROV8008: Free space is " + pct + "% - removing old log entries");
//			boolean didit = removeOldestEntries();
//			pct = didit ? getFreePercentage() : 100; // don't loop endlessly
//			did1 |= didit;
//		}
//		return did1;
//	}
//	private int getFreePercentage() {
//		FileSystem fs = (Paths.get("/opt/app/mysql")).getFileSystem();
//		long total = 0;
//		long avail = 0;
//		try {
//			for (FileStore store : fs.getFileStores()) {
//				total += store.getTotalSpace();
//				avail += store.getUsableSpace();
//			}
//		} catch (IOException e) {
//		}
//		try { fs.close(); } catch (Exception e) { }
//		return (int)((avail * 100) / total);
//	}
//	private boolean removeOldestEntries() {
//		// Remove the last days worth of entries
//		Connection conn = null;
//		try {
//			conn = db.getConnection();
//			Statement stmt = conn.createStatement();
//			ResultSet rs = stmt.executeQuery("select min(event_time) as MIN from LOG_RECORDS");
//			if (rs != null) {
//				if (rs.next()) {
//					// Compute the end of the first day of logs
//					long first = rs.getLong("MIN");
//					Calendar cal = new GregorianCalendar();
//					cal.setTime(new Date(first));
//					cal.add(Calendar.DAY_OF_YEAR, 1);
//					cal.set(Calendar.HOUR_OF_DAY, 0);
//					cal.set(Calendar.MINUTE, 0);
//					cal.set(Calendar.SECOND, 0);
//					cal.set(Calendar.MILLISECOND, 0);
//					if (!stmt.execute("delete from LOG_RECORDS where event_time < " + cal.getTimeInMillis())) {
//						int count = stmt.getUpdateCount();
//						logger.info("PROV0009: Removed "+count+" old log entries.");
//						stmt.execute("OPTIMIZE TABLE LOG_RECORDS");
//					}
//					rs.close();
//					stmt.close();
//					return true;
//				}
//				rs.close();
//			}
//			stmt.close();
//		} catch (SQLException e) {
//			System.err.println(e);
//			e.printStackTrace();
//		} finally {
//			db.release(conn);
//		}
//		return false;
//	}
	@SuppressWarnings("resource")
	private int[] process(File f) {
		int ok = 0, total = 0;
		try {
			Connection conn = db.getConnection();
			PreparedStatement ps = conn.prepareStatement(INSERT_SQL);
			Reader r = f.getPath().endsWith(".gz")
				? new InputStreamReader(new GZIPInputStream(new FileInputStream(f)))
				: new FileReader(f);
			LineNumberReader in = new LineNumberReader(r);
			String line;
			while ((line = in.readLine()) != null) {
				try {
					for (Loadable rec : buildRecords(line)) {
						rec.load(ps);
						if (rec instanceof LogRecord) {
							LogRecord lr = ((LogRecord)rec);
							if (!seq_set.get(lr.getRecordId())) {
								ps.executeUpdate();
								seq_set.set(lr.getRecordId());
							} else
								logger.debug("Duplicate record ignored: "+lr.getRecordId());
						} else {
							if (++nextid > set_end)
								nextid = set_start;
							ps.setLong(18, nextid);
							ps.executeUpdate();
							seq_set.set(nextid);
						}
						ps.clearParameters();
						ok++;
					}
				} catch (SQLException e) {
					logger.warn("PROV8003 Invalid value in record: "+line);
					logger.debug(e);
					e.printStackTrace();
				} catch (NumberFormatException e) {
					logger.warn("PROV8004 Invalid number in record: "+line);
					logger.debug(e);
					e.printStackTrace();
				} catch (ParseException e) {
					logger.warn("PROV8005 Invalid date in record: "+line);
					logger.debug(e);
					e.printStackTrace();
				} catch (Exception e) {
					logger.warn("PROV8006 Invalid pattern in record: "+line);
					logger.debug(e);
					e.printStackTrace();
				}
				total++;
			}
			in.close();
			ps.close();
			db.release(conn);
			conn = null;
		} catch (FileNotFoundException e) {
			logger.warn("PROV8007 Exception reading "+f+": "+e);
		} catch (IOException e) {
			logger.warn("PROV8007 Exception reading "+f+": "+e);
		} catch (SQLException e) {
			logger.warn("PROV8007 Exception reading "+f+": "+e);
		}
		return new int[] { ok, total };
	}
	private Loadable[] buildRecords(String line) throws ParseException {
		String[] pp = line.split("\\|");
		if (pp != null && pp.length >= 7) {
			String rtype = pp[1].toUpperCase();
			if (rtype.equals("PUB") && pp.length == 11) {
				// Fields are: date|PUB|pubid|feedid|requrl|method|ctype|clen|srcip|user|status
				return new Loadable[] { new PublishRecord(pp) };
			}
			if (rtype.equals("DEL") && pp.length == 12) {
				// Fields are: date|DEL|pubid|feedid|subid|requrl|method|ctype|clen|user|status|xpubid
				String[] subs = pp[4].split("\\s+");
				if (subs != null) {
					Loadable[] rv = new Loadable[subs.length];
					for (int i = 0; i < subs.length; i++) {
						// create a new record for each individual sub
						pp[4] = subs[i];
						rv[i] = new DeliveryRecord(pp);
					}
					return rv;
				}
			}
			if (rtype.equals("EXP") && pp.length == 11) {
				// Fields are: date|EXP|pubid|feedid|subid|requrl|method|ctype|clen|reason|attempts
				ExpiryRecord e = new ExpiryRecord(pp);
				if (e.getReason().equals("other"))
					logger.info("Invalid reason '"+pp[9]+"' changed to 'other' for record: "+e.getPublishId());
				return new Loadable[] { e };
			}
			if (rtype.equals("PBF") && pp.length == 12) {
				// Fields are: date|PBF|pubid|feedid|requrl|method|ctype|clen-expected|clen-received|srcip|user|error
				return new Loadable[] { new PubFailRecord(pp) };
			}
			if (rtype.equals("DLX") && pp.length == 7) {
				// Fields are: date|DLX|pubid|feedid|subid|clen-tosend|clen-sent
				return new Loadable[] { new DeliveryExtraRecord(pp) };
			}
			if (rtype.equals("LOG") && (pp.length == 19 || pp.length == 20)) {
				// Fields are: date|LOG|pubid|feedid|requrl|method|ctype|clen|type|feedFileid|remoteAddr|user|status|subid|fileid|result|attempts|reason|record_id
				return new Loadable[] { new LogRecord(pp) };
			}
		}
		logger.warn("PROV8002 bad record: "+line);
		return new Loadable[0];
	}

	/**
	 * The LogfileLoader can be run stand-alone by invoking the main() method of this class.
	 * @param a ignored
	 * @throws InterruptedException
	 */
	public static void main(String[] a) throws InterruptedException {
		LogfileLoader.getLoader();
		Thread.sleep(200000L);
	}
}
