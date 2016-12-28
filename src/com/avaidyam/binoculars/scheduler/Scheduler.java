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
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;

import java.lang.reflect.InvocationHandler;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * Scheduler manages scheduling of actors to threads.
 */
public interface Scheduler {

    /**
     * Creates a wrapper on the given object enqueuing allOf calls to INTERFACE methods of the given object to the given actors's queue.
     * This is used to enable processing of resulting callback's in the callers thread.
     * see also @InThread annotation.
     *
     * in case called from an nuclei, wraps the given interface instance into a proxy such that
     * a calls on the interface get scheduled on the actors thread (avoids accidental multithreading
     * when handing out callback/listener interfaces from an nuclei)
     */
    <T> T inThread(Nucleus nucleus, T callback);

    void delayedCall(long millis, Runnable toRun);

    <T> void runBlockingCall(Nucleus emitter, Callable<T> toCall, Signal<T> resultHandler);

    Object enqueueCall(RemoteRegistry registry, Nucleus sendingNucleus, Nucleus receiver, String methodName, Object args[], boolean isCB);




    // FIXME: REMOVE
    int getDefaultQSize();

    // FIXME: REMOVE
    void threadStopped(Dispatcher th);

    /**
     * yield during polling/spinlooping
     */
    // FIXME: REMOVE
    void pollDelay(int count);

    // FIXME: REMOVE
    void put2QueuePolling(Queue q, boolean isCBQ, Object o, Object sender);

    // FIXME: REMOVE
    InvocationHandler getInvoker(Nucleus dispatcher, Object toWrap);

    // FIXME: REMOVE
    Dispatcher assignDispatcher(int minLoadPerc);

    /**
     * called from inside overloaded thread with load
     * allOf actors assigned to the calling thread therefore can be safely moved
     */
    // FIXME: REMOVE
    void rebalance(Dispatcher dispatcher);

    // FIXME: REMOVE
    SchedulingStrategy getBackoffStrategy();

    // FIXME: REMOVE
    void tryStopThread(Dispatcher dispatcher);

    // FIXME: REMOVE
    void tryIsolate(Dispatcher dp, Nucleus nucleusRef);

    // FIXME: REMOVE
    int getNumNuclei();

    // FIXME: REMOVE
    void terminateIfIdle();
}
