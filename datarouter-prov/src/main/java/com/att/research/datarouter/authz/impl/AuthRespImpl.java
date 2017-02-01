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
 * $Id: AuthRespImpl.java,v 1.1.1.1 2013/04/22 16:47:09 jfl Exp $
 */

package com.att.research.datarouter.authz.impl;

import java.util.ArrayList;
import java.util.List;

import com.att.research.datarouter.authz.AuthorizationResponse;
import com.att.research.datarouter.authz.AuthorizationResponseSupplement;


/** A representation of an authorization response returned by a XACML Policy Decision Point.
 *  In Data Router R1, advice and obligations are not used.
 * @author J. F. Lucas
 *
 */
public class AuthRespImpl implements AuthorizationResponse {
	private boolean authorized;
	private List<AuthorizationResponseSupplement> advice;
	private List<AuthorizationResponseSupplement> obligations;
	
	/** Constructor.  This version will not be used in Data Router R1 since we will not have advice and obligations.
	 * 
	 * @param authorized flag indicating whether the response carried a permit response (<code>true</code>) 
	 * or something else (<code>false</code>).
	 * @param advice list of advice elements returned in the response.
	 * @param obligations list of obligation elements returned in the response.
	 */
	public AuthRespImpl(boolean authorized, List<AuthorizationResponseSupplement> advice, List<AuthorizationResponseSupplement> obligations) {
		this.authorized = authorized;
		this.advice = (advice == null ? null : new ArrayList<AuthorizationResponseSupplement> (advice));
		this.obligations = (obligations == null ? null : new ArrayList<AuthorizationResponseSupplement> (obligations));
	}
	
	/** Constructor.  Simple version for authorization responses that have no advice and no obligations.
	 * 
	 * @param authorized flag indicating whether the response carried a permit (<code>true</code>) or something else (<code>false</code>).
	 */
	public AuthRespImpl(boolean authorized) {
		this(authorized, null, null);
	}

	/**
	 * Indicates whether the request is authorized or not.
	 * 
	 * @return a boolean flag that is <code>true</code> if the request is permitted, and <code>false</code> otherwise.
	 */
	@Override
	public boolean isAuthorized() {
			return authorized;
	}

	/**
	 * Returns any advice elements that were included in the authorization response.
	 * 
	 * @return A list of objects implementing the <code>AuthorizationResponseSupplement</code> interface, with each object representing an
	 * advice element from the authorization response.
	 */
	@Override
	public List<AuthorizationResponseSupplement> getAdvice() {
			return advice;
	}

	/**
	 * Returns any obligation elements that were included in the authorization response.
	 * 
	 * @return A list of objects implementing the <code>AuthorizationResponseSupplement</code> interface, with each object representing an
	 * obligation element from the authorization response.
	 */
	@Override
	public List<AuthorizationResponseSupplement> getObligations() {
		return obligations;
	}

}
