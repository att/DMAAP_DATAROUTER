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

import java.sql.Connection;

/**
 * An object that can be UPDATE-ed in the database.
 * @author Robert Eby
 * @version $Id: Updateable.java,v 1.2 2013/05/29 14:44:36 eby Exp $
 */
public interface Updateable {
	/**
	 * Update this object in the DB.
	 * @param c the JDBC Connection to use
	 * @return true if the UPDATE succeeded, false otherwise
	 */
	public boolean doUpdate(Connection c);
}
