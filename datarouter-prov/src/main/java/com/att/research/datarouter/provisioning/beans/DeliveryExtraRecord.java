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
 * The representation of a Delivery Extra (DLX) Record, as retrieved from the DB.
 * @author Robert Eby
 * @version $Id: DeliveryExtraRecord.java,v 1.1 2013/10/28 18:06:52 eby Exp $
 */
public class DeliveryExtraRecord extends BaseLogRecord {
	private int  subid;
	private long contentLength2;

	public DeliveryExtraRecord(String[] pp) throws ParseException {
		super(pp);
		this.subid = Integer.parseInt(pp[4]);
		this.contentLength2 = Long.parseLong(pp[6]);
	}
	public DeliveryExtraRecord(ResultSet rs) throws SQLException {
		super(rs);
		// Note: because this record should be "rare" these fields are mapped to unconventional fields in the DB
		this.subid  = rs.getInt("DELIVERY_SUBID");
		this.contentLength2 = rs.getInt("CONTENT_LENGTH_2");
	}
	@Override
	public void load(PreparedStatement ps) throws SQLException {
		ps.setString(1, "dlx");		// field 1: type
		super.load(ps);				// loads fields 2-8
		ps.setNull( 9, Types.VARCHAR);
		ps.setNull(10, Types.VARCHAR);
		ps.setNull(11, Types.VARCHAR);
		ps.setNull(12, Types.INTEGER);
		ps.setInt (13, subid);
		ps.setNull(14, Types.VARCHAR);
		ps.setNull(15, Types.INTEGER);
		ps.setNull(16, Types.INTEGER);
		ps.setNull(17, Types.VARCHAR);
		ps.setLong(19, contentLength2);
	}
}
