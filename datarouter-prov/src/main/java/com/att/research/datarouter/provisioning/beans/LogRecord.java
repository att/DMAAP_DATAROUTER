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

package com.att.research.datarouter.provisioning.beans;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.util.Iterator;

import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.RLEBitSet;

/**
 * The representation of a Log Record, as retrieved from the DB.  Since this record format is only used
 * to replicate between provisioning servers, it is very bare-bones; e.g. there are no field setters and only 1 getter.
 * @author Robert Eby
 * @version $Id: LogRecord.java,v 1.7 2014/03/12 19:45:41 eby Exp $
 */
public class LogRecord extends BaseLogRecord {
	/**
	 * Print all log records whose RECORD_IDs are in the bit set provided.
	 * @param os the {@link OutputStream} to print the records on
	 * @param bs the {@link RLEBitSet} listing the record IDs to print
	 * @throws IOException
	 */
	public static void printLogRecords(OutputStream os, RLEBitSet bs) throws IOException {
		final String sql = "select * from LOG_RECORDS where RECORD_ID >= ? AND RECORD_ID <= ?";
		DB db = new DB();
		Connection conn = null;
		try {
			conn = db.getConnection();
			Statement stmt = conn.createStatement();
			Iterator<Long[]> iter = bs.getRangeIterator();
			PreparedStatement ps = conn.prepareStatement(sql);
			while (iter.hasNext()) {
				Long[] n = iter.next();
				ps.setLong(1, n[0]);
				ps.setLong(2, n[1]);
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					LogRecord lr = new LogRecord(rs);
					os.write(lr.toString().getBytes());
				}
				rs.close();
				ps.clearParameters();
			}
			ps.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null)
				db.release(conn);
		}
	}

	private final String type;
	private final String feedFileid;
	private final String remoteAddr;
	private final String user;
	private final int status;
	private final int subid;
	private final String fileid;
	private final int result;
	private final int attempts;
	private final String reason;
	private final long record_id;
	private final long clength2;

	public LogRecord(ResultSet rs) throws SQLException {
		super(rs);
		this.type       = rs.getString("TYPE");
		this.feedFileid = rs.getString("FEED_FILEID");
		this.remoteAddr = rs.getString("REMOTE_ADDR");
		this.user       = rs.getString("USER");
		this.status     = rs.getInt("STATUS");

		this.subid      = rs.getInt("DELIVERY_SUBID");
		this.fileid     = rs.getString("DELIVERY_FILEID");
		this.result     = rs.getInt("RESULT");

		this.attempts   = rs.getInt("ATTEMPTS");
		this.reason     = rs.getString("REASON");

		this.record_id  = rs.getLong("RECORD_ID");
		this.clength2   = rs.getLong("CONTENT_LENGTH_2");
	}
	public LogRecord(String[] pp) throws ParseException {
		super(pp);
		this.type       = pp[8];
		this.feedFileid = pp[9];
		this.remoteAddr = pp[10];
		this.user       = pp[11];
		this.status     = Integer.parseInt(pp[12]);

		this.subid      = Integer.parseInt(pp[13]);
		this.fileid     = pp[14];
		this.result     = Integer.parseInt(pp[15]);

		this.attempts   = Integer.parseInt(pp[16]);
		this.reason     = pp[17];

		this.record_id  = Long.parseLong(pp[18]);
		this.clength2   = (pp.length == 20) ? Long.parseLong(pp[19]) : 0;
	}

	public long getRecordId() {
		return record_id;
	}

	@Override
	public String toString() {
		return
			sdf.format(getEventTime()) + "|"
			+ "LOG|"
			+ getPublishId() + "|"
			+ getFeedid() + "|"
			+ getRequestUri() + "|"
			+ getMethod() + "|"
			+ getContentType() + "|"
			+ getContentLength() + "|"
			+ type + "|"
			+ feedFileid + "|"
			+ remoteAddr + "|"
			+ user + "|"
			+ status + "|"
			+ subid + "|"
			+ fileid + "|"
			+ result + "|"
			+ attempts + "|"
			+ reason + "|"
			+ record_id + "|"
			+ clength2
			+ "\n";
	}

	@Override
	public void load(PreparedStatement ps) throws SQLException {
		ps.setString(1, type);
		super.load(ps);				// loads fields 2-8
		if (type.equals("pub")) {
			ps.setString(9,  feedFileid);
			ps.setString(10, remoteAddr);
			ps.setString(11, user);
			ps.setInt   (12, status);
			ps.setNull  (13, Types.INTEGER);
			ps.setNull  (14, Types.VARCHAR);
			ps.setNull  (15, Types.INTEGER);
			ps.setNull  (16, Types.INTEGER);
			ps.setNull  (17, Types.VARCHAR);
			ps.setLong  (18, record_id);
			ps.setNull  (19, Types.BIGINT);
		} else if (type.equals("del")) {
			ps.setNull  (9,  Types.VARCHAR);
			ps.setNull  (10, Types.VARCHAR);
			ps.setString(11, user);
			ps.setNull  (12, Types.INTEGER);
			ps.setInt   (13, subid);
			ps.setString(14, fileid);
			ps.setInt   (15, result);
			ps.setNull  (16, Types.INTEGER);
			ps.setNull  (17, Types.VARCHAR);
			ps.setLong  (18, record_id);
			ps.setNull  (19, Types.BIGINT);
		} else if (type.equals("exp")) {
			ps.setNull  (9,  Types.VARCHAR);
			ps.setNull  (10, Types.VARCHAR);
			ps.setNull  (11, Types.VARCHAR);
			ps.setNull  (12, Types.INTEGER);
			ps.setInt   (13, subid);
			ps.setString(14, fileid);
			ps.setNull  (15, Types.INTEGER);
			ps.setInt   (16, attempts);
			ps.setString(17, reason);
			ps.setLong  (18, record_id);
			ps.setNull  (19, Types.BIGINT);
		} else if (type.equals("pbf")) {
			ps.setString( 9, feedFileid);
			ps.setString(10, remoteAddr);
			ps.setString(11, user);
			ps.setNull  (12, Types.INTEGER);
			ps.setNull  (13, Types.INTEGER);
			ps.setNull  (14, Types.VARCHAR);
			ps.setNull  (15, Types.INTEGER);
			ps.setNull  (16, Types.INTEGER);
			ps.setNull  (17, Types.VARCHAR);
			ps.setLong  (18, record_id);
			ps.setLong  (19, clength2);
		} else if (type.equals("dlx")) {
			ps.setNull  ( 9, Types.VARCHAR);
			ps.setNull  (10, Types.VARCHAR);
			ps.setNull  (11, Types.VARCHAR);
			ps.setNull  (12, Types.INTEGER);
			ps.setInt   (13, subid);
			ps.setNull  (14, Types.VARCHAR);
			ps.setNull  (15, Types.INTEGER);
			ps.setNull  (16, Types.INTEGER);
			ps.setNull  (17, Types.VARCHAR);
			ps.setLong  (18, record_id);
			ps.setLong  (19, clength2);
		}
	}

	public static void main(String[] a) throws IOException {
		LogRecord.printLogRecords(System.out, new RLEBitSet(a[0]));
	}
}
