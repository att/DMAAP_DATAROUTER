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
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.att.research.datarouter.provisioning.utils.DB;

/**
 * The representation of one route in the Egress Route Table.
 *
 * @author Robert P. Eby
 * @version $Id: EgressRoute.java,v 1.3 2013/12/16 20:30:23 eby Exp $
 */
public class EgressRoute extends NodeClass implements Comparable<EgressRoute> {
	private static Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
	private final int subid;
	private final int nodeid;

	/**
	 * Get a set of all Egress Routes in the DB.  The set is sorted according to the natural sorting order
	 * of the routes (based on the subscription ID in each route).
	 * @return the sorted set
	 */
	public static SortedSet<EgressRoute> getAllEgressRoutes() {
		SortedSet<EgressRoute> set = new TreeSet<EgressRoute>();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet    rs = stmt.executeQuery("select SUBID, NODEID from EGRESS_ROUTES");
			while (rs.next()) {
				int subid  = rs.getInt("SUBID");
				int nodeid = rs.getInt("NODEID");
				set.add(new EgressRoute(subid, nodeid));
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return set;
	}
	/**
	 * Get a single Egress Route for the subscription <i>sub</i>.
	 * @param sub the subscription to lookup
	 * @return an EgressRoute, or null if there is no route for this subscription
	 */
	public static EgressRoute getEgressRoute(int sub) {
		EgressRoute v = null;
		PreparedStatement ps = null;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			String sql = "select NODEID from EGRESS_ROUTES where SUBID = ?";
			ps = conn.prepareStatement(sql);
			ps.setInt(1, sub);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				int node = rs.getInt("NODEID");
				v = new EgressRoute(sub, node);
			}
			rs.close();
			ps.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				ps.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return v;
	}

	public EgressRoute(int subid, int nodeid) throws IllegalArgumentException {
		this.subid = subid;
		this.nodeid = nodeid;
// Note: unlike for Feeds, it subscriptions can be removed from the tables, so it is
// possible that an orphan ERT entry can exist if a sub is removed.
//		if (Subscription.getSubscriptionById(subid) == null)
//			throw new IllegalArgumentException("No such subscription: "+subid);
	}

	public EgressRoute(int subid, String node) throws IllegalArgumentException {
		this(subid, lookupNodeName(node));
	}

	@Override
	public boolean doDelete(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			String sql = "delete from EGRESS_ROUTES where SUBID = ?";
			ps = c.prepareStatement(sql);
			ps.setInt(1, subid);
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
	public boolean doInsert(Connection c) {
		boolean rv = false;
		PreparedStatement ps = null;
		try {
			// Create the NETWORK_ROUTES row
			String sql = "insert into EGRESS_ROUTES (SUBID, NODEID) values (?, ?)";
			ps = c.prepareStatement(sql);
			ps.setInt(1, this.subid);
			ps.setInt(2, this.nodeid);
			ps.execute();
			ps.close();
			rv = true;
		} catch (SQLException e) {
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
			String sql = "update EGRESS_ROUTES set NODEID = ? where SUBID = ?";
			ps = c.prepareStatement(sql);
			ps.setInt(1, nodeid);
			ps.setInt(2, subid);
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
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put(""+subid, lookupNodeID(nodeid));
		return jo;
	}

	@Override
	public String getKey() {
		return ""+subid;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof EgressRoute))
			return false;
		EgressRoute on = (EgressRoute)obj;
		return (subid == on.subid) && (nodeid == on.nodeid);
	}

	@Override
	public int compareTo(EgressRoute o) {
		return this.subid - o.subid;
	}

	@Override
	public String toString() {
		return String.format("EGRESS: sub=%d, node=%d", subid, nodeid);
	}
}
