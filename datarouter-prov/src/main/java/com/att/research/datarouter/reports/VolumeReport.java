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

package com.att.research.datarouter.reports;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import com.att.research.datarouter.provisioning.utils.DB;

/**
 * Generate a traffic volume report. The report is a .csv file containing the following columns:
 * <table>
 * <tr><td>date</td><td>the date for this record</td></tr>
 * <tr><td>feedid</td><td>the Feed ID for this record</td></tr>
 * <tr><td>filespublished</td><td>the number of files published on this feed and date</td></tr>
 * <tr><td>bytespublished</td><td>the number of bytes published on this feed and date</td></tr>
 * <tr><td>filesdelivered</td><td>the number of files delivered on this feed and date</td></tr>
 * <tr><td>bytesdelivered</td><td>the number of bytes delivered on this feed and date</td></tr>
 * <tr><td>filesexpired</td><td>the number of files expired on this feed and date</td></tr>
 * <tr><td>bytesexpired</td><td>the number of bytes expired on this feed and date</td></tr>
 * </table>
 *
 * @author Robert P. Eby
 * @version $Id: VolumeReport.java,v 1.3 2014/02/28 15:11:13 eby Exp $
 */
public class VolumeReport extends ReportBase {
	private static final String SELECT_SQL = "select EVENT_TIME, TYPE, FEEDID, CONTENT_LENGTH, RESULT" +
		" from LOG_RECORDS where EVENT_TIME >= ? and EVENT_TIME <= ? LIMIT ?, ?";

	private class Counters {
		public int  filespublished, filesdelivered, filesexpired;
		public long bytespublished, bytesdelivered, bytesexpired;
		@Override
		public String toString() {
			return String.format("%d,%d,%d,%d,%d,%d",
				filespublished, bytespublished, filesdelivered,
				bytesdelivered, filesexpired, bytesexpired);
		}
	}

	@Override
	public void run() {
		Map<String, Counters> map = new HashMap<String, Counters>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		long start = System.currentTimeMillis();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			// We need to run this SELECT in stages, because otherwise we run out of memory!
			final long stepsize = 6000000L;
			boolean go_again = true;
			for (long i = 0; go_again; i += stepsize) {
				PreparedStatement ps = conn.prepareStatement(SELECT_SQL);
				ps.setLong(1, from);
				ps.setLong(2, to);
				ps.setLong(3, i);
				ps.setLong(4, stepsize);
				ResultSet rs = ps.executeQuery();
				go_again = false;
				while (rs.next()) {
					go_again = true;
					long etime  = rs.getLong("EVENT_TIME");
					String type = rs.getString("TYPE");
					int feed    = rs.getInt("FEEDID");
					long clen   = rs.getLong("CONTENT_LENGTH");
					String key  = sdf.format(new Date(etime)) + ":" + feed;
					Counters c = map.get(key);
					if (c == null) {
						c = new Counters();
						map.put(key, c);
					}
					if (type.equalsIgnoreCase("pub")) {
						c.filespublished++;
						c.bytespublished += clen;
					} else if (type.equalsIgnoreCase("del")) {
						// Only count successful deliveries
						int statusCode = rs.getInt("RESULT");
						if (statusCode >= 200 && statusCode < 300) {
							c.filesdelivered++;
							c.bytesdelivered += clen;
						}
					} else if (type.equalsIgnoreCase("exp")) {
						c.filesexpired++;
						c.bytesexpired += clen;
					}
				}
				rs.close();
				ps.close();
			}
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		logger.debug("Query time: " + (System.currentTimeMillis()-start) + " ms");
		try {
			PrintWriter os = new PrintWriter(outfile);
			os.println("date,feedid,filespublished,bytespublished,filesdelivered,bytesdelivered,filesexpired,bytesexpired");
			for (String key : new TreeSet<String>(map.keySet())) {
				Counters c = map.get(key);
				String[] p = key.split(":");
				os.println(String.format("%s,%s,%s", p[0], p[1], c.toString()));
			}
			os.close();
		} catch (FileNotFoundException e) {
			System.err.println("File cannot be written: "+outfile);
		}
	}
}
