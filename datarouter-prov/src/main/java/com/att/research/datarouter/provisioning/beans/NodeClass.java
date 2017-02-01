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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.att.research.datarouter.provisioning.utils.DB;

/**
 * This class is used to aid in the mapping of node names from/to node IDs.
 *
 * @author Robert P. Eby
 * @version $Id: NodeClass.java,v 1.2 2014/01/15 16:08:43 eby Exp $
 */
public abstract class NodeClass extends Syncable {
	private static Map<String, Integer> map;

	public NodeClass() {
		// init on first use
		if (map == null) {
			reload();
		}
	}

	/**
	 * Add nodes to the NODES table, when the NODES parameter value is changed.
	 * Nodes are only added to the table, they are never deleted.  The node name is normalized
	 * to contain the domain (if missing).
	 * @param nodes a pipe separated list of the current nodes
	 */
	public static void setNodes(String[] nodes) {
		if (map == null)
			reload();
		int nextid = 0;
		for (Integer n : map.values()) {
			if (n >= nextid)
				nextid = n+1;
		}
		// take | separated list, add domain if needed.
		Logger intlogger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
		for (String node : nodes) {
			node = normalizeNodename(node);
			if (!map.containsKey(node)) {
				intlogger.info("..adding "+node+" to NODES with index "+nextid);
				map.put(node, nextid);
				PreparedStatement ps = null;
				try {
					DB db = new DB();
					@SuppressWarnings("resource")
					Connection conn = db.getConnection();
					ps = conn.prepareStatement("insert into NODES (NODEID, NAME, ACTIVE) values (?, ?, 1)");
					ps.setInt(1, nextid);
					ps.setString(2, node);
					ps.execute();
					ps.close();
					db.release(conn);
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
				nextid++;
			}
		}
	}

	public static void reload() {
		Map<String, Integer> m = new HashMap<String, Integer>();
		PreparedStatement ps = null;
		try {
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			String sql = "select NODEID, NAME from NODES";
			ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				int id = rs.getInt("NODEID");
				String name = rs.getString("NAME");
				m.put(name, id);
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
		map = m;
	}

	public static Integer lookupNodeName(final String name) throws IllegalArgumentException {
		Integer n = map.get(name);
		if (n == null)
			throw new IllegalArgumentException("Invalid node name: "+name);
		return n;
	}

	public static Collection<String> lookupNodeNames(String patt) throws IllegalArgumentException {
		Collection<String> coll = new TreeSet<String>();
		final Set<String> keyset = map.keySet();
		for (String s : patt.toLowerCase().split(",")) {
			if (s.endsWith("*")) {
				s = s.substring(0, s.length()-1);
				for (String s2 : keyset) {
					if (s2.startsWith(s))
						coll.add(s2);
				}
			} else if (keyset.contains(s)) {
				coll.add(s);
			} else if (keyset.contains(normalizeNodename(s))) {
				coll.add(normalizeNodename(s));
			} else {
				throw new IllegalArgumentException("Invalid node name: "+s);
			}
		}
		return coll;
	}

	protected String lookupNodeID(int n) {
		for (String s : map.keySet()) {
			if (map.get(s) == n)
				return s;
		}
		return null;
	}

	public static String normalizeNodename(String s) {
		if (s != null && s.indexOf('.') <= 0) {
			Parameters p = Parameters.getParameter(Parameters.PROV_DOMAIN);
			if (p != null) {
				String domain = p.getValue();
				s += "." + domain;
			}
		}
		return s.toLowerCase();
	}
}
