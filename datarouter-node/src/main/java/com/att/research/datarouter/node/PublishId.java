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
  * $Id: PublishId.java,v 1.2 2013/04/23 17:42:28 agg Exp $
  */

package com.att.research.datarouter.node;

/**
 *	Generate publish IDs
 */
public class PublishId	{
	private long	nextuid;
	private String	myname;

	/**
	 *	Generate publish IDs for the specified name
	 *	@param myname	Unique identifier for this publish ID generator (usually fqdn of server)
	 */
	public PublishId(String myname) {
		this.myname = myname;
	}
	/**
	 *	Generate a Data Router Publish ID that uniquely identifies the particular invocation of the Publish API for log correlation purposes.
	 */
	public synchronized String next() {
		long now = System.currentTimeMillis();
		if (now < nextuid) {
			now = nextuid;
		}
		nextuid = now + 1;
		return(now + "." + myname);
	}
}
