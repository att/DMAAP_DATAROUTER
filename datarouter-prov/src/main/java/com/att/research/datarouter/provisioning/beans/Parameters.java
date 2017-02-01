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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.att.research.datarouter.provisioning.utils.DB;

/**
 * Methods to provide access to Provisioning parameters in the DB.
 * This class also provides constants of the standard parameters used by the Data Router.
 * @author Robert Eby
 * @version $Id: Parameters.java,v 1.11 2014/03/12 19:45:41 eby Exp $
 */
public class Parameters extends Syncable {
	public static final String PROV_REQUIRE_SECURE          = "PROV_REQUIRE_SECURE";
	public static final String PROV_REQUIRE_CERT            = "PROV_REQUIRE_CERT";
	public static final String PROV_AUTH_ADDRESSES          = "PROV_AUTH_ADDRESSES";
	public static final String PROV_AUTH_SUBJECTS           = "PROV_AUTH_SUBJECTS";
	public static final String PROV_NAME                    = "PROV_NAME";
	public static final String PROV_ACTIVE_NAME             = "PROV_ACTIVE_NAME";
	public static final String PROV_DOMAIN                  = "PROV_DOMAIN";
	public static final String PROV_MAXFEED_COUNT           = "PROV_MAXFEED_COUNT";
	public static final String PROV_MAXSUB_COUNT            = "PROV_MAXSUB_COUNT";
	public static final String PROV_POKETIMER1              = "PROV_POKETIMER1";
	public static final String PROV_POKETIMER2              = "PROV_POKETIMER2";
	public static final String PROV_SPECIAL_SUBNET          = "PROV_SPECIAL_SUBNET";
	public static final String PROV_LOG_RETENTION           = "PROV_LOG_RETENTION";
	public static final String NODES                        = "NODES";
	public static final String ACTIVE_POD                   = "ACTIVE_POD";
	public static final String STANDBY_POD                  = "STANDBY_POD";
	public static final String LOGROLL_INTERVAL             = "LOGROLL_INTERVAL";
	public static final String DELIVERY_INIT_RETRY_INTERVAL = "DELIVERY_INIT_RETRY_INTERVAL";
	public static final String DELIVERY_MAX_RETRY_INTERVAL  = "DELIVERY_MAX_RETRY_INTERVAL";
	public static final String DELIVERY_RETRY_RATIO         = "DELIVERY_RETRY_RATIO";
	public static final String DELIVERY_MAX_AGE             = "DELIVERY_MAX_AGE";
	public static final String THROTTLE_FILTER              = "THROTTLE_FILTER";
	public static final String STATIC_ROUTING_NODES         = "STATIC_ROUTING_NODES"; //Adding new param for static Routing - Rally:US664862-1610

	private static Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");

	private String keyname;
	private String value;

	/**
	 * Get all parameters in the DB as a Map.
	 * @return the Map of keynames/values from the DB.
	 */
	public static Map<String,String> getParameters() {
		Map<String,String> props = new HashMap<String,String>();
		for (Parameters p : getParameterCollection()) {
			props.put(p.getKeyname(), p.getValue());
		}
		return props;
	}
	public static Collection<Parameters> getParameterCollection() {
		Collection<Parameters> coll = new ArrayList<Parameters>();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			String sql = "select * from PARAMETERS";
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				Parameters p = new Parameters(rs);
				coll.add(p);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return coll;
	}
	/**
	 * Get a specific parameter value from the DB.
	 * @param k the key to lookup
	 * @return the value, or null if non-existant
	 */
	public static Parameters getParameter(String k) {
		Parameters v = null;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			String sql = "select KEYNAME, VALUE from PARAMETERS where KEYNAME = \"" + k + "\"";
			ResultSet rs = stmt.executeQuery(sql);
			if (rs.next()) {
				v = new Parameters(rs);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return v;
	}

	public Parameters() {
		this("", "");
	}
	public Parameters(String k, String v) {
		this.keyname = k;
		this.value   = v;
	}
	public Parameters(ResultSet rs) throws SQLException {
		this.keyname = rs.getString("KEYNAME");
		this.value   = rs.getString("VALUE");
	}
	public String getKeyname() {
		return keyname;
	}
	public void setKeyname(String keyname) {
		this.keyname = keyname;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("keyname", keyname);
		jo.put("value", value);
		return jo;
	}
	@Override
	public boolean doInsert(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			// Create the SUBSCRIPTIONS row
			String sql = "insert into PARAMETERS values (?, ?)";
			ps = c.prepareStatement(sql);
			ps.setString(1, getKeyname());
			ps.setString(2, getValue());
			ps.execute();
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0005 doInsert: "+e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				ps.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return rv;
	}
	@Override
	public boolean doUpdate(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			// Update the PARAMETERS row
			String sql = "update PARAMETERS set VALUE = ? where KEYNAME = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, getValue());
			ps.setString(2, getKeyname());
			ps.executeUpdate();
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0006 doUpdate: "+e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				ps.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return rv;
	}
	@Override
	public boolean doDelete(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			// Create the SUBSCRIPTIONS row
			String sql = "delete from PARAMETERS where KEYNAME = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, getKeyname());
			ps.execute();
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0007 doDelete: "+e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				ps.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return rv;
	}
	@Override
	public String getKey() {
		return getKeyname();
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Parameters))
			return false;
		Parameters of = (Parameters) obj;
		if (!keyname.equals(of.keyname))
			return false;
		if (!value.equals(of.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PARAM: keyname=" + keyname + ", value=" + value;
	}
}

