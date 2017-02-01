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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.research.datarouter.provisioning.beans.EventLogRecord;
import com.att.research.datarouter.provisioning.beans.LogRecord;
import com.att.research.datarouter.provisioning.beans.Parameters;
import com.att.research.datarouter.provisioning.eelf.EelfMsgs;
import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.RLEBitSet;
import com.att.research.datarouter.provisioning.utils.LogfileLoader;

/**
 * <p>
 * This servlet handles requests to URLs under /internal on the provisioning server.
 * These include:
 * </p>
 * <div class="contentContainer">
 * <table class="packageSummary" border="0" cellpadding="3" cellspacing="0">
 * <caption><span>URL Path Summary</span><span class="tabEnd">&nbsp;</span></caption>
 * <tr>
 *   <th class="colFirst" width="15%">URL Path</th>
 *   <th class="colOne">Method</th>
 *   <th class="colLast">Purpose</th>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst">/internal/prov</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET a full JSON copy of the provisioning data.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colFirst">/internal/fetchProv</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to signal to a standby POD that the provisioning data should be fetched from the active POD.</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst" rowspan="2">/internal/logs</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET an index of log files and individual logs for this provisioning server.</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colOne">POST</td>
 *   <td class="colLast">used to POST log files from the individual nodes to this provisioning server.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colFirst" rowspan="4">/internal/api</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET an individual parameter value. The parameter name is specified by the path after /api/.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colOne">PUT</td>
 *   <td class="colLast">used to set an individual parameter value. The parameter name is specified by the path after /api/.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colOne">DELETE</td>
 *   <td class="colLast">used to remove an individual parameter value. The parameter name is specified by the path after /api/.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colOne">POST</td>
 *   <td class="colLast">used to create a new individual parameter value. The parameter name is specified by the path after /api/.</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst">/internal/halt</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to halt the server (must be accessed from 127.0.0.1).</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colFirst" rowspan="2">/internal/drlogs</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to get a list of DR log entries available for retrieval.
 *   Note: these are the actual data router log entries sent to the provisioning server
 *   by the nodes, not the provisioning server's internal logs (access via /internal/logs above).
 *   The range is returned as a list of record sequence numbers.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colOne">POST</td>
 *   <td class="colLast">used to retrieve specific log entries.
 *   The sequence numbers of the records to fetch are POST-ed; the records matching the sequence numbers are returned.</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst">/internal/route/*</td>
 *   <td class="colOne">*</td>
 *   <td class="colLast">URLs under this path are handled via the {@link com.att.research.datarouter.provisioning.RouteServlet}</td>
 * </tr>
 * </table>
 * </div>
 * <p>
 * Authorization to use these URLs is a little different than for other URLs on the provisioning server.
 * For the most part, the IP address that the request comes from should be either:
 * </p>
 * <ol>
 * <li>an IP address of a provisioning server, or</li>
 * <li>the IP address of a node (to allow access to /internal/prov), or</li>
 * <li>an IP address from the "<i>special subnet</i>" which is configured with
 * the PROV_SPECIAL_SUBNET parameter.
 * </ol>
 * <p>
 * In addition, requests to /internal/halt can ONLY come from localhost (127.0.0.1) on the HTTP port.
 * </p>
 * <p>
 * All DELETE/GET/PUT/POST requests made to /internal/api on this servlet on the standby server are
 * proxied to the active server (using the {@link ProxyServlet}) if it is up and reachable.
 * </p>
 *
 * @author Robert Eby
 * @version $Id: InternalServlet.java,v 1.23 2014/03/24 18:47:10 eby Exp $
 */
@SuppressWarnings("serial")
public class InternalServlet extends ProxyServlet {
	private static Integer logseq = new Integer(0); // another piece of info to make log spool file names unique
	//Adding EELF Logger Rally:US664892 
    private static EELFLogger eelflogger = EELFManager.getInstance().getLogger("com.att.research.datarouter.provisioning.InternalServlet");

