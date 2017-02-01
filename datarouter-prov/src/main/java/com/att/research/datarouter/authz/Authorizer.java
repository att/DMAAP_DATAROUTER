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
 * $Id: Authorizer.java,v 1.1.1.1 2013/04/22 16:44:39 jfl Exp $
 */

package com.att.research.datarouter.authz;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * A Data Router API that requires authorization of incoming requests creates an instance of a class that implements
 * the <code>Authorizer</code> interface.   The class implements all of the logic necessary to determine if an API
 * request is permitted.  In Data Router R1, the classes that implement the <code>Authorizer</code> interface will have
 * local logic that makes the authorization decision.  After R1, these classes will instead have logic that creates XACML
 * authorization requests, sends these requests to a Policy Decision Point (PDP), and parses the XACML responses.
 * 
 * @author J. F. Lucas
 *
 */
public interface Authorizer {
	/**
	 * Determine if the API request carried in the <code>request</code> parameter is permitted.
	 * 
	 * @param request the HTTP request for which an authorization decision is needed
	 * @return an object implementing the <code>AuthorizationResponse</code> interface.  This object includes the
	 * permit/deny decision for the request and (after R1) supplemental information related to the response in the form
	 * of advice and obligations.
	 */
	public AuthorizationResponse decide(HttpServletRequest request);
	
	/**
	 * Determine if the API request carried in the <code>request</code> parameter, with additional attributes provided in
	 * the <code>additionalAttrs</code> parameter, is permitted.
	 * 
	 * @param request the HTTP request for which an authorization decision is needed
	 * @param additionalAttrs additional attributes that the <code>Authorizer</code> can in making an authorization decision
	 * @return an object implementing the <code>AuthorizationResponse</code> interface.  This object includes the
	 * permit/deny decision for the request and (after R1) supplemental information related to the response in the form
	 * of advice and obligations.
	 */
	public AuthorizationResponse decide(HttpServletRequest request, Map<String,String> additionalAttrs);
}
