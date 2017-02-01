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

 /*
  * $Id: Target.java,v 1.2 2013/04/23 17:42:28 agg Exp $
  */

package com.att.research.datarouter.node;

/**
 *	A destination to deliver a message
 */
public class Target	{
	private DestInfo	destinfo;
	private String	routing;
	/**
	 *	A destination to deliver a message
	 *	@param destinfo	Either info for a subscription ID or info for a node-to-node transfer
	 *	@param routing	For a node-to-node transfer, what to do when it gets there.
	 */
	public Target(DestInfo destinfo, String routing) {
		this.destinfo = destinfo;
		this.routing = routing;
	}
	/**
	 *	Add additional routing
	 */
	public void addRouting(String routing) {
		this.routing = this.routing + " " + routing;
	}
	/**
	 *	Get the destination information for this target
	 */
	public DestInfo getDestInfo() {
		return(destinfo);
	}
	/**
	 *	Get the next hop information for this target
	 */
	public String getRouting() {
		return(routing);
	}
}
