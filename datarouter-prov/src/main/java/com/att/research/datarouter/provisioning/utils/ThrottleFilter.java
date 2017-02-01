/*
 *                        AT&T - PROPRIETARY
 *          THIS FILE CONTAINS PROPRIETARY INFORMATION OF
 *        AT&T AND IS NOT TO BE DISCLOSED OR USED EXCEPT IN
 *             ACCORDANCE WITH APPLICABLE AGREEMENTS.
 *
 *          Copyright (c) 2014 AT&T Knowledge Ventures
 *              Unpublished and Not for Publication
 *                     All Rights Reserved
 */

package com.att.research.datarouter.provisioning.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.att.research.datarouter.provisioning.beans.Parameters;

import org.apache.log4j.Logger;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Request;

/**
 * This filter checks /publish requests to the provisioning server to allow ill-behaved publishers to be throttled.
 * It is configured via the provisioning parameter THROTTLE_FILTER.
 * The THROTTLE_FILTER provisioning parameter can have these values:
 * <table>
 * <tr><td>(no value)</td><td>filter disabled</td></tr>
 * <tr><td>off</td><td>filter disabled</td></tr>
 * <tr><td>N[,M[,action]]</td><td>set N, M, and action (used in the algorithm below).
 *     Action is <i>drop</i> or <i>throttle</i>.
 *     If M is missing, it defaults to 5 minutes.
 *     If the action is missing, it defaults to <i>drop</i>.
 * </td></tr>
 * </table>
 * <p>
 * The <i>action</i> is triggered iff:
 * <ol>
 * <li>the filter is enabled, and</li>
 * <li>N /publish requests come to the provisioning server in M minutes
 *   <ol>
 *   <li>from the same IP address</li>
 *   <li>for the same feed</li>
 *   <li>lacking the <i>Expect: 100-continue</i> header</li>
 *   </ol>
 * </li>
 * </ol>
 * The action that can be performed (if triggered) are:
 * <ol>
 * <li><i>drop</i> - the connection is dropped immediately.</li>
 * <li><i>throttle</i> - [not supported] the connection is put into a low priority queue with all other throttled connections.
 *   These are then processed at a slower rate.  Note: this option does not work correctly, and is disabled.
 *   The only action that is supported is <i>drop</i>.
 * </li>
 * </ol>
 *
 * @author Robert Eby
 * @version $Id: ThrottleFilter.java,v 1.2 2014/03/12 19:45:41 eby Exp $
 */
public class ThrottleFilter extends TimerTask implements Filter {
	public  static final int    DEFAULT_N       = 10;
	public  static final int    DEFAULT_M       = 5;
	public  static final String THROTTLE_MARKER = "com.att.research.datarouter.provisioning.THROTTLE_MARKER";
	private static final String JETTY_REQUEST   = "org.eclipse.jetty.server.Request";
	private static final long   ONE_MINUTE      = 60000L;
	private static final int    ACTION_DROP     = 0;
	private static final int    ACTION_THROTTLE = 1;

	// Configuration
	private static boolean enabled = false;		// enabled or not
	private static int n_requests = 0;			// number of requests in M minutes
	private static int m_minutes = 0;			// sampling period
	private static int action = ACTION_DROP;	// action to take (throttle or drop)

	private static Logger logger = Logger.getLogger("com.att.research.datarouter.provisioning.internal");
	private static Map<String, Counter> map = new HashMap<String, Counter>();
	private static final Timer rolex = new Timer();

	@Override
	public void init(FilterConfig arg0) throws ServletException {
		configure();
		rolex.scheduleAtFixedRate(this, 5*60000L, 5*60000L);	// Run once every 5 minutes to clean map
	}

