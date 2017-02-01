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

import org.json.LOGJSONObject;

/**
 * An object that can be represented as a {@link JSONObject}.
 * @author Robert Eby
 * @version $Id: JSONable.java,v 1.1 2013/04/26 21:00:26 eby Exp $
 */
public interface LOGJSONable {
	/**
	 * Get a JSONObject representing this object.
	 * @return the JSONObject
	 */
	public LOGJSONObject asJSONObject();
}
