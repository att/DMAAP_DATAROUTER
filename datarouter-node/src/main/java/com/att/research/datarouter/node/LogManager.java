package com.att.research.datarouter.node;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.file.*;
import java.text.*;

/**
 *	Cleanup of old log files.
 *	<p>
 *	Periodically scan the log directory for log files that are older than
 *	the log file retention interval, and delete them.  In a future release,
 *	This class will also be responsible for uploading events logs to the
 *	log server to support the log query APIs.
 */

public class LogManager	extends TimerTask	{
	private NodeConfigManager	config;
	private Matcher	isnodelog;
	private Matcher	iseventlog;
	private Uploader	worker;
	private String	uploaddir;
	private String	logdir;
	private class Uploader extends Thread implements DeliveryQueueHelper {
		public long getInitFailureTimer() { return(10000L); }
		public double getFailureBackoff() { return(2.0); }
		public long getMaxFailureTimer() { return(150000L); }
		public long getExpirationTimer() { return(604800000L); }
		public int getFairFileLimit() { return(10000); }
		public long getFairTimeLimit() { return(86400000); }
		public String getDestURL(DestInfo dest, String fileid) {
			return(config.getEventLogUrl());
		}
		public void handleUnreachable(DestInfo dest) {}
		public boolean handleRedirection(DestInfo dest, String location, String fileid) { return(false); }
		public boolean isFollowRedirects() { return(false); }
		public String getFeedId(String subid) { return(null); }
		private DeliveryQueue dq;
		public Uploader() {
			dq = new DeliveryQueue(this, new DestInfo("LogUpload", uploaddir, null, null, null, config.getMyName(), config.getMyAuth(), false, false));
			setDaemon(true);
			setName("Log Uploader");
			start();
		}
		private synchronized void snooze() {
			try {
				wait(10000);
			} catch (Exception e) {
			}
		}
		private synchronized void poke() {
			notify();
		}
		public void run() {
			while (true) {
				scan();
				dq.run();
				snooze();
			}
		}
		private void scan() {
			long threshold = System.currentTimeMillis() - config.getLogRetention();
			File dir = new File(logdir);
			String[] fns = dir.list();
			Arrays.sort(fns);
			String lastqueued = "events-000000000000.log";
			String curlog = StatusLog.getCurLogFile();
			curlog = curlog.substring(curlog.lastIndexOf('/') + 1);
			try {
				Writer w = new FileWriter(uploaddir + "/.meta");
				w.write("POST\tlogdata\nContent-Type\ttext/plain\n");
				w.close();
				BufferedReader br = new BufferedReader(new FileReader(uploaddir + "/.lastqueued"));
				lastqueued = br.readLine();
				br.close();
			} catch (Exception e) {
			}
			for (String fn: fns) {
				if (!isnodelog.reset(fn).matches()) {
					if (!iseventlog.reset(fn).matches()) {
						continue;
					}
					if (lastqueued.compareTo(fn) < 0 && curlog.compareTo(fn) > 0) {
						lastqueued = fn;
						try {
							String pid = config.getPublishId();
							Files.createLink(Paths.get(uploaddir + "/" + pid), Paths.get(logdir + "/" + fn));
							Files.createLink(Paths.get(uploaddir + "/" + pid + ".M"), Paths.get(uploaddir + "/.meta"));
						} catch (Exception e) {
						}
					}
				}
				File f = new File(dir, fn);
				if (f.lastModified() < threshold) {
					f.delete();
				}
			}
			try {
				(new File(uploaddir + "/.meta")).delete();
				Writer w = new FileWriter(uploaddir + "/.lastqueued");
				w.write(lastqueued + "\n");
				w.close();
			} catch (Exception e) {
			}
		}
	}
	/**
	 *	Construct a log manager
	 *	<p>
	 *	The log manager will check for expired log files every 5 minutes
	 *	at 20 seconds after the 5 minute boundary.  (Actually, the
	 *	interval is the event log rollover interval, which
	 *	defaults to 5 minutes).
	 */
	public LogManager(NodeConfigManager config) {
		this.config = config;
		try {
			isnodelog = Pattern.compile("node\\.log\\.\\d{8}").matcher("");
			iseventlog = Pattern.compile("events-\\d{12}\\.log").matcher("");
		} catch (Exception e) {}
		logdir = config.getLogDir();
		uploaddir = logdir + "/.spool";
		(new File(uploaddir)).mkdirs();
		long now = System.currentTimeMillis();
		long intvl = StatusLog.parseInterval(config.getEventLogInterval(), 300000);
		long when = now - now % intvl + intvl + 20000L;
		config.getTimer().scheduleAtFixedRate(this, when - now, intvl);
		worker = new Uploader();
	}
	/**
	 *	Trigger check for expired log files and log files to upload
	 */
	public void run() {
		worker.poke();
	}
}
