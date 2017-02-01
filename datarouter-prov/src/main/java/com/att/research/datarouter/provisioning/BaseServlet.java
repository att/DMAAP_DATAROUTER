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

import static com.att.eelf.configuration.Configuration.MDC_SERVER_FQDN;

import static com.att.eelf.configuration.Configuration.MDC_SERVER_IP_ADDRESS;
import static com.att.eelf.configuration.Configuration.MDC_SERVICE_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;	
import org.slf4j.MDC;

import com.att.research.datarouter.authz.Authorizer;
import com.att.research.datarouter.authz.impl.ProvAuthorizer;
import com.att.research.datarouter.authz.impl.ProvDataProvider;
import com.att.research.datarouter.provisioning.beans.Deleteable;
import com.att.research.datarouter.provisioning.beans.Feed;
import com.att.research.datarouter.provisioning.beans.Insertable;
import com.att.research.datarouter.provisioning.beans.NodeClass;
import com.att.research.datarouter.provisioning.beans.Parameters;
import com.att.research.datarouter.provisioning.beans.Subscription;
import com.att.research.datarouter.provisioning.beans.Updateable;
import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.ThrottleFilter;
import com.att.research.datarouter.provisioning.beans.Group; //Groups feature Rally:US708115 - 1610	

import java.util.Properties;
import java.util.regex.Pattern;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
/**
 * This is the base class for all Servlets in the provisioning code.
 * It provides standard constants and some common methods.
 *
 * @author Robert Eby
 * @version $Id: BaseServlet.java,v 1.16 2014/03/12 19:45:40 eby Exp $
 */
@SuppressWarnings("serial")
public class BaseServlet extends HttpServlet implements ProvDataProvider {
	public static final String BEHALF_HEADER         = "X-ATT-DR-ON-BEHALF-OF";
	public static final String FEED_BASECONTENT_TYPE = "application/vnd.att-dr.feed";
	public static final String FEED_CONTENT_TYPE     = "application/vnd.att-dr.feed; version=2.0";
	public static final String FEEDFULL_CONTENT_TYPE = "application/vnd.att-dr.feed-full; version=2.0";
	public static final String FEEDLIST_CONTENT_TYPE = "application/vnd.att-dr.feed-list; version=1.0";
	public static final String SUB_BASECONTENT_TYPE  = "application/vnd.att-dr.subscription";
	public static final String SUB_CONTENT_TYPE      = "application/vnd.att-dr.subscription; version=2.0";
	public static final String SUBFULL_CONTENT_TYPE  = "application/vnd.att-dr.subscription-full; version=2.0";
	public static final String SUBLIST_CONTENT_TYPE  = "application/vnd.att-dr.subscription-list; version=1.0";

	
	//Adding groups functionality, ...1610
	public static final String GROUP_BASECONTENT_TYPE = "application/vnd.att-dr.group";
	public static final String GROUP_CONTENT_TYPE     = "application/vnd.att-dr.group; version=2.0";
	public static final String GROUPFULL_CONTENT_TYPE = "application/vnd.att-dr.group-full; version=2.0";
	public static final String GROUPLIST_CONTENT_TYPE = "application/vnd.att-dr.fegrouped-list; version=1.0";


	public static final String LOGLIST_CONTENT_TYPE  = "application/vnd.att-dr.log-list; version=1.0";
	public static final String PROVFULL_CONTENT_TYPE1 = "application/vnd.att-dr.provfeed-full; version=1.0";
	public static final String PROVFULL_CONTENT_TYPE2 = "application/vnd.att-dr.provfeed-full; version=2.0";
	public static final String CERT_ATTRIBUTE        = "javax.servlet.request.X509Certificate";

	public static final String DB_PROBLEM_MSG = "There has been a problem with the DB.  It is suggested you try the operation again.";

