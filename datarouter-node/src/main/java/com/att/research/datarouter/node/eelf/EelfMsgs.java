package com.att.research.datarouter.node.eelf;

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
     * Application message prints keystore file error in EELF errors log
     */

	MESSAGE_KEYSTORE_LOAD_ERROR,
	
	/**
     * Application message prints Error extracting my name from my keystore file
     */

	MESSAGE_KEYSORE_NAME_ERROR,	
	
	/**
     * Application message prints Error parsing configuration data from provisioning server.
     */


	MESSAGE_PARSING_ERROR,		
	
	/**
     * Application message printsConfiguration failed
     */


	MESSAGE_CONF_FAILED,		
	
	/**
     * Application message prints Bad provisioning server URL
     */


	MESSAGE_BAD_PROV_URL,		
	
	/**
     * Application message prints Unable to fetch canonical name from keystore file
     */


	MESSAGE_KEYSTORE_FETCH_ERROR,
	
	/**
     * Application message prints Unable to load local configuration file.
     */


	MESSAGE_PROPERTIES_LOAD_ERROR;

    
    /**
     * Static initializer to ensure the resource bundles for this class are loaded...
     * Here this application loads messages from three bundles
     */
    static {
        EELFResourceManager.loadMessageBundle("EelfMessages");
    }
}
