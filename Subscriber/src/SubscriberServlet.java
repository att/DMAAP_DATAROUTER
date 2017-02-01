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
  * $Id: SubscriberServlet.java,v 1.1 2013/10/02 19:57:45 agg Exp $
  */


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

/**
 *	Example stand alone subscriber servlet with Authorization header checking
 */
public class SubscriberServlet extends HttpServlet	{
	private static Logger logger = Logger.getLogger("com.att.datarouter.pubsub.ssasubscribe.SubscriberServlet");
	private String Login = "LOGIN";
	private String Password = "PASSWORD";
	private String OutputDirectory = "/root/sub/received";

	private String auth;

	private static String gp(ServletConfig config, String param, String deflt) {
		param = config.getInitParameter(param);
		if (param == null || param.length() == 0) {
			param = deflt;
		}
		return(param);
	}
	/**
	 *	Configure this subscriberservlet.  Configuration parameters from config.getInitParameter() are:
	 *	<ul>
	 *	<li>Login - The login expected in the Authorization header (default "LOGIN").
	 *	<li>Password - The password expected in the Authorization header (default "PASSWORD").
	 *	<li>OutputDirectory - The directory where files are placed (default "received").
	 *	</ul>
	 */
	public void init(ServletConfig config) throws ServletException {
		Login = gp(config, "Login", Login);
		Password = gp(config, "Password", Password);
		OutputDirectory = gp(config, "OutputDirectory", OutputDirectory);
		(new File(OutputDirectory)).mkdirs();
		auth = "Basic " + Base64.encodeBase64String((Login + ":" + Password).getBytes());
	}
	/**
	 *	Invoke common(req, resp, false).
	 */
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		common(req, resp, false);
	}
	/**
	 *	Invoke common(req, resp, true).
	 */
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		common(req, resp, true);
	}
	/**
	 *	Process a PUT or DELETE request.
	 *	<ol>
	 *	<li>Verify that the request contains an Authorization header
	 *	or else UNAUTHORIZED.
	 *	<li>Verify that the Authorization header matches the configured
	 *	Login and Password or else FORBIDDEN.
	 *	<li>If the request is PUT, store the message body as a file
	 *	in the configured OutputDirectory directory protecting against
	 *	evil characters in the received FileID.  The file is created
	 *	initially with its name prefixed with a ".", and once it is complete, it is
	 *	renamed to remove the leading "." character.
	 *	<li>If the request is DELETE, instead delete the file (if it exists) from the configured OutputDirectory directory.
	 *	<li>Respond with NO_CONTENT.
	 *	</ol>
	 */
	protected void common(HttpServletRequest req, HttpServletResponse resp, boolean isdelete) throws ServletException, IOException {
		String ah = req.getHeader("Authorization");
		if (ah == null) {
			logger.info("Rejecting request with no Authorization header from " + req.getRemoteAddr() + ": " + req.getPathInfo());
			resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		if (!auth.equals(ah)) {
			logger.info("Rejecting request with incorrect Authorization header from " + req.getRemoteAddr() + ": " + req.getPathInfo());
			resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		String fileid = req.getPathInfo();
		fileid = fileid.substring(fileid.lastIndexOf('/') + 1);
		String qs = req.getQueryString();
		if (qs != null) {
			fileid = fileid + "?" + qs;
		}
		String publishid = req.getHeader("X-ATT-DR-PUBLISH-ID");
		String filename = URLEncoder.encode(fileid, "UTF-8").replaceAll("^\\.", "%2E").replaceAll("\\*", "%2A");
		String finalname = OutputDirectory + "/" + filename;
		String tmpname = OutputDirectory + "/." + filename;
		try {
			if (isdelete) {
				(new File(finalname)).delete();
				logger.info("Received delete for file id " + fileid + " from " + req.getRemoteAddr() + " publish id " + publishid + " as " + finalname);
			} else {
				InputStream is = req.getInputStream();
				OutputStream os = new FileOutputStream(tmpname);
				byte[] buf = new byte[65536];
				int i;
				while ((i = is.read(buf)) > 0) {
					os.write(buf, 0, i);
				}
				is.close();
				os.close();
				(new File(tmpname)).renameTo(new File(finalname));
				logger.info("Received file id " + fileid + " from " + req.getRemoteAddr() + " publish id " + publishid + " as " + finalname);
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				logger.info("Received file id " + fileid + " from " + req.getRemoteAddr() + " publish id " + publishid + " as " + finalname);
			}
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} catch (IOException ioe) {
			(new File(tmpname)).delete();
			logger.info("Failure to save file " + finalname + " from " + req.getRemoteAddr() + ": " + req.getPathInfo(), ioe);
			throw ioe;
		}
	}
}
