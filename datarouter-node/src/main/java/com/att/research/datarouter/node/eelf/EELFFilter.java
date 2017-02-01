package com.att.research.datarouter.node.eelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/*
 * When EELF functionality added it default started logging Jetty logs as well which in turn stopped existing functionality of logging jetty statements in node.log
 * added code in logback.xml to add jetty statements in node.log.
 * This class removes extran EELF statements from node.log since they are being logged in apicalls.log 
 */
public class EELFFilter extends Filter<ILoggingEvent>{
	  @Override
	  public FilterReply decide(ILoggingEvent event) {    
	    if (event.getMessage().contains("EELF")) {
	      return FilterReply.DENY;
	    } else {
	      return FilterReply.ACCEPT;
	    }
	  }
}
