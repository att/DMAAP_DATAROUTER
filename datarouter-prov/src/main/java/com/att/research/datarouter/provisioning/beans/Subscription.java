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

import java.io.InvalidObjectException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import java.util.Properties;

import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.URLUtilities;

/**
 * The representation of a Subscription.  Subscriptions can be retrieved from the DB, or stored/updated in the DB.
 * @author Robert Eby
 * @version $Id: Subscription.java,v 1.9 2013/10/28 18:06:53 eby Exp $
 */
public class Subscription extends Syncable {
	private static Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
	private static int next_subid = getMaxSubID() + 1;

	private int subid;
	private int feedid;
	private int groupid; //New field is added - Groups feature Rally:US708115 - 1610
	private SubDelivery delivery;
	private boolean metadataOnly;
	private String subscriber;
	private SubLinks links;
	private boolean suspended;
	private Date last_mod;
	private Date created_date;

	public static Subscription getSubscriptionMatching(Subscription sub) {
		SubDelivery deli = sub.getDelivery();
		String sql = String.format(
			"select * from SUBSCRIPTIONS where FEEDID = %d and DELIVERY_URL = \"%s\" and DELIVERY_USER = \"%s\" and DELIVERY_PASSWORD = \"%s\" and DELIVERY_USE100 = %d and METADATA_ONLY = %d",
			sub.getFeedid(),
			deli.getUrl(),
			deli.getUser(),
			deli.getPassword(),
			deli.isUse100() ? 1 : 0,
			sub.isMetadataOnly() ? 1 : 0
		);
		List<Subscription> list = getSubscriptionsForSQL(sql);
		return list.size() > 0 ? list.get(0) : null;
	}
	public static Subscription getSubscriptionById(int id) {
		String sql = "select * from SUBSCRIPTIONS where SUBID = " + id;
		List<Subscription> list = getSubscriptionsForSQL(sql);
		return list.size() > 0 ? list.get(0) : null;
	}
	public static Collection<Subscription> getAllSubscriptions() {
		return getSubscriptionsForSQL("select * from SUBSCRIPTIONS");
	}
	private static List<Subscription> getSubscriptionsForSQL(String sql) {
		List<Subscription> list = new ArrayList<Subscription>();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				Subscription sub = new Subscription(rs);
				list.add(sub);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	public static int getMaxSubID() {
		int max = 0;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select MAX(subid) from SUBSCRIPTIONS");
			if (rs.next()) {
				max = rs.getInt(1);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			intlogger.info("getMaxSubID: "+e.getMessage());
			e.printStackTrace();
		}
		return max;
	}
	public static Collection<String> getSubscriptionUrlList(int feedid) {
		List<String> list = new ArrayList<String>();
		String sql = "select SUBID from SUBSCRIPTIONS where FEEDID = "+feedid;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet  rs = stmt.executeQuery(sql);
			while (rs.next()) {
				int subid = rs.getInt("SUBID");
				list.add(URLUtilities.generateSubscriptionURL(subid));
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	/**
	 * Return a count of the number of active subscriptions in the DB.
	 * @return the count
	 */
	public static int countActiveSubscriptions() {
		int count = 0;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select count(*) from SUBSCRIPTIONS");
			if (rs.next()) {
				count = rs.getInt(1);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			intlogger.warn("PROV0008 countActiveSubscriptions: "+e.getMessage());
			e.printStackTrace();
		}
		return count;
	}

	public Subscription() {
		this("", "", "");
	}
	public Subscription(String url, String user, String password) {
		this.subid = -1;
		this.feedid = -1;
		this.groupid = -1; //New field is added - Groups feature Rally:US708115 - 1610
		this.delivery = new SubDelivery(url, user, password, false);
		this.metadataOnly = false;
		this.subscriber = "";
		this.links = new SubLinks();
		this.suspended = false;
		this.last_mod = new Date();
		this.created_date = new Date();
	}
	public Subscription(ResultSet rs) throws SQLException {
		this.subid        = rs.getInt("SUBID");
		this.feedid       = rs.getInt("FEEDID");
		this.groupid       = rs.getInt("GROUPID"); //New field is added - Groups feature Rally:US708115 - 1610
		this.delivery     = new SubDelivery(rs);
		this.metadataOnly = rs.getBoolean("METADATA_ONLY");
		this.subscriber   = rs.getString("SUBSCRIBER");
		this.links        = new SubLinks(rs.getString("SELF_LINK"), URLUtilities.generateFeedURL(feedid), rs.getString("LOG_LINK"));
		this.suspended    = rs.getBoolean("SUSPENDED");
		this.last_mod     = rs.getDate("LAST_MOD");
		this.created_date = rs.getDate("CREATED_DATE");
	}
	public Subscription(JSONObject jo) throws InvalidObjectException {
		this("", "", "");
		try {
			// The JSONObject is assumed to contain a vnd.att-dr.subscription representation
			this.subid  = jo.optInt("subid", -1);
			this.feedid = jo.optInt("feedid", -1);
			this.groupid = jo.optInt("groupid", -1); //New field is added - Groups feature Rally:US708115 - 1610		

			JSONObject jdeli = jo.getJSONObject("delivery");
			String url      = jdeli.getString("url");
			String user     = jdeli.getString("user");
			String password = jdeli.getString("password");
			boolean use100  = jdeli.getBoolean("use100");

			
			//Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
			Properties p = (new DB()).getProperties();
			if(p.get("com.att.research.datarouter.provserver.https.relaxation").toString().equals("false") && !jo.has("sync")) {
				if (!url.startsWith("https://"))
					throw new InvalidObjectException("delivery URL is not HTTPS");
			}

			if (url.length() > 256)
				throw new InvalidObjectException("delivery url field is too long");
			if (user.length() > 20)
				throw new InvalidObjectException("delivery user field is too long");
			if (password.length() > 32)
				throw new InvalidObjectException("delivery password field is too long");
			this.delivery = new SubDelivery(url, user, password, use100);

			this.metadataOnly = jo.getBoolean("metadataOnly");
			this.suspended    = jo.optBoolean("suspend", false);

			this.subscriber = jo.optString("subscriber", "");
			JSONObject jol = jo.optJSONObject("links");
			this.links = (jol == null) ? (new SubLinks()) : (new SubLinks(jol));
		} catch (InvalidObjectException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidObjectException("invalid JSON: "+e.getMessage());
		}
	}
	public int getSubid() {
		return subid;
	}
	public void setSubid(int subid) {
		this.subid = subid;

		// Create link URLs
		SubLinks sl = getLinks();
		sl.setSelf(URLUtilities.generateSubscriptionURL(subid));
		sl.setLog(URLUtilities.generateSubLogURL(subid));
	}
	public int getFeedid() {
		return feedid;
	}
	public void setFeedid(int feedid) {
		this.feedid = feedid;

		// Create link URLs
		SubLinks sl = getLinks();
		sl.setFeed(URLUtilities.generateFeedURL(feedid));
	}

	//New getter setters for Groups feature Rally:US708115 - 1610
	public int getGroupid() {		
		return groupid;		
	}		
	public void setGroupid(int groupid) {		
		this.groupid = groupid;		
	}

	public SubDelivery getDelivery() {
		return delivery;
	}
	public void setDelivery(SubDelivery delivery) {
		this.delivery = delivery;
	}
	public boolean isMetadataOnly() {
		return metadataOnly;
	}
	public void setMetadataOnly(boolean metadataOnly) {
		this.metadataOnly = metadataOnly;
	}
	public boolean isSuspended() {
		return suspended;
	}
	public void setSuspended(boolean suspended) {
		this.suspended = suspended;
	}
	public String getSubscriber() {
		return subscriber;
	}
	public void setSubscriber(String subscriber) {
		if (subscriber != null) {
			if (subscriber.length() > 8)
				subscriber = subscriber.substring(0, 8);
			this.subscriber = subscriber;
		}
	}
	public SubLinks getLinks() {
		return links;
	}
	public void setLinks(SubLinks links) {
		this.links = links;
	}

	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("subid", subid);
		jo.put("feedid", feedid);
		jo.put("groupid", groupid); //New field is added - Groups feature Rally:US708115 - 1610
		jo.put("delivery", delivery.asJSONObject());
		jo.put("metadataOnly", metadataOnly);
		jo.put("subscriber", subscriber);
		jo.put("links", links.asJSONObject());
		jo.put("suspend", suspended);
		jo.put("last_mod", last_mod.getTime());
		jo.put("created_date", created_date.getTime());
		return jo;
	}
	public JSONObject asLimitedJSONObject() {
		JSONObject jo = asJSONObject();
		jo.remove("subid");
		jo.remove("feedid");
		jo.remove("last_mod");
		return jo;
	}
	public JSONObject asJSONObject(boolean hidepasswords) {
		JSONObject jo = asJSONObject();
		if (hidepasswords) {
			jo.remove("subid");	// we no longer hide passwords, however we do hide these
			jo.remove("feedid");
			jo.remove("last_mod");
			jo.remove("created_date");
		}
		return jo;
	}
	@Override
	public boolean doInsert(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			if (subid == -1) {
				// No feed ID assigned yet, so assign the next available one
				setSubid(next_subid++);
			}
			// In case we insert a feed from synchronization
			if (subid > next_subid)
				next_subid = subid+1;

			// Create the SUBSCRIPTIONS row
			String sql = "insert into SUBSCRIPTIONS (SUBID, FEEDID, DELIVERY_URL, DELIVERY_USER, DELIVERY_PASSWORD, DELIVERY_USE100, METADATA_ONLY, SUBSCRIBER, SUSPENDED, GROUPID) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			ps = c.prepareStatement(sql, new String[] { "SUBID" });
			ps.setInt(1, subid);
			ps.setInt(2, feedid);
			ps.setString(3, getDelivery().getUrl());
			ps.setString(4, getDelivery().getUser());
			ps.setString(5, getDelivery().getPassword());
			ps.setInt(6, getDelivery().isUse100()?1:0);
			ps.setInt(7, isMetadataOnly()?1:0);
			ps.setString(8, getSubscriber());
			ps.setBoolean(9, isSuspended());
			ps.setInt(10, groupid); //New field is added - Groups feature Rally:US708115 - 1610
			ps.execute();
			ps.close();
//			ResultSet rs = ps.getGeneratedKeys();
//			rs.first();
//			setSubid(rs.getInt(1));	// side effect - sets the link URLs
//			ps.close();

			// Update the row to set the URLs
			sql = "update SUBSCRIPTIONS set SELF_LINK = ?, LOG_LINK = ? where SUBID = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, getLinks().getSelf());
			ps.setString(2, getLinks().getLog());
			ps.setInt(3, subid);
			ps.execute();
			ps.close();
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
			String sql = "update SUBSCRIPTIONS set DELIVERY_URL = ?, DELIVERY_USER = ?, DELIVERY_PASSWORD = ?, DELIVERY_USE100 = ?, METADATA_ONLY = ?, SUSPENDED = ?, GROUPID = ? where SUBID = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, delivery.getUrl());
			ps.setString(2, delivery.getUser());
			ps.setString(3, delivery.getPassword());
			ps.setInt(4, delivery.isUse100()?1:0);
			ps.setInt(5, isMetadataOnly()?1:0);
			ps.setInt(6, suspended ? 1 : 0);
			ps.setInt(7, groupid); //New field is added - Groups feature Rally:US708115 - 1610				
			ps.setInt(8, subid);
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


	
	/**Rally US708115
	 * Change Ownership of Subscription - 1610
	 * */
	public boolean changeOwnerShip() {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection c = db.getConnection();
			String sql = "update SUBSCRIPTIONS set SUBSCRIBER = ? where SUBID = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, this.subscriber);
			ps.setInt(2, subid);
			ps.execute();
			ps.close();
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
			String sql = "delete from SUBSCRIPTIONS where SUBID = ?";
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
	public String getKey() {
		return ""+getSubid();
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Subscription))
			return false;
		Subscription os = (Subscription) obj;
		if (subid != os.subid)
			return false;
		if (feedid != os.feedid)
			return false;
		if (groupid != os.groupid) //New field is added - Groups feature Rally:US708115 - 1610		 
			return false;
		if (!delivery.equals(os.delivery))
			return false;
		if (metadataOnly != os.metadataOnly)
			return false;
		if (!subscriber.equals(os.subscriber))
			return false;
		if (!links.equals(os.links))
			return false;
		if (suspended != os.suspended)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SUB: subid=" + subid + ", feedid=" + feedid;
	}
}
