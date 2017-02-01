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
 * $Id: AuthorizationResponseSupplement.java,v 1.1.1.1 2013/04/22 16:44:39 jfl Exp $
 */

package com.att.research.datarouter.authz;

import java.util.Map;

/** An object that meets the <code>AuthorizationResponseSupplement</code> interface carries supplementary
 * information for an authorization response.  In a XACML-based system, a response to an authorization request
 * carries not just the permit/deny decision but, optionally, supplemental information in the form of advice and
 * obligation elements.  The structure of a XACML advice element and a XACML obligation element are similar: each has an identifier and
 * a set of attributes (name-value) pairs.  (The difference between a XACML advice element and a XACML obligation element is in
 * how the recipient of the response--the Policy Enforcement Point, in XACML terminology--handles the element.)
 * 
 * @author J. F. Lucas
 *
 */
public interface AuthorizationResponseSupplement {
	/** Return the identifier for the supplementary information element.
	 * 
	 * @return a <code>String</code> containing the identifier.
	 */
	public String getId();
	
	/** Return the attributes for the supplementary information element, as a <code>Map</code> in which
	 * keys represent attribute identifiers and values represent attribute values.
	 * 
	 * @return attributes for the supplementary information element.
	 */
	public Map<String, String> getAttributes();
}
