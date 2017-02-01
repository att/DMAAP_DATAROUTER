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

import java.io.IOException;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.att.research.datarouter.provisioning.beans.Deleteable;
import com.att.research.datarouter.provisioning.beans.EgressRoute;
import com.att.research.datarouter.provisioning.beans.EventLogRecord;
import com.att.research.datarouter.provisioning.beans.IngressRoute;
import com.att.research.datarouter.provisioning.beans.Insertable;
import com.att.research.datarouter.provisioning.beans.NetworkRoute;
import com.att.research.datarouter.provisioning.beans.NodeClass;

/**
 * <p>
 * This servlet handles requests to URLs under /internal/route/ on the provisioning server.
 * This part of the URL tree is used to manipulate the Data Router routing tables.
 * These include:
 * </p>
 * <div class="contentContainer">
 * <table class="packageSummary" border="0" cellpadding="3" cellspacing="0">
 * <caption><span>URL Path Summary</span><span class="tabEnd">&nbsp;</span></caption>
 * <tr>
 *   <th class="colFirst" width="35%">URL Path</th>
 *   <th class="colOne">Method</th>
 *   <th class="colLast">Purpose</th>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst">/internal/route/</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET a full JSON copy of all three routing tables.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colFirst" rowspan="2">/internal/route/ingress/</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET a full JSON copy of the ingress routing table (IRT).</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colOne">POST</td>
 *   <td class="colLast">used to create a new entry in the ingress routing table (IRT).</td></tr>
 * <tr class="altColor">
 *   <td class="colFirst" rowspan="2">/internal/route/egress/</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET a full JSON copy of the egress routing table (ERT).</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colOne">POST</td>
 *   <td class="colLast">used to create a new entry in the egress routing table (ERT).</td></tr>
 * <tr class="rowColor">
 *   <td class="colFirst" rowspan="2">/internal/route/network/</td>
 *   <td class="colOne">GET</td>
 *   <td class="colLast">used to GET a full JSON copy of the network routing table (NRT).</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colOne">POST</td>
 *   <td class="colLast">used to create a new entry in the network routing table (NRT).</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst">/internal/route/ingress/&lt;feed&gt;/&lt;user&gt;/&lt;subnet&gt;</td>
 *   <td class="colOne">DELETE</td>
 *   <td class="colLast">used to DELETE the ingress route corresponding to <i>feed</i>, <i>user</i> and <i>subnet</i>.
 *   The / in the subnet specified should be replaced with a !, since / cannot be used in a URL.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colFirst">/internal/route/ingress/&lt;seq&gt;</td>
 *   <td class="colOne">DELETE</td>
 *   <td class="colLast">used to DELETE all ingress routes with the matching <i>seq</i> sequence number.</td>
 * </tr>
 * <tr class="altColor">
 *   <td class="colFirst">/internal/route/egress/&lt;sub&gt;</td>
 *   <td class="colOne">DELETE</td>
 *   <td class="colLast">used to DELETE the egress route the matching <i>sub</i> subscriber number.</td>
 * </tr>
 * <tr class="rowColor">
 *   <td class="colFirst">/internal/route/network/&lt;fromnode&gt;/&lt;tonode&gt;</td>
 *   <td class="colOne">DELETE</td>
 *   <td class="colLast">used to DELETE the network route corresponding to <i>fromnode</i>
 *   and <i>tonode</i>.</td>
 * </tr>
 * </table>
 * <p>
 * Authorization to use these URLs is a little different than for other URLs on the provisioning server.
 * For the most part, the IP address that the request comes from should be either:
 * </p>
 * <ol>
 * <li>an IP address of a provisioning server, or</li>
 * <li>the IP address of a node, or</li>
 * <li>an IP address from the "<i>special subnet</i>" which is configured with
 * the PROV_SPECIAL_SUBNET parameter.
 * </ol>
 * <p>
 * All DELETE/GET/POST requests made to this servlet on the standby server are proxied to the
 * active server (using the {@link ProxyServlet}) if it is up and reachable.
 * </p>
 *
 * @author Robert Eby
 * @version $Id$
 */
