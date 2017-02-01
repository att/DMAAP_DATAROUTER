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

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The representation of a Feed authorization.  This encapsulates the authorization information about a feed.
 * @author Robert Eby
 * @version $Id: FeedAuthorization.java,v 1.2 2013/06/20 14:11:05 eby Exp $
 */
public class FeedAuthorization implements JSONable {
	private String classification;
	private Set<FeedEndpointID> endpoint_ids;
	private Set<String> endpoint_addrs;

	public FeedAuthorization() {
		this.classification = "";
		this.endpoint_ids = new HashSet<FeedEndpointID>();
		this.endpoint_addrs = new HashSet<String>();
	}
	public String getClassification() {
		return classification;
	}
	public void setClassification(String classification) {
		this.classification = classification;
	}
	public Set<FeedEndpointID> getEndpoint_ids() {
		return endpoint_ids;
	}
	public void setEndpoint_ids(Set<FeedEndpointID> endpoint_ids) {
		this.endpoint_ids = endpoint_ids;
	}
	public Set<String> getEndpoint_addrs() {
		return endpoint_addrs;
	}
	public void setEndpoint_addrs(Set<String> endpoint_addrs) {
		this.endpoint_addrs = endpoint_addrs;
	}

	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("classification", classification);
		JSONArray ja = new JSONArray();
		for (FeedEndpointID eid : endpoint_ids) {
			ja.put(eid.asJSONObject());
		}
		jo.put("endpoint_ids", ja);
		ja = new JSONArray();
		for (String t : endpoint_addrs) {
			ja.put(t);
		}
		jo.put("endpoint_addrs", ja);
		return jo;
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof FeedAuthorization))
			return false;
		FeedAuthorization of = (FeedAuthorization) obj;
		if (!classification.equals(of.classification))
			return false;
		if (!endpoint_ids.equals(of.endpoint_ids))
			return false;
		if (!endpoint_addrs.equals(of.endpoint_addrs))
			return false;
		return true;
	}
}
