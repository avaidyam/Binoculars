package com.binoculars.nuclei.remoting.base;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.Signal;
import com.binoculars.future.Future;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Describes a connectable remote or local nuclei. (implementations hold technical details such as host port etc.)
 *
 * To connect to a remote nuclei usually a connector is configured, then calling connect returns the reference to
 * the remote nuclei.
 *
 * In peer to peer nuclei remoting model, this can be passed around (over network) in order to
 * tell other actors/services on how to connect a certain nuclei directly (e.g. a Service Registry would
 * pass ConnectableNuclei to clients).
 *
 */
public interface ConnectableNucleus extends Serializable {
	
	/**
	 *
	 * @param disconnectSignal - a callback called on disconnect, passing the NucleusClientConnector instance
	 * @param actorDisconnecCB - a consumer called on disconnect passing the remoteactor ref. Rarely needed. added to avoid braking things
	 * @param <T>
	 * @return
	 */
	<T> Future<T> connect(Signal<NucleusClientConnector> disconnectSignal, Consumer<Nucleus> nucleusDisconnectCB);
	
	default <T> Future<T> connect(Signal<NucleusClientConnector> disconnectSignal) {
		return this.connect(disconnectSignal, null);
	}
	
	default <T> Future<T> connect() {
		return this.connect(null, null);
	}
	
	ConnectableNucleus nucleusClass(Class nucleusClz);
	ConnectableNucleus inboundQueueSize(final int inboundQueueSize);
}