package com.att.research.datarouter.provisioning.eelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class JettyFilter extends Filter<ILoggingEvent>{
	  @Override
	  public FilterReply decide(ILoggingEvent event) {    
	    if (event.getLoggerName().contains("org.eclipse.jetty")) {
	      return FilterReply.ACCEPT;
	    } else {
	      return FilterReply.DENY;
	    }
	  }
}
