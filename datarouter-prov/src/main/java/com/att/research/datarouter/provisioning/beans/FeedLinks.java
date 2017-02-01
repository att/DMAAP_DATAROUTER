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
 * The URLs associated with a Feed.
 * @author Robert Eby
 * @version $Id: FeedLinks.java,v 1.3 2013/07/05 13:48:05 eby Exp $
 */
public class FeedLinks implements JSONable {
	private String self;
	private String publish;
	private String subscribe;
	private String log;

	public FeedLinks() {
		self = publish = subscribe = log = null;
	}

	public FeedLinks(JSONObject jo) throws InvalidObjectException {
		this();
		self      = jo.getString("self");
		publish   = jo.getString("publish");
		subscribe = jo.getString("subscribe");
		log       = jo.getString("log");
	}

	public String getSelf() {
		return self;
	}
	public void setSelf(String self) {
		this.self = self;
	}
	public String getPublish() {
		return publish;
	}
	public void setPublish(String publish) {
		this.publish = publish;
	}
	public String getSubscribe() {
		return subscribe;
	}
	public void setSubscribe(String subscribe) {
		this.subscribe = subscribe;
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
		jo.put("publish", publish);
		jo.put("subscribe", subscribe);
		jo.put("log", log);
		return jo;
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof FeedLinks))
			return false;
		FeedLinks of = (FeedLinks) obj;
		if (!self.equals(of.self))
			return false;
		if (!publish.equals(of.publish))
			return false;
		if (!subscribe.equals(of.subscribe))
			return false;
		if (!log.equals(of.log))
			return false;
		return true;
	}
}
