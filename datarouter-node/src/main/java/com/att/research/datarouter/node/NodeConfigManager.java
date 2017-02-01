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
  * $Id: NodeConfigManager.java,v 1.21 2014/02/10 20:53:07 agg Exp $
  */

package com.att.research.datarouter.node;

import java.net.*;
import java.util.*;
import java.io.*;
import org.apache.log4j.Logger;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.research.datarouter.node.eelf.EelfMsgs;


/**
 *	Maintain the configuration of a Data Router node
 *	<p>
 *	The NodeConfigManager is the single point of contact for servlet, delivery, event logging, and log retention subsystems to access configuration information.  (Log4J has its own configuration mechanism).
 *	<p>
 *	There are two basic sets of configuration data.  The
 *	static local configuration data, stored in a local configuration file (created
 *	as part of installation by SWM), and the dynamic global
 *	configuration data fetched from the data router provisioning server.
 */
public class NodeConfigManager implements DeliveryQueueHelper	{
    private static EELFLogger eelflogger = EELFManager.getInstance().getLogger("com.att.research.datarouter.node.NodeConfigManager");
	private static Logger logger = Logger.getLogger("com.att.research.datarouter.node.NodeConfigManager");
	private static NodeConfigManager	base = new NodeConfigManager();

	private Timer timer = new Timer("Node Configuration Timer", true);
	private long	maxfailuretimer;
	private long	initfailuretimer;
	private long	expirationtimer;
	private double	failurebackoff;
	private long	fairtimelimit;
	private int	fairfilelimit;
	private double	fdpstart;
	private double	fdpstop;
	private int	deliverythreads;
	private String	provurl;
	private String	provhost;
	private IsFrom	provcheck;
	private int	gfport;
	private int	svcport;
	private int	port;
	private String	spooldir;
	private String	logdir;
	private long	logretention;
	private String	redirfile;
	private String	kstype;
	private String	ksfile;
	private String	kspass;
	private String	kpass;
	private String	tstype;
	private String	tsfile;
	private String	tspass;
	private String	myname;
	private RedirManager	rdmgr;
	private RateLimitedOperation	pfetcher;
	private NodeConfig	config;
	private File	quiesce;
	private PublishId	pid;
	private String	nak;
	private TaskList	configtasks = new TaskList();
	private String	eventlogurl;
	private String	eventlogprefix;
	private String	eventlogsuffix;
	private String	eventloginterval;
	private boolean	followredirects;

	
	/**
	 *	Get the default node configuration manager
	 */
	public static NodeConfigManager getInstance() {
		return(base);
	}
	/**
	 *	Initialize the configuration of a Data Router node
	 */
	private NodeConfigManager() {
		Properties p = new Properties();
		try {
			p.load(new FileInputStream(System.getProperty("com.att.research.datarouter.node.ConfigFile", "/opt/app/datartr/etc/node.properties")));
		} catch (Exception e) {
			
			NodeUtils.setIpAndFqdnForEelf("NodeConfigManager");
			eelflogger.error(EelfMsgs.MESSAGE_PROPERTIES_LOAD_ERROR);
			logger.error("NODE0301 Unable to load local configuration file " + System.getProperty("com.att.research.datarouter.node.ConfigFile", "/opt/app/datartr/etc/node.properties"), e);
		}
		provurl = p.getProperty("ProvisioningURL", "https://feeds-drtr.web.att.com/internal/prov");
		try {
			provhost = (new URL(provurl)).getHost();
		} catch (Exception e) {
			NodeUtils.setIpAndFqdnForEelf("NodeConfigManager");
			eelflogger.error(EelfMsgs.MESSAGE_BAD_PROV_URL, provurl);
			logger.error("NODE0302 Bad provisioning server URL " + provurl);
			System.exit(1);
		}
		logger.info("NODE0303 Provisioning server is " + provhost);
		eventlogurl = p.getProperty("LogUploadURL", "https://feeds-drtr.web.att.com/internal/logs");
		provcheck = new IsFrom(provhost);
		gfport = Integer.parseInt(p.getProperty("IntHttpPort", "8080"));
		svcport = Integer.parseInt(p.getProperty("IntHttpsPort", "8443"));
		port = Integer.parseInt(p.getProperty("ExtHttpsPort", "443"));
		long minpfinterval = Long.parseLong(p.getProperty("MinProvFetchInterval", "10000"));
		long minrsinterval = Long.parseLong(p.getProperty("MinRedirSaveInterval", "10000"));
		spooldir = p.getProperty("SpoolDir", "spool");
		File fdir = new File(spooldir + "/f");
		fdir.mkdirs();
		for (File junk: fdir.listFiles()) {
			if (junk.isFile()) {
				junk.delete();
			}
		}
		logdir = p.getProperty("LogDir", "logs");
		(new File(logdir)).mkdirs();
		logretention = Long.parseLong(p.getProperty("LogRetention", "30")) * 86400000L;
		eventlogprefix = logdir + "/events";
		eventlogsuffix = ".log";
		String redirfile = p.getProperty("RedirectionFile", "etc/redirections.dat");
		kstype = p.getProperty("KeyStoreType", "jks");
		ksfile = p.getProperty("KeyStoreFile", "etc/keystore");
		kspass = p.getProperty("KeyStorePassword", "changeme");
		kpass = p.getProperty("KeyPassword", "changeme");
		tstype = p.getProperty("TrustStoreType", "jks");
		tsfile = p.getProperty("TrustStoreFile");
		tspass = p.getProperty("TrustStorePassword", "changeme");
		if (tsfile != null && tsfile.length() > 0) {
			System.setProperty("javax.net.ssl.trustStoreType", tstype);
			System.setProperty("javax.net.ssl.trustStore", tsfile);
			System.setProperty("javax.net.ssl.trustStorePassword", tspass);
		}
		nak = p.getProperty("NodeAuthKey", "Node123!");
		quiesce = new File(p.getProperty("QuiesceFile", "etc/SHUTDOWN"));
		myname = NodeUtils.getCanonicalName(kstype, ksfile, kspass);
		if (myname == null) {
			NodeUtils.setIpAndFqdnForEelf("NodeConfigManager");
			eelflogger.error(EelfMsgs.MESSAGE_KEYSTORE_FETCH_ERROR, ksfile);
			logger.error("NODE0309 Unable to fetch canonical name from keystore file " + ksfile);
			System.exit(1);
		}
		logger.info("NODE0304 My certificate says my name is " + myname);
		pid = new PublishId(myname);
		rdmgr = new RedirManager(redirfile, minrsinterval, timer);
		pfetcher = new RateLimitedOperation(minpfinterval, timer) {
			public void run() {
				fetchconfig();
			}
		};
		logger.info("NODE0305 Attempting to fetch configuration at " + provurl);
		pfetcher.request();
	}
	private void localconfig() {
		followredirects = Boolean.parseBoolean(getProvParam("FOLLOW_REDIRECTS", "false"));
		eventloginterval = getProvParam("LOGROLL_INTERVAL", "5m");
		initfailuretimer = 10000;
		maxfailuretimer = 3600000;
		expirationtimer = 86400000;
		failurebackoff = 2.0;
		deliverythreads = 40;
		fairfilelimit = 100;
		fairtimelimit = 60000;
		fdpstart = 0.05;
		fdpstop = 0.2;
		try { initfailuretimer = (long)(Double.parseDouble(getProvParam("DELIVERY_INIT_RETRY_INTERVAL")) * 1000); } catch (Exception e) {}
		try { maxfailuretimer = (long)(Double.parseDouble(getProvParam("DELIVERY_MAX_RETRY_INTERVAL")) * 1000); } catch (Exception e) {}
		try { expirationtimer = (long)(Double.parseDouble(getProvParam("DELIVERY_MAX_AGE")) * 1000); } catch (Exception e) {}
		try { failurebackoff = Double.parseDouble(getProvParam("DELIVERY_RETRY_RATIO")); } catch (Exception e) {}
		try { deliverythreads = Integer.parseInt(getProvParam("DELIVERY_THREADS")); } catch (Exception e) {}
		try { fairfilelimit = Integer.parseInt(getProvParam("FAIR_FILE_LIMIT")); } catch (Exception e) {}
		try { fairtimelimit = (long)(Double.parseDouble(getProvParam("FAIR_TIME_LIMIT")) * 1000); } catch (Exception e) {}
		try { fdpstart = Double.parseDouble(getProvParam("FREE_DISK_RED_PERCENT")) / 100.0; } catch (Exception e) {}
		try { fdpstop = Double.parseDouble(getProvParam("FREE_DISK_YELLOW_PERCENT")) / 100.0; } catch (Exception e) {}
		if (fdpstart < 0.01) {
			fdpstart = 0.01;
		}
		if (fdpstart > 0.5) {
			fdpstart = 0.5;
		}
		if (fdpstop < fdpstart) {
			fdpstop = fdpstart;
		}
		if (fdpstop > 0.5) {
			fdpstop = 0.5;
		}
	}
	private void fetchconfig() {
		try {
			System.out.println("provurl:: "+provurl);
			Reader r = new InputStreamReader((new URL(provurl)).openStream());
			config = new NodeConfig(new ProvData(r), myname, spooldir, port, nak);
			localconfig();
			configtasks.startRun();
			Runnable rr;
			while ((rr = configtasks.next()) != null) {
				try {
					rr.run();
				} catch (Exception e) {
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			NodeUtils.setIpAndFqdnForEelf("fetchconfigs");
			eelflogger.error(EelfMsgs.MESSAGE_CONF_FAILED, e.toString());
			logger.error("NODE0306 Configuration failed " + e.toString() + " - try again later", e);
			pfetcher.request();
		}
	}
	/**
	 *	Process a gofetch request from a particular IP address.  If the
	 *	IP address is not an IP address we would go to to fetch the
	 *	provisioning data, ignore the request.  If the data has been
	 *	fetched very recently (default 10 seconds), wait a while before fetching again.
	 */
	public synchronized void gofetch(String remoteaddr) {
		if (provcheck.isFrom(remoteaddr)) {
			logger.info("NODE0307 Received configuration fetch request from provisioning server " + remoteaddr);
			pfetcher.request();
		} else {
			logger.info("NODE0308 Received configuration fetch request from unexpected server " + remoteaddr);
		}
	}
	/**
	 *	Am I configured?
	 */
	public boolean isConfigured() {
		return(config != null);
	}
	/**
	 *	Am I shut down?
	 */
	public boolean isShutdown() {
		return(quiesce.exists());
	}
	/**
	 *	Given a routing string, get the targets.
	 *	@param routing	Target string
	 *	@return	array of targets
	 */
	public Target[] parseRouting(String routing) {
		return(config.parseRouting(routing));
	}
	/**
	 *	Given a set of credentials and an IP address, is this request from another node?
	 *	@param credentials	Credentials offered by the supposed node
	 *	@param ip	IP address the request came from
	 *	@return	If the credentials and IP address are recognized, true, otherwise false.
	 */
	public boolean isAnotherNode(String credentials, String ip) {
		return(config.isAnotherNode(credentials, ip));
	}
	/**
	 *	Check whether publication is allowed.
	 *	@param feedid	The ID of the feed being requested
	 *	@param credentials	The offered credentials
	 *	@param ip	The requesting IP address
	 *	@return	True if the IP and credentials are valid for the specified feed.
	 */
	public String isPublishPermitted(String feedid, String credentials, String ip) {
		return(config.isPublishPermitted(feedid, credentials, ip));
	}
	/**
	 *	Check who the user is given the feed ID and the offered credentials.
	 *	@param feedid	The ID of the feed specified
	 *	@param credentials	The offered credentials
	 *	@return	Null if the credentials are invalid or the user if they are valid.
	 */
	public String getAuthUser(String feedid, String credentials) {
		return(config.getAuthUser(feedid, credentials));
	}
	/**
	 *	Check if the publish request should be sent to another node based on the feedid, user, and source IP address.
	 *	@param feedid	The ID of the feed specified
	 *	@param user	The publishing user
	 *	@param ip	The IP address of the publish endpoint
	 *	@return	Null if the request should be accepted or the correct hostname if it should be sent to another node.
	 */
	public String getIngressNode(String feedid, String user, String ip) {
		return(config.getIngressNode(feedid, user, ip));
	}
	/**
	 *	Get a provisioned configuration parameter (from the provisioning server configuration)
	 *	@param name	The name of the parameter
	 *	@return	The value of the parameter or null if it is not defined.
	 */
	public String getProvParam(String name) {
		return(config.getProvParam(name));
	}
	/**
	 *	Get a provisioned configuration parameter (from the provisioning server configuration)
	 *	@param name	The name of the parameter
	 *	@param deflt	The value to use if the parameter is not defined
	 *	@return	The value of the parameter or deflt if it is not defined.
	 */
	public String getProvParam(String name, String deflt) {
		name = config.getProvParam(name);
		if (name == null) {
			name = deflt;
		}
		return(name);
	}
	/**
	 *	Generate a publish ID
	 */
	public String getPublishId() {
		return(pid.next());
	}
	/**
	 *	Get all the outbound spooling destinations.
	 *	This will include both subscriptions and nodes.
	 */
	public DestInfo[] getAllDests() {
		return(config.getAllDests());
	}
	/**
	 *	Register a task to run whenever the configuration changes
	 */
	public void registerConfigTask(Runnable task) {
		configtasks.addTask(task);
	}
	/**
	 *	Deregister a task to run whenever the configuration changes
	 */
	public void deregisterConfigTask(Runnable task) {
		configtasks.removeTask(task);
	}
	/**
	 *	Get the URL to deliver a message to.
	 *	@param destinfo	The destination information
	 *	@param fileid	The file ID
	 *	@return	The URL to deliver to
	 */
	public String getDestURL(DestInfo destinfo, String fileid) {
		String subid = destinfo.getSubId();
		String purl = destinfo.getURL();
		if (followredirects && subid != null) {
			purl = rdmgr.lookup(subid, purl);
		}
		return(purl + "/" + fileid);
	}
	/**
	 *	Is a destination redirected?
	 */
	public boolean isDestRedirected(DestInfo destinfo) {
		return(followredirects && rdmgr.isRedirected(destinfo.getSubId()));
	}
	/**
	 *	Set up redirection on receipt of a 3XX from a target URL
	 */
	public boolean handleRedirection(DestInfo destinfo, String redirto, String fileid) {
		fileid = "/" + fileid;
		String subid = destinfo.getSubId();
		String purl = destinfo.getURL();
		if (followredirects && subid != null && redirto.endsWith(fileid)) {
			redirto = redirto.substring(0, redirto.length() - fileid.length());
			if (!redirto.equals(purl)) {
				rdmgr.redirect(subid, purl, redirto);
				return(true);
			}
		}
		return(false);
	}
	/**
	 *	Handle unreachable target URL
	 */
	public void handleUnreachable(DestInfo destinfo) {
		String subid = destinfo.getSubId();
		if (followredirects && subid != null) {
			rdmgr.forget(subid);
		}
	}
	/**
	 *	Get the timeout before retrying after an initial delivery failure
	 */
	public long getInitFailureTimer() {
		return(initfailuretimer);
	}
	/**
	 *	Get the maximum timeout between delivery attempts
	 */
	public long getMaxFailureTimer() {
		return(maxfailuretimer);
	}
	/**
	 *	Get the ratio between consecutive delivery attempts
	 */
	public double getFailureBackoff() {
		return(failurebackoff);
	}
	/**
	 *	Get the expiration timer for deliveries
	 */
	public long getExpirationTimer() {
		return(expirationtimer);
	}
	/**
	 *	Get the maximum number of file delivery attempts before checking
	 *	if another queue has work to be performed.
	 */
	public int getFairFileLimit() {
		return(fairfilelimit);
	}
	/**
	 *	Get the maximum amount of time spent delivering files before
	 *	checking if another queue has work to be performed.
	 */
	public long getFairTimeLimit() {
		return(fairtimelimit);
	}
	/**
	 *	Get the targets for a feed
	 *	@param feedid	The feed ID
	 *	@return	The targets this feed should be delivered to
	 */
	public Target[] getTargets(String feedid) {
		return(config.getTargets(feedid));
	}
	/**
	 *	Get the spool directory for temporary files
	 */
	public String getSpoolDir() {
		return(spooldir + "/f");
	}
	/**
	 *	Get the base directory for spool directories
	 */
	public String getSpoolBase() {
		return(spooldir);
	}
	/**
	 *	Get the key store type
	 */
	public String getKSType() {
		return(kstype);
	}
	/**
	 *	Get the key store file
	 */
	public String getKSFile() {
		return(ksfile);
	}
	/**
	 *	Get the key store password
	 */
	public String getKSPass() {
		return(kspass);
	}
	/**
	 *	Get the key password
	 */
	public String getKPass() {
		return(kpass);
	}
	/**
	 *	Get the http port
	 */
	public int getHttpPort() {
		return(gfport);
	}
	/**
	 *	Get the https port
	 */
	public int getHttpsPort() {
		return(svcport);
	}
	/**
	 *	Get the externally visible https port
	 */
	public int getExtHttpsPort() {
		return(port);
	}
	/**
	 *	Get the external name of this machine
	 */
	public String getMyName() {
		return(myname);
	}
	/**
	 *	Get the number of threads to use for delivery
	 */
	public int	getDeliveryThreads() {
		return(deliverythreads);
	}
	/**
	 *	Get the URL for uploading the event log data
	 */
	public String	getEventLogUrl() {
		return(eventlogurl);
	}
	/**
	 *	Get the prefix for the names of event log files
	 */
	public String	getEventLogPrefix() {
		return(eventlogprefix);
	}
	/**
	 *	Get the suffix for the names of the event log files
	 */
	public String	getEventLogSuffix() {
		return(eventlogsuffix);
	}
	/**
	 *	Get the interval between event log file rollovers
	 */
	public String getEventLogInterval() {
		return(eventloginterval);
	}
	/**
	 *	Should I follow redirects from subscribers?
	 */
	public boolean isFollowRedirects() {
		return(followredirects);
	}
	/**
	 *	Get the directory where the event and node log files live
	 */
	public String getLogDir() {
		return(logdir);
	}
	/**
	 *	How long do I keep log files (in milliseconds)
	 */
	public long getLogRetention() {
		return(logretention);
	}
	/**
	 *	Get the timer
	 */
	public Timer getTimer() {
		return(timer);
	}
	/**
	 *	Get the feed ID for a subscription
	 *	@param subid	The subscription ID
	 *	@return	The feed ID
	 */
	public String getFeedId(String subid) {
		return(config.getFeedId(subid));
	}
	/**
	 *	Get the authorization string this node uses
	 *	@return The Authorization string for this node
	 */
	public String getMyAuth() {
		return(config.getMyAuth());
	}
	/**
	 *	Get the fraction of free spool disk space where we start throwing away undelivered files.  This is FREE_DISK_RED_PERCENT / 100.0.  Default is 0.05.  Limited by 0.01 <= FreeDiskStart <= 0.5.
	 */
	public double getFreeDiskStart() {
		return(fdpstart);
	}
	/**
	 *	Get the fraction of free spool disk space where we stop throwing away undelivered files.  This is FREE_DISK_YELLOW_PERCENT / 100.0.  Default is 0.2.  Limited by FreeDiskStart <= FreeDiskStop <= 0.5.
	 */
	public double getFreeDiskStop() {
		return(fdpstop);
	}
	/**
	 *	Get the spool directory for a subscription
	 */
	public String getSpoolDir(String subid, String remoteaddr) {
		if (provcheck.isFrom(remoteaddr)) {
			String sdir = config.getSpoolDir(subid);
			if (sdir != null) {
				logger.info("NODE0310 Received subscription reset request for subscription " + subid + " from provisioning server " + remoteaddr);
			} else {
				logger.info("NODE0311 Received subscription reset request for unknown subscription " + subid + " from provisioning server " + remoteaddr);
			}
			return(sdir);
		} else {
			logger.info("NODE0312 Received subscription reset request from unexpected server " + remoteaddr);
			return(null);
		}
	}
}
