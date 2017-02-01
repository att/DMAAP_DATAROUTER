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
 * $Id: AuthorizationResponse.java,v 1.1.1.1 2013/04/22 16:44:39 jfl Exp $
 */

package com.att.research.datarouter.authz;

import java.util.List;

/**
 * The <code>AuthorizationResponse</code> interface gives the caller access to information about an authorization
 * decision.  This information includes the permit/deny decision itself, along with supplementary information in the form of
 * advice and obligations.  (The advice and obligations will not be used in Data Router R1.)
 * 
 * @author J. F. Lucas
 *
 */
public interface AuthorizationResponse {
	/**
	 * Indicates whether the request is authorized or not.
	 * 
	 * @return a boolean flag that is <code>true</code> if the request is permitted, and <code>false</code> otherwise.
	 */
	public boolean isAuthorized();
	
	/**
	 * Returns any advice elements that were included in the authorization response.
	 * 
	 * @return A list of objects implementing the <code>AuthorizationResponseSupplement</code> interface, with each object representing an
	 * advice element from the authorization response.
	 */
	public List<AuthorizationResponseSupplement> getAdvice();
	
	/**
	 * Returns any obligation elements that were included in the authorization response.
	 * 
	 * @return A list of objects implementing the <code>AuthorizationResponseSupplement</code> interface, with each object representing an
	 * obligation element from the authorization response.
	 */
	public List<AuthorizationResponseSupplement> getObligations();
}