@SuppressWarnings("serial")
public class RouteServlet extends ProxyServlet {
	/**
	 * DELETE route table entries by deleting part of the route table tree.
	 */
	@Override
	public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}
		if (isProxyOK(req) && isProxyServer()) {
			super.doDelete(req, resp);
			return;
		}

		String path = req.getPathInfo();
		String[] parts = path.substring(1).split("/");
		Deleteable[] d = null;
		if (parts[0].equals("ingress")) {
			if (parts.length == 4) {
				// /internal/route/ingress/<feed>/<user>/<subnet>
				try {
					int feedid = Integer.parseInt(parts[1]);
					IngressRoute er = IngressRoute.getIngressRoute(feedid, parts[2], parts[3].replaceAll("!", "/"));
					if (er == null) {
						resp.sendError(HttpServletResponse.SC_NOT_FOUND, "The specified ingress route does not exist.");
						return;
					}
					d = new Deleteable[] { er };
				} catch (NumberFormatException e) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid feed ID in 'delete ingress' command.");
					return;
				}
			} else if (parts.length == 2) {
				// /internal/route/ingress/<seq>
				try {
					int seq = Integer.parseInt(parts[1]);
					Set<IngressRoute> set = IngressRoute.getIngressRoutesForSeq(seq);
					d = set.toArray(new Deleteable[0]);
				} catch (NumberFormatException e) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid sequence number in 'delete ingress' command.");
					return;
				}
			} else {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid number of arguments in 'delete ingress' command.");
				return;
			}
		} else if (parts[0].equals("egress")) {
			if (parts.length == 2) {
				// /internal/route/egress/<sub>
				try {
					int subid = Integer.parseInt(parts[1]);
					EgressRoute er = EgressRoute.getEgressRoute(subid);
					if (er == null) {
						resp.sendError(HttpServletResponse.SC_NOT_FOUND, "The specified egress route does not exist.");
						return;
					}
					d = new Deleteable[] { er };
				} catch (NumberFormatException e) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid sub ID in 'delete egress' command.");
					return;
				}
			} else {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid number of arguments in 'delete egress' command.");
				return;
			}
		} else if (parts[0].equals("network")) {
			if (parts.length == 3) {
				// /internal/route/network/<from>/<to>
				try {//
					NetworkRoute nr = new NetworkRoute(
						NodeClass.normalizeNodename(parts[1]),
						NodeClass.normalizeNodename(parts[2])
					);
					d = new Deleteable[] { nr };
				} catch (IllegalArgumentException e) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "The specified network route does not exist.");
					return;
				}
			} else {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid number of arguments in 'delete network' command.");
				return;
			}
		}
		if (d == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
			return;
		}
		boolean rv = true;
		for (Deleteable dd : d) {
			rv &= doDelete(dd);
		}
		if (rv) {
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
	}
	/**
	 * GET route table entries from the route table tree specified by the URL path.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}
		if (isProxyOK(req) && isProxyServer()) {
			super.doGet(req, resp);
			return;
		}

		String path = req.getPathInfo();
		if (!path.endsWith("/"))
			path += "/";
		if (!path.equals("/") && !path.equals("/ingress/") && !path.equals("/egress/") && !path.equals("/network/")) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
			return;
		}

		StringBuilder sb = new StringBuilder("{\n");
		String px2 = "";
		if (path.equals("/") || path.equals("/ingress/")) {
			String pfx = "\n";
			sb.append("\"ingress\": [");
			for (IngressRoute in : IngressRoute.getAllIngressRoutes()) {
				sb.append(pfx);
				sb.append(in.asJSONObject().toString());
				pfx = ",\n";
			}
			sb.append("\n]");
			px2 = ",\n";
		}

		if (path.equals("/") || path.equals("/egress/")) {
			String pfx = "\n";
			sb.append(px2);
			sb.append("\"egress\": {");
			for (EgressRoute eg : EgressRoute.getAllEgressRoutes()) {
				JSONObject jx = eg.asJSONObject();
				for (String key : jx.keySet()) {
					sb.append(pfx);
					sb.append("  \"").append(key).append("\": ");
					sb.append("\"").append(jx.getString(key)).append("\"");
					pfx = ",\n";
				}
			}
			sb.append("\n}");
			px2 = ",\n";
		}

		if (path.equals("/") || path.equals("/network/")) {
			String pfx = "\n";
			sb.append(px2);
			sb.append("\"routing\": [");
			for (NetworkRoute ne : NetworkRoute.getAllNetworkRoutes()) {
				sb.append(pfx);
				sb.append(ne.asJSONObject().toString());
				pfx = ",\n";
			}
			sb.append("\n]");
		}
		sb.append("}\n");
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.getOutputStream().print(sb.toString());
	}
	/**
	 * PUT on &lt;/internal/route/*&gt; -- not supported.
	 */
	@Override
	public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}
		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
	}
	/**
	 * POST - modify existing route table entries in the route table tree specified by the URL path.
	 */
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		EventLogRecord elr = new EventLogRecord(req);
		if (!isAuthorizedForInternal(req)) {
			elr.setMessage("Unauthorized.");
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized.");
			return;
		}
		if (isProxyOK(req) && isProxyServer()) {
			super.doPost(req, resp);
			return;
		}
		String path = req.getPathInfo();
		Insertable[] ins = null;
		if (path.startsWith("/ingress/")) {
			// /internal/route/ingress/?feed=%s&amp;user=%s&amp;subnet=%s&amp;nodepatt=%s
			try {
				// Although it probably doesn't make sense, you can install two identical routes in the IRT
				int feedid = Integer.parseInt(req.getParameter("feed"));
				String user = req.getParameter("user");
				if (user == null)
					user = "-";
				String subnet = req.getParameter("subnet");
				if (subnet == null)
					subnet = "-";
				String nodepatt = req.getParameter("nodepatt");
				String t = req.getParameter("seq");
				int seq = (t != null) ? Integer.parseInt(t) : (IngressRoute.getMaxSequence() + 100);
				ins = new Insertable[] { new IngressRoute(seq, feedid, user, subnet, NodeClass.lookupNodeNames(nodepatt)) };
			} catch (Exception e) {
				intlogger.info(e);
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid arguments in 'add ingress' command.");
				return;
			}
		} else if (path.startsWith("/egress/")) {
			// /internal/route/egress/?sub=%s&amp;node=%s
			try {
				int subid = Integer.parseInt(req.getParameter("sub"));
				EgressRoute er = EgressRoute.getEgressRoute(subid);
				if (er != null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "An egress route already exists for that subscriber.");
					return;
				}
				String node = NodeClass.normalizeNodename(req.getParameter("node"));
				ins = new Insertable[] { new EgressRoute(subid, node) };
			} catch (Exception e) {
				intlogger.info(e);
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid arguments in 'add egress' command.");
				return;
			}
		} else if (path.startsWith("/network/")) {
			// /internal/route/network/?from=%s&amp;to=%s&amp;via=%s
			try {
				String nfrom = req.getParameter("from");
				String nto   = req.getParameter("to");
				String nvia  = req.getParameter("via");
				if (nfrom == null || nto == null || nvia == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing arguments in 'add network' command.");
					return;
				}
				nfrom = NodeClass.normalizeNodename(nfrom);
				nto   = NodeClass.normalizeNodename(nto);
				nvia  = NodeClass.normalizeNodename(nvia);
				NetworkRoute nr = new NetworkRoute(nfrom, nto, nvia);
				for (NetworkRoute route : NetworkRoute.getAllNetworkRoutes()) {
					if (route.getFromnode() == nr.getFromnode() && route.getTonode() == nr.getTonode()) {
						resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Network route table already contains a route for "+nfrom+" and "+nto);
						return;
					}
				}
				ins = new Insertable[] { nr };
			} catch (IllegalArgumentException e) {
				intlogger.info(e);
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid arguments in 'add network' command.");
				return;
			}
		}
		if (ins == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Bad URL.");
			return;
		}
		boolean rv = true;
		for (Insertable dd : ins) {
			rv &= doInsert(dd);
		}
		if (rv) {
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
	}
}
