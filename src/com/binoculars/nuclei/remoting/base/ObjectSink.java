package com.binoculars.nuclei.remoting.base;

import com.binoculars.future.Future;

import java.util.List;

/**
 * an object able to process decoded incoming messages
 */
public interface ObjectSink {
	
	/**
	 * @param sink - usually this or a wrapper of this
	 * @param received - decoded object(s)
	 * @param createdFutures - list of futures/callbacks contained in the decoded object remote calls (unused)
	 */
	void receiveObject(ObjectSink sink, Object received, List<Future> createdFutures);
	default void receiveObject(Object received, List<Future> createdFutures) {
		receiveObject(this,received,createdFutures);
	}
	void sinkClosed();
	
}


