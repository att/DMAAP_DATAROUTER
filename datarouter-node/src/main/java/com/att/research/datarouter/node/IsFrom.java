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
  * $Id: IsFrom.java,v 1.3 2013/05/01 15:28:37 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.*;
import java.net.*;

/**
 *	Determine if an IP address is from a machine
 */
public class IsFrom	{
	private long	nextcheck;
	private String[] ips;
	private String	fqdn;
	/**
	 *	Configure the JVM DNS cache to have a 10 second TTL.  This needs to be called very very early or it won't have any effect.
	 */
	public static void setDNSCache() {
		java.security.Security.setProperty("networkaddress.cache.ttl", "10");
	}
	/**
	 *	Create an IsFrom for the specified fully qualified domain name.
	 */
	public IsFrom(String fqdn) {
		this.fqdn = fqdn;
	}
	/**
	 *	Check if an IP address matches.  If it has been more than
	 *	10 seconds since DNS was last checked for changes to the
	 *	IP address(es) of this FQDN, check again.  Then check
	 *	if the specified IP address belongs to the FQDN.
	 */
	public synchronized boolean isFrom(String ip) {
		long now = System.currentTimeMillis();
		if (now > nextcheck) {
			nextcheck = now + 10000;
			Vector<String> v = new Vector<String>();
			try {
				InetAddress[] addrs = InetAddress.getAllByName(fqdn);
				for (InetAddress a: addrs) {
					v.add(a.getHostAddress());
				}
			} catch (Exception e) {
			}
			ips = v.toArray(new String[v.size()]);
		}
		for (String s: ips) {
			if (s.equals(ip)) {
				return(true);
			}
		}
		return(false);
	}
	/**
	 *	Return the fully qualified domain name
	 */
	public String toString() {
		return(fqdn);
	}
}
