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
  * $Id: NodeConfig.java,v 1.11 2014/02/10 20:53:06 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.*;
import java.io.*;

/**
 *	Processed configuration for this node.
 *	<p>
 *	The NodeConfig represents a processed configuration from the Data Router
 *	provisioning server.  Each time configuration data is received from the
 *	provisioning server, a new NodeConfig is created and the previous one
 *	discarded.
 */
public class NodeConfig	{
	/**
	 *	Raw configuration entry for a data router node
	 */
	public static class ProvNode {
		private String cname;
		/**
		 *	Construct a node configuration entry.
		 *	@param cname	The cname of the node.
		 */
		public ProvNode(String cname) {
			this.cname = cname;
		}
		/**
		 *	Get the cname of the node
		 */
		public String getCName() {
			return(cname);
		}
	}
	/**
	 *	Raw configuration entry for a provisioning parameter
	 */
	public static class ProvParam {
		private String name;
		private String value;
		/**
		 *	Construct a provisioning parameter configuration entry.
		 *	@param	name The name of the parameter.
		 *	@param	value The value of the parameter.
		 */
		public ProvParam(String name, String value) {
			this.name = name;
			this.value = value;
		}
		/**
		 *	Get the name of the parameter.
		 */
		public String getName() {
			return(name);
		}
		/**
		 *	Get the value of the parameter.
		 */
		public String getValue() {
			return(value);
		}
	}
	/**
	 *	Raw configuration entry for a data feed.
	 */
	public static class ProvFeed {
		private String id;
		private String logdata;
		private String status;
		/**
		 *	Construct a feed configuration entry.
		 *	@param id	The feed ID of the entry.
		 *	@param logdata	String for log entries about the entry.
		 *	@param status	The reason why this feed cannot be used (Feed has been deleted, Feed has been suspended) or null if it is valid.
		 */
		public ProvFeed(String id, String logdata, String status) {
			this.id = id;
			this.logdata = logdata;
			this.status = status;
		}
		/**
		 *	Get the feed id of the data feed.
		 */
		public String getId() {
			return(id);
		}
		/**
		 *	Get the log data of the data feed.
		 */
		public String getLogData() {
			return(logdata);
		}
		/**
		 *	Get the status of the data feed.
		 */
		public String getStatus() {
			return(status);
		}
	}
	/**
	 *	Raw configuration entry for a feed user.
	 */
	public static class ProvFeedUser	{
		private String feedid;
		private String user;
		private String credentials;
		/**
		 *	Construct a feed user configuration entry
		 *	@param feedid	The feed id.
		 *	@param user	The user that will publish to the feed.
		 *	@param credentials	The Authorization header the user will use to publish.
		 */
		public ProvFeedUser(String feedid, String user, String credentials) {
			this.feedid = feedid;
			this.user = user;
			this.credentials = credentials;
		}
		/**
		 *	Get the feed id of the feed user.
		 */
		public String getFeedId() {
			return(feedid);
		}
		/**
		 *	Get the user for the feed user.
		 */
		public String getUser() {
			return(user);
		}
		/**
		 *	Get the credentials for the feed user.
		 */
		public String getCredentials() {
			return(credentials);
		}
	}
	/**
	 *	Raw configuration entry for a feed subnet
	 */
	public static class ProvFeedSubnet	{
		private String feedid;
		private String cidr;
		/**
		 *	Construct a feed subnet configuration entry
		 *	@param feedid	The feed ID
		 *	@param cidr	The CIDR allowed to publish to the feed.
		 */
		public ProvFeedSubnet(String feedid, String cidr) {
			this.feedid = feedid;
			this.cidr = cidr;
		}
		/**
		 *	Get the feed id of the feed subnet.
		 */
		public String getFeedId() {
			return(feedid);
		}
		/**
		 *	Get the CIDR of the feed subnet.
		 */
		public String getCidr() {
			return(cidr);
		}
	}
	/**
	 *	Raw configuration entry for a subscription
	 */
	public static class ProvSubscription	{
		private String	subid;
		private String	feedid;
		private String	url;
		private String	authuser;
		private String	credentials;
		private boolean	metaonly;
		private boolean	use100;
		/**
		 *	Construct a subscription configuration entry
		 *	@param subid	The subscription ID
		 *	@param feedid	The feed ID
		 *	@param url	The base delivery URL (not including the fileid)
		 *	@param authuser	The user in the credentials used to deliver
		 *	@param credentials	The credentials used to authenticate to the delivery URL exactly as they go in the Authorization header.
		 *	@param metaonly	Is this a meta data only subscription?
		 *	@param use100	Should we send Expect: 100-continue?
		 */
		public ProvSubscription(String subid, String feedid, String url, String authuser, String credentials, boolean metaonly, boolean use100) {
			this.subid = subid;
			this.feedid = feedid;
			this.url = url;
			this.authuser = authuser;
			this.credentials = credentials;
			this.metaonly = metaonly;
			this.use100 = use100;
		}
		/**
		 *	Get the subscription ID
		 */
		public String getSubId() {
			return(subid);
		}
		/**
		 *	Get the feed ID
		 */
		public String getFeedId() {
			return(feedid);
		}
		/**
		 *	Get the delivery URL
		 */
		public String getURL() {
			return(url);
		}
		/**
		 *	Get the user
		 */
		public String getAuthUser() {
			return(authuser);
		}
		/**
		 *	Get the delivery credentials
		 */
		public String getCredentials() {
			return(credentials);
		}
		/**
		 *	Is this a meta data only subscription?
		 */
		public boolean isMetaDataOnly() {
			return(metaonly);
		}
		/**
		 *	Should we send Expect: 100-continue?
		 */
		public boolean isUsing100() {
			return(use100);
		}
	}
	/**
	 *	Raw configuration entry for controlled ingress to the data router node
	 */
	public static class ProvForceIngress	{
		private String feedid;
		private String subnet;
		private String user;
		private String[] nodes;
		/**
		 *	Construct a forced ingress configuration entry
		 *	@param feedid	The feed ID that this entry applies to
		 *	@param subnet	The CIDR for which publisher IP addresses this entry applies to or "" if it applies to all publisher IP addresses
		 *	@param user	The publishing user this entry applies to or "" if it applies to all publishing users.
		 *	@param nodes	The array of FQDNs of the data router nodes to redirect publication attempts to.
		 */
		public ProvForceIngress(String feedid, String subnet, String user, String[] nodes) {
			this.feedid = feedid;
			this.subnet = subnet;
			this.user = user;
			this.nodes = nodes;
		}
		/**
		 *	Get the feed ID
		 */
		public String getFeedId() {
			return(feedid);
		}
		/**
		 *	Get the subnet
		 */
		public String getSubnet() {
			return(subnet);
		}
		/**
		 *	Get the user
		 */
		public String getUser() {
			return(user);
		}
		/**
		 *	Get the node
		 */
		public String[] getNodes() {
			return(nodes);
		}
	}
	/**
	 *	Raw configuration entry for controlled egress from the data router
	 */
	public static class ProvForceEgress	{
		private String subid;
		private String node;
		/**
		 *	Construct a forced egress configuration entry
		 *	@param subid	The subscription ID the subscription with forced egress
		 *	@param node	The node handling deliveries for this subscription
		 */
		public ProvForceEgress(String subid, String node) {
			this.subid = subid;
			this.node = node;
		}
		/**
		 *	Get the subscription ID
		 */
		public String getSubId() {
			return(subid);
		}
		/**
		 *	Get the node
		 */
		public String getNode() {
			return(node);
		}
	}
	/**
	 *	Raw configuration entry for routing within the data router network
	 */
	public static class ProvHop	{
		private String	from;
		private String	to;
		private String	via;
		/**
		 *	A human readable description of this entry
		 */
		public String toString() {
			return("Hop " + from + "->" + to + " via " + via);
		}
		/**
		 *	Construct a hop entry
		 *	@param from	The FQDN of the node with the data to be delivered
		 *	@param to	The FQDN of the node that will deliver to the subscriber
		 *	@param via	The FQDN of the node where the from node should send the data
		 */
		public ProvHop(String from, String to, String via) {
			this.from = from;
			this.to = to;
			this.via = via;
		}
		/**
		 *	Get the from node
		 */
		public String getFrom() {
			return(from);
		}
		/**
		 *	Get the to node
		 */
		public String getTo() {
			return(to);
		}
		/**
		 *	Get the next intermediate node
		 */
		public String getVia() {
			return(via);
		}
	}
	private static class Redirection	{
		public SubnetMatcher snm;
		public String user;
		public String[] nodes;
	}
	private static class Feed	{
		public String	loginfo;
		public String	status;
		public SubnetMatcher[] subnets;
		public Hashtable<String, String> authusers = new Hashtable<String, String>();
		public Redirection[]	redirections;
		public Target[]	targets;
	}
	private Hashtable<String, String> params = new Hashtable<String, String>();
	private Hashtable<String, Feed>	feeds = new Hashtable<String, Feed>();
	private Hashtable<String, DestInfo> nodeinfo = new Hashtable<String, DestInfo>();
	private Hashtable<String, DestInfo> subinfo = new Hashtable<String, DestInfo>();
	private Hashtable<String, IsFrom> nodes = new Hashtable<String, IsFrom>();
	private String	myname;
	private String	myauth;
	private DestInfo[]	alldests;
	private int	rrcntr;
	/**
	 *	Process the raw provisioning data to configure this node
	 *	@param pd	The parsed provisioning data
	 *	@param myname	My name as seen by external systems
	 *	@param spooldir	The directory where temporary files live
	 *	@param port	The port number for URLs
	 *	@param nodeauthkey	The keying string used to generate node authentication credentials
	 */
	public NodeConfig(ProvData pd, String myname, String spooldir, int port, String nodeauthkey) {
		this.myname = myname;
		for (ProvParam p: pd.getParams()) {
			params.put(p.getName(), p.getValue());
		}
		Vector<DestInfo>	div = new Vector<DestInfo>();
		myauth = NodeUtils.getNodeAuthHdr(myname, nodeauthkey);
		for (ProvNode pn: pd.getNodes()) {
			String cn = pn.getCName();
			if (nodeinfo.get(cn) != null) {
				continue;
			}
			String auth = NodeUtils.getNodeAuthHdr(cn, nodeauthkey);
			DestInfo di = new DestInfo("n:" + cn, spooldir + "/n/" + cn, null, "n2n-" + cn, "https://" + cn + ":" + port + "/internal/publish", cn, myauth, false, true);
			(new File(di.getSpool())).mkdirs();
			div.add(di);
			nodeinfo.put(cn, di);
			nodes.put(auth, new IsFrom(cn));
		}
		PathFinder pf = new PathFinder(myname, nodeinfo.keySet().toArray(new String[nodeinfo.size()]), pd.getHops());
		Hashtable<String, Vector<Redirection>> rdtab = new Hashtable<String, Vector<Redirection>>();
		for (ProvForceIngress pfi: pd.getForceIngress()) {
			Vector<Redirection> v = rdtab.get(pfi.getFeedId());
			if (v == null) {
				v = new Vector<Redirection>();
				rdtab.put(pfi.getFeedId(), v);
			}
			Redirection r = new Redirection();
			if (pfi.getSubnet() != null) {
				r.snm = new SubnetMatcher(pfi.getSubnet());
			}
			r.user = pfi.getUser();
			r.nodes = pfi.getNodes();
			v.add(r);
		}
		Hashtable<String, Hashtable<String, String>> pfutab = new Hashtable<String, Hashtable<String, String>>();
		for (ProvFeedUser pfu: pd.getFeedUsers()) {
			Hashtable<String, String> t = pfutab.get(pfu.getFeedId());
			if (t == null) {
				t = new Hashtable<String, String>();
				pfutab.put(pfu.getFeedId(), t);
			}
			t.put(pfu.getCredentials(), pfu.getUser());
		}
		Hashtable<String, String> egrtab = new Hashtable<String, String>();
		for (ProvForceEgress pfe: pd.getForceEgress()) {
			if (pfe.getNode().equals(myname) || nodeinfo.get(pfe.getNode()) == null) {
				continue;
			}
			egrtab.put(pfe.getSubId(), pfe.getNode());
		}
		Hashtable<String, Vector<SubnetMatcher>> pfstab = new Hashtable<String, Vector<SubnetMatcher>>();
		for (ProvFeedSubnet pfs: pd.getFeedSubnets()) {
			Vector<SubnetMatcher> v = pfstab.get(pfs.getFeedId());
			if (v == null) {
				v = new Vector<SubnetMatcher>();
				pfstab.put(pfs.getFeedId(), v);
			}
			v.add(new SubnetMatcher(pfs.getCidr()));
		}
		Hashtable<String, StringBuffer> ttab = new Hashtable<String, StringBuffer>();
		HashSet<String> allfeeds = new HashSet<String>();
		for (ProvFeed pfx: pd.getFeeds()) {
			if (pfx.getStatus() == null) {
				allfeeds.add(pfx.getId());
			}
		}
		for (ProvSubscription ps: pd.getSubscriptions()) {
			String sid = ps.getSubId();
			String fid = ps.getFeedId();
			if (!allfeeds.contains(fid)) {
				continue;
			}
			if (subinfo.get(sid) != null) {
				continue;
			}
			int sididx = 999;
			try {
				sididx = Integer.parseInt(sid);
				sididx -= sididx % 100;
			} catch (Exception e) {
			}
			String siddir = sididx + "/" + sid;
			DestInfo di = new DestInfo("s:" + sid, spooldir + "/s/" + siddir, sid, fid, ps.getURL(), ps.getAuthUser(), ps.getCredentials(), ps.isMetaDataOnly(), ps.isUsing100());
			(new File(di.getSpool())).mkdirs();
			div.add(di);
			subinfo.put(sid, di);
			String egr = egrtab.get(sid);
			if (egr != null) {
				sid = pf.getPath(egr) + sid;
			}
			StringBuffer sb = ttab.get(fid);
			if (sb == null) {
				sb = new StringBuffer();
				ttab.put(fid, sb);
			}
			sb.append(' ').append(sid);
		}
		alldests = div.toArray(new DestInfo[div.size()]);
		for (ProvFeed pfx: pd.getFeeds()) {
			String fid = pfx.getId();
			Feed f = feeds.get(fid);
			if (f != null) {
				continue;
			}
			f = new Feed();
			feeds.put(fid, f);
			f.loginfo = pfx.getLogData();
			f.status = pfx.getStatus();
			Vector<SubnetMatcher> v1 = pfstab.get(fid);
			if (v1 == null) {
				f.subnets = new SubnetMatcher[0];
			} else {
				f.subnets = v1.toArray(new SubnetMatcher[v1.size()]);
			}
			Hashtable<String, String> h1 = pfutab.get(fid);
			if (h1 == null) {
				h1 = new Hashtable<String, String>();
			}
			f.authusers = h1;
			Vector<Redirection> v2 = rdtab.get(fid);
			if (v2 == null) {
				f.redirections = new Redirection[0];
			} else {
				f.redirections = v2.toArray(new Redirection[v2.size()]);
			}
			StringBuffer sb = ttab.get(fid);
			if (sb == null) {
				f.targets = new Target[0];
			} else {
				f.targets = parseRouting(sb.toString());
			}
		}
	}
	/**
	 *	Parse a target string into an array of targets
	 *	@param routing Target string
	 *	@return	Array of targets.
	 */
	public Target[] parseRouting(String routing) {
		routing = routing.trim();
		if ("".equals(routing)) {
			return(new Target[0]);
		}
		String[] xx = routing.split("\\s+");
		Hashtable<String, Target> tmap = new Hashtable<String, Target>();
		HashSet<String> subset = new HashSet<String>();
		Vector<Target> tv = new Vector<Target>();
		Target[] ret = new Target[xx.length];
		for (int i = 0; i < xx.length; i++) {
			String t = xx[i];
			int j = t.indexOf('/');
			if (j == -1) {
				DestInfo di = subinfo.get(t);
				if (di == null) {
					tv.add(new Target(null, t));
				} else {
					if (!subset.contains(t)) {
						subset.add(t);
						tv.add(new Target(di, null));
					}
				}
			} else {
				String node = t.substring(0, j);
				String rtg = t.substring(j + 1);
				DestInfo di = nodeinfo.get(node);
				if (di == null) {
					tv.add(new Target(null, t));
				} else {
					Target tt = tmap.get(node);
					if (tt == null) {
						tt = new Target(di, rtg);
						tmap.put(node, tt);
						tv.add(tt);
					} else {
						tt.addRouting(rtg);
					}
				}
			}
		}
		return(tv.toArray(new Target[tv.size()]));
	}
	/**
	 *	Check whether this is a valid node-to-node transfer
	 *	@param credentials	Credentials offered by the supposed node
	 *	@param ip	IP address the request came from
	 */
	public boolean isAnotherNode(String credentials, String ip) {
		IsFrom n = nodes.get(credentials);
		return (n != null && n.isFrom(ip));
	}
	/**
	 *	Check whether publication is allowed.
	 *	@param feedid	The ID of the feed being requested.
	 *	@param credentials	The offered credentials
	 *	@param ip	The requesting IP address
	 */
	public String isPublishPermitted(String feedid, String credentials, String ip) {
		Feed f = feeds.get(feedid);
		String nf = "Feed does not exist";
		if (f != null) {
			nf = f.status;
		}
		if (nf != null) {
			return(nf);
		}
		String user = f.authusers.get(credentials);
		if (user == null) {
			return("Publisher not permitted for this feed");
		}
		if (f.subnets.length == 0) {
			return(null);
		}
		byte[] addr = NodeUtils.getInetAddress(ip);
		for (SubnetMatcher snm: f.subnets) {
			if (snm.matches(addr)) {
				return(null);
			}
		}
		return("Publisher not permitted for this feed");
	}
	/**
	 *	Get authenticated user
	 */
	public String getAuthUser(String feedid, String credentials) {
		return(feeds.get(feedid).authusers.get(credentials));
	}
	/**
	 *	Check if the request should be redirected to a different ingress node
	 */
	public String getIngressNode(String feedid, String user, String ip) {
		Feed f = feeds.get(feedid);
		if (f.redirections.length == 0) {
			return(null);
		}
		byte[] addr = NodeUtils.getInetAddress(ip);
		for (Redirection r: f.redirections) {
			if (r.user != null && !user.equals(r.user)) {
				continue;
			}
			if (r.snm != null && !r.snm.matches(addr)) {
				continue;
			}
			for (String n: r.nodes) {
				if (myname.equals(n)) {
					return(null);
				}
			}
			if (r.nodes.length == 0) {
				return(null);
			}
			return(r.nodes[rrcntr++ % r.nodes.length]);
		}
		return(null);
	}
	/**
	 *	Get a provisioned configuration parameter
	 */
	public String getProvParam(String name) {
		return(params.get(name));
	}
	/**
	 *	Get all the DestInfos
	 */
	public DestInfo[]	getAllDests() {
		return(alldests);
	}
	/**
	 *	Get the targets for a feed
	 *	@param feedid	The feed ID
	 *	@return	The targets this feed should be delivered to
	 */
	public Target[] getTargets(String feedid) {
		if (feedid == null) {
			return(new Target[0]);
		}
		Feed f = feeds.get(feedid);
		if (f == null) {
			return(new Target[0]);
		}
		return(f.targets);
	}
	/**
	 *	Get the feed ID for a subscription
	 *	@param subid	The subscription ID
	 *	@return	The feed ID
	 */
	public String getFeedId(String subid) {
		DestInfo di = subinfo.get(subid);
		if (di == null) {
			return(null);
		}
		return(di.getLogData());
	}
	/**
	 *	Get the spool directory for a subscription
	 *	@param subid	The subscription ID
	 *	@return The spool directory
	 */
	public String getSpoolDir(String subid) {
		DestInfo di = subinfo.get(subid);
		if (di == null) {
			return(null);
		}
		return(di.getSpool());
	}
	/**
	 *	Get the Authorization value this node uses
	 *	@return The Authorization header value for this node
	 */
	public String getMyAuth() {
		return(myauth);
	}

}
