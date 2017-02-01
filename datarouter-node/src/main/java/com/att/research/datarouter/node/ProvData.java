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
  * $Id: ProvData.java,v 1.7 2013/10/29 19:56:36 agg Exp $
  */

package com.att.research.datarouter.node;

import java.io.*;
import java.util.*;
import org.json.*;
import org.apache.log4j.Logger;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.research.datarouter.node.eelf.EelfMsgs;

/**
 *	Parser for provisioning data from the provisioning server.
 *	<p>
 *	The ProvData class uses a Reader for the text configuration from the
 *	provisioning server to construct arrays of raw configuration entries.
 */
public class ProvData	{
    private static EELFLogger eelflogger = EELFManager.getInstance().getLogger("com.att.research.datarouter.node.ProvData");
	private static Logger logger = Logger.getLogger("com.att.research.datarouter.node.ProvData");
	private NodeConfig.ProvNode[]	pn;
	private NodeConfig.ProvParam[]	pp;
	private NodeConfig.ProvFeed[]	pf;
	private NodeConfig.ProvFeedUser[]	pfu;
	private NodeConfig.ProvFeedSubnet[]	pfsn;
	private NodeConfig.ProvSubscription[]	ps;
	private NodeConfig.ProvForceIngress[]	pfi;
	private NodeConfig.ProvForceEgress[]	pfe;
	private NodeConfig.ProvHop[]	ph;
	private static String[] gvasa(JSONArray a, int index) {
		return(gvasa(a.get(index)));
	}
	private static String[] gvasa(JSONObject o, String key) {
		return(gvasa(o.opt(key)));
	}
	private static String[] gvasa(Object o) {
		if (o instanceof JSONArray) {
			JSONArray a = (JSONArray)o;
			Vector<String> v = new Vector<String>();
			for (int i = 0; i < a.length(); i++) {
				String s = gvas(a, i);
				if (s != null) {
					v.add(s);
				}
			}
			return(v.toArray(new String[v.size()]));
		} else {
			String s = gvas(o);
			if (s == null) {
				return(new String[0]);
			} else {
				return(new String[] { s });
			}
		}
	}
	private static String gvas(JSONArray a, int index) {
		return(gvas(a.get(index)));
	}
	private static String gvas(JSONObject o, String key) {
		return(gvas(o.opt(key)));
	}
	private static String gvas(Object o) {
		if (o instanceof Boolean || o instanceof Number || o instanceof String) {
			return(o.toString());
		}
		return(null);
	}
	/**
	 *	Construct raw provisioing data entries from the text (JSON)
	 *	provisioning document received from the provisioning server
	 *	@param r	The reader for the JSON text.
	 */
	public ProvData(Reader r) throws IOException {
		Vector<NodeConfig.ProvNode> pnv = new Vector<NodeConfig.ProvNode>();
		Vector<NodeConfig.ProvParam> ppv = new Vector<NodeConfig.ProvParam>();
		Vector<NodeConfig.ProvFeed> pfv = new Vector<NodeConfig.ProvFeed>();
		Vector<NodeConfig.ProvFeedUser> pfuv = new Vector<NodeConfig.ProvFeedUser>();
		Vector<NodeConfig.ProvFeedSubnet> pfsnv = new Vector<NodeConfig.ProvFeedSubnet>();
		Vector<NodeConfig.ProvSubscription> psv = new Vector<NodeConfig.ProvSubscription>();
		Vector<NodeConfig.ProvForceIngress> pfiv = new Vector<NodeConfig.ProvForceIngress>();
		Vector<NodeConfig.ProvForceEgress> pfev = new Vector<NodeConfig.ProvForceEgress>();
		Vector<NodeConfig.ProvHop> phv = new Vector<NodeConfig.ProvHop>();
		try {
			JSONTokener jtx = new JSONTokener(r);
			JSONObject jcfg = new JSONObject(jtx);
			char c = jtx.nextClean();
			if (c != '\0') {
				throw new JSONException("Spurious characters following configuration");
			}
			r.close();
			JSONArray jfeeds = jcfg.optJSONArray("feeds");
			if (jfeeds != null) {
				for (int fx = 0; fx < jfeeds.length(); fx++) {
					JSONObject jfeed = jfeeds.getJSONObject(fx);
					String stat = null;
					if (jfeed.optBoolean("suspend", false)) {
						stat = "Feed is suspended";
					}
					if (jfeed.optBoolean("deleted", false)) {
						stat = "Feed is deleted";
					}
					String fid = gvas(jfeed, "feedid");
					String fname = gvas(jfeed, "name");
					String fver = gvas(jfeed, "version");
					pfv.add(new NodeConfig.ProvFeed(fid, fname + "//" + fver, stat));
					JSONObject jauth = jfeed.optJSONObject("authorization");
					if (jauth == null) {
						continue;
					}
					JSONArray jeids = jauth.optJSONArray("endpoint_ids");
					if (jeids != null) {
						for (int ux = 0; ux < jeids.length(); ux++) {
							JSONObject ju = jeids.getJSONObject(ux);
							String login = gvas(ju, "id");
							String password = gvas(ju, "password");
							pfuv.add(new NodeConfig.ProvFeedUser(fid, login, NodeUtils.getAuthHdr(login, password)));
						}
					}
					JSONArray jeips = jauth.optJSONArray("endpoint_addrs");
					if (jeips != null) {
						for (int ix = 0; ix < jeips.length(); ix++) {
							String sn = gvas(jeips, ix);
							pfsnv.add(new NodeConfig.ProvFeedSubnet(fid, sn));
						}
					}
				}
			}
			JSONArray jsubs = jcfg.optJSONArray("subscriptions");
			if (jsubs != null) {
				for (int sx = 0; sx < jsubs.length(); sx++) {
					JSONObject jsub = jsubs.getJSONObject(sx);
					if (jsub.optBoolean("suspend", false)) {
						continue;
					}
					String sid = gvas(jsub, "subid");
					String fid = gvas(jsub, "feedid");
					JSONObject jdel = jsub.getJSONObject("delivery");
					String delurl = gvas(jdel, "url");
					String id = gvas(jdel, "user");
					String password = gvas(jdel, "password");
					boolean monly = jsub.getBoolean("metadataOnly");
					boolean use100 = jdel.getBoolean("use100");
					psv.add(new NodeConfig.ProvSubscription(sid, fid, delurl, id, NodeUtils.getAuthHdr(id, password), monly, use100));
				}
			}
			JSONObject jparams = jcfg.optJSONObject("parameters");
			if (jparams != null) {
				for (String pname: JSONObject.getNames(jparams)) {
					String pvalue = gvas(jparams, pname);
					if (pvalue != null) {
						ppv.add(new NodeConfig.ProvParam(pname, pvalue));
					}
				}
				String sfx = gvas(jparams, "PROV_DOMAIN");
				JSONArray jnodes = jparams.optJSONArray("NODES");
				if (jnodes != null) {
					for (int nx = 0; nx < jnodes.length(); nx++) {
						String nn = gvas(jnodes, nx);
						if (nn.indexOf('.') == -1) {
							nn = nn + "." + sfx;
						}
						pnv.add(new NodeConfig.ProvNode(nn));
					}
				}
			}
			JSONArray jingresses = jcfg.optJSONArray("ingress");
			if (jingresses != null) {
				for (int fx = 0; fx < jingresses.length(); fx++) {
					JSONObject jingress = jingresses.getJSONObject(fx);
					String fid = gvas(jingress, "feedid");
					String subnet = gvas(jingress, "subnet");
					String user = gvas(jingress, "user");
					String[] nodes = gvasa(jingress, "node");
					if (fid == null || "".equals(fid)) {
						continue;
					}
					if ("".equals(subnet)) {
						subnet = null;
					}
					if ("".equals(user)) {
						user = null;
					}
					pfiv.add(new NodeConfig.ProvForceIngress(fid, subnet, user, nodes));
				}
			}
			JSONObject jegresses = jcfg.optJSONObject("egress");
			if (jegresses != null && JSONObject.getNames(jegresses) != null) {
				for (String esid: JSONObject.getNames(jegresses)) {
					String enode = gvas(jegresses, esid);
					if (esid != null && enode != null && !"".equals(esid) && !"".equals(enode)) {
						pfev.add(new NodeConfig.ProvForceEgress(esid, enode));
					}
				}
			}
			JSONArray jhops = jcfg.optJSONArray("routing");
			if (jhops != null) {
				for (int fx = 0; fx < jhops.length(); fx++) {
					JSONObject jhop = jhops.getJSONObject(fx);
					String from = gvas(jhop, "from");
					String to = gvas(jhop, "to");
					String via = gvas(jhop, "via");
					if (from == null || to == null || via == null || "".equals(from) || "".equals(to) || "".equals(via)) {
						continue;
					}
					phv.add(new NodeConfig.ProvHop(from, to, via));
				}
			}
		} catch (JSONException jse) {
			NodeUtils.setIpAndFqdnForEelf("ProvData");
			eelflogger.error(EelfMsgs.MESSAGE_PARSING_ERROR, jse.toString());
			logger.error("NODE0201 Error parsing configuration data from provisioning server " + jse.toString(), jse);
			throw new IOException(jse.toString(), jse);
		}
		pn = pnv.toArray(new NodeConfig.ProvNode[pnv.size()]);
		pp = ppv.toArray(new NodeConfig.ProvParam[ppv.size()]);
		pf = pfv.toArray(new NodeConfig.ProvFeed[pfv.size()]);
		pfu = pfuv.toArray(new NodeConfig.ProvFeedUser[pfuv.size()]);
		pfsn = pfsnv.toArray(new NodeConfig.ProvFeedSubnet[pfsnv.size()]);
		ps = psv.toArray(new NodeConfig.ProvSubscription[psv.size()]);
		pfi = pfiv.toArray(new NodeConfig.ProvForceIngress[pfiv.size()]);
		pfe = pfev.toArray(new NodeConfig.ProvForceEgress[pfev.size()]);
		ph = phv.toArray(new NodeConfig.ProvHop[phv.size()]);
	}
	/**
	 *	Get the raw node configuration entries
	 */
	public NodeConfig.ProvNode[] getNodes() {
		return(pn);
	}
	/**
	 *	Get the raw parameter configuration entries
	 */
	public NodeConfig.ProvParam[] getParams() {
		return(pp);
	}
	/**
	 *	Ge the raw feed configuration entries
	 */
	public NodeConfig.ProvFeed[] getFeeds() {
		return(pf);
	}
	/**
	 *	Get the raw feed user configuration entries
	 */
	public NodeConfig.ProvFeedUser[] getFeedUsers() {
		return(pfu);
	}
	/**
	 *	Get the raw feed subnet configuration entries
	 */
	public NodeConfig.ProvFeedSubnet[] getFeedSubnets() {
		return(pfsn);
	}
	/**
	 *	Get the raw subscription entries
	 */
	public NodeConfig.ProvSubscription[] getSubscriptions() {
		return(ps);
	}
	/**
	 *	Get the raw forced ingress entries
	 */
	public NodeConfig.ProvForceIngress[] getForceIngress() {
		return(pfi);
	}
	/**
	 *	Get the raw forced egress entries
	 */
	public NodeConfig.ProvForceEgress[] getForceEgress() {
		return(pfe);
	}
	/**
	 *	Get the raw next hop entries
	 */
	public NodeConfig.ProvHop[] getHops() {
		return(ph);
	}
}
