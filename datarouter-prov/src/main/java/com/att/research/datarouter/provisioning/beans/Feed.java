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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.JSONUtilities;
import com.att.research.datarouter.provisioning.utils.URLUtilities;

/**
 * The representation of a Feed.  Feeds can be retrieved from the DB, or stored/updated in the DB.
 * @author Robert Eby
 * @version $Id: Feed.java,v 1.13 2013/10/28 18:06:52 eby Exp $
 */
public class Feed extends Syncable {
	private static Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
	private static int next_feedid = getMaxFeedID() + 1;

	private int feedid;
	private int groupid; //New field is added - Groups feature Rally:US708115 - 1610
	private String name;
	private String version;
	private String description;
	private String business_description; // New field is added - Groups feature Rally:US708102 - 1610
	private FeedAuthorization authorization;
	private String publisher;
	private FeedLinks links;
	private boolean deleted;
	private boolean suspended;
	private Date last_mod;
	private Date created_date;

	/**
	 * Check if a feed ID is valid.
	 * @param id the Feed ID
	 * @return true if it is valid
	 */
	@SuppressWarnings("resource")
	public static boolean isFeedValid(int id) {
		int count = 0;
		try {
			DB db = new DB();
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select COUNT(*) from FEEDS where FEEDID = " + id);
			if (rs.next()) {
				count = rs.getInt(1);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return count != 0;
	}
	/**
	 * Get a specific feed from the DB, based upon its ID.
	 * @param id the Feed ID
	 * @return the Feed object, or null if it does not exist
	 */
	public static Feed getFeedById(int id) {
		String sql = "select * from FEEDS where FEEDID = " + id;
		return getFeedBySQL(sql);
	}
	/**
	 * Get a specific feed from the DB, based upon its name and version.
	 * @param name the name of the Feed
	 * @param version the version of the Feed
	 * @return the Feed object, or null if it does not exist
	 */
	public static Feed getFeedByNameVersion(String name, String version) {
		name = name.replaceAll("'", "''");
		version = version.replaceAll("'", "''");
		String sql = "select * from FEEDS where NAME = '" + name + "' and VERSION ='" + version + "'";
		return getFeedBySQL(sql);
	}
	/**
	 * Return a count of the number of active feeds in the DB.
	 * @return the count
	 */
	public static int countActiveFeeds() {
		int count = 0;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select count(*) from FEEDS where DELETED = 0");
			if (rs.next()) {
				count = rs.getInt(1);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			intlogger.info("countActiveFeeds: "+e.getMessage());
			e.printStackTrace();
		}
		return count;
	}
	public static int getMaxFeedID() {
		int max = 0;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select MAX(feedid) from FEEDS");
			if (rs.next()) {
				max = rs.getInt(1);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			intlogger.info("getMaxFeedID: "+e.getMessage());
			e.printStackTrace();
		}
		return max;
	}
	public static Collection<Feed> getAllFeeds() {
		Map<Integer, Feed> map = new HashMap<Integer, Feed>();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select * from FEEDS");
			while (rs.next()) {
				Feed feed = new Feed(rs);
				map.put(feed.getFeedid(), feed);
			}
			rs.close();

			String sql = "select * from FEED_ENDPOINT_IDS";
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				int id = rs.getInt("FEEDID");
				Feed feed = map.get(id);
				if (feed != null) {
					FeedEndpointID epi = new FeedEndpointID(rs);
					Collection<FeedEndpointID> ecoll = feed.getAuthorization().getEndpoint_ids();
					ecoll.add(epi);
				}
			}
			rs.close();

			sql = "select * from FEED_ENDPOINT_ADDRS";
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				int id = rs.getInt("FEEDID");
				Feed feed = map.get(id);
				if (feed != null) {
					Collection<String> acoll = feed.getAuthorization().getEndpoint_addrs();
					acoll.add(rs.getString("ADDR"));
				}
			}
			rs.close();

			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return map.values();
	}
	public static List<String> getFilteredFeedUrlList(final String name, final String val) {
		List<String> list = new ArrayList<String>();
		String sql = "select SELF_LINK from FEEDS where DELETED = 0";
		if (name.equals("name")) {
			sql += " and NAME = ?";
		} else if (name.equals("publ")) {
			sql += " and PUBLISHER = ?";
		} else if (name.equals("subs")) {
			sql = "select distinct FEEDS.SELF_LINK from FEEDS, SUBSCRIPTIONS " +
				"where DELETED = 0 " +
				"and FEEDS.FEEDID = SUBSCRIPTIONS.FEEDID " +
				"and SUBSCRIPTIONS.SUBSCRIBER = ?";
		}
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			PreparedStatement ps = conn.prepareStatement(sql);
			if (sql.indexOf('?') >= 0)
				ps.setString(1, val);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				String t = rs.getString(1);
				list.add(t.trim());
			}
			rs.close();
			ps.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	@SuppressWarnings("resource")
	private static Feed getFeedBySQL(String sql) {
		Feed feed = null;
		try {
			DB db = new DB();
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			if (rs.next()) {
				feed = new Feed(rs);
				rs.close();

				sql = "select * from FEED_ENDPOINT_IDS where FEEDID = " + feed.feedid;
				rs = stmt.executeQuery(sql);
				Collection<FeedEndpointID> ecoll = feed.getAuthorization().getEndpoint_ids();
				while (rs.next()) {
					FeedEndpointID epi = new FeedEndpointID(rs);
					ecoll.add(epi);
				}
				rs.close();

				sql = "select * from FEED_ENDPOINT_ADDRS where FEEDID = " + feed.feedid;
				rs = stmt.executeQuery(sql);
				Collection<String> acoll = feed.getAuthorization().getEndpoint_addrs();
				while (rs.next()) {
					acoll.add(rs.getString("ADDR"));
				}
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return feed;
	}

	public Feed() {
		this("", "", "","");
	}

	public Feed(String name, String version, String desc,String business_description) {
		this.feedid = -1;
		this.groupid = -1; //New field is added - Groups feature Rally:US708115 - 1610
		this.name = name;
		this.version = version;
		this.description = desc;
		this.business_description=business_description; // New field is added - Groups feature Rally:US708102 - 1610
		this.authorization = new FeedAuthorization();
		this.publisher = "";
		this.links = new FeedLinks();
		this.deleted = false;
		this.suspended = false;
		this.last_mod = new Date();
		this.created_date = new Date();
	}
	public Feed(ResultSet rs) throws SQLException {
		this.feedid = rs.getInt("FEEDID");
		this.groupid = rs.getInt("GROUPID"); //New field is added - Groups feature Rally:US708115 - 1610
		this.name = rs.getString("NAME");
		this.version = rs.getString("VERSION");
		this.description = rs.getString("DESCRIPTION");
		this.business_description=rs.getString("BUSINESS_DESCRIPTION"); // New field is added - Groups feature Rally:US708102 - 1610
		this.authorization = new FeedAuthorization();
		this.authorization.setClassification(rs.getString("AUTH_CLASS"));
		this.publisher   = rs.getString("PUBLISHER");
		this.links       = new FeedLinks();
		this.links.setSelf(rs.getString("SELF_LINK"));
		this.links.setPublish(rs.getString("PUBLISH_LINK"));
		this.links.setSubscribe(rs.getString("SUBSCRIBE_LINK"));
		this.links.setLog(rs.getString("LOG_LINK"));
		this.deleted     = rs.getBoolean("DELETED");
		this.suspended   = rs.getBoolean("SUSPENDED");
		this.last_mod    = rs.getDate("LAST_MOD");
		this.created_date    = rs.getTimestamp("CREATED_DATE");
	}
	public Feed(JSONObject jo) throws InvalidObjectException {
		this("", "", "","");
		try {
			// The JSONObject is assumed to contain a vnd.att-dr.feed representation
			this.feedid = jo.optInt("feedid", -1);
			this.groupid = jo.optInt("groupid"); //New field is added - Groups feature Rally:US708115 - 1610
			this.name = jo.getString("name");
			if (name.length() > 255)
				throw new InvalidObjectException("name field is too long");
			this.version = jo.getString("version");
			if (version.length() > 20)
				throw new InvalidObjectException("version field is too long");
			this.description = jo.optString("description");
			this.business_description = jo.optString("business_description"); // New field is added - Groups feature Rally:US708102 - 1610
			if (description.length() > 1000)
				throw new InvalidObjectException("technical description field is too long");
			
			if (business_description.length() > 1000) // New field is added - Groups feature Rally:US708102 - 1610
				throw new InvalidObjectException("business description field is too long");

			this.authorization = new FeedAuthorization();
			JSONObject jauth = jo.getJSONObject("authorization");
			this.authorization.setClassification(jauth.getString("classification"));
			if (this.authorization.getClassification().length() > 32)
				throw new InvalidObjectException("classification field is too long");
			JSONArray ja = jauth.getJSONArray("endpoint_ids");
			for (int i = 0; i < ja.length(); i++) {
				JSONObject id = ja.getJSONObject(i);
				FeedEndpointID fid = new FeedEndpointID(id.getString("id"), id.getString("password"));
				if (fid.getId().length() > 20)
					throw new InvalidObjectException("id field is too long ("+fid.getId()+")");
				if (fid.getPassword().length() > 32)
					throw new InvalidObjectException("password field is too long ("+fid.getPassword()+")");
				this.authorization.getEndpoint_ids().add(fid);
			}
			if (this.authorization.getEndpoint_ids().size() < 1)
				throw new InvalidObjectException("need to specify at least one endpoint_id");
			ja = jauth.getJSONArray("endpoint_addrs");
			for (int i = 0; i < ja.length(); i++) {
				String addr = ja.getString(i);
				if (!JSONUtilities.validIPAddrOrSubnet(addr))
					throw new InvalidObjectException("bad IP addr or subnet mask: "+addr);
				this.authorization.getEndpoint_addrs().add(addr);
			}

			this.publisher = jo.optString("publisher", "");
			this.deleted   = jo.optBoolean("deleted", false);
			this.suspended = jo.optBoolean("suspend", false);
			JSONObject jol = jo.optJSONObject("links");
			this.links = (jol == null) ? (new FeedLinks()) : (new FeedLinks(jol));
		} catch (InvalidObjectException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidObjectException("invalid JSON: "+e.getMessage());
		}
	}
	public int getFeedid() {
		return feedid;
	}
	public void setFeedid(int feedid) {
		this.feedid = feedid;

		// Create link URLs
		FeedLinks fl = getLinks();
		fl.setSelf(URLUtilities.generateFeedURL(feedid));
		fl.setPublish(URLUtilities.generatePublishURL(feedid));
		fl.setSubscribe(URLUtilities.generateSubscribeURL(feedid));
		fl.setLog(URLUtilities.generateFeedLogURL(feedid));
	}
	
	//new getter setters for groups- Rally:US708115 - 1610
	public int getGroupid() {
		return groupid;
	}

	public void setGroupid(int groupid) {
		this.groupid = groupid;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
    // New field is added - Groups feature Rally:US708102 - 1610
	public String getBusiness_description() {
		return business_description;
	}

	public void setBusiness_description(String business_description) {
		this.business_description = business_description;
	}

	public FeedAuthorization getAuthorization() {
		return authorization;
	}
	public void setAuthorization(FeedAuthorization authorization) {
		this.authorization = authorization;
	}
	public String getPublisher() {
		return publisher;
	}
	public void setPublisher(String publisher) {
		if (publisher != null) {
			if (publisher.length() > 8)
				publisher = publisher.substring(0, 8);
			this.publisher = publisher;
		}
	}
	public FeedLinks getLinks() {
		return links;
	}
	public void setLinks(FeedLinks links) {
		this.links = links;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isSuspended() {
		return suspended;
	}

	public void setSuspended(boolean suspended) {
		this.suspended = suspended;
	}

	public Date getLast_mod() {
		return last_mod;
	}

	public Date getCreated_date() {
		return created_date;
	}

	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("feedid", feedid);
		jo.put("groupid", groupid); //New field is added - Groups feature Rally:US708115 - 1610
		jo.put("name", name);
		jo.put("version", version);
		jo.put("description", description);
		jo.put("business_description", business_description); // New field is added - Groups feature Rally:US708102 - 1610
		jo.put("authorization", authorization.asJSONObject());
		jo.put("publisher", publisher);
		jo.put("links", links.asJSONObject());
		jo.put("deleted", deleted);
		jo.put("suspend", suspended);
		jo.put("last_mod", last_mod.getTime());
		jo.put("created_date", created_date.getTime());
		return jo;
	}
	public JSONObject asLimitedJSONObject() {
		JSONObject jo = asJSONObject();
		jo.remove("deleted");
		jo.remove("feedid");
		jo.remove("last_mod");
		jo.remove("created_date");
		return jo;
	}
	public JSONObject asJSONObject(boolean hidepasswords) {
		JSONObject jo = asJSONObject();
		if (hidepasswords) {
			jo.remove("feedid");	// we no longer hide passwords, however we do hide these
			jo.remove("deleted");
			jo.remove("last_mod");
			jo.remove("created_date");
		}
		return jo;
	}
	@Override
	public boolean doDelete(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			String sql = "delete from FEEDS where FEEDID = ?";
			ps = c.prepareStatement(sql);
			ps.setInt(1, feedid);
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
	public synchronized boolean doInsert(Connection c) {
		boolean rv = true;
//		PreparedStatement ps = null;
		try {
			if (feedid == -1) {
//				// Get the next feedid
//				String sql = "insert into FEEDS_UNIQUEID (FEEDID) values (0)";
//				ps = c.prepareStatement(sql, new String[] { "FEEDID" });
//				ps.execute();
//				ResultSet rs = ps.getGeneratedKeys();
//				rs.first();
//				setFeedid(rs.getInt(1));
				// No feed ID assigned yet, so assign the next available one
				setFeedid(next_feedid++);
			}
			// In case we insert a feed from synchronization
			if (feedid > next_feedid)
				next_feedid = feedid+1;

			// Create FEED_ENDPOINT_IDS rows
			FeedAuthorization auth = getAuthorization();
			String sql = "insert into FEED_ENDPOINT_IDS values (?, ?, ?)";
			PreparedStatement ps2 = c.prepareStatement(sql);
			for (FeedEndpointID fid : auth.getEndpoint_ids()) {
				ps2.setInt(1, feedid);
				ps2.setString(2, fid.getId());
				ps2.setString(3, fid.getPassword());
				ps2.executeUpdate();
			}
			ps2.close();

			// Create FEED_ENDPOINT_ADDRS rows
			sql = "insert into FEED_ENDPOINT_ADDRS values (?, ?)";
			ps2 = c.prepareStatement(sql);
			for (String t : auth.getEndpoint_addrs()) {
				ps2.setInt(1, feedid);
				ps2.setString(2, t);
				ps2.executeUpdate();
			}
			ps2.close();

			// Finally, create the FEEDS row
			sql = "insert into FEEDS (FEEDID, NAME, VERSION, DESCRIPTION, AUTH_CLASS, PUBLISHER, SELF_LINK, PUBLISH_LINK, SUBSCRIBE_LINK, LOG_LINK, DELETED, SUSPENDED,BUSINESS_DESCRIPTION, GROUPID) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?)";
			ps2 = c.prepareStatement(sql);
			ps2.setInt(1, feedid);
			ps2.setString(2, getName());
			ps2.setString(3, getVersion());
			ps2.setString(4, getDescription());
			ps2.setString(5, getAuthorization().getClassification());
			ps2.setString(6, getPublisher());
			ps2.setString(7, getLinks().getSelf());
			ps2.setString(8, getLinks().getPublish());
			ps2.setString(9, getLinks().getSubscribe());
			ps2.setString(10, getLinks().getLog());
			ps2.setBoolean(11, isDeleted());
			ps2.setBoolean(12, isSuspended());
			ps2.setString(13,getBusiness_description()); // New field is added - Groups feature Rally:US708102 - 1610
			ps2.setInt(14,groupid); //New field is added - Groups feature Rally:US708115 - 1610
			ps2.executeUpdate();
			ps2.close();
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0005 doInsert: "+e.getMessage());
			e.printStackTrace();
//		} finally {
//			try {
//				ps.close();
//			} catch (SQLException e) {
//				e.printStackTrace();
//			}
		}
		return rv;
	}
	@Override
	public boolean doUpdate(Connection c) {
		boolean rv = true;
		Feed oldobj = getFeedById(feedid);
		PreparedStatement ps = null;
		try {
			Set<FeedEndpointID> newset = getAuthorization().getEndpoint_ids();
			Set<FeedEndpointID> oldset = oldobj.getAuthorization().getEndpoint_ids();

			// Insert new FEED_ENDPOINT_IDS rows
			String sql = "insert into FEED_ENDPOINT_IDS values (?, ?, ?)";
			ps = c.prepareStatement(sql);
			for (FeedEndpointID fid : newset) {
				if (!oldset.contains(fid)) {
					ps.setInt(1, feedid);
					ps.setString(2, fid.getId());
					ps.setString(3, fid.getPassword());
					ps.executeUpdate();
				}
			}
			ps.close();

			// Delete old FEED_ENDPOINT_IDS rows
			sql = "delete from FEED_ENDPOINT_IDS where FEEDID = ? AND USERID = ? AND PASSWORD = ?";
			ps = c.prepareStatement(sql);
			for (FeedEndpointID fid : oldset) {
				if (!newset.contains(fid)) {
					ps.setInt(1, feedid);
					ps.setString(2, fid.getId());
					ps.setString(3, fid.getPassword());
					ps.executeUpdate();
				}
			}
			ps.close();

			// Insert new FEED_ENDPOINT_ADDRS rows
			Set<String> newset2 = getAuthorization().getEndpoint_addrs();
			Set<String> oldset2 = oldobj.getAuthorization().getEndpoint_addrs();
			sql = "insert into FEED_ENDPOINT_ADDRS values (?, ?)";
			ps = c.prepareStatement(sql);
			for (String t : newset2) {
				if (!oldset2.contains(t)) {
					ps.setInt(1, feedid);
					ps.setString(2, t);
					ps.executeUpdate();
				}
			}
			ps.close();

			// Delete old FEED_ENDPOINT_ADDRS rows
			sql = "delete from FEED_ENDPOINT_ADDRS where FEEDID = ? AND ADDR = ?";
			ps = c.prepareStatement(sql);
			for (String t : oldset2) {
				if (!newset2.contains(t)) {
					ps.setInt(1, feedid);
					ps.setString(2, t);
					ps.executeUpdate();
				}
			}
			ps.close();

			// Finally, update the FEEDS row
			sql = "update FEEDS set DESCRIPTION = ?, AUTH_CLASS = ?, DELETED = ?, SUSPENDED = ?, BUSINESS_DESCRIPTION=?, GROUPID=? where FEEDID = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, getDescription());
			ps.setString(2, getAuthorization().getClassification());
			ps.setInt(3, deleted ? 1 : 0);
			ps.setInt(4, suspended ? 1 : 0);
			ps.setString(5, getBusiness_description()); // New field is added - Groups feature Rally:US708102 - 1610
			ps.setInt(6, groupid); //New field is added - Groups feature Rally:US708115 - 1610
			ps.setInt(7, feedid);
			ps.executeUpdate();
			ps.close();
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0006 doUpdate: "+e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (ps != null)
					ps.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return rv;
	}
	
	/**Rally US708115
	 * Change Ownership of FEED - 1610
	 * */
	public boolean changeOwnerShip() {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection c = db.getConnection();
			String sql = "update FEEDS set PUBLISHER = ? where FEEDID = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, this.publisher);
			ps.setInt(2, feedid);
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
	public String getKey() {
		return ""+getFeedid();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Feed))
			return false;
		Feed of = (Feed) obj;
		if (feedid != of.feedid)
			return false;
		if (groupid != of.groupid) //New field is added - Groups feature Rally:US708115 - 1610
			return false;
		if (!name.equals(of.name))
			return false;
		if (!version.equals(of.version))
			return false;
		if (!description.equals(of.description))
			return false;
		if (!business_description.equals(of.business_description)) // New field is added - Groups feature Rally:US708102 - 1610
			return false;
		if (!publisher.equals(of.publisher))
			return false;
		if (!authorization.equals(of.authorization))
			return false;
		if (!links.equals(of.links))
			return false;
		if (deleted != of.deleted)
			return false;
		if (suspended != of.suspended)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "FEED: feedid=" + feedid + ", name=" + name + ", version=" + version;
	}
}
