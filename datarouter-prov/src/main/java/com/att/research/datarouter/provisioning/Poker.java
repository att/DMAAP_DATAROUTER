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

package com.att.research.datarouter.provisioning;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import javax.servlet.ServletException;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.att.research.datarouter.provisioning.beans.EgressRoute;
import com.att.research.datarouter.provisioning.beans.Feed;
import com.att.research.datarouter.provisioning.beans.IngressRoute;
import com.att.research.datarouter.provisioning.beans.NetworkRoute;
import com.att.research.datarouter.provisioning.beans.Parameters;
import com.att.research.datarouter.provisioning.beans.Subscription;
import com.att.research.datarouter.provisioning.beans.Group; //Groups feature Rally:US708115 - 1610	
import com.att.research.datarouter.provisioning.utils.*;

/**
 * This class handles the two timers (described in R1 Design Notes), and takes care of issuing
 * the GET to each node of the URL to "poke".
 *
 * @author Robert Eby
 * @version $Id: Poker.java,v 1.11 2014/01/08 16:13:47 eby Exp $
 */
public class Poker extends TimerTask {
	/** Template used to generate the URL to issue the GET against */
	public static final String POKE_URL_TEMPLATE = "http://%s/internal/fetchProv";
	
	
	

	/** This is a singleton -- there is only one Poker object in the server */
	private static Poker p;

	/**
	 * Get the singleton Poker object.
	 * @return the Poker
	 */
	public static synchronized Poker getPoker() {
		if (p == null)
			p = new Poker();
		return p;
	}

	private long timer1;
	private long timer2;
	private Timer rolex;
	private String this_pod;		// DNS name of this machine
	private Logger logger;
	private String provstring;

	private Poker() {
		timer1 = timer2 = 0;
		rolex = new Timer();
		logger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
		try {
			this_pod = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			this_pod = "*UNKNOWN*";	// not a major problem
		}
		provstring = buildProvisioningString();

		rolex.scheduleAtFixedRate(this, 0L, 1000L);	// Run once a second to check the timers
	}

	/**
	 * This method sets the two timers described in the design notes.
	 * @param t1 the first timer controls how long to wait after a provisioning request before poking each node
	 *   This timer can be reset if it has not "gone off".
	 * @param t2 the second timer set the outer bound on how long to wait.  It cannot be reset.
	 */
	public void setTimers(long t1, long t2) {
		synchronized (this_pod) {
			if (timer1 == 0 || t1 > timer1)
				timer1 = t1;
			if (timer2 == 0)
				timer2 = t2;
		}
		if (logger.isDebugEnabled())
			logger.debug("Poker timers set to " + timer1 + " and " + timer2);
	
		
	}

	/**
	 * Return the last provisioning string built.
	 * @return the last provisioning string built.
	 */
	public String getProvisioningString() {
		return provstring;
	}

