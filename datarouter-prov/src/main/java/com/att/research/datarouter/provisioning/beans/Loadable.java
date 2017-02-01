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

package com.att.research.datarouter.provisioning.beans;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.att.research.datarouter.provisioning.utils.LogfileLoader;

/**
 * This interface is used by bean classes that can be loaded into the LOG_RECORDS table using the
 * PreparedStatement at {@link LogfileLoader}.INSERT_SQL.
 *
 * @author Robert Eby
 * @version $Id: Loadable.java,v 1.2 2013/08/06 13:28:33 eby Exp $
 */
public interface Loadable {
	/**
	 * Load the 18 fields in the PreparedStatement <i>ps</i>. The fields are:
	 * <ol>
	 * <li>type (String)</li>
	 * <li>event_time (long)</li>
	 * <li>publish ID (String)</li>
	 * <li>feed ID (int)</li>
	 * <li>request URI (String)</li>
	 * <li>method (String)</li>
	 * <li>content type (String)</li>
	 * <li>content length (long)</li>
	 * <li>feed File ID (String)</li>
	 * <li>remote address (String)</li>
	 * <li>user (String)</li>
	 * <li>status (int)</li>
	 * <li>delivery subscriber id (int)</li>
	 * <li>delivery File ID (String)</li>
	 * <li>result (int)</li>
	 * <li>attempts (int)</li>
	 * <li>reason (String)</li>
	 * <li>record ID (long)</li>
	 * </ol>
	 * @param ps the PreparedStatement to load
	 */
	public void load(PreparedStatement ps) throws SQLException;
}