	public static final int    DEFAULT_MAX_FEEDS     = 10000;
	public static final int    DEFAULT_MAX_SUBS      = 100000;
	public static final int    DEFAULT_POKETIMER1    = 5;
	public static final int    DEFAULT_POKETIMER2    = 30;
	public static final String DEFAULT_DOMAIN        = "web.att.com";
	public static final String DEFAULT_PROVSRVR_NAME = "feeds-drtr.web.att.com";
	public static final String RESEARCH_SUBNET       = "135.207.136.128/25";
	public static final String STATIC_ROUTING_NODES       = ""; //Adding new param for static Routing - Rally:US664862-1610

	/** A boolean to trigger one time "provisioning changed" event on startup */
	private static boolean startmsg_flag  = true;
	/** This POD should require SSL connections from clients; pulled from the DB (PROV_REQUIRE_SECURE) */
	private static boolean require_secure = true;
	/** This POD should require signed, recognized certificates from clients; pulled from the DB (PROV_REQUIRE_CERT) */
	private static boolean require_cert   = true;
	/** The set of authorized addresses and networks; pulled from the DB (PROV_AUTH_ADDRESSES) */
	private static Set<String> authorizedAddressesAndNetworks = new HashSet<String>();
	/** The set of authorized names; pulled from the DB (PROV_AUTH_SUBJECTS) */
	private static Set<String> authorizedNames = new HashSet<String>();
	/** The FQDN of the initially "active" provisioning server in this Data Router ecosystem */
	private static String initial_active_pod;
	/** The FQDN of the initially "standby" provisioning server in this Data Router ecosystem */
	private static String initial_standby_pod;
	/** The FQDN of this provisioning server in this Data Router ecosystem */
	private static String this_pod;
	/** "Timer 1" - used to determine when to notify nodes of provisioning changes */
	private static long poke_timer1;
	/** "Timer 2" - used to determine when to notify nodes of provisioning changes */
	private static long poke_timer2;
	/** Array of nodes names and/or FQDNs */
	private static String[] nodes = new String[0];
	/** Array of node IP addresses */
	private static InetAddress[] nodeAddresses = new InetAddress[0];
	/** Array of POD IP addresses */
	private static InetAddress[] podAddresses = new InetAddress[0];
	/** The maximum number of feeds allowed; pulled from the DB (PROV_MAXFEED_COUNT) */
	protected static int max_feeds    = 0;
	/** The maximum number of subscriptions allowed; pulled from the DB (PROV_MAXSUB_COUNT) */
	protected static int max_subs     = 0;
	/** The current number of feeds in the system */
	protected static int active_feeds = 0;
	/** The current number of subscriptions in the system */
	protected static int active_subs  = 0;
	/** The domain used to generate a FQDN from the "bare" node names */
	public static String prov_domain = "web.att.com";
	/** The standard FQDN of the provisioning server in this Data Router ecosystem */
	public static String prov_name   = "feeds-drtr.web.att.com";
	/** The standard FQDN of the ACTIVE provisioning server in this Data Router ecosystem */
	public static String active_prov_name   = "feeds-drtr.web.att.com";
	/** Special subnet that is allowed access to /internal */
	protected static String special_subnet = RESEARCH_SUBNET;

	/** Special subnet that is allowed access to /internal to Lab Machine */
	protected static String special_subnet_secondary = RESEARCH_SUBNET;
	protected static String static_routing_nodes = STATIC_ROUTING_NODES; //Adding new param for static Routing - Rally:US664862-1610

	/** This logger is used to log provisioning events */
	protected static Logger eventlogger;
	/** This logger is used to log internal events (errors, etc.) */
	protected static Logger intlogger;
	/** Authorizer - interface to the Policy Engine */
	protected static Authorizer authz;
	/** The Synchronizer used to sync active DB to standby one */
	protected static SynchronizerTask synctask = null;
    
	//Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
	private InetAddress thishost;
	private InetAddress loopback;
    private static Boolean mailSendFlag = false;

