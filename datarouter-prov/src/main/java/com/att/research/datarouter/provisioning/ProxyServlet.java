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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import com.att.research.datarouter.provisioning.utils.DB;
import com.att.research.datarouter.provisioning.utils.URLUtilities;

/**
 * This class is the base class for those servlets that need to proxy their requests from the
 * standby to active server.  Its methods perform the proxy function to the active server. If the
 * active server is not reachable, a 503 (SC_SERVICE_UNAVAILABLE) is returned.  Only
 * DELETE/GET/PUT/POST are supported.
 *
 * @author Robert Eby
 * @version $Id: ProxyServlet.java,v 1.3 2014/03/24 18:47:10 eby Exp $
 */
@SuppressWarnings("serial")
public class ProxyServlet extends BaseServlet {
	private boolean inited = false;
	private Scheme sch;

	/**
	 * Initialize this servlet, by setting up SSL.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			// Set up keystore
			Properties props = (new DB()).getProperties();
			String type  = props.getProperty(Main.KEYSTORE_TYPE_PROPERTY, "jks");
			String store = props.getProperty(Main.KEYSTORE_PATH_PROPERTY);
			String pass  = props.getProperty(Main.KEYSTORE_PASSWORD_PROPERTY);
			KeyStore keyStore = readStore(store, pass, type);

			store = props.getProperty(Main.TRUSTSTORE_PATH_PROPERTY);
			pass  = props.getProperty(Main.TRUSTSTORE_PASSWORD_PROPERTY);
			if (store == null || store.length() == 0) {
				store = Main.DEFAULT_TRUSTSTORE;
				pass = "changeit";
			}
			KeyStore trustStore = readStore(store, pass, KeyStore.getDefaultType());

			// We are connecting with the node name, but the certificate will have the CNAME
			// So we need to accept a non-matching certificate name
			SSLSocketFactory socketFactory = new SSLSocketFactory(keyStore, "changeit", trustStore);
			socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			sch = new Scheme("https", 443, socketFactory);
			inited = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		intlogger.info("ProxyServlet: inited = "+inited);
	}
	private KeyStore readStore(String store, String pass, String type) throws KeyStoreException, FileNotFoundException {
		KeyStore ks = KeyStore.getInstance(type);
		FileInputStream instream = new FileInputStream(new File(store));
		try {
		    ks.load(instream, pass.toCharArray());
		} catch (Exception x) {
			System.err.println("READING TRUSTSTORE: "+x);
		} finally {
		    try { instream.close(); } catch (Exception ignore) {}
		}
		return ks;
	}
	/**
	 * Return <i>true</i> if the requester has NOT set the <i>noproxy</i> CGI variable.
	 * If they have, this indicates they want to forcibly turn the proxy off.
	 * @param req the HTTP request
	 * @return true or false
	 */
	protected boolean isProxyOK(final HttpServletRequest req) {
		String t = req.getQueryString();
		if (t != null) {
			t = t.replaceAll("&amp;", "&");
			for (String s : t.split("&")) {
				if (s.equals("noproxy") || s.startsWith("noproxy="))
					return false;
			}
		}
		return true;
	}
	/**
	 * Is this the standby server?  If it is, the proxy functions can be used.
	 * If not, the proxy functions should not be called, and will send a response of 500
	 * (Internal Server Error).
	 * @return true if this server is the standby (and hence a proxy server).
	 */
	public boolean isProxyServer() {
		SynchronizerTask st = SynchronizerTask.getSynchronizer();
		return st.getState() == SynchronizerTask.STANDBY;
	}
	/**
	 * Issue a proxy DELETE to the active provisioning server.
	 */
	@Override
	public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		doProxy(req, resp, "DELETE");
	}
	/**
	 * Issue a proxy GET to the active provisioning server.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		doProxy(req, resp, "GET");
	}
	/**
	 * Issue a proxy PUT to the active provisioning server.
	 */
	@Override
	public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		doProxy(req, resp, "PUT");
	}
	/**
	 * Issue a proxy POST to the active provisioning server.
	 */
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		doProxy(req, resp, "POST");
	}
	/**
	 * Issue a proxy GET to the active provisioning server.  Unlike doGet() above,
	 * this method will allow the caller to fall back to other code if the remote server is unreachable.
	 * @return true if the proxy succeeded
	 */
	public boolean doGetWithFallback(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		boolean rv = false;
		if (inited) {
			String url = buildUrl(req);
			intlogger.info("ProxyServlet: proxying with fallback GET "+url);
			AbstractHttpClient httpclient = new DefaultHttpClient();
			HttpRequestBase proxy = new HttpGet(url);
			try {
				httpclient.getConnectionManager().getSchemeRegistry().register(sch);

				// Copy request headers and request body
				copyRequestHeaders(req, proxy);

				// Execute the request
				HttpResponse pxy_response = httpclient.execute(proxy);

				// Get response headers and body
				int code = pxy_response.getStatusLine().getStatusCode();
				resp.setStatus(code);
				copyResponseHeaders(pxy_response, resp);

				HttpEntity entity = pxy_response.getEntity();
				if (entity != null) {
					InputStream in = entity.getContent();
					IOUtils.copy(in, resp.getOutputStream());
					in.close();
				}
				rv = true;
			} catch (IOException e) {
				System.err.println("ProxyServlet: "+e);
				e.printStackTrace();
			} finally {
				proxy.releaseConnection();
				httpclient.getConnectionManager().shutdown();
			}
		} else {
			intlogger.warn("ProxyServlet: proxy disabled");
		}
		return rv;
	}
	private void doProxy(HttpServletRequest req, HttpServletResponse resp, final String method) throws IOException {
		if (inited && isProxyServer()) {
			String url = buildUrl(req);
			intlogger.info("ProxyServlet: proxying "+method + " "+url);
			AbstractHttpClient httpclient = new DefaultHttpClient();
			ProxyHttpRequest proxy = new ProxyHttpRequest(method, url);
			try {
				httpclient.getConnectionManager().getSchemeRegistry().register(sch);

				// Copy request headers and request body
				copyRequestHeaders(req, proxy);
				if (method.equals("POST") || method.equals("PUT")){
					BasicHttpEntity body = new BasicHttpEntity();
					body.setContent(req.getInputStream());
					body.setContentLength(-1);	// -1 = unknown
					proxy.setEntity(body);
				}

				// Execute the request
				HttpResponse pxy_response = httpclient.execute(proxy);

				// Get response headers and body
				int code = pxy_response.getStatusLine().getStatusCode();
				resp.setStatus(code);
				copyResponseHeaders(pxy_response, resp);

				HttpEntity entity = pxy_response.getEntity();
				if (entity != null) {
					InputStream in = entity.getContent();
					IOUtils.copy(in, resp.getOutputStream());
					in.close();
				}
			} catch (IOException e) {
				intlogger.warn("ProxyServlet: "+e);
				resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				e.printStackTrace();
			} finally {
				proxy.releaseConnection();
				httpclient.getConnectionManager().shutdown();
			}
		} else {
			intlogger.warn("ProxyServlet: proxy disabled");
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
	private String buildUrl(HttpServletRequest req) {
		StringBuilder sb = new StringBuilder("https://");
		sb.append(URLUtilities.getPeerPodName());
		sb.append(req.getRequestURI());
		String q = req.getQueryString();
		if (q != null)
			sb.append("?").append(q);
		return sb.toString();
	}
	private void copyRequestHeaders(HttpServletRequest from, HttpRequestBase to) {
		@SuppressWarnings("unchecked")
		List<String> list = Collections.list(from.getHeaderNames());
		for (String name : list) {
			// Proxy code will add this one
			if (!name.equalsIgnoreCase("Content-Length"))
				to.addHeader(name, from.getHeader(name));
		}
	}
	private void copyResponseHeaders(HttpResponse from, HttpServletResponse to) {
		for (Header hdr : from.getAllHeaders()) {
			// Don't copy Date: our Jetty will add another Date header
			if (!hdr.getName().equals("Date"))
				to.addHeader(hdr.getName(), hdr.getValue());
		}
	}

	public class ProxyHttpRequest extends HttpEntityEnclosingRequestBase {
		private final String method;

		public ProxyHttpRequest(final String method, final String uri) {
			super();
			this.method = method;
	        setURI(URI.create(uri));
		}
		@Override
		public String getMethod() {
			return method;
		}
	}
}
