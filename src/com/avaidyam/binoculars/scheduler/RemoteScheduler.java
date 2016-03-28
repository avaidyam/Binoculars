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

package com.avaidyam.binoculars.scheduler;

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.Signal;

import java.util.concurrent.Callable;

/**
 *
 */
public class RemoteScheduler extends ElasticScheduler {

	// FIXME: dissimilar from source
    public RemoteScheduler() {
        super(DEFQSIZE);
    }

    public RemoteScheduler(int defQSize) {
        super(1, defQSize);
    }

    @Override
    protected Dispatcher createDispatcherThread() {
        return new Dispatcher(this) {
            @Override
            public synchronized void start() {
                // fake thread, just don't start
            }

			public String toString() {
				return "REMOTEREF "+super.toString();
			}
        };
    }

	@Override
	public void delayedCall(long millis, Runnable toRun) {
		throw new RuntimeException("cannot be used on a remote reference (no thread)");
	}

	@Override
	public <T> void runBlockingCall(Nucleus emitter, Callable<T> toCall, Signal<T> resultHandler) {
		throw new RuntimeException("cannot be used on a remote reference (no thread)");
	}
}
