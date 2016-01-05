package com.binoculars.nuclei.remoting.base;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.Future;

import java.util.function.Consumer;

/**
 * Configuration object to publish an nuclei to network. Serverside aequivalent to ConnectableNucleus
 */
public interface NucleusPublisher {
	
	default Future<NucleusServer>  publish() {
		return publish(null);
	}
	Future<NucleusServer> publish(Consumer<Nucleus> disconnectCallback);
	NucleusPublisher facade(Nucleus facade );
	
}
