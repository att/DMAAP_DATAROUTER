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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.att.research.datarouter.provisioning.beans.EgressRoute;
import com.att.research.datarouter.provisioning.beans.Feed;
import com.att.research.datarouter.provisioning.beans.IngressRoute;
import com.att.research.datarouter.provisioning.beans.NetworkRoute;
import com.att.research.datarouter.provisioning.beans.Parameters;
import com.att.research.datarouter.provisioning.beans.Subscription;
import com.att.research.datarouter.provisioning.beans.Syncable;
import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.RLEBitSet;
import com.att.research.datarouter.provisioning.utils.LogfileLoader;
import com.att.research.datarouter.provisioning.utils.URLUtilities;
import com.att.research.datarouter.provisioning.beans.Group; //Groups feature Rally:US708115 - 1610	

/**
 * This class handles synchronization between provisioning servers (PODs).  It has three primary functions:
 * <ol>
 * <li>Checking DNS once per minute to see which POD the DNS CNAME points to. The CNAME will point to
 * the active (master) POD.</li>
 * <li>On non-master (standby) PODs, fetches provisioning data and logs in order to keep MySQL in sync.</li>
 * <li>Providing information to other parts of the system as to the current role (ACTIVE, STANDBY, UNKNOWN)
 * of this POD.</li>
 * </ol>
 * <p>For this to work correctly, the following code needs to be placed at the beginning of main().</p>
 * <code>
 * 		Security.setProperty("networkaddress.cache.ttl", "10");
 * </code>
 *
 * @author Robert Eby
 * @version $Id: SynchronizerTask.java,v 1.10 2014/03/21 13:50:10 eby Exp $
 */
public class SynchronizerTask extends TimerTask {
	/** This is a singleton -- there is only one SynchronizerTask object in the server */
	private static SynchronizerTask synctask;

	/** This POD is unknown -- not on the list of PODs */
	public static final int UNKNOWN = 0;
	/** This POD is active -- on the list of PODs, and the DNS CNAME points to us */
	public static final int ACTIVE = 1;
	/** This POD is standby -- on the list of PODs, and the DNS CNAME does not point to us */
	public static final int STANDBY = 2;
	private static final String[] stnames = { "UNKNOWN", "ACTIVE", "STANDBY" };
	private static final long ONE_HOUR = 60 * 60 * 1000L;

	private final Logger logger;
	private final Timer rolex;
	private final String spooldir;
	private int state;
	private boolean doFetch;
	private long nextsynctime;
	private AbstractHttpClient httpclient = null;

	/**
	 * Get the singleton SynchronizerTask object.
	 * @return the SynchronizerTask
	 */
	public static synchronized SynchronizerTask getSynchronizer() {
		if (synctask == null)
			synctask = new SynchronizerTask();
		return synctask;
	}

	@SuppressWarnings("deprecation")
	private SynchronizerTask() {
		logger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
		rolex = new Timer();
		spooldir = (new DB()).getProperties().getProperty("com.att.research.datarouter.provserver.spooldir");
		state = UNKNOWN;
		doFetch = true;		// start off with a fetch
		nextsynctime = 0;

		logger.info("PROV5000: Sync task starting, server state is UNKNOWN");
		try {
			Properties props = (new DB()).getProperties();
			String type  = props.getProperty(Main.KEYSTORE_TYPE_PROPERTY, "jks");
			String store = props.getProperty(Main.KEYSTORE_PATH_PROPERTY);
			String pass  = props.getProperty(Main.KEYSTORE_PASSWORD_PROPERTY);
			KeyStore keyStore = KeyStore.getInstance(type);
			FileInputStream instream = new FileInputStream(new File(store));
			keyStore.load(instream, pass.toCharArray());
			instream.close();

			store = props.getProperty(Main.TRUSTSTORE_PATH_PROPERTY);
			pass  = props.getProperty(Main.TRUSTSTORE_PASSWORD_PROPERTY);
			KeyStore trustStore = null;
			if (store != null && store.length() > 0) {
				trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
				instream = new FileInputStream(new File(store));
				trustStore.load(instream, pass.toCharArray());
				instream.close();
			}

			// We are connecting with the node name, but the certificate will have the CNAME
			// So we need to accept a non-matching certificate name
			String keystorepass  = props.getProperty(Main.KEYSTORE_PASSWORD_PROPERTY); //itrack.web.att.com/browse/DATARTR-6 for changing hard coded passphase ref
			AbstractHttpClient hc = new DefaultHttpClient();
			SSLSocketFactory socketFactory =
				(trustStore == null)
				? new SSLSocketFactory(keyStore, keystorepass)
				: new SSLSocketFactory(keyStore, keystorepass, trustStore);
			socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			Scheme sch = new Scheme("https", 443, socketFactory);
			hc.getConnectionManager().getSchemeRegistry().register(sch);
			httpclient = hc;

			// Run once every 5 seconds to check DNS, etc.
			long interval = 0;
			try {
				String s = props.getProperty("com.att.research.datarouter.provserver.sync_interval", "5000");
				interval = Long.parseLong(s);
			} catch (NumberFormatException e) {
				interval = 5000L;
			}
			rolex.scheduleAtFixedRate(this, 0L, interval);
		} catch (Exception e) {
			logger.warn("PROV5005: Problem starting the synchronizer: "+e);
		}
	}

