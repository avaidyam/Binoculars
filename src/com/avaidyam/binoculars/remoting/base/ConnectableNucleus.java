/*
 * Copyright (c) 2016 Aditya Vaidyam
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.avaidyam.binoculars.remoting.base;

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.Future;

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