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
  * $Id: TaskList.java,v 1.3 2013/05/01 15:28:37 agg Exp $
  */

package com.att.research.datarouter.node;

import java.util.*;

/**
 *	Manage a list of tasks to be executed when an event occurs.
 *	This makes the following guarantees:
 *	<ul>
 *	<li>Tasks can be safely added and removed in the middle of a run.</li>
 *	<li>No task will be returned more than once during a run.</li>
 *	<li>No task will be returned when it is not, at that moment, in the list of tasks.</li>
 *	<li>At the moment when next() returns null, all tasks on the list have been returned during the run.</li>
 *	<li>Initially and once next() returns null during a run, next() will continue to return null until startRun() is called.
 *	</ul>
 */
public class TaskList	{
	private Iterator<Runnable>	runlist;
	private HashSet<Runnable>	tasks = new HashSet<Runnable>();
	private HashSet<Runnable>	togo;
	private HashSet<Runnable>	sofar;
	private HashSet<Runnable>	added;
	private HashSet<Runnable>	removed;
	/**
	 *	Construct a new TaskList
	 */
	public TaskList() {
	}
	/**
	 *	Start executing the sequence of tasks.
	 */
	public synchronized void	startRun() {
		sofar = new HashSet<Runnable>();
		added = new HashSet<Runnable>();
		removed = new HashSet<Runnable>();
		togo = new HashSet<Runnable>(tasks);
		runlist = togo.iterator();
	}
	/**
	 *	Get the next task to execute
	 */
	public synchronized Runnable	next() {
		while (runlist != null) {
			if (runlist.hasNext()) {
				Runnable task = runlist.next();
				if (removed.contains(task)) {
					continue;
				}
				if (sofar.contains(task)) {
					continue;
				}
				sofar.add(task);
				return(task);
			}
			if (added.size() != 0) {
				togo = added;
				added = new HashSet<Runnable>();
				removed.clear();
				runlist = togo.iterator();
				continue;
			}
			togo = null;
			added = null;
			removed = null;
			sofar = null;
			runlist = null;
		}
		return(null);
	}
	/**
	 *	Add a task to the list of tasks to run whenever the event occurs.
	 */
	public synchronized void addTask(Runnable task) {
		if (runlist != null) {
			added.add(task);
			removed.remove(task);
		}
		tasks.add(task);
	}
	/**
	 *	Remove a task from the list of tasks to run whenever the event occurs.
	 */
	public synchronized void removeTask(Runnable task) {
		if (runlist != null) {
			removed.add(task);
			added.remove(task);
		}
		tasks.remove(task);
	}
}
