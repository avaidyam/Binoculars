package com.binoculars.nuclei.remoting.base;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.Signal;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;

import java.util.function.Consumer;

/**
 *
 * A connectable simply connecting to a local nuclei. A close connection event will never happen (FIXME: send on stop instead)
 *
 */
public class LocalConnectable implements ConnectableNucleus {

	Nucleus nucleus;

	public LocalConnectable(Nucleus nucleus) {
		this.nucleus = nucleus;
	}

	/**
	 * disconnect callback will never be called (local nuclei connection)
	 * @param <T>
	 * @param disconnectSignal
	 * @param actorDisconnecCB
	 * @return
	 */
	@Override
	public <T> Future<T> connect(Signal<NucleusClientConnector> disconnectSignal, Consumer<Nucleus> actorDisconnecCB) {
		return new CompletableFuture<>((T) nucleus);
	}

	@Override
	public ConnectableNucleus nucleusClass(Class nucleusClz) {
		if (!nucleusClz.isAssignableFrom(nucleus.getClass()))
			throw new RuntimeException("nuclei class mismatch");
		return this;
	}

	@Override
	public ConnectableNucleus inboundQueueSize(int inboundQueueSize) {
		return this;
	}

	public Nucleus getNucleus() {
		return nucleus;
	}
}