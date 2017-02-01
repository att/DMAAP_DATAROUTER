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

import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.JSONObject;

/**
 * The representation of a Feed endpoint.  This contains a login/password pair.
 * @author Robert Eby
 * @version $Id: FeedEndpointID.java,v 1.1 2013/04/26 21:00:26 eby Exp $
 */
public class FeedEndpointID implements JSONable {
	private String id;
	private String password;

	public FeedEndpointID() {
		this("", "");
	}
	public FeedEndpointID(String id, String password) {
		this.id = id;
		this.password = password;
	}
	public FeedEndpointID(ResultSet rs) throws SQLException {
		this.id       = rs.getString("USERID");
		this.password = rs.getString("PASSWORD");
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("id", id);
		jo.put("password", password);
		return jo;
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof FeedEndpointID))
			return false;
		FeedEndpointID f2 = (FeedEndpointID) obj;
		return id.equals(f2.id) && password.equals(f2.password);
	}
	@Override
	public int hashCode() {
		return (id + ":" + password).hashCode();
	}
}