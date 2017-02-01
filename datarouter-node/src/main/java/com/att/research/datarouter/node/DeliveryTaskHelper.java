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
  * $Id: DeliveryTaskHelper.java,v 1.6 2013/10/04 16:29:17 agg Exp $
  */

package com.att.research.datarouter.node;

/**
 *	Interface to allow independent testing of the DeliveryTask code.
 *	<p>
 *	This interface represents all the configuraiton information and
 *	feedback mechanisms that a delivery task needs.
 */

public interface DeliveryTaskHelper	{
	/**
	 *	Report that a delivery attempt failed due to an exception (like can't connect to remote host)
	 *	@param task	The task that failed
	 *	@param exception	The exception that occurred
	 */
	public void reportException(DeliveryTask task, Exception exception);
	/**
	 *	Report that a delivery attempt completed (successfully or unsuccessfully)
	 *	@param task	The task that failed
	 *	@param status	The HTTP status
	 *	@param xpubid	The publish ID from the far end (if any)
	 *	@param location	The redirection location for a 3XX response
	 */
	public void reportStatus(DeliveryTask task, int status, String xpubid, String location);
	/**
	 *	Report that a delivery attempt either failed while sending data or that an error was returned instead of a 100 Continue.
	 *	@param task	The task that failed
	 *	@param sent	The number of bytes sent or -1 if an error was returned instead of 100 Continue.
	 */
	public void reportDeliveryExtra(DeliveryTask task, long sent);
	/**
	 *	Get the destination information for the delivery queue
	 *	@return	The destination information
	 */
	public DestInfo getDestInfo();
	/**
	 *	Given a file ID, get the URL to deliver to
	 *	@param fileid	The file id
	 *	@return	The URL to deliver to
	 */
	public String	getDestURL(String fileid);
	/**
	 *	Get the feed ID for a subscription
	 *	@param subid	The subscription ID
	 *	@return	The feed iD
	 */
	public String	getFeedId(String subid);
}
