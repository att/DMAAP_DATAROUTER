package com.att.research.datarouter.authz.impl;

/** Interface to access data about subscriptions and feeds.  A software component that 
 * uses the <code>ProvAuthorizer</code> needs to supply an implementation of this interface.
 * @author J. F. Lucas
 *
 */
public interface ProvDataProvider {
	
	/** Get the identity of the owner of a feed.
	 * 
	 * @param feedId the feed ID of the feed whose owner is being looked up.
	 * @return the feed owner's identity
	 */
	public String getFeedOwner(String feedId);
	
	/** Get the security classification of a feed.
	 * 
	 * @param feedId the ID of the feed whose classification is being looked up.
	 * @return the classification of the feed.
	 */
	public String getFeedClassification(String feedId);
	
	/** Get the identity of the owner of a feed
	 * 
	 * @param subId the ID of the subscripition whose owner is being looked up.
	 * @return the subscription owner's identity.
	 */
	public String getSubscriptionOwner(String subId);

	/** Get the identity of the owner of a feed by group id -  Rally : US708115
	 * 
	 * @param feedid, user the ID of the feed whose owner is being looked up.
	 * @return the feed owner's identity by group.
	 */
	public String getGroupByFeedGroupId(String owner, String feedId);
	
	/** Get the identity of the owner of a sub by group id Rally : US708115
	 * 
	 * @param subid, user the ID of the feed whose owner is being looked up.
	 * @return the feed owner's identity by group.
	 */
	public String getGroupBySubGroupId(String owner, String subId);
}
