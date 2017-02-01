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
  * $Id: NodeServlet.java,v 1.14 2014/02/10 20:53:07 agg Exp $
  */

package com.att.research.datarouter.node;

import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.file.*;
import org.apache.log4j.Logger;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.research.datarouter.node.eelf.EelfMsgs;

import java.net.*;

/**
 *	Servlet for handling all http and https requests to the data router node
 *	<p>
 *	Handled requests are:
 *	<br>
 *	GET http://<i>node</i>/internal/fetchProv - fetch the provisioning data
 *	<br>
 *	PUT/DELETE https://<i>node</i>/internal/publish/<i>fileid</i> - n2n transfer
 *	<br>
 *	PUT/DELETE https://<i>node</i>/publish/<i>feedid</i>/<i>fileid</i> - publsh request
 */
public class NodeServlet extends HttpServlet	{
	private static Logger logger = Logger.getLogger("com.att.research.datarouter.node.NodeServlet");
	private static NodeConfigManager	config;
	private static Pattern	MetaDataPattern;
	private static SubnetMatcher internalsubnet = new SubnetMatcher("135.207.136.128/25");
	//Adding EELF Logger Rally:US664892  
    private static EELFLogger eelflogger = EELFManager.getInstance().getLogger("com.att.research.datarouter.node.NodeServlet");

