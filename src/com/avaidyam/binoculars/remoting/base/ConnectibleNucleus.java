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
import java.util.function.Function;

/**
 * Describes a connectible remote or local nuclei. A NucleusClientConnector must be
 * configured after which, calling connect() returns the remote nucleus reference.
 * Remote Nucleus references may be passed across the network for nuclei to be connected
 * directly.
 */
public interface ConnectibleNucleus<T extends Nucleus> extends Serializable {

	/**
	 * Protocol which unifies Nucleus connectors translating local to remote async calls.
	 */
	interface NucleusClientConnector {

		/**
		 * Configure a Source --> Sink flow and connect with a NucleusClient.
		 *
		 * @param factory the lambda that configures the Flow
		 * @return a Future containing the Nucleus reference
		 * @throws Exception if factory fails
		 */
		Future connect(Function<ObjectFlow.Source, ObjectFlow.Sink> factory) throws Exception;

		/**
		 * Disconnect from the remote end.
		 *
		 * @return A Future returned when disconnection is complete
		 */
		Future disconnect();
	}

	/**
	 * Connects to the remote Nucleus with provided disconnection Signals.
	 *
	 * @apiNote disconnectHandler is rarely used or needed.
	 *
	 * @param disconnectSignal called on disconnect
	 * @param disconnectHandler called on disconnect, with the RemoteNucleus.
	 * @return a Future containing the Nucleus reference
	 */
	Future<T> connect(Signal<NucleusClientConnector> disconnectSignal, Consumer<T> disconnectHandler);

	/**
	 * Connects to the remote Nucleus with provided disconnection Signal.
	 *
	 * @param disconnectSignal called on disconnect
	 * @return a Future containing the Nucleus reference
	 */
	default Future<T> connect(Signal<NucleusClientConnector> disconnectSignal) {
		return this.connect(disconnectSignal, null);
	}

	/**
	 * Connects to the remote Nucleus.
	 *
	 * @return a Future containing the Nucleus reference
	 */
	default Future<T> connect() {
		return this.connect(null, null);
	}
}