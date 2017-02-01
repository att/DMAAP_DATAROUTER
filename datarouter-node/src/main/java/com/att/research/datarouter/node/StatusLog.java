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
  * $Id: StatusLog.java,v 1.12 2013/10/04 16:29:18 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.regex.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.text.*;

/**
 *	Logging for data router delivery events (PUB/DEL/EXP)
 */
public class StatusLog	{
	private static StatusLog instance = new StatusLog();
	private HashSet<String> toship = new HashSet<String>();
	private SimpleDateFormat	filedate;
	private String	prefix = "logs/events";
	private	String	suffix = ".log";
	private String	plainfile;
	private String	curfile;
	private long	nexttime;
	private OutputStream	os;
	private long	intvl;
	private NodeConfigManager	config = NodeConfigManager.getInstance();
	{
		try { filedate = new SimpleDateFormat("-yyyyMMddHHmm"); } catch (Exception e) {}
	}
	/**
	 *	Parse an interval of the form xxhyymzzs and round it to the nearest whole fraction of 24 hours.  If no units are specified, assume seconds.
	 */
	public static long parseInterval(String interval, int def) {
		try {
			Matcher m = Pattern.compile("(?:(\\d+)[Hh])?(?:(\\d+)[Mm])?(?:(\\d+)[Ss]?)?").matcher(interval);
			if (m.matches()) {
				int dur = 0;
				String x = m.group(1);
				if (x != null) {
					dur += 3600 * Integer.parseInt(x);
				}
				x = m.group(2);
				if (x != null) {
					dur += 60 * Integer.parseInt(x);
				}
				x = m.group(3);
				if (x != null) {
					dur += Integer.parseInt(x);
				}
				if (dur < 60) {
					dur = 60;
				}
				int best = 86400;
				int dist = best - dur;
				if (dur > best) {
					dist = dur - best;
				}
				int base = 1;
				for (int i = 0; i < 8; i++) {
					int base2 = base;
					base *= 2;
					for (int j = 0; j < 4; j++) {
						int base3 = base2;
						base2 *= 3;
						for (int k = 0; k < 3; k++) {
							int cur = base3;
							base3 *= 5;
							int ndist = cur - dur;
							if (dur > cur) {
								ndist = dur - cur;
							}
							if (ndist < dist) {
								best = cur;
								dist = ndist;
							}
						}
					}
				}
				def = best * 1000;
			}
		} catch (Exception e) {
		}
		return(def);
	}
	private synchronized void checkRoll(long now) throws IOException {
		if (now >= nexttime) {
			if (os != null) {
				os.close();
				os = null;
			}
			intvl = parseInterval(config.getEventLogInterval(), 300000);
			prefix = config.getEventLogPrefix();
			suffix = config.getEventLogSuffix();
			nexttime = now - now % intvl + intvl;
			curfile = prefix + filedate.format(new Date(nexttime - intvl)) + suffix;
			plainfile = prefix + suffix;
			notify();
		}
	}
	/**
	 *	Get the name of the current log file
	 *	@return	The full path name of the current event log file
	 */
	public static synchronized String getCurLogFile() {
		try {
			instance.checkRoll(System.currentTimeMillis());
		} catch (Exception e) {
		}
		return(instance.curfile);
	}
	private synchronized void log(String s) {
		try {
			long now = System.currentTimeMillis();
			checkRoll(now);
			if (os == null) {
				os = new FileOutputStream(curfile, true);
				(new File(plainfile)).delete();
				Files.createLink(Paths.get(plainfile), Paths.get(curfile));
			}
			os.write((NodeUtils.logts(new Date(now)) + '|' + s + '\n').getBytes());
			os.flush();
		} catch (IOException ioe) {
		}
	}
	/**
	 *	Log a received publication attempt.
	 *	@param pubid	The publish ID assigned by the node
	 *	@param feedid	The feed id given by the publisher
	 *	@param requrl	The URL of the received request
	 *	@param method	The method (DELETE or PUT) in the received request
	 *	@param ctype	The content type (if method is PUT and clen > 0)
	 *	@param clen	The content length (if method is PUT)
	 *	@param srcip	The IP address of the publisher
	 *	@param user	The identity of the publisher
	 *	@param status	The status returned to the publisher
	 */
	public static void logPub(String pubid, String feedid, String requrl, String method, String ctype, long clen, String srcip, String user, int status) {
		instance.log("PUB|" + pubid + "|" + feedid + "|" + requrl + "|" + method + "|" + ctype + "|" + clen + "|" + srcip + "|" + user + "|" + status);
	}
	/**
	 *	Log a data transfer error receiving a publication attempt
	 *	@param pubid	The publish ID assigned by the node
	 *	@param feedid	The feed id given by the publisher
	 *	@param requrl	The URL of the received request
	 *	@param method	The method (DELETE or PUT) in the received request
	 *	@param ctype	The content type (if method is PUT and clen > 0)
	 *	@param clen	The expected content length (if method is PUT)
	 *	@param rcvd	The content length received
	 *	@param srcip	The IP address of the publisher
	 *	@param user	The identity of the publisher
	 *	@param error	The error message from the IO exception
	 */
	public static void logPubFail(String pubid, String feedid, String requrl, String method, String ctype, long clen, long rcvd, String srcip, String user, String error) {
		instance.log("PBF|" + pubid + "|" + feedid + "|" + requrl + "|" + method + "|" + ctype + "|" + clen + "|" + rcvd + "|" + srcip + "|" + user + "|" + error);
	}
	/**
	 *	Log a delivery attempt.
	 *	@param pubid	The publish ID assigned by the node
	 *	@param feedid	The feed ID
	 *	@param subid	The (space delimited list of) subscription ID
	 *	@param requrl	The URL used in the attempt
	 *	@param method	The method (DELETE or PUT) in the attempt
	 *	@param ctype	The content type (if method is PUT, not metaonly, and clen > 0)
	 *	@param clen	The content length (if PUT and not metaonly)
	 *	@param user	The identity given to the subscriber
	 *	@param status	The status returned by the subscriber or -1 if an exeception occured trying to connect
	 *	@param xpubid	The publish ID returned by the subscriber
	 */
	public static void logDel(String pubid, String feedid, String subid, String requrl, String method, String ctype, long clen, String user, int status, String xpubid) {
		if (feedid == null) {
			return;
		}
		instance.log("DEL|" + pubid + "|" + feedid + "|" + subid + "|" + requrl + "|" + method + "|" + ctype + "|" + clen + "|" + user + "|" + status + "|" + xpubid);
	}
	/**
	 *	Log delivery attempts expired
	 *	@param pubid	The publish ID assigned by the node
	 *	@param feedid	The feed ID
	 *	@param subid	The (space delimited list of) subscription ID
	 *	@param requrl	The URL that would be delivered to
	 *	@param method	The method (DELETE or PUT) in the request
	 *	@param ctype	The content type (if method is PUT, not metaonly, and clen > 0)
	 *	@param clen	The content length (if PUT and not metaonly)
	 *	@param reason	The reason the attempts were discontinued
	 *	@param attempts	The number of attempts made
	 */
	public static void logExp(String pubid, String feedid, String subid, String requrl, String method, String ctype, long clen, String reason, int attempts) {
		if (feedid == null) {
			return;
		}
		instance.log("EXP|" + pubid + "|" + feedid + "|" + subid + "|" + requrl + "|" + method + "|" + ctype + "|" + clen + "|" + reason + "|" + attempts);
	}
	/**
	 *	Log extra statistics about unsuccessful delivery attempts.
	 *	@param pubid	The publish ID assigned by the node
	 *	@param feedid	The feed ID
	 *	@param subid	The (space delimited list of) subscription ID
	 *	@param clen	The content length
	 *	@param sent	The # of bytes sent or -1 if subscriber returned an error instead of 100 Continue, otherwise, the number of bytes sent before an error occurred.
	 */
	public static void logDelExtra(String pubid, String feedid, String subid, long clen, long sent) {
		if (feedid == null) {
			return;
		}
		instance.log("DLX|" + pubid + "|" + feedid + "|" + subid + "|" + clen + "|" + sent);
	}
	private StatusLog() {
	}
}
