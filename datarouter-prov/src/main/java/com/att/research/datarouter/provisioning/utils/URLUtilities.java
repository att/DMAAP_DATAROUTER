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
import java.util.Arrays;

import com.att.research.datarouter.provisioning.BaseServlet;

/**
 * Utility functions used to generate the different URLs used by the Data Router.
 *
 * @author Robert Eby
 * @version $Id: URLUtilities.java,v 1.2 2014/03/12 19:45:41 eby Exp $
 */
public class URLUtilities {
	/**
	 * Generate the URL used to access a feed.
	 * @param feedid the feed id
	 * @return the URL
	 */
	public static String generateFeedURL(int feedid) {
		return "https://" + BaseServlet.prov_name + "/feed/" + feedid;
	}
	/**
	 * Generate the URL used to publish to a feed.
	 * @param feedid the feed id
	 * @return the URL
	 */
	public static String generatePublishURL(int feedid) {
		return "https://" + BaseServlet.prov_name + "/publish/" + feedid;
	}
	/**
	 * Generate the URL used to subscribe to a feed.
	 * @param feedid the feed id
	 * @return the URL
	 */
	public static String generateSubscribeURL(int feedid) {
		return "https://" + BaseServlet.prov_name + "/subscribe/" + feedid;
	}
	/**
	 * Generate the URL used to access a feed's logs.
	 * @param feedid the feed id
	 * @return the URL
	 */
	public static String generateFeedLogURL(int feedid) {
		return "https://" + BaseServlet.prov_name + "/feedlog/" + feedid;
	}
	/**
	 * Generate the URL used to access a subscription.
	 * @param subid the subscription id
	 * @return the URL
	 */
	public static String generateSubscriptionURL(int subid) {
		return "https://" + BaseServlet.prov_name + "/subs/" + subid;
	}
	/**
	 * Generate the URL used to access a subscription's logs.
	 * @param subid the subscription id
	 * @return the URL
	 */
	public static String generateSubLogURL(int subid) {
		return "https://" + BaseServlet.prov_name + "/sublog/" + subid;
	}
	/**
	 * Generate the URL used to access the provisioning data on the peer POD.
	 * @return the URL
	 */
	public static String generatePeerProvURL() {
		return "https://" + getPeerPodName() + "/internal/prov";
	}
	/**
	 * Generate the URL used to access the logfile data on the peer POD.
	 * @return the URL
	 */
	public static String generatePeerLogsURL() {
		//Fixes for Itrack ticket - DATARTR-4#Fixing if only one Prov is configured, not to give exception to fill logs.
		String peerPodUrl = getPeerPodName();
		if(peerPodUrl.equals("") || peerPodUrl.equals(null)){
			return "";
		}
				
		return "https://" + peerPodUrl + "/internal/drlogs/";
	}
	/**
	 * Return the real (non CNAME) version of the peer POD's DNS name.
	 * @return the name
	 */
	public static String getPeerPodName() {
		if (other_pod == null) {
			String this_pod = "";
			try {
				this_pod = InetAddress.getLocalHost().getHostName();
				System.out.println("this_pod: "+this_pod);
			} catch (UnknownHostException e) {
				this_pod = "";
			}
			System.out.println("ALL PODS: "+Arrays.asList(BaseServlet.getPods()));
			for (String pod : BaseServlet.getPods()) {
				if (!pod.equals(this_pod))
					other_pod = pod;
			}
		}
		return other_pod;
	}
	private static String other_pod;
}
