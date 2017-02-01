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

package com.att.research.datarouter.provisioning.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * Some utility functions used when creating/validating JSON.
 *
 * @author Robert Eby
 * @version $Id: JSONUtilities.java,v 1.1 2013/04/26 21:00:26 eby Exp $
 */
public class JSONUtilities {
	/**
	 * Does the String <i>v</i> represent a valid Internet address (with or without a
	 * mask length appended).
	 * @param v the string to check
	 * @return true if valid, false otherwise
	 */
	public static boolean validIPAddrOrSubnet(String v) {
		String[] pp = { v, "" };
		if (v.indexOf('/') > 0)
			pp = v.split("/");
		try {
			InetAddress addr = InetAddress.getByName(pp[0]);
			if (pp[1].length() > 0) {
				// check subnet mask
				int mask = Integer.parseInt(pp[1]);
				if (mask > (addr.getAddress().length * 8))
					return false;
			}
			return true;
		} catch (UnknownHostException e) {
			return false;
		}
	}
	/**
	 * Build a JSON array from a collection of Strings.
	 * @param coll the collection
	 * @return a String containing a JSON array
	 */
	public static String createJSONArray(Collection<String> coll) {
		StringBuilder sb = new StringBuilder("[");
		String pfx = "\n";
		for (String t : coll) {
			sb.append(pfx).append("  \"").append(t).append("\"");
			pfx = ",\n";
		}
		sb.append("\n]\n");
		return sb.toString();
	}
}