	/**
	 * Delete a parameter at the address /internal/api/&lt;parameter&gt;.
	 * See the <b>Internal API</b> document for details on how this method should be invoked.
	 */
	@Override
	public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doDelete");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader(BEHALF_HEADER),getIdFromPath(req)+"");
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}

		String path = req.getPathInfo();
		if (path.startsWith("/api/")) {
			if (isProxyOK(req) && isProxyServer()) {
				super.doDelete(req, resp);
				return;
			}
			String key = path.substring(5);
			if (key.length() > 0) {
				Parameters param = Parameters.getParameter(key);
				if (param != null) {
					if (doDelete(param)) {
						elr.setResult(HttpServletResponse.SC_OK);
						eventlogger.info(elr);
						resp.setStatus(HttpServletResponse.SC_OK);
						provisioningDataChanged();
						provisioningParametersChanged();
					} else {
						// Something went wrong with the DELETE
						elr.setResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
						eventlogger.info(elr);
						resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DB_PROBLEM_MSG);
					}
					return;
				}
			}
		}
		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
	}
	/**
	 * Get some information (such as a parameter) underneath the /internal/ namespace.
	 * See the <b>Internal API</b> document for details on how this method should be invoked.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doGet");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader(BEHALF_HEADER),getIdFromPath(req)+"");
		String path = req.getPathInfo();
		if (path.equals("/halt") && !req.isSecure()) {
			// request to halt the server - can ONLY come from localhost
			String remote = req.getRemoteAddr();
			if (remote.equals("127.0.0.1")) {
				intlogger.info("PROV0009 Request to HALT received.");
				resp.setStatus(HttpServletResponse.SC_OK);
				Main.shutdown();
			} else {
				intlogger.info("PROV0010 Disallowed request to HALT received from "+remote);
				resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			}
			return;
		}

		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}
		if (path.equals("/fetchProv") && !req.isSecure()) {
			// if request came from active_pod or standby_pod and it is not us, reload prov data
			SynchronizerTask s = SynchronizerTask.getSynchronizer();
			s.doFetch();
			resp.setStatus(HttpServletResponse.SC_OK);
			return;
		}
		if (path.equals("/prov")) {
			if (isProxyOK(req) && isProxyServer()) {
				if (super.doGetWithFallback(req, resp))
					return;
				// fall back to returning the local data if the remote is unreachable
				intlogger.info("Active server unavailable; falling back to local copy.");
			}
			Poker p = Poker.getPoker();
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType(PROVFULL_CONTENT_TYPE2);
			resp.getOutputStream().print(p.getProvisioningString());
			return;
		}
		if (path.equals("/logs") || path.equals("/logs/")) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.getOutputStream().print(generateLogfileList().toString());
			return;
		}
		if (path.startsWith("/logs/")) {
			Properties p = (new DB()).getProperties();
			String logdir = p.getProperty("com.att.research.datarouter.provserver.accesslog.dir");
			String logfile = path.substring(6);
			if (logdir != null && logfile != null && logfile.indexOf('/') < 0) {
				File log = new File(logdir + "/" + logfile);
				if (log.exists() && log.isFile()) {
					resp.setStatus(HttpServletResponse.SC_OK);
					resp.setContentType("text/plain");
					Path logpath = Paths.get(log.getAbsolutePath());
					Files.copy(logpath, resp.getOutputStream());
					return;
				}
			}
			resp.sendError(HttpServletResponse.SC_NO_CONTENT, "No file.");
			return;
		}
		if (path.startsWith("/api/")) {
			if (isProxyOK(req) && isProxyServer()) {
				super.doGet(req, resp);
				return;
			}
			String key = path.substring(5);
			if (key.length() > 0) {
				Parameters param = Parameters.getParameter(key);
				if (param != null) {
					resp.setStatus(HttpServletResponse.SC_OK);
					resp.setContentType("text/plain");
					resp.getOutputStream().print(param.getValue() + "\n");
					return;
				}
			}
		}
		if (path.equals("/drlogs") || path.equals("/drlogs/")) {
			// Special POD <=> POD API to determine what log file records are loaded here
			LogfileLoader lfl = LogfileLoader.getLoader();
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("text/plain");
			resp.getOutputStream().print(lfl.getBitSet().toString());
			return;
		}
		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
	}
	/**
	 * Modify a parameter at the address /internal/api/&lt;parameter&gt;.
	 * See the <b>Internal API</b> document for details on how this method should be invoked.
	 */
	@Override
	public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doPut");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader(BEHALF_HEADER),getIdFromPath(req)+"");
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}
		String path = req.getPathInfo();
		if (path.startsWith("/api/")) {
			if (isProxyOK(req) && isProxyServer()) {
				super.doPut(req, resp);
				return;
			}
			String key = path.substring(5);
			if (key.length() > 0) {
				Parameters param = Parameters.getParameter(key);
				if (param != null) {
					String t = catValues(req.getParameterValues("val"));
					param.setValue(t);
					if (doUpdate(param)) {
						elr.setResult(HttpServletResponse.SC_OK);
						eventlogger.info(elr);
						resp.setStatus(HttpServletResponse.SC_OK);
						provisioningDataChanged();
						provisioningParametersChanged();
					} else {
						// Something went wrong with the UPDATE
						elr.setResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
						eventlogger.info(elr);
						resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DB_PROBLEM_MSG);
					}
					return;
				}
			}
		}
		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
	}
	/**
	 * Create some new information (such as a parameter or log entries) underneath the /internal/ namespace.
	 * See the <b>Internal API</b> document for details on how this method should be invoked.
	 */
	@SuppressWarnings("resource")
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doPost");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF, req.getHeader(BEHALF_HEADER));
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}

		String path = req.getPathInfo();
		if (path.startsWith("/api/")) {
			if (isProxyOK(req) && isProxyServer()) {
				super.doPost(req, resp);
				return;
			}
			String key = path.substring(5);
			if (key.length() > 0) {
				Parameters param = Parameters.getParameter(key);
				if (param == null) {
					String t = catValues(req.getParameterValues("val"));
					param = new Parameters(key, t);
					if (doInsert(param)) {
						elr.setResult(HttpServletResponse.SC_OK);
						eventlogger.info(elr);
						resp.setStatus(HttpServletResponse.SC_OK);
						provisioningDataChanged();
						provisioningParametersChanged();
					} else {
						// Something went wrong with the INSERT
						elr.setResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
						eventlogger.info(elr);
						resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DB_PROBLEM_MSG);
					}
					return;
				}
			}
		}

		if (path.equals("/logs") || path.equals("/logs/")) {
			String ctype = req.getHeader("Content-Type");
			if (ctype == null || !ctype.equals("text/plain")) {
				elr.setResult(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				elr.setMessage("Bad media type: "+ctype);
				resp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				eventlogger.info(elr);
				return;
			}
			String spooldir = (new DB()).getProperties().getProperty("com.att.research.datarouter.provserver.spooldir");
			String spoolname = String.format("%d-%d-", System.currentTimeMillis(), Thread.currentThread().getId());
			synchronized (logseq) {
				// perhaps unnecessary, but it helps make the name unique
				spoolname += logseq.toString();
				logseq++;
			}
			String encoding = req.getHeader("Content-Encoding");
			if (encoding != null) {
				if (encoding.trim().equals("gzip")) {
					spoolname += ".gz";
				} else {
					elr.setResult(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
					resp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
					eventlogger.info(elr);
					return;
				}
			}
			// Determine space available -- available space must be at least 5%
			FileSystem fs = (Paths.get(spooldir)).getFileSystem();
			long total = 0;
			long avail = 0;
			for (FileStore store: fs.getFileStores()) {
				total += store.getTotalSpace();
				avail += store.getUsableSpace();
			}
			try { fs.close(); } catch (Exception e) { }
			if (((avail * 100) / total) < 5) {
				elr.setResult(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				eventlogger.info(elr);
				return;
			}
			Path tmppath = Paths.get(spooldir, spoolname);
			Path donepath = Paths.get(spooldir, "IN."+spoolname);
			Files.copy(req.getInputStream(), Paths.get(spooldir, spoolname), StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmppath, donepath, StandardCopyOption.REPLACE_EXISTING);
			elr.setResult(HttpServletResponse.SC_CREATED);
			resp.setStatus(HttpServletResponse.SC_CREATED);
			eventlogger.info(elr);
			LogfileLoader.getLoader();	// This starts the logfile loader "task"
			return;
		}

		if (path.equals("/drlogs") || path.equals("/drlogs/")) {
			// Receive post request and generate log entries
			String ctype = req.getHeader("Content-Type");
			if (ctype == null || !ctype.equals("text/plain")) {
				elr.setResult(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				elr.setMessage("Bad media type: "+ctype);
				resp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				eventlogger.info(elr);
				return;
			}
			InputStream is = req.getInputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int ch = 0;
			while ((ch = is.read()) >= 0)
				bos.write(ch);
			RLEBitSet bs = new RLEBitSet(bos.toString());	// The set of records to retrieve
			elr.setResult(HttpServletResponse.SC_OK);
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("text/plain");
			LogRecord.printLogRecords(resp.getOutputStream(), bs);
			eventlogger.info(elr);
			return;
		}

		elr.setResult(HttpServletResponse.SC_NOT_FOUND);
		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
		eventlogger.info(elr);
	}

	private String catValues(String[] v) {
		StringBuilder sb = new StringBuilder();
		if (v != null) {
			String pfx = "";
			for (String s : v) {
				sb.append(pfx);
				sb.append(s);
				pfx = "|";
			}
		}
		return sb.toString();
	}
	private JSONArray generateLogfileList() {
		JSONArray ja = new JSONArray();
		Properties p = (new DB()).getProperties();
		String s = p.getProperty("com.att.research.datarouter.provserver.accesslog.dir");
		if (s != null) {
			String[] dirs = s.split(",");
			for (String dir : dirs) {
				File f = new File(dir);
				String[] list = f.list();
				if (list != null) {
					for (String s2 : list) {
						if (!s2.startsWith("."))
							ja.put(s2);
					}
				}
			}
		}
		return ja;
	}
}
