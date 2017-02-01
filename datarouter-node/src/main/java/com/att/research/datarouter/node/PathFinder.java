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
  * $Id: PathFinder.java,v 1.3 2013/04/29 12:40:05 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.*;

/**
 *	Given a set of node names and next hops, identify and ignore any cycles and figure out the sequence of next hops to get from this node to any other node
 */

public class PathFinder	{
	private static class Hop	{
		public boolean	mark;
		public boolean	bad;
		public NodeConfig.ProvHop	basis;
	}
	private Vector<String> errors = new Vector<String>();
	private Hashtable<String, String> routes = new Hashtable<String, String>();
	/**
	 *	Get list of errors encountered while finding paths
	 *	@return array of error descriptions
	 */
	public String[] getErrors() {
		return(errors.toArray(new String[errors.size()]));
	}
	/**
	 *	Get the route from this node to the specified node
	 *	@param destination node
	 *	@return	list of node names separated by and ending with "/"
	 */
	public String getPath(String destination) {
		String ret = routes.get(destination);
		if (ret == null) {
			return("");
		}
		return(ret);
	}
	private String plot(String from, String to, Hashtable<String, Hop> info) {
		Hop nh = info.get(from);
		if (nh == null || nh.bad) {
			return(to);
		}
		if (nh.mark) {
			// loop detected;
			while (!nh.bad) {
				nh.bad = true;
				errors.add(nh.basis + " is part of a cycle");
				nh = info.get(nh.basis.getVia());
			}
			return(to);
		}
		nh.mark = true;
		String x = plot(nh.basis.getVia(), to, info);
		nh.mark = false;
		if (nh.bad) {
			return(to);
		}
		return(nh.basis.getVia() + "/" + x);
	}
	/**
	 *	Find routes from a specified origin to all of the nodes given a set of specified next hops.
	 *	@param origin	where we start
	 *	@param nodes	where we can go
	 *	@param hops	detours along the way
	 */
	public PathFinder(String origin, String[] nodes, NodeConfig.ProvHop[] hops) {
		HashSet<String> known = new HashSet<String>();
		Hashtable<String, Hashtable<String, Hop>> ht = new Hashtable<String, Hashtable<String, Hop>>();
		for (String n: nodes) {
			known.add(n);
			ht.put(n, new Hashtable<String, Hop>());
		}
		for (NodeConfig.ProvHop ph: hops) {
			if (!known.contains(ph.getFrom())) {
				errors.add(ph + " references unknown from node");
				continue;
			}
			if (!known.contains(ph.getTo())) {
				errors.add(ph + " references unknown destination node");
				continue;
			}
			Hashtable<String, Hop> ht2 = ht.get(ph.getTo());
			Hop h = ht2.get(ph.getFrom());
			if (h != null) {
				h.bad = true;
				errors.add(ph + " gives duplicate next hop - previous via was " + h.basis.getVia());
				continue;
			}
			h = new Hop();
			h.basis = ph;
			ht2.put(ph.getFrom(), h);
			if (!known.contains(ph.getVia())) {
				errors.add(ph + " references unknown via node");
				h.bad = true;
				continue;
			}
			if (ph.getVia().equals(ph.getTo())) {
				errors.add(ph + " gives destination as via");
				h.bad = true;
				continue;
			}
		}
		for (String n: known) {
			if (n.equals(origin)) {
				routes.put(n, "");
			}
			routes.put(n, plot(origin, n, ht.get(n)) + "/");
		}
	}
}
