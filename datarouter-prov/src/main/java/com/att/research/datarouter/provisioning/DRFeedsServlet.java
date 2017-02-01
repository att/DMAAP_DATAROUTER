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
import java.io.InvalidObjectException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.research.datarouter.authz.AuthorizationResponse;
import com.att.research.datarouter.provisioning.beans.EventLogRecord;
import com.att.research.datarouter.provisioning.beans.Feed;
import com.att.research.datarouter.provisioning.eelf.EelfMsgs;
import com.att.research.datarouter.provisioning.utils.JSONUtilities;

/**
 * This servlet handles provisioning for the &lt;drFeedsURL&gt; which is the URL on the
 * provisioning server used to create new feeds.  It supports POST to create new feeds,
 * and GET to support the Feeds Collection Query function.
 *
 * @author Robert Eby
 * @version $Id$
 */
@SuppressWarnings("serial")
public class DRFeedsServlet extends ProxyServlet {
	//Adding EELF Logger Rally:US664892  
    private static EELFLogger eelflogger = EELFManager.getInstance().getLogger("com.att.research.datarouter.provisioning.DRFeedsServlet");
    
	/**
	 * DELETE on the &lt;drFeedsURL&gt; -- not supported.
	 */
	@Override
	public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doDelete");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader(BEHALF_HEADER),getIdFromPath(req)+"");
		String message = "DELETE not allowed for the drFeedsURL.";
		EventLogRecord elr = new EventLogRecord(req);
		elr.setMessage(message);
		elr.setResult(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		eventlogger.info(elr);
		resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
	}
	/**
	 * GET on the &lt;drFeedsURL&gt; -- query the list of feeds already existing in the DB.
	 * See the <i>Feeds Collection Queries</i> section in the <b>Provisioning API</b>
	 * document for details on how this method should be invoked.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doGet");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader(BEHALF_HEADER),getIdFromPath(req)+"");
		EventLogRecord elr = new EventLogRecord(req);
		String message = isAuthorizedForProvisioning(req);
		if (message != null) {
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
			return;
		}
		if (isProxyServer()) {
			super.doGet(req, resp);
			return;
		}
		String bhdr = req.getHeader(BEHALF_HEADER);
		if (bhdr == null) {
			message = "Missing "+BEHALF_HEADER+" header.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_BAD_REQUEST);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
			return;
		}
		String path = req.getRequestURI(); // Note: I think this should be getPathInfo(), but that doesn't work (Jetty bug?)
		if (path != null && !path.equals("/")) {
			message = "Bad URL.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_NOT_FOUND);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, message);
			return;
		}
		// Check with the Authorizer
		AuthorizationResponse aresp = authz.decide(req);
		if (! aresp.isAuthorized()) {
			message = "Policy Engine disallows access.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
			return;
		}

		String name = req.getParameter("name");
		String vers = req.getParameter("version");
		String publ = req.getParameter("publisher");
		String subs = req.getParameter("subscriber");
		if (name != null && vers != null) {
			// Display a specific feed
			Feed feed = Feed.getFeedByNameVersion(name, vers);
			if (feed == null || feed.isDeleted()) {
				message = "This feed does not exist in the database.";
				elr.setMessage(message);
				elr.setResult(HttpServletResponse.SC_BAD_REQUEST);
				eventlogger.info(elr);
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
			} else {
				// send response
				elr.setResult(HttpServletResponse.SC_OK);
				eventlogger.info(elr);
				resp.setStatus(HttpServletResponse.SC_OK);
				resp.setContentType(FEEDFULL_CONTENT_TYPE);
				resp.getOutputStream().print(feed.asJSONObject(true).toString());
			}
		} else {
			// Display a list of URLs
			List<String> list = null;
			if (name != null) {
				list = Feed.getFilteredFeedUrlList("name", name);
			} else if (publ != null) {
				list = Feed.getFilteredFeedUrlList("publ", publ);
			} else if (subs != null) {
				list = Feed.getFilteredFeedUrlList("subs", subs);
			} else {
				list = Feed.getFilteredFeedUrlList("all", null);
			}
			String t = JSONUtilities.createJSONArray(list);
			// send response
			elr.setResult(HttpServletResponse.SC_OK);
			eventlogger.info(elr);
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType(FEEDLIST_CONTENT_TYPE);
			resp.getOutputStream().print(t);
		}
	}
	/**
	 * PUT on the &lt;drFeedsURL&gt; -- not supported.
	 */
	@Override
	public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doPut");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF_AND_FEEDID, req.getHeader(BEHALF_HEADER),getIdFromPath(req)+"");
		String message = "PUT not allowed for the drFeedsURL.";
		EventLogRecord elr = new EventLogRecord(req);
		elr.setMessage(message);
		elr.setResult(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		eventlogger.info(elr);
		resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
	}
	/**
	 * POST on the &lt;drFeedsURL&gt; -- create a new feed.
	 * See the <i>Creating a Feed</i> section in the <b>Provisioning API</b>
	 * document for details on how this method should be invoked.
	 */
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		setIpAndFqdnForEelf("doPost");
		eelflogger.info(EelfMsgs.MESSAGE_WITH_BEHALF, req.getHeader(BEHALF_HEADER));
		EventLogRecord elr = new EventLogRecord(req);
		String message = isAuthorizedForProvisioning(req);
		if (message != null) {
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
			return;
		}
		if (isProxyServer()) {
			super.doPost(req, resp);
			return;
		}
		String bhdr = req.getHeader(BEHALF_HEADER);
		if (bhdr == null) {
			message = "Missing "+BEHALF_HEADER+" header.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_BAD_REQUEST);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
			return;
		}
		String path = req.getRequestURI(); // Note: I think this should be getPathInfo(), but that doesn't work (Jetty bug?)
		if (path != null && !path.equals("/")) {
			message = "Bad URL.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_NOT_FOUND);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, message);
			return;
		}
		// check content type is FEED_CONTENT_TYPE, version 1.0
		ContentHeader ch = getContentHeader(req);
		String ver = ch.getAttribute("version");
		if (!ch.getType().equals(FEED_BASECONTENT_TYPE) || !(ver.equals("1.0") || ver.equals("2.0"))) {
			message = "Incorrect content-type";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, message);
			return;
		}
		// Check with the Authorizer
		AuthorizationResponse aresp = authz.decide(req);
		if (! aresp.isAuthorized()) {
			message = "Policy Engine disallows access.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_FORBIDDEN);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
			return;
		}
		JSONObject jo = getJSONfromInput(req);
		if (jo == null) {
			message = "Badly formed JSON";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_BAD_REQUEST);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
			return;
		}
		if (intlogger.isDebugEnabled())
			intlogger.debug(jo.toString());
		if (++active_feeds > max_feeds) {
			active_feeds--;
			message = "Cannot create feed; the maximum number of feeds has been configured.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_CONFLICT);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_CONFLICT, message);
			return;
		}
		Feed feed = null;
		try {
			feed = new Feed(jo);
		} catch (InvalidObjectException e) {
			message = e.getMessage();
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_BAD_REQUEST);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
			return;
		}
		feed.setPublisher(bhdr);	// set from X-ATT-DR-ON-BEHALF-OF header

		// Check if this feed already exists
		Feed feed2 = Feed.getFeedByNameVersion(feed.getName(), feed.getVersion());
		if (feed2 != null) {
			message = "This feed already exists in the database.";
			elr.setMessage(message);
			elr.setResult(HttpServletResponse.SC_BAD_REQUEST);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
			return;
		}

		// Create FEED table entries
		if (doInsert(feed)) {
			// send response
			elr.setResult(HttpServletResponse.SC_CREATED);
			eventlogger.info(elr);
			resp.setStatus(HttpServletResponse.SC_CREATED);
			resp.setContentType(FEEDFULL_CONTENT_TYPE);
			resp.setHeader("Location", feed.getLinks().getSelf());
			resp.getOutputStream().print(feed.asLimitedJSONObject().toString());
			provisioningDataChanged();
		} else {
			// Something went wrong with the INSERT
			elr.setResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			eventlogger.info(elr);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DB_PROBLEM_MSG);
		}
	}
}