	/**
	 * What is the state of this POD?
	 * @return one of ACTIVE, STANDBY, UNKNOWN
	 */
	public int getState() {
		return state;
	}

	/**
	 * Is this the active POD?
	 * @return true if we are active (the master), false otherwise
	 */
	public boolean isActive() {
		return state == ACTIVE;
	}

	/**
	 * This method is used to signal that another POD (the active POD) has sent us a /fetchProv request,
	 * and that we should re-synchronize with the master.
	 */
	public void doFetch() {
		doFetch = true;
	}

	/**
	 * Runs once a minute in order to <ol>
	 * <li>lookup DNS names,</li>
	 * <li>determine the state of this POD,</li>
	 * <li>if this is a standby POD, and the fetch flag is set, perform a fetch of state from the active POD.</li>
	 * <li>if this is a standby POD, check if there are any new log records to be replicated.</li>
	 * </ol>
	 */
	@Override
	public void run() {
		try {
			state = lookupState();
			if (state == STANDBY) {
				// Only copy provisioning data FROM the active server TO the standby
				if (doFetch || (System.currentTimeMillis() >= nextsynctime)) {
					logger.debug("Initiating a sync...");
					JSONObject jo = readProvisioningJSON();
					if (jo != null) {
						doFetch = false;
						syncFeeds( jo.getJSONArray("feeds"));
						syncSubs(  jo.getJSONArray("subscriptions"));
						syncGroups(  jo.getJSONArray("groups")); //Rally:US708115 - 1610
						syncParams(jo.getJSONObject("parameters"));
						// The following will not be present in a version=1.0 provfeed
						JSONArray ja = jo.optJSONArray("ingress");
						if (ja != null)
							syncIngressRoutes(ja);
						JSONObject j2 = jo.optJSONObject("egress");
						if (j2 != null)
							syncEgressRoutes( j2);
						ja = jo.optJSONArray("routing");
						if (ja != null)
							syncNetworkRoutes(ja);
					}
					logger.info("PROV5013: Sync completed.");
					nextsynctime = System.currentTimeMillis() + ONE_HOUR;
				}
			} else {
				// Don't do fetches on non-standby PODs
				doFetch = false;
			}

			// Fetch DR logs as needed - server to server
			LogfileLoader lfl = LogfileLoader.getLoader();
			if (lfl.isIdle()) {
				// Only fetch new logs if the loader is waiting for them.
				logger.trace("Checking for logs to replicate...");
				RLEBitSet local  = lfl.getBitSet();
				RLEBitSet remote = readRemoteLoglist();
				remote.andNot(local);
				if (!remote.isEmpty()) {
					logger.debug(" Replicating logs: "+remote);
					replicateDRLogs(remote);
				}
			}
		} catch (Exception e) {
			logger.warn("PROV0020: Caught exception in SynchronizerTask: "+e);
			e.printStackTrace();
		}
	}

