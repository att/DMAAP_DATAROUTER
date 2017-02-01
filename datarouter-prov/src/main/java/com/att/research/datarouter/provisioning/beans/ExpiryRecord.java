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
import java.util.LinkedHashMap;

import org.json.LOGJSONObject;

/**
 * The representation of a Expiry Record, as retrieved from the DB.
 * @author Robert Eby
 * @version $Id: ExpiryRecord.java,v 1.4 2013/10/28 18:06:52 eby Exp $
 */
public class ExpiryRecord extends BaseLogRecord {
	private int subid;
	private String fileid;
	private int attempts;
	private String reason;

	public ExpiryRecord(String[] pp) throws ParseException {
		super(pp);
		String fileid = pp[5];
		if (fileid.lastIndexOf('/') >= 0)
			fileid = fileid.substring(fileid.lastIndexOf('/')+1);
		this.subid    = Integer.parseInt(pp[4]);
		this.fileid   = fileid;
		this.attempts = Integer.parseInt(pp[10]);
		this.reason   = pp[9];
		if (!reason.equals("notRetryable") && !reason.equals("retriesExhausted") && !reason.equals("diskFull"))
			this.reason = "other";
	}
	public ExpiryRecord(ResultSet rs) throws SQLException {
		super(rs);
		this.subid    = rs.getInt("DELIVERY_SUBID");
		this.fileid   = rs.getString("DELIVERY_FILEID");
		this.attempts = rs.getInt("ATTEMPTS");
		this.reason   = rs.getString("REASON");
	}

	public int getSubid() {
		return subid;
	}

	public void setSubid(int subid) {
		this.subid = subid;
	}

	public String getFileid() {
		return fileid;
	}

	public void setFileid(String fileid) {
		this.fileid = fileid;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
	
	public LOGJSONObject reOrderObject(LOGJSONObject jo) {
		LinkedHashMap<String,Object> logrecordObj = new LinkedHashMap<String,Object>();
		
		logrecordObj.put("expiryReason", jo.get("expiryReason"));
		logrecordObj.put("publishId", jo.get("publishId"));
		logrecordObj.put("attempts", jo.get("attempts"));
		logrecordObj.put("requestURI", jo.get("requestURI"));
		logrecordObj.put("method", jo.get("method"));
		logrecordObj.put("contentType", jo.get("contentType"));
		logrecordObj.put("type", jo.get("type"));
		logrecordObj.put("date", jo.get("date"));
		logrecordObj.put("contentLength", jo.get("contentLength"));

		LOGJSONObject newjo = new LOGJSONObject(logrecordObj);
		return newjo;
	}
	
	@Override
	public LOGJSONObject asJSONObject() {
		LOGJSONObject jo = super.asJSONObject();
		jo.put("type", "exp");
		jo.put("expiryReason", reason);
		jo.put("attempts", attempts);
		
		LOGJSONObject newjo = this.reOrderObject(jo);
		return newjo;
	}
	@Override
	public void load(PreparedStatement ps) throws SQLException {
		ps.setString(1, "exp");		// field 1: type
		super.load(ps);				// loads fields 2-8
		ps.setNull  (9,  Types.VARCHAR);
		ps.setNull  (10, Types.VARCHAR);
		ps.setNull  (11, Types.VARCHAR);
		ps.setNull  (12, Types.INTEGER);
		ps.setInt   (13, getSubid());
		ps.setString(14, getFileid());
		ps.setNull  (15, Types.INTEGER);
		ps.setInt   (16, getAttempts());
		ps.setString(17, getReason());
		ps.setNull  (19, Types.BIGINT);
	}
}