	public static final String MAILCONFIG_FILE = "mail.properties";
	private static Properties mailprops;
	/**
	 * Initialize data common to all the provisioning server servlets.
	 */
	protected BaseServlet() {
		if (eventlogger == null)
			eventlogger = Logger.getLogger("com.att.research.datarouter.provisioning.events");
		if (intlogger == null)
			intlogger   = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
		if (authz == null)
			authz = new ProvAuthorizer(this);
		if (startmsg_flag) {
			startmsg_flag = false;
			provisioningParametersChanged();
		}
		if (synctask == null) {
			synctask = SynchronizerTask.getSynchronizer();
		}
		String name = this.getClass().getName();
		intlogger.info("PROV0002 Servlet "+name+" started.");
	}
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			thishost = InetAddress.getLocalHost();
			loopback = InetAddress.getLoopbackAddress();
			checkHttpsRelaxation(); //Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
		} catch (UnknownHostException e) {
			// ignore
		}
	}
	protected int getIdFromPath(HttpServletRequest req) {
		String path = req.getPathInfo();
		if (path == null || path.length() < 2)
			return -1;
		try {
			return Integer.parseInt(path.substring(1));
		} catch (NumberFormatException e) {
			return -1;
		}
	}
	/**
	 * Read the request's input stream and return a JSONObject from it
	 * @param req the HTTP request
	 * @return the JSONObject, or null if the stream cannot be parsed
	 */
	protected JSONObject getJSONfromInput(HttpServletRequest req) {
		JSONObject jo = null;
		try {
			jo = new JSONObject(new JSONTokener(req.getInputStream()));
			if (intlogger.isDebugEnabled())
				intlogger.debug("JSON: "+jo.toString());
		} catch (Exception e) {
			intlogger.info("Error reading JSON: "+e);
		}
		return jo;
	}
	/**
	 * Check if the remote host is authorized to perform provisioning.
	 * Is the request secure?
	 * Is it coming from an authorized IP address or network (configured via PROV_AUTH_ADDRESSES)?
	 * Does it have a valid client certificate (configured via PROV_AUTH_SUBJECTS)?
	 * @param request the request
	 * @return an error string, or null if all is OK
	 */
	protected String isAuthorizedForProvisioning(HttpServletRequest request) {
		// Is the request https?
		if (require_secure && !request.isSecure()) {
			return "Request must be made over an HTTPS connection.";
		}

		// Is remote IP authorized?
		String remote = request.getRemoteAddr();
		try {
			boolean found = false;
			InetAddress ip = InetAddress.getByName(remote);
			for (String addrnet : authorizedAddressesAndNetworks) {
				found |= addressMatchesNetwork(ip, addrnet);
			}
			if (!found) {
				return "Unauthorized address: "+remote;
			}
		} catch (UnknownHostException e) {
			return "Unauthorized address: "+remote;
		}

		// Does remote have a valid certificate?
		if (require_cert) {
			X509Certificate certs[] = (X509Certificate[]) request.getAttribute(CERT_ATTRIBUTE);
			if (certs == null || certs.length == 0) {
				return "Client certificate is missing.";
			}
			// cert[0] is the client cert
			// see http://www.proto.research.att.com/java/java7/api/javax/net/ssl/SSLSession.html#getPeerCertificates()
			String name = certs[0].getSubjectX500Principal().getName();
			if (!authorizedNames.contains(name)) {
				return "No authorized certificate found.";
			}
		}

		// No problems!
		return null;
	}
	/**
	 * Check if the remote IP address is authorized to see the /internal URL tree.
	 * @param request the HTTP request
	 * @return true iff authorized
	 */
	protected boolean isAuthorizedForInternal(HttpServletRequest request) {
		try {
			InetAddress ip = InetAddress.getByName(request.getRemoteAddr());
			for (InetAddress node : getNodeAddresses()) {
				if (node != null && ip.equals(node))
					return true;
			}
			for (InetAddress pod : getPodAddresses()) {
				if (pod != null && ip.equals(pod))
					return true;
			}
			if (thishost != null && ip.equals(thishost))
				return true;
			if (loopback != null && ip.equals(loopback))
				return true;
			// Also allow the "special subnet" access
			if (addressMatchesNetwork(ip, special_subnet_secondary))
				return true;
			if (addressMatchesNetwork(ip, special_subnet))
				return true;
		} catch (UnknownHostException e) {
			// ignore
		}
		return false;
	}
	/**
	 * Check if an IP address matches a network address.
	 * @param ip the IP address
	 * @param s the network address; a bare IP address may be matched also
	 * @return true if they intersect
	 */
	protected static boolean addressMatchesNetwork(InetAddress ip, String s) {
		int mlen = -1;
		int n = s.indexOf("/");
		if (n >= 0) {
			mlen = Integer.parseInt(s.substring(n+1));
			s = s.substring(0, n);
		}
		try {
			InetAddress i2 = InetAddress.getByName(s);
			byte[] b1 = ip.getAddress();
			byte[] b2 = i2.getAddress();
			if (b1.length != b2.length)
				return false;
			if (mlen > 0) {
				byte[] masks = {
					(byte)0x00, (byte)0x80, (byte)0xC0, (byte)0xE0,
					(byte)0xF0, (byte)0xF8, (byte)0xFC, (byte)0xFE
				};
				byte mask = masks[mlen%8];
				for (n = mlen/8; n < b1.length; n++) {
					b1[n] &= mask;
					b2[n] &= mask;
					mask = 0;
				}
			}
			for (n = 0; n < b1.length; n++)
				if (b1[n] != b2[n])
					return false;
		} catch (UnknownHostException e) {
			return false;
		}
		return true;
	}
	/**
	 * Something has changed in the provisioning data.
	 * Start the timers that will cause the pre-packaged JSON string to be regenerated,
	 * and cause nodes and the other provisioning server to be notified.
	 */
	public static void provisioningDataChanged() {
		long now = System.currentTimeMillis();
		Poker p = Poker.getPoker();
		p.setTimers(now + (poke_timer1 * 1000L), now + (poke_timer2 * 1000L));
	}
	/**
	 * Something in the parameters has changed, reload all parameters from the DB.
	 */
	public static void provisioningParametersChanged() {
		Map<String,String> map         = Parameters.getParameters();
		require_secure   = getBoolean(map, Parameters.PROV_REQUIRE_SECURE);
		require_cert     = getBoolean(map, Parameters.PROV_REQUIRE_CERT);
		authorizedAddressesAndNetworks = getSet(map, Parameters.PROV_AUTH_ADDRESSES);
		authorizedNames  = getSet    (map, Parameters.PROV_AUTH_SUBJECTS);
		nodes            = getSet    (map, Parameters.NODES).toArray(new String[0]);
		max_feeds        = getInt    (map, Parameters.PROV_MAXFEED_COUNT, DEFAULT_MAX_FEEDS);
		max_subs         = getInt    (map, Parameters.PROV_MAXSUB_COUNT, DEFAULT_MAX_SUBS);
		poke_timer1      = getInt    (map, Parameters.PROV_POKETIMER1, DEFAULT_POKETIMER1);
		poke_timer2      = getInt    (map, Parameters.PROV_POKETIMER2, DEFAULT_POKETIMER2);
		prov_domain      = getString (map, Parameters.PROV_DOMAIN, DEFAULT_DOMAIN);
		prov_name        = getString (map, Parameters.PROV_NAME, DEFAULT_PROVSRVR_NAME);
		active_prov_name = getString (map, Parameters.PROV_ACTIVE_NAME, prov_name);
		special_subnet   = getString (map, Parameters.PROV_SPECIAL_SUBNET, RESEARCH_SUBNET);
		static_routing_nodes = getString (map, Parameters.STATIC_ROUTING_NODES, ""); //Adding new param for static Routing - Rally:US664862-1610
		initial_active_pod  = getString (map, Parameters.ACTIVE_POD, "");
		initial_standby_pod = getString (map, Parameters.STANDBY_POD, "");
		static_routing_nodes = getString (map, Parameters.STATIC_ROUTING_NODES, ""); //Adding new param for static Routing - Rally:US664862-1610
		active_feeds     = Feed.countActiveFeeds();
		active_subs      = Subscription.countActiveSubscriptions();
		try {
			this_pod = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			this_pod = "";
			intlogger.warn("PROV0014 Cannot determine the name of this provisioning server.");
		}

		// Normalize the nodes, and fill in nodeAddresses
		InetAddress[] na = new InetAddress[nodes.length];
		for (int i = 0; i < nodes.length; i++) {
			if (nodes[i].indexOf('.') < 0)
				nodes[i] += "." + prov_domain;
			try {
				na[i] = InetAddress.getByName(nodes[i]);
				intlogger.debug("PROV0003 DNS lookup: "+nodes[i]+" => "+na[i].toString());
			} catch (UnknownHostException e) {
				na[i] = null;
				intlogger.warn("PROV0004 Cannot lookup "+nodes[i]+": "+e);
			}
		}

		//Reset Nodes arr after - removing static routing Nodes, Rally Userstory - US664862 .	
		List<String> filterNodes = new ArrayList<>();		
		for (int i = 0; i < nodes.length; i++) {		
			if(!static_routing_nodes.contains(nodes[i])){		
				filterNodes.add(nodes[i]);		
			}		
		}		
		String [] filteredNodes = filterNodes.toArray(new String[filterNodes.size()]);		  		
		nodes = filteredNodes;

		nodeAddresses = na;
		NodeClass.setNodes(nodes);		// update NODES table

		// Normalize the PODs, and fill in podAddresses
		String[] pods = getPods();
		na = new InetAddress[pods.length];
		for (int i = 0; i < pods.length; i++) {
			if (pods[i].indexOf('.') < 0)
				pods[i] += "." + prov_domain;
			try {
				na[i] = InetAddress.getByName(pods[i]);
				intlogger.debug("PROV0003 DNS lookup: "+pods[i]+" => "+na[i].toString());
			} catch (UnknownHostException e) {
				na[i] = null;
				intlogger.warn("PROV0004 Cannot lookup "+pods[i]+": "+e);
			}
		}
		podAddresses = na;

		// Update ThrottleFilter
		ThrottleFilter.configure();

		// Check if we are active or standby POD
		if (!isInitialActivePOD() && !isInitialStandbyPOD())
			intlogger.warn("PROV0015 This machine is neither the active nor the standby POD.");
	}


	/**Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
	 * Load mail properties.
	 * @author vs215k
	 *  
	**/
	private void loadMailProperties() {
		if (mailprops == null) {
			mailprops = new Properties();
			InputStream inStream = getClass().getClassLoader().getResourceAsStream(MAILCONFIG_FILE);
			try {
				mailprops.load(inStream);
			} catch (IOException e) {
				intlogger.fatal("PROV9003 Opening properties: "+e.getMessage());
				e.printStackTrace();
				System.exit(1);
			}
			finally {
				try {
					inStream.close();
				} 
				catch (IOException e) {
				}
			}
		}
	}
	
	/**Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
	 * Check if HTTPS Relexaction is enabled 
	 * @author vs215k
	 *  
	**/
	private void checkHttpsRelaxation() {
		if(mailSendFlag == false) {
			Properties p = (new DB()).getProperties();
			intlogger.info("HTTPS relaxatio: "+p.get("com.att.research.datarouter.provserver.https.relaxation"));
			
			if(p.get("com.att.research.datarouter.provserver.https.relaxation").equals("true")) {
			    try {
			    	  notifyPSTeam(p.get("com.att.research.datarouter.provserver.https.relax.notify").toString());
			    } 
				catch (Exception e) {
				    e.printStackTrace();
			    }
			 }
			mailSendFlag = true;
		}
	}
	
	/**Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
	 * @author vs215k
	 * @param email - list of email ids to notify if HTTP relexcation is enabled. 
	**/
	private void notifyPSTeam(String email) throws Exception {
		loadMailProperties(); //Load HTTPS Relex mail properties.
		String[] emails = email.split(Pattern.quote("|"));
    	
    	Properties mailproperties = new Properties();
		mailproperties.put("mail.smtp.host", mailprops.get("com.att.dmaap.datarouter.mail.server"));
		mailproperties.put("mail.transport.protocol", mailprops.get("com.att.dmaap.datarouter.mail.protocol"));
		
    	Session session = Session.getDefaultInstance(mailproperties, null);
    	Multipart mp = new MimeMultipart();
    	MimeBodyPart htmlPart = new MimeBodyPart();
    	
    	try {
    		
    	  Message msg = new MimeMessage(session);
    	  msg.setFrom(new InternetAddress(mailprops.get("com.att.dmaap.datarouter.mail.from").toString()));
    	  
    	  InternetAddress[] addressTo = new InternetAddress[emails.length];
    	  for ( int x =0 ; x < emails.length; x++) {
    	       addressTo[x] = new InternetAddress(emails[x]);
    	  }
    	  
    	  msg.addRecipients(Message.RecipientType.TO, addressTo);
    	  msg.setSubject(mailprops.get("com.att.dmaap.datarouter.mail.subject").toString());
    	  htmlPart.setContent(mailprops.get("com.att.dmaap.datarouter.mail.body").toString().replace("[SERVER]", InetAddress.getLocalHost().getHostName()), "text/html");
    	  mp.addBodyPart(htmlPart);
      	  msg.setContent(mp);
      	  
      	  System.out.println(mailprops.get("com.att.dmaap.datarouter.mail.body").toString().replace("[SERVER]", InetAddress.getLocalHost().getHostName()));
      	
    	  Transport.send(msg);
    	  intlogger.info("HTTPS relaxation mail is sent to - : "+email);
    	  
    	} catch (AddressException e) {
    		  intlogger.error("Invalid email address, unable to send https relaxation mail to - : "+email);
    	} catch (MessagingException e) {
    		intlogger.error("Invalid email address, unable to send https relaxation mail to - : "+email);
    	} 
	}


	/**
	 * Get an array of all node names in the DR network.
	 * @return an array of Strings
	 */
	public static String[] getNodes() {
		return nodes;
	}
	/**
	 * Get an array of all node InetAddresses in the DR network.
	 * @return an array of InetAddresses
	 */
	public static InetAddress[] getNodeAddresses() {
		return nodeAddresses;
	}
	/**
	 * Get an array of all POD names in the DR network.
	 * @return an array of Strings
	 */
	public static String[] getPods() {
		return new String[] { initial_active_pod, initial_standby_pod };
	}
	/**
	 * Get an array of all POD InetAddresses in the DR network.
	 * @return an array of InetAddresses
	 */
	public static InetAddress[] getPodAddresses() {
		return podAddresses;
	}
	/**
	 * Gets the FQDN of the initially ACTIVE provisioning server (POD).
	 * Note: this used to be called isActivePOD(), however, that is a misnomer, as the active status
	 * could shift to the standby POD without these parameters changing.  Hence, the function names
	 * have been changed to more accurately reflect their purpose.
	 * @return the FQDN
	 */
	public static boolean isInitialActivePOD() {
		return this_pod.equals(initial_active_pod);
	}
	/**
	 * Gets the FQDN of the initially STANDBY provisioning server (POD).
	 * Note: this used to be called isStandbyPOD(), however, that is a misnomer, as the standby status
	 * could shift to the active POD without these parameters changing.  Hence, the function names
	 * have been changed to more accurately reflect their purpose.
	 * @return the FQDN
	 */
	public static boolean isInitialStandbyPOD() {
		return this_pod.equals(initial_standby_pod);
	}
	/**
	 * INSERT an {@link Insertable} bean into the database.
	 * @param bean the bean representing a row to insert
	 * @return true if the INSERT was successful
	 */
	protected boolean doInsert(Insertable bean) {
		boolean rv = false;
		DB db = new DB();
		Connection conn = null;
		try {
			conn = db.getConnection();
			rv = bean.doInsert(conn);
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0005 doInsert: "+e.getMessage());
			e.printStackTrace();
		} finally {
			if (conn != null)
				db.release(conn);
		}
		return rv;
	}
	/**
	 * UPDATE an {@link Updateable} bean in the database.
	 * @param bean the bean representing a row to update
	 * @return true if the UPDATE was successful
	 */
	protected boolean doUpdate(Updateable bean) {
		boolean rv = false;
		DB db = new DB();
		Connection conn = null;
		try {
			conn = db.getConnection();
			rv = bean.doUpdate(conn);
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0006 doUpdate: "+e.getMessage());
			e.printStackTrace();
		} finally {
			if (conn != null)
				db.release(conn);
		}
		return rv;
	}
	/**
	 * DELETE an {@link Deleteable} bean from the database.
	 * @param bean the bean representing a row to delete
	 * @return true if the DELETE was successful
	 */
	protected boolean doDelete(Deleteable bean) {
		boolean rv = false;
		DB db = new DB();
		Connection conn = null;
		try {
			conn = db.getConnection();
			rv = bean.doDelete(conn);
		} catch (SQLException e) {
			rv = false;
			intlogger.warn("PROV0007 doDelete: "+e.getMessage());
			e.printStackTrace();
		} finally {
			if (conn != null)
				db.release(conn);
		}
		return rv;
	}
	private static boolean getBoolean(Map<String,String> map, String name) {
		String s = map.get(name);
		return (s != null) && s.equalsIgnoreCase("true");
	}
	private static String getString(Map<String,String> map, String name, String dflt) {
		String s = map.get(name);
		return (s != null) ? s : dflt;
	}
	private static int getInt(Map<String,String> map, String name, int dflt) {
		try {
			String s = map.get(name);
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return dflt;
		}
	}
	private static Set<String> getSet(Map<String,String> map, String name) {
		Set<String> set = new HashSet<String>();
		String s = map.get(name);
		if (s != null) {
			String[] pp = s.split("\\|");
			if (pp != null) {
				for (String t : pp) {
					String t2 = t.trim();
					if (t2.length() > 0)
						set.add(t2);
				}
			}
		}
		return set;
	}

	/**
	 * A class used to encapsulate a Content-type header, separating out the "version" attribute
	 * (which defaults to "1.0" if missing).
	 */
	public class ContentHeader {
		private String type = "";
		private Map<String, String> map = new HashMap<String, String>();
		public ContentHeader() {
			this("", "1.0");
		}
		public ContentHeader(String t, String v) {
			type = t.trim();
			map.put("version", v);
		}
		public String getType() {
			return type;
		}
		public String getAttribute(String key) {
			String s = map.get(key);
			if (s == null)
				s = "";
			return s;
		}
	}

	/**
	 * Get the ContentHeader from an HTTP request.
	 * @param req the request
	 * @return the header, encapsulated in a ContentHeader object
	 */
	public ContentHeader getContentHeader(HttpServletRequest req) {
		ContentHeader ch = new ContentHeader();
		String s = req.getHeader("Content-Type");
		if (s != null) {
			String[] pp = s.split(";");
			ch.type = pp[0].trim();
			for (int i = 1; i < pp.length; i++) {
				int ix = pp[i].indexOf('=');
				if (ix > 0) {
					String k = pp[i].substring(0, ix).trim();
					String v = pp[i].substring(ix+1).trim();
					ch.map.put(k,  v);
				} else {
					ch.map.put(pp[i].trim(), "");
				}
			}
		}
		return ch;
	}
	// Methods for the Policy Engine classes - ProvDataProvider interface
	@Override
	public String getFeedOwner(String feedId) {
		try {
			int n = Integer.parseInt(feedId);
			Feed f = Feed.getFeedById(n);
			if (f != null)
				return f.getPublisher();
		} catch (NumberFormatException e) {
			// ignore
		}
		return null;
	}
	@Override
	public String getFeedClassification(String feedId) {
		try {
			int n = Integer.parseInt(feedId);
			Feed f = Feed.getFeedById(n);
			if (f != null)
				return f.getAuthorization().getClassification();
		} catch (NumberFormatException e) {
			// ignore
		}
		return null;
	}
	@Override
	public String getSubscriptionOwner(String subId) {
		try {
			int n = Integer.parseInt(subId);
			Subscription s = Subscription.getSubscriptionById(n);
			if (s != null)
				return s.getSubscriber();
		} catch (NumberFormatException e) {
			// ignore
		}
		return null;
	}

	/*
	 * @Method - isUserMemberOfGroup - Rally:US708115 
	 * @Params - group object and user to check if exists in given group
	 * @return - boolean value /true/false
	 */
	private boolean isUserMemberOfGroup(Group group, String user) {
			 
		String groupdetails = group.getMembers().replace("]", "").replace("[", "");
	    String s[] =	groupdetails.split("},");
		
		for(int i=0; i < s.length; i++) {
				JSONObject jsonObj = null;
			 	try {
		            jsonObj = new JSONObject(s[i]+"}");
		            if(jsonObj.get("id").equals(user))
		            	return true;
		        } catch (JSONException e) {
		            e.printStackTrace();
		        }
		}
		return false;
		
	}
	
	/*
	 * @Method - getGroupByFeedGroupId- Rally:US708115 
	 * @Params - User to check in group and feedid which is assigned the group.
	 * @return - string value grupid/null
	 */
	@Override
	public String getGroupByFeedGroupId(String owner, String feedId) {
		try {
			int n = Integer.parseInt(feedId);
			Feed f = Feed.getFeedById(n);
			if (f != null) {
				int groupid = f.getGroupid();
				if(groupid > 0) {
					Group group = Group.getGroupById(groupid);
					if(isUserMemberOfGroup(group, owner)) {
						return group.getAuthid();
					}
				}
			}
		} catch (NumberFormatException e) {
			// ignore
		}
		return null;
	}
	
	/*
	 * @Method - getGroupBySubGroupId - Rally:US708115  
	 * @Params - User to check in group and subid which is assigned the group.
	 * @return - string value grupid/null
	 */
	@Override
	public String getGroupBySubGroupId(String owner, String subId) {
		try {
			int n = Integer.parseInt(subId);
			Subscription s = Subscription.getSubscriptionById(n);
			if (s != null) {
				int groupid = s.getGroupid();
				if(groupid > 0) {
					Group group = Group.getGroupById(groupid);
					if(isUserMemberOfGroup(group, owner)) {
						return group.getAuthid();
					}
				}
			}
		} catch (NumberFormatException e) {
			// ignore
		}
		return null;
	}
	
	/*
	 * @Method - setIpAndFqdnForEelf - Rally:US664892  
	 * @Params - method, prints method name in EELF log.
	 */	
	protected void setIpAndFqdnForEelf(String method) {
	 	MDC.clear();
        MDC.put(MDC_SERVICE_NAME, method);
        try {
            MDC.put(MDC_SERVER_FQDN, InetAddress.getLocalHost().getHostName());
            MDC.put(MDC_SERVER_IP_ADDRESS, InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }

	}
}