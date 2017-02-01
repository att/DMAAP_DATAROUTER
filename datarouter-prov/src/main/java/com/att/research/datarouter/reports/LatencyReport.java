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
import java.util.ArrayList;
import java.util.List;

import com.att.research.datarouter.provisioning.utils.DB;

/**
 * Generate a per-file latency report.  It reports on the details related to one file published
 * on one feed. This report can be further reduced in order to generate more specific reports
 * based on feed ID or node name. The report is a .csv file containing the following columns:
 * <table>
 * <tr><td>recordid</td><td>the unique record ID assigned to a particular incoming feed</td></tr>
 * <tr><td>feedid</td><td>the Feed ID for this record</td></tr>
 * <tr><td>uri</td><td>the URI of the file delivered</td></tr>
 * <tr><td>size</td><td>the size of the file delivered</td></tr>
 * <tr><td>min</td><td>the minimum latency in delivering this feed to a subscriber (in ms)</td></tr>
 * <tr><td>max</td><td>the maximum latency in delivering this feed to a subscriber (in ms)</td></tr>
 * <tr><td>avg</td><td>the average latency in delivering this feed to all subscribers (in ms)</td></tr>
 * <tr><td>fanout</td><td>the number of subscribers this feed was delivered to</td></tr>
 * </table>
 *
 * @author Robert P. Eby
 * @version $Id: LatencyReport.java,v 1.1 2013/10/28 18:06:53 eby Exp $
 */
public class LatencyReport extends ReportBase {
	private static final String SELECT_SQL =
		"select EVENT_TIME, TYPE, PUBLISH_ID, FEED_FILEID, FEEDID, CONTENT_LENGTH from LOG_RECORDS" +
		" where EVENT_TIME >= ? and EVENT_TIME <= ? order by PUBLISH_ID, EVENT_TIME";

	private class Event {
		public final String type;
		public final long time;
		public Event(String t, long tm) {
			type = t;
			time = tm;
		}
	}
	private class Counters {
		public final String id;
		public final int feedid;
		public final long clen;
		public final String fileid;
		public final List<Event> events;
		public Counters(String i, int fid, long c, String s) {
			id = i;
			feedid = fid;
			clen = c;
			fileid = s;
			events = new ArrayList<Event>();
		}
		private long pubtime;
		public void addEvent(String t, long tm) {
			events.add(new Event(t, tm));
			if (t.equals("pub"))
				pubtime = tm;
		}
		public long min() {
			long min = Long.MAX_VALUE;
			for (Event e : events) {
				if (e.type.equals("del")) {
					min = Math.min(min, e.time - pubtime);
				}
			}
			return min;
		}
		public long max() {
			long max = 0;
			for (Event e : events) {
				if (e.type.equals("del")) {
					max = Math.max(max, e.time - pubtime);
				}
			}
			return max;
		}
		public long avg() {
			long total = 0, c = 0;
			for (Event e : events) {
				if (e.type.equals("del")) {
					total += e.time - pubtime;
					c++;
				}
			}
			return (c == 0) ? 0 : total/c;
		}
		public int fanout() {
			int n = 0;
			for (Event e : events) {
				if (e.type.equals("del")) {
					n++;
				}
			}
			return n;
		}
		@Override
		public String toString() {
			return feedid + "," + fileid + "," + clen + "," + min() + "," + max() + "," + avg() + "," + fanout();
		}
	}

	@Override
	public void run() {
		long start = System.currentTimeMillis();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			PreparedStatement ps = conn.prepareStatement(SELECT_SQL);
			ps.setLong(1, from);
			ps.setLong(2, to);
			ResultSet rs = ps.executeQuery();
			PrintWriter os = new PrintWriter(outfile);
			os.println("recordid,feedid,uri,size,min,max,avg,fanout");
			Counters c = null;
			while (rs.next()) {
				long etime  = rs.getLong("EVENT_TIME");
				String type = rs.getString("TYPE");
				String id   = rs.getString("PUBLISH_ID");
				String fid  = rs.getString("FEED_FILEID");
				int feed    = rs.getInt("FEEDID");
				long clen   = rs.getLong("CONTENT_LENGTH");
				if (c != null && !id.equals(c.id)) {
					String line = id + "," + c.toString();
					os.println(line);
					c = null;
				}
				if (c == null) {
					c = new Counters(id, feed, clen, fid);
				}
				if (feed != c.feedid)
					System.err.println("Feed ID mismatch, "+feed+" <=> "+c.feedid);
				if (clen != c.clen)
					System.err.println("Cont Len mismatch, "+clen+" <=> "+c.clen);
//				if (fid != c.fileid)
//					System.err.println("File ID mismatch, "+fid+" <=> "+c.fileid);
				c.addEvent(type, etime);
			}
			rs.close();
			ps.close();
			db.release(conn);
			os.close();
		} catch (FileNotFoundException e) {
			System.err.println("File cannot be written: "+outfile);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		logger.debug("Query time: " + (System.currentTimeMillis()-start) + " ms");
	}
}
