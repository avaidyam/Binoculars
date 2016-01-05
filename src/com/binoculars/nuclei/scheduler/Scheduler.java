package com.binoculars.nuclei.scheduler;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.RemoteRegistry;
import com.binoculars.future.Signal;

import java.lang.reflect.InvocationHandler;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * Scheduler manages scheduling of actors to threads.
 */
public interface Scheduler {

    int getDefaultQSize();

    // yield during polling/spinlooping
    void pollDelay(int count);

    void put2QueuePolling(Queue q, boolean isCBQ, Object o, Object sender);

    Object enqueueCall(Nucleus sendingNucleus, Nucleus receiver, String methodName, Object args[], boolean isCB);

    Object enqueueCallFromRemote(RemoteRegistry registry, Nucleus sendingNucleus, Nucleus receiver, String methodName, Object args[], boolean isCB);

    void threadStopped(Dispatcher th);

    void terminateIfIdle();

    InvocationHandler getInvoker(Nucleus dispatcher, Object toWrap);

    /**
     * Creates a wrapper on the given object enqueuing allOf calls to INTERFACE methods of the given object to the given actors's queue.
     * This is used to enable processing of resulting callback's in the callers thread.
     * see also @InThread annotation.
     *
     * @param callback
     * @param <T>
     * @return
     */
    <T> T inThread(Nucleus nucleus, T callback);

    void delayedCall(long millis, Runnable toRun);

    <T> void runBlockingCall(Nucleus emitter, Callable<T> toCall, Signal<T> resultHandler);

    Dispatcher assignDispatcher(int minLoadPerc);

    /**
     * called from inside overloaded thread with load
     * allOf actors assigned to the calling thread therefore can be safely moved
     *
     * @param dispatcher
     */
    void rebalance(Dispatcher dispatcher);

    BackOffStrategy getBackoffStrategy();

    void tryStopThread(Dispatcher dispatcher);

    void tryIsolate(Dispatcher dp, Nucleus nucleusRef);

    /**
     * @return number of actors scheduled by this scheduler. Note this
     * is not precise as not thread safe'd.
     */
    int getNumNuclei();
}
