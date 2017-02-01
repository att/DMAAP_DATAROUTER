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
  * $Id: DeliveryTask.java,v 1.13 2013/10/04 16:29:17 agg Exp $
  */

package com.att.research.datarouter.node;

import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.log4j.Logger;

/**
 *	A file to be delivered to a destination.
 *	<p>
 *	A Delivery task represents a work item for the data router - a file that
 *	needs to be delivered and provides mechanisms to get information about
 *	the file and its delivery data as well as to attempt delivery.
 */
public class DeliveryTask implements Runnable, Comparable<DeliveryTask>	{
	private static Logger logger = Logger.getLogger("com.att.research.datarouter.node.DeliveryTask");
	private DeliveryTaskHelper	dth;
	private String	pubid;
	private	DestInfo	di;
	private String	spool;
	private File	datafile;
	private File	metafile;
	private long	length;
	private long	date;
	private String	method;
	private String	fileid;
	private String	ctype;
	private String	url;
	private String	feedid;
	private String	subid;
	private int	attempts;
	private String[][]	hdrs;
	/**
	 *	Is the object a DeliveryTask with the same publication ID?
	 */
	public boolean equals(Object o) {
		if (!(o instanceof DeliveryTask)) {
			return(false);
		}
		return(pubid.equals(((DeliveryTask)o).pubid));
	}
	/**
	 *	Compare the publication IDs.
	 */
	public int compareTo(DeliveryTask o) {
		return(pubid.compareTo(o.pubid));
	}
	/**
	 *	Get the hash code of the publication ID.
	 */
	public int hashCode() {
		return(pubid.hashCode());
	}
	/**
	 *	Return the publication ID.
	 */
	public String toString() {
		return(pubid);
	}
	/**
	 *	Create a delivery task for a given delivery queue and pub ID
	 *	@param	dth	The delivery task helper for the queue this task is in.
	 *	@param	pubid	The publish ID for this file.  This is used as
	 *	the base for the file name in the spool directory and is of
	 *	the form <milliseconds since 1970>.<fqdn of initial data router node>
	 */
	public DeliveryTask(DeliveryTaskHelper dth, String pubid) {
		this.dth = dth;
		this.pubid = pubid;
		di = dth.getDestInfo();
		subid = di.getSubId();
		feedid = di.getLogData();
		spool = di.getSpool();
		String dfn = spool + "/" + pubid;
		String mfn = dfn + ".M";
		datafile = new File(spool + "/" + pubid);
		metafile = new File(mfn);
		boolean monly = di.isMetaDataOnly();
		date = Long.parseLong(pubid.substring(0, pubid.indexOf('.')));
		Vector<String[]> hdrv = new Vector<String[]>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(metafile));
			String s = br.readLine();
			int i = s.indexOf('\t');
			method = s.substring(0, i);
			if (!"DELETE".equals(method) && !monly) {
				length = datafile.length();
			}
			fileid = s.substring(i + 1);
			while ((s = br.readLine()) != null) {
				i = s.indexOf('\t');
				String h = s.substring(0, i);
				String v = s.substring(i + 1);
				if ("x-att-dr-routing".equalsIgnoreCase(h)) {
					subid = v.replaceAll("[^ ]*/", "");
					feedid = dth.getFeedId(subid.replaceAll(" .*", ""));
				}
				if (length == 0 && h.toLowerCase().startsWith("content-")) {
					continue;
				}
				if (h.equalsIgnoreCase("content-type")) {
					ctype = v;
				}
				hdrv.add(new String[] {h, v});
			}
			br.close();
		} catch (Exception e) {
		}
		hdrs = hdrv.toArray(new String[hdrv.size()][]);
		url = dth.getDestURL(fileid);
	}
	/**
	 *	Get the publish ID
	 */
	public String getPublishId() {
		return(pubid);
	}
	/**
	 *	Attempt delivery
	 */
	public void run() {
		attempts++;
		try {
			di = dth.getDestInfo();
			boolean expect100 = di.isUsing100();
			boolean monly = di.isMetaDataOnly();
			length = 0;
			if (!"DELETE".equals(method) && !monly) {
				length = datafile.length();
			}
			url = dth.getDestURL(fileid);
			URL u = new URL(url);
			HttpURLConnection uc = (HttpURLConnection)u.openConnection();
			uc.setConnectTimeout(60000);
			uc.setReadTimeout(60000);
			uc.setInstanceFollowRedirects(false);
			uc.setRequestMethod(method);
			uc.setRequestProperty("Content-Length", Long.toString(length));
			uc.setRequestProperty("Authorization", di.getAuth());
			uc.setRequestProperty("X-ATT-DR-PUBLISH-ID", pubid);
			for (String[] nv: hdrs) {
				uc.addRequestProperty(nv[0], nv[1]);
			}
			if (length > 0) {
				if (expect100) {
					uc.setRequestProperty("Expect", "100-continue");
				}
				uc.setFixedLengthStreamingMode(length);
				uc.setDoOutput(true);
				OutputStream os = null;
				try {
					os = uc.getOutputStream();
				} catch (ProtocolException pe) {
					dth.reportDeliveryExtra(this, -1L);
					// Rcvd error instead of 100-continue
				}
				if (os != null) {
					long sofar = 0;
					try {
						byte[] buf = new byte[1024 * 1024];
						InputStream is = new FileInputStream(datafile);
						while (sofar < length) {
							int i = buf.length;
							if (sofar + i > length) {
								i = (int)(length - sofar);
							}
							i = is.read(buf, 0, i);
							if (i <= 0) {
								throw new IOException("Unexpected problem reading data file " + datafile);
							}
							sofar += i;
							os.write(buf, 0, i);
						}
						is.close();
						os.close();
					} catch (IOException ioe) {
						dth.reportDeliveryExtra(this, sofar);
						throw ioe;
					}
				}
			}
			int rc = uc.getResponseCode();
			String rmsg = uc.getResponseMessage();
			if (rmsg == null) {
				String h0 = uc.getHeaderField(0);
				if (h0 != null) {
					int i = h0.indexOf(' ');
					int j = h0.indexOf(' ', i + 1);
					if (i != -1 && j != -1) {
						rmsg = h0.substring(j + 1);
					}
				}
			}
			String xpubid = null;
			InputStream is;
			if (rc >= 200 && rc <= 299) {
				is = uc.getInputStream();
				xpubid = uc.getHeaderField("X-ATT-DR-PUBLISH-ID");
			} else {
				if (rc >= 300 && rc <= 399) {
					rmsg = uc.getHeaderField("Location");
				}
				is = uc.getErrorStream();
			}
			byte[] buf = new byte[4096];
			if (is != null) {
				while (is.read(buf) > 0) {
				}
				is.close();
			}
			dth.reportStatus(this, rc, xpubid, rmsg);
		} catch (Exception e) {
			dth.reportException(this, e);
		}
	}
	/**
	 *	Remove meta and data files
	 */
	public void clean() {
		datafile.delete();
		metafile.delete();
		hdrs = null;
	}
	/**
	 *	Has this delivery task been cleaned?
	 */
	public boolean isCleaned() {
		return(hdrs == null);
	}
	/**
	 *	Get length of body
	 */
	public long	getLength() {
		return(length);
	}
	/**
	 *	Get creation date as encoded in the publish ID.
	 */
	public long	getDate() {
		return(date);
	}
	/**
	 *	Get the most recent delivery attempt URL
	 */
	public String getURL() {
		return(url);
	}
	/**
	 *	Get the content type
	 */
	public String	getCType() {
		return(ctype);
	}
	/**
	 *	Get the method
	 */
	public String	getMethod() {
		return(method);
	}
	/**
	 *	Get the file ID
	 */
	public String	getFileId() {
		return(fileid);
	}
	/**
	 *	Get the number of delivery attempts
	 */
	public int	getAttempts() {
		return(attempts);
	}
	/**
	 *	Get the (space delimited list of) subscription ID for this delivery task
	 */
	public String	getSubId() {
		return(subid);
	}
	/**
	 *	Get the feed ID for this delivery task
	 */
	public String	getFeedId() {
		return(feedid);
	}
}
