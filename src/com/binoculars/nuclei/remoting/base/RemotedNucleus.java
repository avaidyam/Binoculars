package com.binoculars.nuclei.remoting.base;

/**
 * optional interface implementing some notification callbacks
 * related to remoting.
 */
public interface RemotedNucleus {

	/**
	 * notification method called once an nuclei has been unpublished. E.g. in case of a ClientSession role
	 * nuclei, this will be called once the client disconnects or times out
	 */
	public void hasBeenUnpublished();

}