	/**
	 * The method to run at the predefined interval (once per second).  This method checks
	 * to see if either of the two timers has expired, and if so, will rebuild the provisioning
	 * string, and poke all the nodes and other PODs.  The timers are then reset to 0.
	 */
	@Override
	public void run() {
		try {
			if (timer1 > 0) {
				long now = System.currentTimeMillis();
				boolean fire = false;
				synchronized (this_pod) {
					if (now > timer1 || now > timer2) {
						timer1 = timer2 = 0;
						fire = true;
					}
				}
				if (fire) {
					// Rebuild the prov string
					provstring = buildProvisioningString();

					// Only the active POD should poke nodes, etc.
					boolean active = SynchronizerTask.getSynchronizer().isActive();
					if (active) {
						// Poke all the DR nodes
						for (String n : BaseServlet.getNodes()) {
							pokeNode(n);
						}
						// Poke the pod that is not us
						for (String n : BaseServlet.getPods()) {
							if (n.length() > 0 && !n.equals(this_pod))
								pokeNode(n);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.warn("PROV0020: Caught exception in Poker: "+e);
			e.printStackTrace();
		}
	}
	private void pokeNode(final String nodename) {
		logger.debug("PROV0012 Poking node " + nodename + " ...");
		Runnable r = new Runnable() {
			@Override
			public void run() {
			
				try {
					String u = String.format(POKE_URL_TEMPLATE, nodename+":"+DB.HTTP_PORT);
					URL url = new URL(u);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setConnectTimeout(60000);	//Fixes for Itrack DATARTR-3, poke timeout
					conn.connect();
					conn.getContentLength();	// Force the GET through
					conn.disconnect();
				} catch (MalformedURLException e) {
					logger.warn("PROV0013 MalformedURLException Error poking node "+nodename+": " + e.getMessage());
				} catch (IOException e) {
					logger.warn("PROV0013 IOException Error poking node "+nodename+": " + e.getMessage());
				}
			}
		};
//		Thread t = new Thread(r);
//		t.start();
		r.run();
	}
	@SuppressWarnings("unused")
	private String buildProvisioningString() {
		StringBuilder sb = new StringBuilder("{\n");

		// Append Feeds to the string
		String pfx = "\n";
		sb.append("\"feeds\": [");
		for (Feed f : Feed.getAllFeeds()) {
			sb.append(pfx);
			sb.append(f.asJSONObject().toString());
			pfx = ",\n";
		}
		sb.append("\n],\n");
		
		//Append groups to the string - Rally:US708115  - 1610		
		pfx = "\n";		
		sb.append("\"groups\": [");		
		for (Group s : Group.getAllgroups()) {		
			sb.append(pfx);		
			sb.append(s.asJSONObject().toString());		
			pfx = ",\n";		
		}		
		sb.append("\n],\n");		
				

		// Append Subscriptions to the string
		pfx = "\n";
		sb.append("\"subscriptions\": [");
		for (Subscription s : Subscription.getAllSubscriptions()) {
			sb.append(pfx);
			if(s!=null)
			sb.append(s.asJSONObject().toString());
			pfx = ",\n";
		}
		sb.append("\n],\n");

		// Append Parameters to the string
		pfx = "\n";
		sb.append("\"parameters\": {");
		Map<String,String> props = Parameters.getParameters();
		Set<String> ivals = new HashSet<String>();
		String intv = props.get("_INT_VALUES");
		if (intv != null)
			ivals.addAll(Arrays.asList(intv.split("\\|")));
		for (String key : new TreeSet<String>(props.keySet())) {
			String v = props.get(key);
			sb.append(pfx);
			sb.append("  \"").append(key).append("\": ");
			if (ivals.contains(key)) {
				// integer value
				sb.append(v);
			} else if (key.endsWith("S")) {
				// Split and append array of strings
				String[] pp = v.split("\\|");
				String p2 = "";
				sb.append("[");
				for (String t : pp) {
					sb.append(p2).append("\"").append(quote(t)).append("\"");
					p2 = ",";
				}
				sb.append("]");
			} else {
				sb.append("\"").append(quote(v)).append("\"");
			}
			pfx = ",\n";
		}
		sb.append("\n},\n");

		// Append Routes to the string
		pfx = "\n";
		sb.append("\"ingress\": [");
		for (IngressRoute in : IngressRoute.getAllIngressRoutes()) {
			sb.append(pfx);
			sb.append(in.asJSONObject().toString());
			pfx = ",\n";
		}
		sb.append("\n],\n");

		pfx = "\n";
		sb.append("\"egress\": {");
		for (EgressRoute eg : EgressRoute.getAllEgressRoutes()) {
			sb.append(pfx);
			String t = eg.asJSONObject().toString();
			t = t.substring(1, t.length()-1);
			sb.append(t);
			pfx = ",\n";
		}
		sb.append("\n},\n");

		pfx = "\n";
		sb.append("\"routing\": [");
		for (NetworkRoute ne : NetworkRoute.getAllNetworkRoutes()) {
			sb.append(pfx);
			sb.append(ne.asJSONObject().toString());
			pfx = ",\n";
		}
		sb.append("\n]");
		sb.append("\n}");

		// Convert to string and verify it is valid JSON
		String provstring = sb.toString();
		try {
			new JSONObject(new JSONTokener(provstring));
		} catch (JSONException e) {
			logger.warn("PROV0016: Possible invalid prov string: "+e);
		}
		return provstring;
	}
	private String quote(String s) {
		StringBuilder sb = new StringBuilder();
		for (char ch : s.toCharArray()) {
			if (ch == '\\' || ch == '"') {
				sb.append('\\');
			}
			sb.append(ch);
		}
		return sb.toString();
	}
}
