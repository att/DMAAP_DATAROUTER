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

import org.json.JSONObject;

/**
 * This abstract class defines the "contract" for beans that can be sync-ed with the database,
 * by means of straight comparison.  The <i>getKey</i> method is used to return the primary key
 * used to identify a record.
 *
 * @author Robert Eby
 * @version $Id: Syncable.java,v 1.1 2013/07/05 13:48:05 eby Exp $
 */
public abstract class Syncable implements Deleteable, Insertable, Updateable, JSONable {
	@Override
	abstract public JSONObject asJSONObject();

	@Override
	abstract public boolean doUpdate(Connection c);

	@Override
	abstract public boolean doInsert(Connection c);

	@Override
	abstract public boolean doDelete(Connection c);

	/**
	 * Get the "natural key" for this object type, as a String.
	 * @return the key
	 */
	abstract public String getKey();
}