	/**
	 * This method is used to lookup the CNAME that points to the active server.
	 * It returns 0 (UNKNOWN), 1(ACTIVE), or 2 (STANDBY) to indicate the state of this server.
	 * @return the current state
	 */
	private int lookupState() {
		int newstate = UNKNOWN;
		try {
			InetAddress myaddr = InetAddress.getLocalHost();
			if (logger.isTraceEnabled())
				logger.trace("My address: "+myaddr);
			String this_pod = myaddr.getHostName();
			Set<String> pods = new TreeSet<String>(Arrays.asList(BaseServlet.getPods()));
			if (pods.contains(this_pod)) {
				InetAddress pserver = InetAddress.getByName(BaseServlet.active_prov_name);
				newstate = myaddr.equals(pserver) ? ACTIVE : STANDBY;
				if (logger.isDebugEnabled() && System.currentTimeMillis() >= next_msg) {
					logger.debug("Active POD = "+pserver+", Current state is "+stnames[newstate]);
					next_msg = System.currentTimeMillis() + (5 * 60 * 1000L);
				}
			} else {
				logger.warn("PROV5003: My name ("+this_pod+") is missing from the list of provisioning servers.");
			}
		} catch (UnknownHostException e) {
			logger.warn("PROV5002: Cannot determine the name of this provisioning server.");
		}

		if (newstate != state)
			logger.info(String.format("PROV5001: Server state changed from %s to %s", stnames[state], stnames[newstate]));
		return newstate;
	}
	private static long next_msg = 0;	// only display the "Current state" msg every 5 mins.
	/** Synchronize the Feeds in the JSONArray, with the Feeds in the DB. */
	private void syncFeeds(JSONArray ja) {
		Collection<Syncable> coll = new ArrayList<Syncable>();
		for (int n = 0; n < ja.length(); n++) {
			try {
				Feed f = new Feed(ja.getJSONObject(n));
				coll.add(f);
			} catch (Exception e) {
				logger.warn("PROV5004: Invalid object in feed: "+ja.optJSONObject(n));
			}
		}
		if (sync(coll, Feed.getAllFeeds()))
			BaseServlet.provisioningDataChanged();
	}
	/** Synchronize the Subscriptions in the JSONArray, with the Subscriptions in the DB. */
	private void syncSubs(JSONArray ja) {
		Collection<Syncable> coll = new ArrayList<Syncable>();
		for (int n = 0; n < ja.length(); n++) {
			try {
				//Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
				JSONObject j = ja.getJSONObject(n);	 
				j.put("sync", "true");
				Subscription s = new Subscription(j);
				coll.add(s);
			} catch (Exception e) {
				logger.warn("PROV5004: Invalid object in subscription: "+ja.optJSONObject(n));
			}
		}
		if (sync(coll, Subscription.getAllSubscriptions()))
			BaseServlet.provisioningDataChanged();
	}

	/**  Rally:US708115  - Synchronize the Groups in the JSONArray, with the Groups in the DB. */		
	private void syncGroups(JSONArray ja) {		
		Collection<Syncable> coll = new ArrayList<Syncable>();		
		for (int n = 0; n < ja.length(); n++) {		
			try {		
				Group g = new Group(ja.getJSONObject(n));		
				coll.add(g);		
			} catch (Exception e) {		
				logger.warn("PROV5004: Invalid object in subscription: "+ja.optJSONObject(n));		
			}		
		}		
		if (sync(coll, Group.getAllgroups()))		
			BaseServlet.provisioningDataChanged();		
	}


