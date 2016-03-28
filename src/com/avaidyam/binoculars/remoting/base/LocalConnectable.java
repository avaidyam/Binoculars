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
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;

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