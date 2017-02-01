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
  * $Id: DeliveryQueueHelper.java,v 1.7 2013/08/08 20:33:40 agg Exp $
  */

package com.att.research.datarouter.node;

/**
 *	Interface to allow independent testing of the DeliveryQueue code
 *	<p>
 *	This interface represents all of the configuration information and
 *	feedback mechanisms that a delivery queue needs.
 */
public interface	DeliveryQueueHelper	{
	/**
	 *	Get the timeout (milliseconds) before retrying after an initial delivery failure
	 */
	public long getInitFailureTimer();
	/**
	 *	Get the ratio between timeouts on consecutive delivery attempts
	 */
	public double	getFailureBackoff();
	/**
	 *	Get the maximum timeout (milliseconds) between delivery attempts
	 */
	public long	getMaxFailureTimer();
	/**
	 *	Get the expiration timer (milliseconds) for deliveries
	 */
	public long	getExpirationTimer();
	/**
	 *	Get the maximum number of file delivery attempts before checking
	 *	if another queue has work to be performed.
	 */
	public int getFairFileLimit();
	/**
	 *	Get the maximum amount of time spent delivering files before checking if another queue has work to be performed.
	 */
	public long getFairTimeLimit();
	/**
	 *	Get the URL for delivering a file
	 *	@param dest	The destination information for the file to be delivered.
	 *	@param fileid	The file id for the file to be delivered.
	 *	@return	The URL for delivering the file (typically, dest.getURL() + "/" + fileid).
	 */
	public String	getDestURL(DestInfo dest, String fileid);
	/**
	 *	Forget redirections associated with a subscriber
	 *	@param	dest	Destination information to forget
	 */
	public void	handleUnreachable(DestInfo dest);
	/**
	 *	Post redirection for a subscriber
	 *	@param	dest	Destination information to update
	 *	@param	location	Location given by subscriber
	 *	@param	fileid	File ID of request
	 *	@return	true if this 3xx response is retryable, otherwise, false.
	 */
	public boolean	handleRedirection(DestInfo dest, String location, String fileid);
	/**
	 *	Should I handle 3xx responses differently than 4xx responses?
	 */
	public boolean	isFollowRedirects();
	/**
	 *	Get the feed ID for a subscription
	 *	@param subid	The subscription ID
	 *	@return	The feed ID
	 */
	public String getFeedId(String subid);
}
