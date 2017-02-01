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

import java.security.cert.X509Certificate;

import javax.servlet.http.HttpServletRequest;

import com.att.research.datarouter.provisioning.BaseServlet;

/**
 * This class is used to log provisioning server events.  Each event consists of a who
 * (who made the provisioning request including the IP address, the X-ATT-DR-ON-BEHALF-OF
 * header value, and the client certificate), a what (what request was made; the method
 * and servlet involved), and a how (how the request was handled; the result code and
 * message returned to the client).  EventLogRecords are logged using log4j at the INFO level.
 *
 * @author Robert Eby
 * @version $Id: EventLogRecord.java,v 1.1 2013/04/26 21:00:25 eby Exp $
 */
public class EventLogRecord {
	private final String ipaddr;		// Who
	private final String behalfof;
	private final String clientSubject;
	private final String method;		// What
	private final String servlet;
	private int result;					// How
	private String message;

	public EventLogRecord(HttpServletRequest request) {
		// Who is making the request
		this.ipaddr = request.getRemoteAddr();
		String s = request.getHeader(BaseServlet.BEHALF_HEADER);
		this.behalfof = (s != null) ? s : "";
		X509Certificate certs[] = (X509Certificate[]) request.getAttribute(BaseServlet.CERT_ATTRIBUTE);
		this.clientSubject = (certs != null && certs.length > 0)
			? certs[0].getSubjectX500Principal().getName() : "";

		// What is the request
		this.method  = request.getMethod();
		this.servlet = request.getServletPath();

		// How was it dealt with
		this.result = -1;
		this.message = "";
	}
	public void setResult(int result) {
		this.result = result;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	@Override
	public String toString() {
		return String.format(
			"%s %s \"%s\" %s %s %d \"%s\"",
			ipaddr, behalfof, clientSubject,
			method, servlet,
			result, message
		);
	}
}
