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
  * $Id: NodeUtils.java,v 1.5 2013/04/29 12:40:05 agg Exp $
  */

package com.att.research.datarouter.node;

import static com.att.eelf.configuration.Configuration.MDC_SERVER_FQDN;
import static com.att.eelf.configuration.Configuration.MDC_SERVER_IP_ADDRESS;
import static com.att.eelf.configuration.Configuration.MDC_SERVICE_NAME;

import java.security.*;
import java.io.*;
import java.util.*;
import java.security.cert.*;
import java.net.*;
import java.text.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.slf4j.MDC;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.research.datarouter.node.eelf.EelfMsgs;

/**
 *	Utility functions for the data router node
 */
public class NodeUtils	{
    private static EELFLogger eelflogger = EELFManager.getInstance().getLogger("com.att.research.datarouter.node.NodeUtils");
	private static Logger logger = Logger.getLogger("com.att.research.datarouter.node.NodeUtils");
	private static SimpleDateFormat	logdate;
	static {
		logdate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		logdate.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	private NodeUtils() {}
	/**
	 *	Base64 encode a byte array
	 *	@param raw	The bytes to be encoded
	 *	@return	The encoded string
	 */
	public static String base64Encode(byte[] raw) {
		return(Base64.encodeBase64String(raw));
	}
	/**
	 *	Given a user and password, generate the credentials
	 *	@param user	User name
	 *	@param password	User password
	 *	@return	Authorization header value
	 */
	public static String getAuthHdr(String user, String password) {
		if (user == null || password == null) {
			return(null);
		}
		return("Basic " + base64Encode((user + ":" + password).getBytes()));
	}
	/**
	 *	Given a node name, generate the credentials
	 *	@param node	Node name
	 */
	public static String	getNodeAuthHdr(String node, String key) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA");
			md.update(key.getBytes());
			md.update(node.getBytes());
			md.update(key.getBytes());
			return(getAuthHdr(node, base64Encode(md.digest())));
		} catch (Exception e) {
			return(null);
		}
	}
	/**
	 *	Given a keystore file and its password, return the value of the CN of the first private key entry with a certificate.
	 *	@param kstype	The type of keystore
	 *	@param ksfile	The file name of the keystore
	 *	@param kspass	The password of the keystore
	 *	@return	CN of the certificate subject or null
	 */
	public static String getCanonicalName(String kstype, String ksfile, String kspass) {
		try {
			KeyStore ks = KeyStore.getInstance(kstype);
			ks.load(new FileInputStream(ksfile), kspass.toCharArray());
			return(getCanonicalName(ks));
		} catch (Exception e) {
			setIpAndFqdnForEelf("getCanonicalName");
			eelflogger.error(EelfMsgs.MESSAGE_KEYSTORE_LOAD_ERROR, ksfile, e.toString());
			logger.error("NODE0401 Error loading my keystore file + " + ksfile + " " + e.toString(), e);
			return(null);
		}
	}
	/**
	 *	Given a keystore, return the value of the CN of the first private key entry with a certificate.
	 *	@param ks	The KeyStore
	 *	@return	CN of the certificate subject or null
	 */
	public static String getCanonicalName(KeyStore ks) {
		try {
			Enumeration<String> aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				String s = aliases.nextElement();
				if (ks.entryInstanceOf(s, KeyStore.PrivateKeyEntry.class)) {
					X509Certificate c = (X509Certificate)ks.getCertificate(s);
					if (c != null) {
						String subject = c.getSubjectX500Principal().getName();
						String[] parts = subject.split(",");
						if (parts.length < 1) {
							return(null);
						}
						subject = parts[0].trim();
						if (!subject.startsWith("CN=")) {
							return(null);

						}
						return(subject.substring(3));
					}
				}
			}
		} catch (Exception e) {
			logger.error("NODE0402 Error extracting my name from my keystore file " + e.toString(), e);
		}
		return(null);
	}
	/**
	 *	Given a string representation of an IP address, get the corresponding byte array
	 *	@param ip	The IP address as a string
	 *	@return	The IP address as a byte array or null if the address is invalid
	 */
	public static byte[] getInetAddress(String ip) {
		try {
			return(InetAddress.getByName(ip).getAddress());
		} catch (Exception e) {
		}
		return(null);
	}
	/**
	 *	Given a uri with parameters, split out the feed ID and file ID
	 */
	public static String[] getFeedAndFileID(String uriandparams) {
		int end = uriandparams.length();
		int i = uriandparams.indexOf('#');
		if (i != -1 && i < end) {
			end = i;
		}
		i = uriandparams.indexOf('?');
		if (i != -1 && i < end) {
			end = i;
		}
		end = uriandparams.lastIndexOf('/', end);
		if (end < 2) {
			return(null);
		}
		i = uriandparams.lastIndexOf('/', end - 1);
		if (i == -1) {
			return(null);
		}
		return(new String[] { uriandparams.substring(i + 1, end - 1), uriandparams.substring(end + 1) });
	}
	/**
	 *	Escape fields that might contain vertical bar, backslash, or newline by replacing them with backslash p, backslash e and backslash n.
	 */
	public static String loge(String s) {
		if (s == null) {
			return(s);
		}
		return(s.replaceAll("\\\\", "\\\\e").replaceAll("\\|", "\\\\p").replaceAll("\n", "\\\\n"));
	}
	/**
	 *	Undo what loge does.
	 */
	public static String unloge(String s) {
		if (s == null) {
			return(s);
		}
		return(s.replaceAll("\\\\p", "\\|").replaceAll("\\\\n", "\n").replaceAll("\\\\e", "\\\\"));
	}
	/**
	 *	Format a logging timestamp as yyyy-mm-ddThh:mm:ss.mmmZ
	 */
	public static String logts(long when) {
		return(logts(new Date(when)));
	}
	/**
	 *	Format a logging timestamp as yyyy-mm-ddThh:mm:ss.mmmZ
	 */
	public static synchronized String logts(Date when) {
		return(logdate.format(when));
	}
	
	/* Method prints method name, server FQDN and IP Address of the machine in EELF logs
	 * @Method - setIpAndFqdnForEelf - Rally:US664892  
	 * @Params - method, prints method name in EELF log.
	 */	
	public static void setIpAndFqdnForEelf(String method) {
	 	MDC.clear();
        MDC.put(MDC_SERVICE_NAME, method);
        try {
            MDC.put(MDC_SERVER_FQDN, InetAddress.getLocalHost().getHostName());
            MDC.put(MDC_SERVER_IP_ADDRESS, InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }

	}
	

}
