package com.binoculars.nuclei.remoting.base;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.Future;

import java.util.function.Function;

/**
 * Interface unifying publishing an nuclei via network (the thingy translating remote calls to local calls).
 * Mostly of internal interest.
 */
public interface NucleusServerConnector {

	void connect(Nucleus facade, Function<ObjectSocket, ObjectSink> factory) throws Exception;
	Future closeServer();
}
