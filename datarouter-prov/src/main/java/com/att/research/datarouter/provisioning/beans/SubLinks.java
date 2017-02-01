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

import org.json.JSONObject;

/**
 * The URLs associated with a Subscription.
 * @author Robert Eby
 * @version $Id: SubLinks.java,v 1.3 2013/07/05 13:48:05 eby Exp $
 */
public class SubLinks implements JSONable {
	private String self;
	private String feed;
	private String log;

	public SubLinks() {
		self = feed = log = null;
	}
	public SubLinks(JSONObject jo) throws InvalidObjectException {
		this();
		self = jo.getString("self");
		feed = jo.getString("feed");
		log  = jo.getString("log");
	}
	public SubLinks(String self, String feed, String log) {
		this.self = self;
		this.feed = feed;
		this.log  = log;
	}
	public String getSelf() {
		return self;
	}
	public void setSelf(String self) {
		this.self = self;
	}
	public String getFeed() {
		return feed;
	}
	public void setFeed(String feed) {
		this.feed = feed;
	}
	public String getLog() {
		return log;
	}
	public void setLog(String log) {
		this.log = log;
	}

	@Override
	public JSONObject asJSONObject() {
		JSONObject jo = new JSONObject();
		jo.put("self", self);
		jo.put("feed", feed);
		jo.put("log", log);
		return jo;
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SubLinks))
			return false;
		SubLinks os = (SubLinks) obj;
		if (!self.equals(os.self))
			return false;
		if (!feed.equals(os.feed))
			return false;
		if (!log.equals(os.log))
			return false;
		return true;
	}
}
