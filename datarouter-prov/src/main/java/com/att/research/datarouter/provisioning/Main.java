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

import java.security.Security;
import java.util.Properties;
import java.util.Timer;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.LogfileLoader;
import com.att.research.datarouter.provisioning.utils.PurgeLogDirTask;
import com.att.research.datarouter.provisioning.utils.ThrottleFilter;

/**
 * <p>
 * A main class which may be used to start the provisioning server with an "embedded" Jetty server.
 * Configuration is done via the properties file <i>provserver.properties</i>, which should be in the CLASSPATH.
 * The provisioning server may also be packaged with a web.xml and started as a traditional webapp.
 * </p>
 * <p>
 * Most of the work of the provisioning server is carried out within the eight servlets (configured below)
 * that are used to handle each of the eight types of requests the server may receive.
 * In addition, there are background threads started to perform other tasks:
 * </p>
 * <ul>
 * <li>One background Thread runs the {@link LogfileLoader} in order to process incoming logfiles.
 *   This Thread is created as a side effect of the first successful POST to the /internal/logs/ servlet.</li>
 * <li>One background Thread runs the {@link SynchronizerTask} which is used to periodically
 *   synchronize the database between active and standby servers.</li>
 * <li>One background Thread runs the {@link Poker} which is used to notify the nodes whenever
 *   provisioning data changes.</li>
 * <li>One task is run once a day to run {@link PurgeLogDirTask} which purges older logs from the
 *   /opt/app/datartr/logs directory.</li>
 * </ul>
 * <p>
 * The provisioning server is stopped by issuing a GET to the URL http://127.0.0.1/internal/halt
 * using <i>curl</i> or some other such tool.
 * </p>
 *
 * @author Robert Eby
 * @version $Id: Main.java,v 1.12 2014/03/12 19:45:41 eby Exp $
 */
public class Main {
	/** The truststore to use if none is specified */
	public static final String DEFAULT_TRUSTSTORE           = "/opt/java/jdk/jdk180/jre/lib/security/cacerts";
	public static final String KEYSTORE_TYPE_PROPERTY       = "com.att.research.datarouter.provserver.keystore.type";
	public static final String KEYSTORE_PATH_PROPERTY       = "com.att.research.datarouter.provserver.keystore.path";
	public static final String KEYSTORE_PASSWORD_PROPERTY   = "com.att.research.datarouter.provserver.keystore.password";
	public static final String TRUSTSTORE_PATH_PROPERTY     = "com.att.research.datarouter.provserver.truststore.path";
	public static final String TRUSTSTORE_PASSWORD_PROPERTY = "com.att.research.datarouter.provserver.truststore.password";

	/** The one and only {@link Server} instance in this JVM */
	private static Server server;

