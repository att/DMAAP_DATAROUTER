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
 * $Id: AuthRespSupplementImpl.java,v 1.1.1.1 2013/04/22 16:47:09 jfl Exp $
 */

package com.att.research.datarouter.authz.impl;

import java.util.HashMap;
import java.util.Map;

import com.att.research.datarouter.authz.AuthorizationResponseSupplement;

/** Carries supplementary information--an advice or an obligation--from the authorization response returned
 *  by a XACML Policy Decision Point.   Not used in Data Router R1.
 * @author J. F. Lucas
 *
 */
public class AuthRespSupplementImpl implements AuthorizationResponseSupplement {
	
	private String id = null;
	private Map<String, String> attributes = null;

	/** Constructor, available within the package.
	 * 
	 * @param id  The identifier for the advice or obligation element
	 * @param attributes The attributes (name-value pairs) for the advice or obligation element.
	 */
	AuthRespSupplementImpl (String id, Map<String, String> attributes) {
		this.id = id;
		this.attributes = new HashMap<String,String>(attributes);
	}

	/** Return the identifier for the supplementary information element.
	 * 
	 * @return a <code>String</code> containing the identifier.
	 */
	@Override
	public String getId() {
		return id;
	}

	/** Return the attributes for the supplementary information element, as a <code>Map</code> in which
	 * keys represent attribute identifiers and values represent attribute values.
	 * 
	 * @return attributes for the supplementary information element.
	 */
	@Override
	public Map<String, String> getAttributes() {
		return attributes;
	}

}