	/** Synchronize the Parameters in the JSONObject, with the Parameters in the DB. */
	private void syncParams(JSONObject jo) {
		Collection<Syncable> coll = new ArrayList<Syncable>();
		for (String k : jo.keySet()) {
			String v = "";
			try {
				v = jo.getString(k);
			} catch (JSONException e) {
				try {
					v = ""+jo.getInt(k);
				} catch (JSONException e1) {
					JSONArray ja = jo.getJSONArray(k);
					for (int i = 0; i < ja.length(); i++) {
						if (i > 0)
							v += "|";
						v += ja.getString(i);
					}
				}
			}
			coll.add(new Parameters(k, v));
		}
		if (sync(coll, Parameters.getParameterCollection())) {
			BaseServlet.provisioningDataChanged();
			BaseServlet.provisioningParametersChanged();
		}
	}
	private void syncIngressRoutes(JSONArray ja) {
		Collection<Syncable> coll = new ArrayList<Syncable>();
		for (int n = 0; n < ja.length(); n++) {
			try {
				IngressRoute in = new IngressRoute(ja.getJSONObject(n));
				coll.add(in);
			} catch (NumberFormatException e) {
				logger.warn("PROV5004: Invalid object in ingress routes: "+ja.optJSONObject(n));
			}
		}
		if (sync(coll, IngressRoute.getAllIngressRoutes()))
			BaseServlet.provisioningDataChanged();
	}
	private void syncEgressRoutes(JSONObject jo) {
		Collection<Syncable> coll = new ArrayList<Syncable>();
		for (String key : jo.keySet()) {
			try {
				int sub = Integer.parseInt(key);
				String node = jo.getString(key);
				EgressRoute er = new EgressRoute(sub, node);
				coll.add(er);
			} catch (NumberFormatException e) {
				logger.warn("PROV5004: Invalid subid in egress routes: "+key);
			} catch (IllegalArgumentException e) {
				logger.warn("PROV5004: Invalid node name in egress routes: "+key);
			}
		}
		if (sync(coll, EgressRoute.getAllEgressRoutes()))
			BaseServlet.provisioningDataChanged();
	}
	private void syncNetworkRoutes(JSONArray ja) {
		Collection<Syncable> coll = new ArrayList<Syncable>();
		for (int n = 0; n < ja.length(); n++) {
			try {
				NetworkRoute nr = new NetworkRoute(ja.getJSONObject(n));
				coll.add(nr);
			} catch (JSONException e) {
				logger.warn("PROV5004: Invalid object in network routes: "+ja.optJSONObject(n));
			}
		}
		if (sync(coll, NetworkRoute.getAllNetworkRoutes()))
			BaseServlet.provisioningDataChanged();
	}
	private boolean sync(Collection<? extends Syncable> newc, Collection<? extends Syncable> oldc) {
		boolean changes = false;
		try {
			Map<String, Syncable> newmap = getMap(newc);
			Map<String, Syncable> oldmap = getMap(oldc);
			Set<String> union = new TreeSet<String>(newmap.keySet());
			union.addAll(oldmap.keySet());
			DB db = new DB();
			@SuppressWarnings("resource")
			Connection conn = db.getConnection();
			for (String n : union) {
				Syncable newobj = newmap.get(n);
				Syncable oldobj = oldmap.get(n);
				if (oldobj == null) {
					if (logger.isDebugEnabled())
						logger.debug("  Inserting record: "+newobj);
					newobj.doInsert(conn);
					changes = true;
				} else if (newobj == null) {
					if (logger.isDebugEnabled())
						logger.debug("  Deleting record: "+oldobj);
					oldobj.doDelete(conn);
					changes = true;
				} else if (!newobj.equals(oldobj)) {
					if (logger.isDebugEnabled())
						logger.debug("  Updating record: "+newobj);
					newobj.doUpdate(conn);

					/**Rally US708115
					 * Change Ownership of FEED - 1610, Syncronised with secondary DB.
					 * */
					checkChnageOwner(newobj, oldobj);

					changes = true;
				}
			}
			db.release(conn);
		} catch (SQLException e) {
			logger.warn("PROV5009: problem during sync, exception: "+e);
			e.printStackTrace();
		}
		return changes;
	}
	private Map<String, Syncable> getMap(Collection<? extends Syncable> c) {
		Map<String, Syncable> map = new HashMap<String, Syncable>();
		for (Syncable v : c) {
			map.put(v.getKey(), v);
		}
		return map;
	}
	

	/**Change owner of FEED/SUBSCRIPTION*/
	/**Rally US708115
	 * Change Ownership of FEED - 1610
	 * 
	 * */
	private void checkChnageOwner(Syncable newobj, Syncable oldobj) {
		if(newobj instanceof Feed) {
			Feed oldfeed = (Feed) oldobj;
			Feed newfeed = (Feed) newobj;
			
			if(!oldfeed.getPublisher().equals(newfeed.getPublisher())){
				logger.info("PROV5013 -  Previous publisher: "+oldfeed.getPublisher() +": New publisher-"+newfeed.getPublisher());
				oldfeed.setPublisher(newfeed.getPublisher());
				oldfeed.changeOwnerShip();
			}
		}
		else if(newobj instanceof Subscription) {
			Subscription oldsub = (Subscription) oldobj;
			Subscription newsub = (Subscription) newobj;
			
			if(!oldsub.getSubscriber().equals(newsub.getSubscriber())){
				logger.info("PROV5013 -  Previous subscriber: "+oldsub.getSubscriber() +": New subscriber-"+newsub.getSubscriber());
				oldsub.setSubscriber(newsub.getSubscriber());
				oldsub.changeOwnerShip();
			}
		}
		
	}

