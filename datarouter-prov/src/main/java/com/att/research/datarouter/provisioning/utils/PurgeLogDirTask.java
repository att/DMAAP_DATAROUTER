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

package com.att.research.datarouter.provisioning.utils;

import java.io.File;
import java.util.Properties;
import java.util.TimerTask;

/**
 * This class provides a {@link TimerTask} that purges old logfiles
 * (older than the number of days specified by the com.att.research.datarouter.provserver.logretention property).
 * @author Robert Eby
 * @version $Id: PurgeLogDirTask.java,v 1.2 2013/07/05 13:48:05 eby Exp $
 */
public class PurgeLogDirTask extends TimerTask {
	private static final long ONEDAY = 86400000L;

	private final String logdir;
	private final long interval;

	public PurgeLogDirTask() {
		Properties p = (new DB()).getProperties();
		logdir   = p.getProperty("com.att.research.datarouter.provserver.accesslog.dir");
		String s = p.getProperty("com.att.research.datarouter.provserver.logretention", "30");
		long n = 30;
		try {
			n = Long.parseLong(s);
		} catch (NumberFormatException e) {
			// ignore
		}
		interval = n * ONEDAY;
	}
	@Override
	public void run() {
		try {
			File dir = new File(logdir);
			if (dir.exists()) {
				long exptime = System.currentTimeMillis() - interval;
				for (File logfile : dir.listFiles()) {
					if (logfile.lastModified() < exptime)
						logfile.delete();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