	/**
	 * Configure the throttle.  This should be called from BaseServlet.provisioningParametersChanged(), to make sure it stays up to date.
	 */
	public static void configure() {
		Parameters p = Parameters.getParameter(Parameters.THROTTLE_FILTER);
		if (p != null) {
			try {
				Class.forName(JETTY_REQUEST);
				String v = p.getValue();
				if (v != null && !v.equals("off")) {
					String[] pp = v.split(",");
					if (pp != null) {
						n_requests = (pp.length > 0) ? getInt(pp[0], DEFAULT_N) : DEFAULT_N;
						m_minutes  = (pp.length > 1) ? getInt(pp[1], DEFAULT_M) : DEFAULT_M;
						action     = (pp.length > 2 && pp[2] != null && pp[2].equalsIgnoreCase("throttle")) ? ACTION_THROTTLE : ACTION_DROP;
						enabled    = true;
						// ACTION_THROTTLE is not currently working, so is not supported
						if (action == ACTION_THROTTLE) {
							action = ACTION_DROP;
							logger.info("Throttling is not currently supported; action changed to DROP");
						}
						logger.info("ThrottleFilter is ENABLED for /publish requests; N="+n_requests+", M="+m_minutes+", Action="+action);
						return;
					}
				}
			} catch (ClassNotFoundException e) {
				logger.warn("Class "+JETTY_REQUEST+" is not available; this filter requires Jetty.");
			}
		}
		logger.info("ThrottleFilter is DISABLED for /publish requests.");
		enabled = false;
		map.clear();
	}
	private static int getInt(String s, int deflt) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException x) {
			return deflt;
		}
	}
	@Override
	public void destroy() {
		rolex.cancel();
		map.clear();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
		throws IOException, ServletException
	{
		if (enabled && action == ACTION_THROTTLE) {
			throttleFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
		} else if (enabled) {
			dropFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
		} else {
			chain.doFilter(request, response);
		}
	}
	public void dropFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws IOException, ServletException
	{
		int rate = getRequestRate((HttpServletRequest) request);
		if (rate >= n_requests) {
			// drop request - only works under Jetty
			String m = String.format("Dropping connection: %s %d bad connections in %d minutes", getConnectionId((HttpServletRequest) request), rate, m_minutes);
			logger.info(m);
			Request base_request = (request instanceof Request)
				? (Request) request
				: AbstractHttpConnection.getCurrentConnection().getRequest();
			base_request.getConnection().getEndPoint().close();
		} else {
			chain.doFilter(request, response);
		}
	}
	public void throttleFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws IOException, ServletException
	{
		// throttle request
		String id = getConnectionId((HttpServletRequest) request);
		int rate = getRequestRate((HttpServletRequest) request);
		Object results = request.getAttribute(THROTTLE_MARKER);
		if (rate >= n_requests && results == null) {
			String m = String.format("Throttling connection: %s %d bad connections in %d minutes", getConnectionId((HttpServletRequest) request), rate, m_minutes);
			logger.info(m);
			Continuation continuation = ContinuationSupport.getContinuation(request);
			continuation.suspend();
			register(id, continuation);
			continuation.undispatch();
		} else {
			chain.doFilter(request, response);
			@SuppressWarnings("resource")
			InputStream is = request.getInputStream();
			byte[] b = new byte[4096];
			int n = is.read(b);
			while (n > 0) {
				n = is.read(b);
			}
			resume(id);
		}
	}
	private Map<String, List<Continuation>> suspended_requests = new HashMap<String, List<Continuation>>();
	private void register(String id, Continuation continuation) {
		synchronized (suspended_requests) {
			List<Continuation> list = suspended_requests.get(id);
			if (list == null) {
				list = new ArrayList<Continuation>();
				suspended_requests.put(id,  list);
			}
			list.add(continuation);
		}
	}
	private void resume(String id) {
		synchronized (suspended_requests) {
			List<Continuation> list = suspended_requests.get(id);
			if (list != null) {
				// when the waited for event happens
				Continuation continuation = list.remove(0);
				continuation.setAttribute(ThrottleFilter.THROTTLE_MARKER, new Object());
				continuation.resume();
			}
		}
	}

	/**
	 * Return a count of number of requests in the last M minutes, iff this is a "bad" request.
	 * If the request has been resumed (if it contains the THROTTLE_MARKER) it is considered good.
	 * @param request the request
	 * @return number of requests in the last M minutes, 0 means it is a "good" request
	 */
	private int getRequestRate(HttpServletRequest request) {
		String expecthdr = request.getHeader("Expect");
		if (expecthdr != null && expecthdr.equalsIgnoreCase("100-continue"))
			return 0;

		String key = getConnectionId(request);
		synchronized (map) {
			Counter cnt = map.get(key);
			if (cnt == null) {
				cnt = new Counter();
				map.put(key, cnt);
			}
			int n = cnt.getRequestRate();
			return n;
		}
	}

	public class Counter {
		private List<Long> times = new Vector<Long>();	// a record of request times
		public int prune() {
			try {
				long n = System.currentTimeMillis() - (m_minutes * ONE_MINUTE);
				long t = times.get(0);
				while (t < n) {
					times.remove(0);
					t = times.get(0);
				}
			} catch (IndexOutOfBoundsException e) {
				// ignore
			}
			return times.size();
		}
		public int getRequestRate() {
			times.add(System.currentTimeMillis());
			return prune();
		}
	}

	/**
	 *  Identify a connection by endpoint IP address, and feed ID.
	 */
	private String getConnectionId(HttpServletRequest req) {
		return req.getRemoteAddr() + "/" + getFeedId(req);
	}
	private int getFeedId(HttpServletRequest req) {
		String path = req.getPathInfo();
		if (path == null || path.length() < 2)
			return -1;
		path = path.substring(1);
		int ix = path.indexOf('/');
		if (ix < 0 || ix == path.length()-1)
			return -2;
		try {
			int feedid = Integer.parseInt(path.substring(0, ix));
			return feedid;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	@Override
	public void run() {
		// Once every 5 minutes, go through the map, and remove empty entrys
		for (Object s : map.keySet().toArray()) {
			synchronized (map) {
				Counter c = map.get(s);
				if (c.prune() <= 0)
					map.remove(s);
			}
		}
	}
}
