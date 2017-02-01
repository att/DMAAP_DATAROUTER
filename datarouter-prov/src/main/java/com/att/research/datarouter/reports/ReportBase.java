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

package com.att.research.datarouter.reports;

import org.apache.log4j.Logger;

/**
 * Base class for all the report generating classes.
 *
 * @author Robert P. Eby
 * @version $Id: ReportBase.java,v 1.1 2013/10/28 18:06:53 eby Exp $
 */
abstract public class ReportBase implements Runnable {
	protected long from, to;
	protected String outfile;
	protected Logger logger;

	public ReportBase() {
		this.from = 0;
		this.to = System.currentTimeMillis();
		this.logger = Logger.getLogger("com.att.research.datarouter.reports");
	}

	public void setFrom(long from) {
		this.from = from;
	}

	public void setTo(long to) {
		this.to = to;
	}

	public String getOutfile() {
		return outfile;
	}

	public void setOutputFile(String s) {
		this.outfile = s;
	}

	@Override
	abstract public void run();
}
