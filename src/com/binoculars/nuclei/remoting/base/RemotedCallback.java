package com.binoculars.nuclei.remoting.base;

/**
 *
 * Tagging interface for callbacks forwarding to a remote location. A remoted callback is
 * deserialized as CallbackWrapper with the "realCallback" field containing an
 * instance of RemotedCallback
 *
 */
public interface RemotedCallback {

	boolean isTerminated();

}
