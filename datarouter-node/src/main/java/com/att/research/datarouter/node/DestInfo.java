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
  * $Id: DestInfo.java,v 1.3 2013/04/29 12:40:05 agg Exp $
  */

package com.att.research.datarouter.node;

/**
 *	Information for a delivery destination that doesn't change from message to message
 */
public class DestInfo	{
	private String	name;
	private String	spool;
	private String	subid;
	private String	logdata;
	private String	url;
	private String	authuser;
	private String	authentication;
	private boolean	metaonly;
	private boolean	use100;
	/**
	 *	Create a destination information object.
	 *	@param	name	n:fqdn or s:subid
	 *	@param	spool	The directory where files are spooled.
	 *	@param	subid	The subscription ID (if applicable).
	 *	@param	logdata	Text to be included in log messages
	 *	@param	url	The URL to deliver to.
	 *	@param	authuser	The auth user for logging.
	 *	@param	authentication	The credentials.
	 *	@param	metaonly	Is this a metadata only delivery?
	 *	@param	use100	Should I use expect 100-continue?
	 */
	public DestInfo(String name, String spool, String subid, String logdata, String url, String authuser, String authentication, boolean metaonly, boolean use100) {
		this.name = name;
		this.spool = spool;
		this.subid = subid;
		this.logdata = logdata;
		this.url = url;
		this.authuser = authuser;
		this.authentication = authentication;
		this.metaonly = metaonly;
		this.use100 = use100;
	}
	public boolean equals(Object o) {
		return((o instanceof DestInfo) && ((DestInfo)o).spool.equals(spool));
	}
	public int hashCode() {
		return(spool.hashCode());
	}
	/**
	 *	Get the name of this destination
	 */
	public String getName() {
		return(name);
	}
	/**
	 *	Get the spool directory for this destination.
	 *	@return	The spool directory
	 */
	public String getSpool() {
		return(spool);
	}
	/**
	 *	Get the subscription ID.
	 *	@return	Subscription ID or null if this is a node to node delivery.
	 */
	public String getSubId() {
		return(subid);
	}
	/**
	 *	Get the log data.
	 *	@return	Text to be included in a log message about delivery attempts.
	 */
	public String getLogData() {
		return(logdata);
	}
	/**
	 *	Get the delivery URL.
	 *	@return	The URL to deliver to (the primary URL).
	 */
	public String getURL() {
		return(url);

	}
	/**
	 *	Get the user for authentication
	 *	@return	The name of the user for logging
	 */
	public String	getAuthUser() {
		return(authuser);
	}
	/**
	 *	Get the authentication header
	 *	@return	The string to use to authenticate to the recipient.
	 */
	public String getAuth() {
		return(authentication);
	}
	/**
	 *	Is this a metadata only delivery?
	 *	@return	True if this is a metadata only delivery
	 */
	public boolean	isMetaDataOnly() {
		return(metaonly);
	}
	/**
	 *	Should I send expect 100-continue header?
	 *	@return	True if I should.
	 */
	public boolean isUsing100() {
		return(use100);
	}
}
