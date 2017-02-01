package com.att.research.datarouter.provisioning.eelf;

import com.att.eelf.i18n.EELFResolvableErrorEnum;
import com.att.eelf.i18n.EELFResourceManager;

public enum EelfMsgs implements EELFResolvableErrorEnum {
	
	/**
     * Application message prints user (accepts one argument)
     */
	MESSAGE_WITH_BEHALF,

	/**
     * Application message prints user and FeedID (accepts two arguments)
     */

	MESSAGE_WITH_BEHALF_AND_FEEDID,

	/**
     * Application message prints user and SUBID (accepts two arguments)
     */

	MESSAGE_WITH_BEHALF_AND_SUBID;

		
    
    /**
     * Static initializer to ensure the resource bundles for this class are loaded...
     * Here this application loads messages from three bundles
     */
    static {
        EELFResourceManager.loadMessageBundle("EelfMessages");
    }
}