	/**
	 * Starts the Data Router Provisioning server.
	 * @param args not used
	 * @throws Exception if Jetty has a problem starting
	 */
	public static void main(String[] args) throws Exception {
		Security.setProperty("networkaddress.cache.ttl", "4");
		Logger logger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");

		// Check DB is accessible and contains the expected tables
		if (! checkDatabase(logger))
			System.exit(1);

		logger.info("PROV0000 **** AT&T Data Router Provisioning Server starting....");

		// Get properties
		Properties p = (new DB()).getProperties();
		int http_port  = Integer.parseInt(p.getProperty("com.att.research.datarouter.provserver.http.port", "8080"));
		int https_port = Integer.parseInt(p.getProperty("com.att.research.datarouter.provserver.https.port", "8443"));

		// HTTP connector
		SelectChannelConnector http = new SelectChannelConnector();
		http.setPort(http_port);
		http.setMaxIdleTime(300000);
		http.setRequestHeaderSize(2048);
		http.setAcceptors(2);
		http.setConfidentialPort(https_port);
		http.setLowResourcesConnections(20000);

		// HTTPS connector
		SslSelectChannelConnector https = new SslSelectChannelConnector();
		https.setPort(https_port);
		https.setMaxIdleTime(30000);
		https.setRequestHeaderSize(8192);
		https.setAcceptors(2);

		// SSL stuff
		SslContextFactory cf = https.getSslContextFactory();
		
		/**Skip SSLv3 Fixes*/
		cf.addExcludeProtocols("SSLv3");
		logger.info("Excluded protocols prov-"+cf.getExcludeProtocols());
		/**End of SSLv3 Fixes*/

		cf.setKeyStoreType(p.getProperty(KEYSTORE_TYPE_PROPERTY, "jks"));
		cf.setKeyStorePath(p.getProperty(KEYSTORE_PATH_PROPERTY));
		cf.setKeyStorePassword(p.getProperty(KEYSTORE_PASSWORD_PROPERTY));
		cf.setKeyManagerPassword(p.getProperty("com.att.research.datarouter.provserver.keymanager.password"));
		String ts = p.getProperty(TRUSTSTORE_PATH_PROPERTY);
		if (ts != null && ts.length() > 0) {
			System.out.println("@@ TS -> "+ts);
			cf.setTrustStore(ts);
			cf.setTrustStorePassword(p.getProperty(TRUSTSTORE_PASSWORD_PROPERTY));
		} else {
			cf.setTrustStore(DEFAULT_TRUSTSTORE);
			cf.setTrustStorePassword("changeit");
		}
		cf.setTrustStore("/opt/app/datartr/self_signed/cacerts.jks");
		cf.setTrustStorePassword("changeit");
		cf.setWantClientAuth(true);

		// Servlet and Filter configuration
		ServletContextHandler ctxt = new ServletContextHandler(0);
		ctxt.setContextPath("/");
		ctxt.addServlet(new ServletHolder(new FeedServlet()),         "/feed/*");
		ctxt.addServlet(new ServletHolder(new FeedLogServlet()),      "/feedlog/*");
		ctxt.addServlet(new ServletHolder(new PublishServlet()),      "/publish/*");
		ctxt.addServlet(new ServletHolder(new SubscribeServlet()),    "/subscribe/*");
		ctxt.addServlet(new ServletHolder(new StatisticsServlet()), 	  "/statistics/*");
		ctxt.addServlet(new ServletHolder(new SubLogServlet()),       "/sublog/*");
		ctxt.addServlet(new ServletHolder(new GroupServlet()),    	  "/group/*"); //Provision groups - Rally US708115 -1610 
		ctxt.addServlet(new ServletHolder(new SubscriptionServlet()), "/subs/*");
		ctxt.addServlet(new ServletHolder(new InternalServlet()),     "/internal/*");
		ctxt.addServlet(new ServletHolder(new RouteServlet()),        "/internal/route/*");
		ctxt.addServlet(new ServletHolder(new DRFeedsServlet()),      "/");
		ctxt.addFilter (new FilterHolder (new ThrottleFilter()),      "/publish/*", FilterMapping.REQUEST);

		ContextHandlerCollection contexts = new ContextHandlerCollection();
		contexts.addHandler(ctxt);

		// Request log configuration
		NCSARequestLog nrl = new NCSARequestLog();
		nrl.setFilename(p.getProperty("com.att.research.datarouter.provserver.accesslog.dir") + "/request.log.yyyy_mm_dd");
		nrl.setFilenameDateFormat("yyyyMMdd");
		nrl.setRetainDays(90);
		nrl.setAppend(true);
		nrl.setExtended(false);
		nrl.setLogCookies(false);
		nrl.setLogTimeZone("GMT");

		RequestLogHandler reqlog = new RequestLogHandler();
		reqlog.setRequestLog(nrl);

		// Server's Handler collection
		HandlerCollection hc = new HandlerCollection();
		hc.setHandlers(new Handler[] { contexts, new DefaultHandler() });
		hc.addHandler(reqlog);

		// Server's thread pool
		QueuedThreadPool pool = new QueuedThreadPool();
		pool.setMinThreads(10);
		pool.setMaxThreads(200);
		pool.setDetailedDump(false);

		// Daemon to clean up the log directory on a daily basis
		Timer rolex = new Timer();
		rolex.scheduleAtFixedRate(new PurgeLogDirTask(), 0, 86400000L);	// run once per day

		// Start LogfileLoader
		LogfileLoader.getLoader();

		// The server itself
		server = new Server();
		server.setThreadPool(pool);
		server.setConnectors(new Connector[] { http, https });
		server.setHandler(hc);
		server.setStopAtShutdown(true);
		server.setSendServerVersion(true);
		server.setSendDateHeader(true);
		server.setGracefulShutdown(5000);	// allow 5 seconds for servlets to wrap up
		server.setDumpAfterStart(false);
		server.setDumpBeforeStop(false);

		server.start();
		server.join();
		logger.info("PROV0001 **** AT&T Data Router Provisioning Server halted.");
	}

	private static boolean checkDatabase(Logger logger) {
		DB db = new DB();
		return db.runRetroFits();
	}

	/**
	 * Stop the Jetty server.
	 */
	public static void shutdown() {
		new Thread() {
			@Override
			public void run() {
				try {
					server.stop();
					Thread.sleep(5000L);
					System.exit(0);
				} catch (Exception e) {
					// ignore
				}
			}
		}.start();
	}
}