	static {
		try {
			String ws = "\\s*";
			// assume that \\ and \" have been replaced by X
			String string = "\"[^\"]*\"";
			//String string = "\"(?:[^\"\\\\]|\\\\.)*\"";
			String number = "[+-]?(?:\\.\\d+|(?:0|[1-9]\\d*)(?:\\.\\d*)?)(?:[eE][+-]?\\d+)?";
			String value = "(?:" + string + "|" + number + "|null|true|false)";
			String item = string + ws + ":" + ws + value + ws;
			String object = ws + "\\{" + ws + "(?:" + item + "(?:" + "," + ws + item + ")*)?\\}" + ws;
			MetaDataPattern = Pattern.compile(object, Pattern.DOTALL);
		} catch (Exception e) {
		}
	}
	/**
	 *	Get the NodeConfigurationManager
	 */
	public void init() {
		config = NodeConfigManager.getInstance();
		logger.info("NODE0101 Node Servlet Configured");
	}
	private boolean down(HttpServletResponse resp) throws IOException {
		if (config.isShutdown() || !config.isConfigured()) {
			resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			logger.info("NODE0102 Rejecting request: Service is being quiesced");
			return(true);
		}
		return(false);
	}
	/**
	 *	Handle a GET for /internal/fetchProv
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		NodeUtils.setIpAndFqdnForEelf("doGet");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader("X-ATT-DR-ON-BEHALF-OF"),getIdFromPath(req)+"");
		if (down(resp)) {
			return;
		}
		String path = req.getPathInfo();
		String qs = req.getQueryString();
		String ip = req.getRemoteAddr();
		if (qs != null) {
			path = path + "?" + qs;
		}
		if ("/internal/fetchProv".equals(path)) {
			config.gofetch(ip);
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		} else if (path.startsWith("/internal/resetSubscription/")) {
			String subid = path.substring(28);
			if (subid.length() != 0 && subid.indexOf('/') == -1) {
				NodeMain.resetQueue(subid, ip);
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				return;
			}
		}
		if (internalsubnet.matches(NodeUtils.getInetAddress(ip))) {
			if (path.startsWith("/internal/logs/")) {
				String f = path.substring(15);
				File fn = new File(config.getLogDir() + "/" + f);
				if (f.indexOf('/') != -1 || !fn.isFile()) {
					logger.info("NODE0103 Rejecting invalid GET of " + path + " from " + ip);
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				byte[] buf = new byte[65536];
				resp.setContentType("text/plain");
				resp.setContentLength((int)fn.length());
				resp.setStatus(200);
				InputStream is = new FileInputStream(fn);
				OutputStream os = resp.getOutputStream();
				int i;
				while ((i = is.read(buf)) > 0) {
					os.write(buf, 0, i);
				}
				is.close();
				return;
			}
			if (path.startsWith("/internal/rtt/")) {
				String xip = path.substring(14);
				long st = System.currentTimeMillis();
				String status = " unknown";
				try {
					Socket s = new Socket(xip, 443);
					s.close();
					status = " connected";
				} catch (Exception e) {
					status = " error " + e.toString();
				}
				long dur = System.currentTimeMillis() - st;
				resp.setContentType("text/plain");
				resp.setStatus(200);
				byte[] buf = (dur + status + "\n").getBytes();
				resp.setContentLength(buf.length);
				resp.getOutputStream().write(buf);
				return;
			}
		}
		logger.info("NODE0103 Rejecting invalid GET of " + path + " from " + ip);
		resp.sendError(HttpServletResponse.SC_NOT_FOUND);
		return;
	}
	/**
	 *	Handle all PUT requests
	 */
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		NodeUtils.setIpAndFqdnForEelf("doPut");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader("X-ATT-DR-ON-BEHALF-OF"),getIdFromPath(req)+"");
		common(req, resp, true);
	}
	/**
	 *	Handle all DELETE requests
	 */
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		NodeUtils.setIpAndFqdnForEelf("doDelete");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader("X-ATT-DR-ON-BEHALF-OF"),getIdFromPath(req)+"");
		common(req, resp, false);
	}
	private void common(HttpServletRequest req, HttpServletResponse resp, boolean isput) throws ServletException, IOException {
		if (down(resp)) {
			return;
		}
		if (!req.isSecure()) {
			logger.info("NODE0104 Rejecting insecure PUT or DELETE of " + req.getPathInfo() + " from " + req.getRemoteAddr());
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "https required on publish requests");
			return;
		}
		String fileid = req.getPathInfo();
		if (fileid == null) {
			logger.info("NODE0105 Rejecting bad URI for PUT or DELETE of " + req.getPathInfo() + " from " + req.getRemoteAddr());
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid request URI.  Expecting <feed-publishing-url>/<fileid>.");
			return;
		}
		String feedid = null;
		String user = null;
		String credentials = req.getHeader("Authorization");
		if (credentials == null) {
			logger.info("NODE0106 Rejecting unauthenticated PUT or DELETE of " + req.getPathInfo() + " from " + req.getRemoteAddr());
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Authorization header required");
			return;
		}
		String ip = req.getRemoteAddr();
		String lip = req.getLocalAddr();
		String pubid = null;
		String xpubid = null;
		String rcvd = NodeUtils.logts(System.currentTimeMillis()) + ";from=" + ip + ";by=" + lip;
		Target[]	targets = null;
		if (fileid.startsWith("/publish/")) {
			fileid = fileid.substring(9);
			int i = fileid.indexOf('/');
			if (i == -1 || i == fileid.length() - 1) {
				logger.info("NODE0105 Rejecting bad URI for PUT or DELETE of " + req.getPathInfo() + " from " + req.getRemoteAddr());
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid request URI.  Expecting <feed-publishing-url>/<fileid>.  Possible missing fileid.");
				return;
			}
			feedid = fileid.substring(0, i);
			fileid = fileid.substring(i + 1);
			pubid = config.getPublishId();
			xpubid = req.getHeader("X-ATT-DR-PUBLISH-ID");
			targets = config.getTargets(feedid);
		} else if (fileid.startsWith("/internal/publish/")) {
			if (!config.isAnotherNode(credentials, ip)) {
				logger.info("NODE0107 Rejecting unauthorized node-to-node transfer attempt from " + ip);
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			fileid = fileid.substring(18);
			pubid = req.getHeader("X-ATT-DR-PUBLISH-ID");
			targets = config.parseRouting(req.getHeader("X-ATT-DR-ROUTING"));
		} else {
			logger.info("NODE0105 Rejecting bad URI for PUT or DELETE of " + req.getPathInfo() + " from " + req.getRemoteAddr());
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid request URI.  Expecting <feed-publishing-url>/<fileid>.");
			return;
		}
		if (fileid.indexOf('/') != -1) {
			logger.info("NODE0105 Rejecting bad URI for PUT or DELETE of " + req.getPathInfo() + " from " + req.getRemoteAddr());
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid request URI.  Expecting <feed-publishing-url>/<fileid>.");
			return;
		}
		String qs = req.getQueryString();
		if (qs != null) {
			fileid = fileid + "?" + qs;
		}
		String hp = config.getMyName();
		int xp = config.getExtHttpsPort();
		if (xp != 443) {
			hp = hp + ":" + xp;
		}
		String logurl = "https://" + hp + "/internal/publish/" + fileid;
		if (feedid != null) {
			logurl = "https://" + hp + "/publish/" + feedid + "/" + fileid;
			String reason = config.isPublishPermitted(feedid, credentials, ip);
			if (reason != null) {
				logger.info("NODE0111 Rejecting unauthorized publish attempt to feed " + feedid + " fileid " + fileid + " from " + ip + " reason " + reason);
				resp.sendError(HttpServletResponse.SC_FORBIDDEN,reason);
				return;
			}
			user = config.getAuthUser(feedid, credentials);
			String newnode = config.getIngressNode(feedid, user, ip);
			if (newnode != null) {
				String port = "";
				int iport = config.getExtHttpsPort();
				if (iport != 443) {
					port = ":" + iport;
				}
				String redirto = "https://" + newnode + port + "/publish/" + feedid + "/" + fileid;
				logger.info("NODE0108 Redirecting publish attempt for feed " + feedid + " user " + user + " ip " + ip + " to " + redirto);
				resp.sendRedirect(redirto);
				return;
			}
			resp.setHeader("X-ATT-DR-PUBLISH-ID", pubid);
		}
		String fbase = config.getSpoolDir() + "/" + pubid;
		File data = new File(fbase);
		File meta = new File(fbase + ".M");
		OutputStream dos = null;
		Writer mw = null;
		InputStream is = null;
		try {
			StringBuffer mx = new StringBuffer();
			mx.append(req.getMethod()).append('\t').append(fileid).append('\n');
			Enumeration hnames = req.getHeaderNames();
			String ctype = null;
			while (hnames.hasMoreElements()) {
				String hn = (String)hnames.nextElement();
				String hnlc = hn.toLowerCase();
				if ((isput && ("content-type".equals(hnlc) ||
				    "content-language".equals(hnlc) ||
				    "content-md5".equals(hnlc) ||
				    "content-range".equals(hnlc))) ||
				    "x-att-dr-meta".equals(hnlc) ||
				    (feedid == null && "x-att-dr-received".equals(hnlc)) ||
				    (hnlc.startsWith("x-") && !hnlc.startsWith("x-att-dr-"))) {
					Enumeration hvals = req.getHeaders(hn);
					while (hvals.hasMoreElements()) {
						String hv = (String)hvals.nextElement();
						if ("content-type".equals(hnlc)) {
							ctype = hv;
						}
						if ("x-att-dr-meta".equals(hnlc)) {
							if (hv.length() > 4096) {
								logger.info("NODE0109 Rejecting publish attempt with metadata too long for feed " + feedid + " user " + user + " ip " + ip);
								resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Metadata too long");
								return;
							}
							if (!MetaDataPattern.matcher(hv.replaceAll("\\\\.", "X")).matches()) {
								logger.info("NODE0109 Rejecting publish attempt with malformed metadata for feed " + feedid + " user " + user + " ip " + ip);
								resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed metadata");
								return;
							}
						}
						mx.append(hn).append('\t').append(hv).append('\n');
					}
				}
			}
			mx.append("X-ATT-DR-RECEIVED\t").append(rcvd).append('\n');
			String metadata = mx.toString();
			byte[] buf = new byte[1024 * 1024];
			int i;
			try {
				is = req.getInputStream();
				dos = new FileOutputStream(data);
				while ((i = is.read(buf)) > 0) {
					dos.write(buf, 0, i);
				}
				is.close();
				is = null;
				dos.close();
				dos = null;
			} catch (IOException ioe) {
				long exlen = -1;
				try {
					exlen = Long.parseLong(req.getHeader("Content-Length"));
				} catch (Exception e) {
				}
				StatusLog.logPubFail(pubid, feedid, logurl, req.getMethod(), ctype, exlen, data.length(), ip, user, ioe.getMessage());
				throw ioe;
			}
			Path dpath = Paths.get(fbase);
			for (Target t: targets) {
				DestInfo di = t.getDestInfo();
				if (di == null) {
					// TODO: unknown destination
					continue;
				}
				String dbase = di.getSpool() + "/" + pubid;
				Files.createLink(Paths.get(dbase), dpath);
				mw = new FileWriter(meta);
				mw.write(metadata);
				if (di.getSubId() == null) {
					mw.write("X-ATT-DR-ROUTING\t" + t.getRouting() + "\n");
				}
				mw.close();
				meta.renameTo(new File(dbase + ".M"));
			}
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.getOutputStream().close();
			StatusLog.logPub(pubid, feedid, logurl, req.getMethod(), ctype, data.length(), ip, user, HttpServletResponse.SC_NO_CONTENT);
		} catch (IOException ioe) {
			logger.info("NODE0110 IO Exception receiving publish attempt for feed " + feedid + " user " + user + " ip " + ip + " " + ioe.toString(), ioe);
			throw ioe;
		} finally {
			if (is != null) { try { is.close(); } catch (Exception e) {}}
			if (dos != null) { try { dos.close(); } catch (Exception e) {}}
			if (mw != null) { try { mw.close(); } catch (Exception e) {}}
			try { data.delete(); } catch (Exception e) {}
			try { meta.delete(); } catch (Exception e) {}
		}
	}
	
	private int getIdFromPath(HttpServletRequest req) {
		String path = req.getPathInfo();
		if (path == null || path.length() < 2)
			return -1;
		try {
			return Integer.parseInt(path.substring(1));
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
