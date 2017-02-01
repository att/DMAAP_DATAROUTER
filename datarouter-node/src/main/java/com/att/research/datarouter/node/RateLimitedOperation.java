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
  * $Id: RateLimitedOperation.java,v 1.2 2013/04/23 17:42:28 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.*;

/**
 *	Execute an operation no more frequently than a specified interval
 */

public abstract class RateLimitedOperation implements Runnable	{
	private boolean	marked;	// a timer task exists
	private boolean	executing;	// the operation is currently in progress
	private boolean remark;	// a request was made while the operation was in progress
	private Timer	timer;
	private long	last;	// when the last operation started
	private long	mininterval;
	/**
	 *	Create a rate limited operation
	 *	@param mininterval	The minimum number of milliseconds after the last execution starts before a new execution can begin
	 *	@param timer	The timer used to perform deferred executions
	 */
	public RateLimitedOperation(long mininterval, Timer timer) {
		this.timer = timer;
		this.mininterval = mininterval;
	}
	private class deferred extends TimerTask	{
		public void run() {
			execute();
		}
	}
	private synchronized void unmark() {
		marked = false;
	}
	private void execute() {
		unmark();
		request();
	}
	/**
	 *	Request that the operation be performed by this thread or at a later time by the timer
	 */
	public void request() {
		if (premark()) {
			return;
		}
		do {
			run();
		} while (demark());
	}
	private synchronized boolean premark() {
		if (executing) {
			// currently executing - wait until it finishes
			remark = true;
			return(true);
		}
		if (marked) {
			// timer currently running - will run when it expires
			return(true);
		}
		long now = System.currentTimeMillis();
		if (last + mininterval > now) {
			// too soon - schedule a timer
			marked = true;
			timer.schedule(new deferred(), last + mininterval - now);
			return(true);
		}
		last = now;
		executing = true;
		// start execution
		return(false);
	}
	private synchronized boolean demark() {
		executing = false;
		if (remark) {
			remark = false;
			return(!premark());
		}
		return(false);
	}
}
