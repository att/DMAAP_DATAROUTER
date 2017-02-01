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
  * $Id: NodeMain.java,v 1.6 2014/02/10 20:53:07 agg Exp $
  */

package com.att.research.datarouter.node;

import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.util.ssl.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.nio.*;
import org.eclipse.jetty.server.ssl.*;
import org.apache.log4j.Logger;

/**
 *	The main starting point for the Data Router node
 */
public class NodeMain	{
	private NodeMain() {}
	private static Logger	logger = Logger.getLogger("com.att.research.datarouter.node.NodeMain");
	private static class wfconfig implements Runnable	{
		private NodeConfigManager ncm;
		public wfconfig(NodeConfigManager ncm) {
			this.ncm = ncm;
		}
		public synchronized void run() {
			notify();
		}
		public synchronized void waitforconfig() {
			ncm.registerConfigTask(this);
			while (!ncm.isConfigured()) {
				logger.info("NODE0003 Waiting for Node Configuration");
				try {
					wait();
				} catch (Exception e) {
				}
			}
			ncm.deregisterConfigTask(this);
			logger.info("NODE0004 Node Configuration Data Received");
		}
	}
	private static Delivery d;
	private static NodeConfigManager ncm;
	/**
	 *	Reset the retry timer for a subscription
	 */
	public static void resetQueue(String subid, String ip) {
		d.resetQueue(ncm.getSpoolDir(subid, ip));
	}
	/**
	 *	Start the data router.
	 *	<p>
	 *	The location of the node configuration file can be set using the
	 *	com.att.research.datarouter.node.ConfigFile system property.  By
	 *	default, it is "etc/node.properties".
	 */
	public static void main(String[] args) throws Exception {
		logger.info("NODE0001 Data Router Node Starting");
		IsFrom.setDNSCache();
		ncm = NodeConfigManager.getInstance();
		logger.info("NODE0002 I am " + ncm.getMyName());
		(new wfconfig(ncm)).waitforconfig();
		d = new Delivery(ncm);
		LogManager lm = new LogManager(ncm);
		Server server = new Server();
		SelectChannelConnector http = new SelectChannelConnector();
		http.setPort(ncm.getHttpPort());
		http.setMaxIdleTime(2000);
		http.setRequestHeaderSize(2048);
		SslSelectChannelConnector https = new SslSelectChannelConnector();
		https.setPort(ncm.getHttpsPort());
		https.setMaxIdleTime(30000);
		https.setRequestHeaderSize(8192);
		SslContextFactory cf = https.getSslContextFactory();
		
		/**Skip SSLv3 Fixes*/
		cf.addExcludeProtocols("SSLv3");
		logger.info("Excluded protocols node-"+cf.getExcludeProtocols());
		/**End of SSLv3 Fixes*/

		cf.setKeyStoreType(ncm.getKSType());
		cf.setKeyStorePath(ncm.getKSFile());
		cf.setKeyStorePassword(ncm.getKSPass());
		cf.setKeyManagerPassword(ncm.getKPass());
		server.setConnectors(new Connector[] { http, https });
		ServletContextHandler ctxt = new ServletContextHandler(0);
		ctxt.setContextPath("/");
		server.setHandler(ctxt);
		ctxt.addServlet(new ServletHolder(new NodeServlet()), "/*");
		logger.info("NODE0005 Data Router Node Activating Service");
		server.start();
		server.join();
	}
}
