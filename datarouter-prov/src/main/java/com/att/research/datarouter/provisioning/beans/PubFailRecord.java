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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;

/**
 * The representation of a Publish Failure (PBF) Record, as retrieved from the DB.
 * @author Robert Eby
 * @version $Id: PubFailRecord.java,v 1.1 2013/10/28 18:06:53 eby Exp $
 */
public class PubFailRecord extends BaseLogRecord {
	private long contentLengthReceived;
	private String sourceIP;
	private String user;
	private String error;

	public PubFailRecord(String[] pp) throws ParseException {
		super(pp);
		this.contentLengthReceived = Long.parseLong(pp[8]);
		this.sourceIP = pp[9];
		this.user     = pp[10];
		this.error    = pp[11];
	}
	public PubFailRecord(ResultSet rs) throws SQLException {
		super(rs);
		// Note: because this record should be "rare" these fields are mapped to unconventional fields in the DB
		this.contentLengthReceived = rs.getLong("CONTENT_LENGTH_2");
		this.sourceIP = rs.getString("REMOTE_ADDR");
		this.user     = rs.getString("USER");
		this.error    = rs.getString("FEED_FILEID");
	}
	public long getContentLengthReceived() {
		return contentLengthReceived;
	}
	public String getSourceIP() {
		return sourceIP;
	}
	public String getUser() {
		return user;
	}
	public String getError() {
		return error;
	}
	@Override
	public void load(PreparedStatement ps) throws SQLException {
		ps.setString(1, "pbf");		// field 1: type
		super.load(ps);				// loads fields 2-8
		ps.setString( 9, getError());
		ps.setString(10, getSourceIP());
		ps.setString(11, getUser());
		ps.setNull  (12, Types.INTEGER);
		ps.setNull  (13, Types.INTEGER);
		ps.setNull  (14, Types.VARCHAR);
		ps.setNull  (15, Types.INTEGER);
		ps.setNull  (16, Types.INTEGER);
		ps.setNull  (17, Types.VARCHAR);
		ps.setLong  (19, getContentLengthReceived());
	}
}
