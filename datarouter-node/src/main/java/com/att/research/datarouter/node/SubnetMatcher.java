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
  * $Id: SubnetMatcher.java,v 1.3 2013/05/01 15:28:37 agg Exp $
  */

package com.att.research.datarouter.node;

import java.net.*;

/**
 *	Compare IP addresses as byte arrays to a subnet specified as a CIDR
 */
public class SubnetMatcher	{
	private byte[]	sn;
	private int	len;
	private int	mask;
	/**
	 *	Construct a subnet matcher given a CIDR
	 *	@param subnet	The CIDR to match
	 */
	public SubnetMatcher(String subnet) {
		int i = subnet.lastIndexOf('/');
		if (i == -1) {
			sn = NodeUtils.getInetAddress(subnet);
			len = sn.length;
		} else {
			len = Integer.parseInt(subnet.substring(i + 1));
			sn = NodeUtils.getInetAddress(subnet.substring(0, i));
			mask = ((0xff00) >> (len % 8)) & 0xff;
			len /= 8;
		}
	}
	/**
	 *	Is the IP address in the CIDR?
	 *	@param addr the IP address as bytes in network byte order
	 *	@return true if the IP address matches.
	 */
	public boolean matches(byte[] addr) {
		if (addr.length != sn.length) {
			return(false);
		}
		for (int i = 0; i < len; i++) {
			if (addr[i] != sn[i]) {
				return(false);
			}
		}
		if (mask != 0 && ((addr[len] ^ sn[len]) & mask) != 0) {
			return(false);
		}
		return(true);
	}
}
