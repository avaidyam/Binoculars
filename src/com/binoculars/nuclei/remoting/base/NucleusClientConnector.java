package com.binoculars.nuclei.remoting.base;

import com.binoculars.future.Future;

import java.util.function.Function;

/**
 * Interface unifying remote nuclei connectors (the thingy translating local calls to remote calls).
 * Mostly of internal interest.
 */
public interface NucleusClientConnector {
	
	/**
	 * used in most client and server connector implementations
	 */
	int OBJECT_MAX_BATCH_SIZE = 100;
	
	Future connect(Function<ObjectSocket, ObjectSink> factory) throws Exception;
	Future closeClient();
	
}
