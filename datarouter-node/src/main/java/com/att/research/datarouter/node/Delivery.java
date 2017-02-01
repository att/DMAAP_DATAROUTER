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
  * $Id: Delivery.java,v 1.6 2014/02/10 20:53:06 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.*;
import java.io.*;
import org.apache.log4j.Logger;

/**
 *	Main control point for delivering files to destinations.
 *	<p>
 *	The Delivery class manages assignment of delivery threads to delivery
 *	queues and creation and destruction of delivery queues as
 *	configuration changes.  DeliveryQueues are assigned threads based on a
 *	modified round-robin approach giving priority to queues with more work
 *	as measured by both bytes to deliver and files to deliver and lower
 *	priority to queues that already have delivery threads working.
 *	A delivery thread continues to work for a delivery queue as long as
 *	that queue has more files to deliver.
 */
public class Delivery {
	private static Logger logger = Logger.getLogger("com.att.research.datarouter.node.Delivery");
	private static class DelItem implements Comparable<DelItem>	{
		private String pubid;
		private String spool;
		public int compareTo(DelItem x) {
			int i = pubid.compareTo(x.pubid);
			if (i == 0) {
				i = spool.compareTo(x.spool);
			}
			return(i);
		}
		public String getPublishId() {
			return(pubid);
		}
		public String getSpool() {
			return(spool);
		}
		public DelItem(String pubid, String spool) {
			this.pubid = pubid;
			this.spool = spool;
		}
	}
	private double	fdstart;
	private double	fdstop;
	private int	threads;
	private int	curthreads;
	private NodeConfigManager	config;
	private Hashtable<String, DeliveryQueue>	dqs = new Hashtable<String, DeliveryQueue>();
	private DeliveryQueue[]	queues = new DeliveryQueue[0];
	private int	qpos = 0;
	private long	nextcheck;
	private Runnable	cmon = new Runnable() {
		public void run() {
			checkconfig();
		}
	};
	/**
	 *	Constructs a new Delivery system using the specified configuration manager.
	 *	@param config	The configuration manager for this delivery system.
	 */
	public Delivery(NodeConfigManager config) {
		this.config = config;
		config.registerConfigTask(cmon);
		checkconfig();
	}
	private void cleardir(String dir) {
		if (dqs.get(dir) != null) {
			return;
		}
		File fdir = new File(dir);
		for (File junk: fdir.listFiles()) {
			if (junk.isFile()) {
				junk.delete();
			}
		}
		fdir.delete();
	}
	private void freeDiskCheck() {
		File spoolfile = new File(config.getSpoolBase());
		long tspace = spoolfile.getTotalSpace();
		long start = (long)(tspace * fdstart);
		long stop = (long)(tspace * fdstop);
		long cur = spoolfile.getUsableSpace();
		if (cur >= start) {
			return;
		}
		Vector<DelItem> cv = new Vector<DelItem>();
		for (String sdir: dqs.keySet()) {
			for (String meta: (new File(sdir)).list()) {
				if (!meta.endsWith(".M") || meta.charAt(0) == '.') {
					continue;
				}
				cv.add(new DelItem(meta.substring(0, meta.length() - 2), sdir));
			}
		}
		DelItem[] items = cv.toArray(new DelItem[cv.size()]);
		Arrays.sort(items);
		logger.info("NODE0501 Free disk space below red threshold.  current=" + cur + " red=" + start + " total=" + tspace);
		for (DelItem item: items) {
			long amount = dqs.get(item.getSpool()).cancelTask(item.getPublishId());
			logger.info("NODE0502 Attempting to discard " + item.getSpool() + "/" + item.getPublishId() + " to free up disk");
			if (amount > 0) {
				cur += amount;
				if (cur >= stop) {
					cur = spoolfile.getUsableSpace();
				}
				if (cur >= stop) {
					logger.info("NODE0503 Free disk space at or above yellow threshold.  current=" + cur + " yellow=" + stop + " total=" + tspace);
					return;
				}
			}
		}
		cur = spoolfile.getUsableSpace();
		if (cur >= stop) {
			logger.info("NODE0503 Free disk space at or above yellow threshold.  current=" + cur + " yellow=" + stop + " total=" + tspace);
			return;
		}
		logger.warn("NODE0504 Unable to recover sufficient disk space to reach green status.  current=" + cur + " yellow=" + stop + " total=" + tspace);
	}
	private void cleardirs() {
		String basedir = config.getSpoolBase();
		String nbase = basedir + "/n";
		for (String nodedir: (new File(nbase)).list()) {
			if (!nodedir.startsWith(".")) {
				cleardir(nbase + "/" + nodedir);
			}
		}
		String sxbase = basedir + "/s";
		for (String sxdir: (new File(sxbase)).list()) {
			if (sxdir.startsWith(".")) {
				continue;
			}
			File sxf = new File(sxbase + "/" + sxdir);
			for (String sdir: sxf.list()) {
				if (!sdir.startsWith(".")) {
					cleardir(sxbase + "/" + sxdir + "/" + sdir);
				}
			}
			sxf.delete();  // won't if anything still in it
		}
	}
	private synchronized void checkconfig() {
		if (!config.isConfigured()) {
			return;
		}
		fdstart = config.getFreeDiskStart();
		fdstop = config.getFreeDiskStop();
		threads = config.getDeliveryThreads();
		if (threads < 1) {
			threads = 1;
		}
		DestInfo[] alldis = config.getAllDests();
		DeliveryQueue[] nqs = new DeliveryQueue[alldis.length];
		qpos = 0;
		Hashtable<String, DeliveryQueue> ndqs = new Hashtable<String, DeliveryQueue>();
		for (DestInfo di: alldis) {
			String spl = di.getSpool();
			DeliveryQueue dq = dqs.get(spl);
			if (dq == null) {
				dq = new DeliveryQueue(config, di);
			} else {
				dq.config(di);
			}
			ndqs.put(spl, dq);
			nqs[qpos++] = dq;
		}
		queues = nqs;
		dqs = ndqs;
		cleardirs();
		while (curthreads < threads) {
			curthreads++;
			(new Thread() {
				{
					setName("Delivery Thread");
				}
				public void run() {
					dodelivery();
				}
			}).start();
		}
		nextcheck = 0;
		notify();
	}
	private void dodelivery() {
		DeliveryQueue dq;
		while ((dq = getNextQueue()) != null) {
			dq.run();
		}
	}
	private synchronized DeliveryQueue getNextQueue() {
		while (true) {
			if (curthreads > threads) {
				curthreads--;
				return(null);
			}
			if (qpos < queues.length) {
				DeliveryQueue dq = queues[qpos++];
				if (dq.isSkipSet()) {
					continue;
				}
				nextcheck = 0;
				notify();
				return(dq);
			}
			long now = System.currentTimeMillis();
			if (now < nextcheck) {
				try {
					wait(nextcheck + 500 - now);
				} catch (Exception e) {
				}
				now = System.currentTimeMillis();
			}
			if (now >= nextcheck) {
				nextcheck = now + 5000;
				qpos = 0;
				freeDiskCheck();
			}
		}
	}
	/**
	 *	Reset the retry timer for a delivery queue
	 */
	public synchronized void resetQueue(String spool) {
		if (spool != null) {
			DeliveryQueue dq = dqs.get(spool);
			if (dq != null) {
				dq.resetQueue();
			}
		}
	}
}
