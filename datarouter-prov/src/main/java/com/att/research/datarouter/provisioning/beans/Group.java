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

import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.URLUtilities;

/**
 * The representation of a Subscription.  Subscriptions can be retrieved from the DB, or stored/updated in the DB.
 * @author vikram
 * @version $Id: Group.java,v 1.0 2016/07/19 
 */
public class Group extends Syncable {
	private static Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
	private static int next_groupid = getMaxGroupID() + 1;

	private int groupid;
	private String authid;
	private String name;
	private String description;
	private String classification;
	private String members;
	private Date last_mod;
	
	
	public static Group getGroupMatching(Group gup) {
		String sql = String.format(
			"select * from GROUPS where  NAME = \"%s\"",
			gup.getName()
		);
		List<Group> list = getGroupsForSQL(sql);
		return list.size() > 0 ? list.get(0) : null;
	}
	
	public static Group getGroupMatching(Group gup, int groupid) {
		String sql = String.format(
			"select * from GROUPS where  NAME = \"%s\" and GROUPID != %d ",
			gup.getName(),
			gup.getGroupid()
		);
		List<Group> list = getGroupsForSQL(sql);
		return list.size() > 0 ? list.get(0) : null;
	}
	
	public static Group getGroupById(int id) {
		String sql = "select * from GROUPS where GROUPID = " + id;
		List<Group> list = getGroupsForSQL(sql);
		return list.size() > 0 ? list.get(0) : null;
	}
	
	public static Group getGroupByAuthId(String id) {
		String sql = "select * from GROUPS where AUTHID = '" + id +"'";
		List<Group> list = getGroupsForSQL(sql);
		return list.size() > 0 ? list.get(0) : null;
	}
	
	public static Collection<Group> getAllgroups() {
		return getGroupsForSQL("select * from GROUPS");
	}
	private static List<Group> getGroupsForSQL(String sql) {
		List<Group> list = new ArrayList<Group>();
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				Group group = new Group(rs);
				list.add(group);
			}
			rs.close();
			stmt.close();
			db.release(conn);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	public static int getMaxGroupID() {
		int max = 0;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select MAX(groupid) from GROUPS");
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
	public static Collection<String> getGroupsByClassfication(String classfication) {
		List<String> list = new ArrayList<String>();
		String sql = "select * from GROUPS where classification = '"+classfication+"'";
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			Statement  stmt = conn.createStatement();
			ResultSet  rs = stmt.executeQuery(sql);
			while (rs.next()) {
				int groupid = rs.getInt("groupid");
				//list.add(URLUtilities.generateSubscriptionURL(groupid));
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

	public Group() {
		this("", "", "");
	}
	public Group(String name, String desc, String members) {
		this.groupid = -1;
		this.authid = "";
		this.name = name;
		this.description = desc;
		this.members = members;
		this.classification = "";
		this.last_mod = new Date();
	}
	
	
	public Group(ResultSet rs) throws SQLException {
		this.groupid        = rs.getInt("GROUPID");
		this.authid       = rs.getString("AUTHID");
		this.name       = rs.getString("NAME");
		this.description       = rs.getString("DESCRIPTION");
		this.classification       = rs.getString("CLASSIFICATION");
		this.members       = rs.getString("MEMBERS");
		this.last_mod     = rs.getDate("LAST_MOD");
	}
	

	
	public Group(JSONObject jo) throws InvalidObjectException {
		this("", "", "");
		try {
			// The JSONObject is assumed to contain a vnd.att-dr.group representation
			this.groupid  = jo.optInt("groupid", -1);
			String gname      = jo.getString("name");
			String gdescription     = jo.getString("description");
			
			this.authid = jo.getString("authid");
			this.name = gname;
			this.description = gdescription;
			this.classification = jo.getString("classification");
			this.members = jo.getString("members");
		
			if (gname.length() > 50)
				throw new InvalidObjectException("Group name is too long");
			if (gdescription.length() > 256)
				throw new InvalidObjectException("Group Description is too long");
		} catch (InvalidObjectException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidObjectException("invalid JSON: "+e.getMessage());
		}
	}
	public int getGroupid() {
		return groupid;
	}
	
	public static Logger getIntlogger() {
		return intlogger;
	}
	public void setGroupid(int groupid) {
		this.groupid = groupid;
	}
	
	public static void setIntlogger(Logger intlogger) {
		Group.intlogger = intlogger;
	}
	public static int getNext_groupid() {
		return next_groupid;
	}
	public static void setNext_groupid(int next_groupid) {
		Group.next_groupid = next_groupid;
	}
	public String getAuthid() {
		return authid;
	}
	public void setAuthid(String authid) {
		this.authid = authid;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getClassification() {
		return classification;
	}
	public void setClassification(String classification) {
		this.classification = classification;
	}
	public String getMembers() {
		return members;
	}
	public void setMembers(String members) {
		this.members = members;
	}
	public Date getLast_mod() {
		return last_mod;
	}
	public void setLast_mod(Date last_mod) {
		this.last_mod = last_mod;
	}
	

	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("groupid", groupid);
		jo.put("authid", authid);
		jo.put("name", name);
		jo.put("description", description);
		jo.put("classification", classification);
		jo.put("members", members);
		jo.put("last_mod", last_mod.getTime());
		return jo;
	}
	@Override
	public boolean doInsert(Connection c) {
		boolean rv = true;
		PreparedStatement ps = null;
		try {
			if (groupid == -1) {
				// No feed ID assigned yet, so assign the next available one
				setGroupid(next_groupid++);
			}
			// In case we insert a gropup from synchronization
			if (groupid > next_groupid)
				next_groupid = groupid+1;

			
			// Create the GROUPS row
			String sql = "insert into GROUPS (GROUPID, AUTHID, NAME, DESCRIPTION, CLASSIFICATION, MEMBERS) values (?, ?, ?, ?, ?, ?)";
			ps = c.prepareStatement(sql, new String[] { "GROUPID" });
			ps.setInt(1, groupid);
			ps.setString(2, authid);
			ps.setString(3, name);
			ps.setString(4, description);
			ps.setString(5, classification);
			ps.setString(6, members);
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
			String sql = "update GROUPS set AUTHID = ?, NAME = ?, DESCRIPTION = ?, CLASSIFICATION = ? ,  MEMBERS = ? where GROUPID = ?";
			ps = c.prepareStatement(sql);
			ps.setString(1, authid);
			ps.setString(2, name);
			ps.setString(3, description);
			ps.setString(4, classification);
			ps.setString(5, members);
			ps.setInt(6, groupid);
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
			String sql = "delete from GROUPS where GROUPID = ?";
			ps = c.prepareStatement(sql);
			ps.setInt(1, groupid);
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
		return ""+getGroupid();
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Group))
			return false;
		Group os = (Group) obj;
		if (groupid != os.groupid)
			return false;
		if (authid != os.authid)
			return false;
		if (!name.equals(os.name))
			return false;
		if (description != os.description)
			return false;
		if (!classification.equals(os.classification))
			return false;
		if (!members.equals(os.members))
			return false;
		
		return true;
	}

	@Override
	public String toString() {
		return "GROUP: groupid=" + groupid;
	}
}
