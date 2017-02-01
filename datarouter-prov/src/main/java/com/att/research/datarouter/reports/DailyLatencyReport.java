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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.att.research.datarouter.provisioning.utils.DB;

/**
 * Generate a daily per feed latency report.  The report is a .csv file containing the following columns:
 * <table>
 * <tr><td>date</td><td>the date for this record</td></tr>
 * <tr><td>feedid</td><td>the Feed ID for this record</td></tr>
 * <tr><td>minsize</td><td>the minimum size of all files published on this feed and date</td></tr>
 * <tr><td>maxsize</td><td>the maximum size of all files published on this feed and date</td></tr>
 * <tr><td>avgsize</td><td>the average size of all files published on this feed and date</td></tr>
 * <tr><td>minlat</td><td>the minimum latency in delivering this feed to all subscribers (in ms)</td></tr>
 * <tr><td>maxlat</td><td>the maximum latency in delivering this feed to all subscribers (in ms)</td></tr>
 * <tr><td>avglat</td><td>the average latency in delivering this feed to all subscribers (in ms)</td></tr>
 * <tr><td>fanout</td><td>the average number of subscribers this feed was delivered to</td></tr>
 * </table>
 * <p>
 * In the context of this report, latency is defined as the value
 * <i>(D<sub>e</sub> - P<sub>s</sub>)</i>
 * where:
 * </p>
 * <p>P<sub>s</sub> is the time that the publication of the file to the node starts.</p>
 * <p>D<sub>e</sub> is the time that the delivery of the file to the subscriber ends.</p>
 *
 * @author Robert P. Eby
 * @version $Id: DailyLatencyReport.java,v 1.2 2013/11/06 16:23:54 eby Exp $
 */
public class DailyLatencyReport extends ReportBase {
	private static final String SELECT_SQL =
		"select EVENT_TIME, TYPE, PUBLISH_ID, FEED_FILEID, FEEDID, CONTENT_LENGTH from LOG_RECORDS" +
		" where EVENT_TIME >= ? and EVENT_TIME <= ?";

	private class Job {
		public long pubtime = 0;
		public long clen = 0;
		public List<Long> deltime = new ArrayList<Long>();
		public long minLatency() {
			long n = deltime.isEmpty() ? 0 : Long.MAX_VALUE;
			for (Long l : deltime)
				n = Math.min(n, l-pubtime);
			return n;
		}
		public long maxLatency() {
			long n = 0;
			for (Long l : deltime)
				n = Math.max(n, l-pubtime);
			return n;
		}
		public long totalLatency() {
			long n = 0;
			for (Long l : deltime)
				n += (l-pubtime);
			return n;
		}
	}
	private class Counters {
		public final String date;
		public final int feedid;
		public final Map<String, Job> jobs;
		public Counters(String d, int fid) {
			date = d;
			feedid = fid;
			jobs = new HashMap<String, Job>();
		}
		public void addEvent(long etime, String type, String id, String fid, long clen) {
			Job j = jobs.get(id);
			if (j == null) {
				j = new Job();
				jobs.put(id, j);
			}
			if (type.equals("pub")) {
				j.pubtime = getPstart(id);
				j.clen = clen;
			} else if (type.equals("del")) {
				j.deltime.add(etime);
			}
		}
		@Override
		public String toString() {
			long minsize = Long.MAX_VALUE, maxsize = 0, avgsize = 0;
			long minl    = Long.MAX_VALUE, maxl    = 0;
			long fanout  = 0, totall = 0, totaln = 0;
			for (Job j : jobs.values()) {
				minsize = Math.min(minsize, j.clen);
				maxsize = Math.max(maxsize, j.clen);
				avgsize += j.clen;
				minl    = Math.min(minl, j.minLatency());
				maxl    = Math.max(maxl, j.maxLatency());
				totall  += j.totalLatency();
				totaln  += j.deltime.size();
				fanout  += j.deltime.size();
			}
			if (jobs.size() > 0) {
				avgsize /= jobs.size();
				fanout  /= jobs.size();
			}
			long avgl = (totaln > 0) ? (totall / totaln) : 0;
			return date + "," + feedid + "," + minsize + "," + maxsize + "," + avgsize + "," + minl + "," + maxl + "," + avgl + "," + fanout;
		}
	}
	private long getPstart(String t) {
		if (t.indexOf('.') > 0)
			t = t.substring(0, t.indexOf('.'));
		return Long.parseLong(t);
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
			PreparedStatement ps = conn.prepareStatement(SELECT_SQL);
			ps.setLong(1, from);
			ps.setLong(2, to);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				String id   = rs.getString("PUBLISH_ID");
				int feed    = rs.getInt("FEEDID");
				long etime  = rs.getLong("EVENT_TIME");
				String type = rs.getString("TYPE");
				String fid  = rs.getString("FEED_FILEID");
				long clen   = rs.getLong("CONTENT_LENGTH");
				String date = sdf.format(new Date(getPstart(id)));
				String key  = date + "," + feed;
				Counters c = map.get(key);
				if (c == null) {
					c = new Counters(date, feed);
					map.put(key, c);
				}
				c.addEvent(etime, type, id, fid, clen);
			}
			rs.close();
			ps.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		logger.debug("Query time: " + (System.currentTimeMillis()-start) + " ms");
		try {
			PrintWriter os = new PrintWriter(outfile);
			os.println("date,feedid,minsize,maxsize,avgsize,minlat,maxlat,avglat,fanout");
			for (String key : new TreeSet<String>(map.keySet())) {
				Counters c = map.get(key);
				os.println(c.toString());
			}
			os.close();
		} catch (FileNotFoundException e) {
			System.err.println("File cannot be written: "+outfile);
		}
	}
}