	/**
	 * Issue a GET on the peer POD's /internal/prov/ URL to get a copy of its provisioning data.
	 * @return the provisioning data (as a JONObject)
	 */
	private synchronized JSONObject readProvisioningJSON() {
		String url  = URLUtilities.generatePeerProvURL();
		HttpGet get = new HttpGet(url);
		try {
			HttpResponse response = httpclient.execute(get);
			int code = response.getStatusLine().getStatusCode();
			if (code != HttpServletResponse.SC_OK) {
				logger.warn("PROV5010: readProvisioningJSON failed, bad error code: "+code);
				return null;
			}
			HttpEntity entity = response.getEntity();
			String ctype = entity.getContentType().getValue().trim();
			if (!ctype.equals(BaseServlet.PROVFULL_CONTENT_TYPE1) && !ctype.equals(BaseServlet.PROVFULL_CONTENT_TYPE2)) {
				logger.warn("PROV5011: readProvisioningJSON failed, bad content type: "+ctype);
				return null;
			}
			return new JSONObject(new JSONTokener(entity.getContent()));
		} catch (Exception e) {
			logger.warn("PROV5012: readProvisioningJSON failed, exception: "+e);
			return null;
		} finally {
			get.releaseConnection();
		}
	}
	/**
	 * Issue a GET on the peer POD's /internal/drlogs/ URL to get an RELBitSet representing the
	 * log records available in the remote database.
	 * @return the bitset
	 */
	private RLEBitSet readRemoteLoglist() {
		RLEBitSet bs = new RLEBitSet();
		String url  = URLUtilities.generatePeerLogsURL();

		//Fixing if only one Prov is configured, not to give exception to fill logs, return empty bitset.
		if(url.equals("")) {
			return bs;
		}
		//End of fix.

		HttpGet get = new HttpGet(url);
		try {
			HttpResponse response = httpclient.execute(get);
			int code = response.getStatusLine().getStatusCode();
			if (code != HttpServletResponse.SC_OK) {
				logger.warn("PROV5010: readRemoteLoglist failed, bad error code: "+code);
				return bs;
			}
			HttpEntity entity = response.getEntity();
			String ctype = entity.getContentType().getValue().trim();
			if (!ctype.equals("text/plain")) {
				logger.warn("PROV5011: readRemoteLoglist failed, bad content type: "+ctype);
				return bs;
			}
			InputStream is = entity.getContent();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int ch = 0;
			while ((ch = is.read()) >= 0)
				bos.write(ch);
			bs.set(bos.toString());
			is.close();
		} catch (Exception e) {
			logger.warn("PROV5012: readRemoteLoglist failed, exception: "+e);
			return bs;
		} finally {
			get.releaseConnection();
		}
		return bs;
	}
	/**
	 * Issue a POST on the peer POD's /internal/drlogs/ URL to fetch log records available
	 * in the remote database that we wish to copy to the local database.
	 * @param bs the bitset (an RELBitSet) of log records to fetch
	 */
	private void replicateDRLogs(RLEBitSet bs) {
		String url  = URLUtilities.generatePeerLogsURL();
		HttpPost post = new HttpPost(url);
		try {
			String t = bs.toString();
			HttpEntity body = new ByteArrayEntity(t.getBytes(), ContentType.create("text/plain"));
			post.setEntity(body);
			if (logger.isDebugEnabled())
				logger.debug("Requesting records: "+t);

			HttpResponse response = httpclient.execute(post);
			int code = response.getStatusLine().getStatusCode();
			if (code != HttpServletResponse.SC_OK) {
				logger.warn("PROV5010: replicateDRLogs failed, bad error code: "+code);
				return;
			}
			HttpEntity entity = response.getEntity();
			String ctype = entity.getContentType().getValue().trim();
			if (!ctype.equals("text/plain")) {
				logger.warn("PROV5011: replicateDRLogs failed, bad content type: "+ctype);
				return;
			}

			String spoolname = "" + System.currentTimeMillis();
			Path tmppath = Paths.get(spooldir, spoolname);
			Path donepath = Paths.get(spooldir, "IN."+spoolname);
			Files.copy(entity.getContent(), Paths.get(spooldir, spoolname), StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmppath, donepath, StandardCopyOption.REPLACE_EXISTING);
			logger.info("Approximately "+bs.cardinality()+" records replicated.");
		} catch (Exception e) {
			logger.warn("PROV5012: replicateDRLogs failed, exception: "+e);
		} finally {
			post.releaseConnection();
		}
	}
}
