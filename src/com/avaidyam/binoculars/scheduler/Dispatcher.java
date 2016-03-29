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

import com.avaidyam.binoculars.Exceptions;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.management.DispatcherStatusMXBean;
import com.avaidyam.binoculars.remoting.RemoteInvocation;
import com.avaidyam.binoculars.util.Log;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import external.jaq.mpsc.MpscConcurrentQueue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Implements the default dispatcher/scheduling of actors.
 * For each nuclei created from "outside" (not from within another nuclei). A new DispatcherThread is created
 * automatically. An nuclei created from within another nuclei inherits the dispatcher of the enclosing nuclei
 * by default.
 * Calls from actors sharing the same dispatcher are done directly (no queueing). Calls across actors in
 * different dispatchers are put to the Channel of the receiving dispatcher. Note that cross-dispatcher calls
 * are like 1000 times slower than inbound calls.
 *
 * Each dispatcher owns exactly one single thread.
 * Note that dispatchers must be terminated if not needed any longer, as a thread is associated with them.
 *
 * For more sophisticated applications it might be appropriate to manually set up dispatchers (Nucleus.assignDispatcher()).
 * The Nucleus.Channel method allows to specifiy a dedicated dispatcher on which to run the nuclei. This way it is possible
 * to exactly balance and control the number of threads created and which thread operates a set of actors.
 *
 */
public class Dispatcher extends Thread {

	/**
     * Invokes printStackTrace() on any uncaught exceptions from a Future.
     */
    public static boolean DUMP_EXCEPTIONS = true;

	/**
	 * The duration specifying how often load profiling and balancing is done.
     */
    public static int SCHEDULE_TICK_NANOS = 1000 * 500;

	/**
	 * The threshold percentage for the queue to reach before it is considered
	 * for rebalancing. That is, if the queue is X% full, it will be rebalanced.
     */
    public static int QUEUE_PERCENTAGE_TRIGGERING_REBALANCE = 50;

	/**
	 * Provides a leeway for caches to perform operations before a rebalance.
     */
    public static int MILLIS_AFTER_CREATION_BEFORE_REBALANCING = 2;

	/**
	 * The number of active Dispatchers present in the system.
	 */
    public static AtomicInteger activeDispatchers = new AtomicInteger(0);

	/**
	 * TODO: FIXME.
	 */
    /*package*/ static AtomicInteger dtcount = new AtomicInteger(0);

	/**
	 * The stack of Futures present in this Dispatcher.
	 */
    public ArrayList<CompletableFuture> __stack = new ArrayList<>();

	/**
	 * Has the Dispatcher been shut down?
	 */
    protected boolean shutDown = false;

	/**
	 * The queue of Nuclei to add to this Dispatcher.
	 */
	/*package*/ ConcurrentLinkedQueue<Nucleus> toAdd = new ConcurrentLinkedQueue<>();

	/**
	 * Is the Dispatcher isolated?
	 */
	/*package*/ volatile boolean isIsolated = false;

	/**
	 * Will the Dispatcher be shut down automatically?
	 */
    private boolean autoShutdown = true;

	/**
	 * TODO: FIXME.
	 */
	/*package*/ int emptySinceLastCheck = 0;

	/**
	 * The polled count of nuclei in the round-robin queue.
	 */
    /*package*/ int count = 0;

	/**
	 * The time (in millis) when this Dispatcher was created.
	 */
    /*package*/ long created = System.currentTimeMillis();

    private Scheduler scheduler;
    private Nucleus nuclei[] = new Nucleus[0]; // always refs

    public Dispatcher(Scheduler scheduler) {
        this.scheduler = scheduler;
        setName("Dispatcher" + dtcount.incrementAndGet());
    }

    public Dispatcher(Scheduler scheduler, boolean autoShutdown) {
        this(scheduler);
        this.autoShutdown = autoShutdown;
    }

    @Override
    public String toString() {
        return "Dispatcher{" +
                " name:" + getName() +
                '}';
    }

    public boolean isIsolated() {
        return isIsolated;
    }

    public void setIsolated(boolean isIsolated) {
        this.isIsolated = isIsolated;
    }

    public boolean isAutoShutdown() {
        return autoShutdown;
    }

    public void setAutoShutdown(boolean autoShutdown) {
        this.autoShutdown = autoShutdown;
    }

    public void addNucleus(Nucleus act) {
        act.getNucleusRef().__currentDispatcher = act.getNucleus().__currentDispatcher = this;
        toAdd.offer(act.getNucleusRef());
    }

    // removes immediate must be called from this thread
    void removeNucleusImmediate(Nucleus act) {
        if (Thread.currentThread() != this)
            throw new RuntimeException("wrong thread");
        Nucleus newAct[] = new Nucleus[nuclei.length - 1];
        int idx = 0;
        for (int i = 0; i < nuclei.length; i++) {
            Nucleus nucleus = nuclei[i];
            if (nucleus != act)
                newAct[idx++] = nucleus;
        }
        if (idx != newAct.length)
            throw new RuntimeException("could not remove nuclei");
        nuclei = newAct;
    }

    public void run() {
        int emptyCount = 0;
        long scheduleTickTime = System.nanoTime();
        boolean isShutDown = false;
        activeDispatchers.incrementAndGet();
        try {
            while (!isShutDown) {
                try {
                    if (pollQs()) {
                        emptyCount = 0;
                        if (System.nanoTime() - scheduleTickTime > SCHEDULE_TICK_NANOS) {
                            if (emptySinceLastCheck == 0) // no idle during last interval
                            {
                                checkForSplit();
                            }
                            emptySinceLastCheck = 0;
                            scheduleTickTime = System.nanoTime();
                            schedulePendingAdds();
                        }
                    } else {
                        emptyCount++;
                        emptySinceLastCheck++;
                        scheduler.pollDelay(emptyCount);
                        if (shutDown) // access volatile only when idle
                            isShutDown = true;
                        if (scheduler.getBackoffStrategy().isSleeping(emptyCount)) {
                            scheduleTickTime = 0;
                            schedulePendingAdds();
                            if (autoShutdown && System.currentTimeMillis() - created > 5000) {
                                if (nuclei.length == 0 && toAdd.peek() == null) {
                                    shutDown();
                                } else {
                                    scheduler.tryStopThread(this);
                                }
                            }
                        }
                    }
                } catch (Throwable th) {
                    Log.w(this.toString(), "from main poll loop", th);
                }
            }
            scheduler.threadStopped(this);
            LockSupport.parkNanos(1000 * 1000 * 1000);
            if (nuclei.length > 0 || toAdd.peek() != null) {
                if (ElasticScheduler.DEBUG_SCHEDULING)
                    Log.w(this.toString(), "Severe: zombie dispatcher thread detected, but can be a debugger artifact.");
                scheduler.tryStopThread(this);
            }
            if (ElasticScheduler.DEBUG_SCHEDULING)
                Log.i(this.toString(), "dispatcher thread terminated " + getName());
        } finally {
            activeDispatchers.decrementAndGet();
        }
    }

    /**
     * add actors which have been marked to be scheduled on this
     */
    public void schedulePendingAdds() {
        ArrayList<Nucleus> newOnes = new ArrayList<>();
        Nucleus a;
        while ((a = toAdd.poll()) != null) {
            newOnes.add(a);
        }
        if (newOnes.size() > 0) {
            Nucleus newQueue[] = new Nucleus[newOnes.size() + nuclei.length];
            System.arraycopy(nuclei, 0, newQueue, 0, nuclei.length);
            for (int i = 0; i < newOnes.size(); i++) {
                Nucleus nucleus = newOnes.get(i);
                newQueue[nuclei.length + i] = nucleus;
            }
            nuclei = newQueue;
        }

    }

    // poll all actors in queue arr round robin
    int currentPolledNucleus = 0;
    protected RemoteInvocation pollQueues(Nucleus[] nuclei) {
        if ( nuclei.length == 0 ) {
            return null;
        }
        RemoteInvocation res = null;
        int alen  = nuclei.length;
        int count = 0;
        while( res == null && count < alen ) {
            if ( currentPolledNucleus >= nuclei.length ) {
                currentPolledNucleus = 0;
            }
            Nucleus nucleus2Poll = nuclei[currentPolledNucleus];
            res = (RemoteInvocation) nucleus2Poll.__cbQueue.poll();
            if ( res == null )
                res = (RemoteInvocation) nucleus2Poll.__mailbox.poll();
            currentPolledNucleus++;
            count++;
        }
        return res;
    }

    /**
     * @return false if no message could be polled
     */
    public boolean pollQs() {
        return pollQs(nuclei);
    }

    /**
     * @return false if no message could be polled
     */
    public boolean pollQs(Nucleus nuclei[]) {
        RemoteInvocation invocation = pollQueues(nuclei);
        if (invocation != null) {
            try {
                // before calling the nuclei method, set current sender
                // to target, so for each method/callback invoked by the nuclei method,
                // sender has correct value
                Nucleus targetNucleus = invocation.getTargetNucleus();
                Nucleus.sender.set(targetNucleus);
                Nucleus.connection.set(invocation.getRemoteRegistry());
                if (targetNucleus.__stopped) {
                    targetNucleus.__addDeadLetter(targetNucleus, invocation.getMethod().getName());
                    return true;
                }


                Object invoke = null;
                try {
                    invoke = invoke(invocation);
                } catch (IllegalArgumentException iae) {
                    // FIXME: boolean is translated wrong by minbin .. this fix is expensive
                    final Class<?>[] parameterTypes = invocation.getMethod().getParameterTypes();
                    final Object[] args = invocation.getArgs();
                    if (args.length == parameterTypes.length) {
                        for (int i = 0; i < args.length; i++) {
                            Object arg = args[i];
                            if ((parameterTypes[i] == boolean.class || parameterTypes[i] == Boolean.class) &&
                                    arg instanceof Byte) {
                                args[i] = ((Byte) arg).intValue() != 0;
                            }
                        }
                        invoke = invoke(invocation);
                    } else {
                        System.out.println("mismatch when invoking method " + invocation);
                        for (int i = 0; i < invocation.getArgs().length; i++) {
                            Object o = invocation.getArgs()[i];
                            System.out.println("arg " + i + " " + o + (o != null ? " " + o.getClass().getSimpleName() : "") + ",");
                        }
                        System.out.println();
                        throw iae;
                    }
                }
                if (invocation.getFutureCB() != null) {
                    final Future futureCB = invocation.getFutureCB();   // the future of caller side
                    final CompletableFuture<Object> invokeResult = (CompletableFuture<Object>)invoke;  // the future returned sync from call
                    if(invokeResult != null) { //if return null instead a promise, method is handled like void
                        invokeResult.then((Signal<Object>)futureCB::complete);
                    }
                }
                return true;
            } catch (Throwable e) {
                if (e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException() == Exceptions.InternalNucleusStoppedException.INSTANCE) {
                    // fixme: rare classcast exception with elasticscheduler seen here when $stop is called from a callback ..
                    Nucleus nucleus = (Nucleus) invocation.getTarget();
                    nucleus.__stopped = true;
                    removeNucleusImmediate(nucleus.getNucleusRef());
// FIXME: Many Testcases fail if uncommented. Rethink
//                    if (invocation.getFutureCB() != null)
//                        invocation.getFutureCB().complete(null, e);
//                    else
//                        Log.Warn(this,e,"");
//                    if (invocation.getFutureCB() != null)
//                        invocation.getFutureCB().complete(null, e);
//                    else
//                        Log.Warn(this,e,"");
                    return true;
                }
                if (e instanceof InvocationTargetException) {
                    e = e.getCause();
                }
                if (invocation.getFutureCB() != null) {
                    Log.w(this.toString(), "Unhandled exception in message: " + invocation + ".\nReturned caught exception to Future.\nEnable DispatcherThread.DUMP_EXCEPTIONS to dump stacktrace.", e);
                    if (DUMP_EXCEPTIONS)
                        e.printStackTrace();
                    invocation.getFutureCB().complete(null, e);
                } else
                    Log.w(this.toString(), "" + invocation + Arrays.toString(invocation.getArgs()), e);
            }
        }
        return false;
    }

    private Object invoke(RemoteInvocation poll) throws IllegalAccessException, InvocationTargetException {
        final Object target = poll.getTarget();
        //RemoteRegistry remoteRefRegistry = poll.getRemoteRegistry();
        //Nucleus.registry.set(remoteRefRegistry);
        return poll.getMethod().invoke(target, poll.getArgs());
    }

    private void checkForSplit() {
        int load = getLoad();
        if (load > QUEUE_PERCENTAGE_TRIGGERING_REBALANCE &&
                nuclei.length > 1 &&
                System.currentTimeMillis() - created > MILLIS_AFTER_CREATION_BEFORE_REBALANCING) {
            scheduler.rebalance(this);
        }
    }

    /**
     * @return percentage of queue fill of max nuclei
     */
    public int getLoad() {
        int res = 0;
        final Nucleus nuclei[] = this.nuclei;
        for (int i = 0; i < nuclei.length; i++) {
            MpscConcurrentQueue queue = (MpscConcurrentQueue) nuclei[i].__mailbox;
            int load = queue.size() * 100 / queue.getCapacity();
            if (load > res)
                res = load;
            queue = (MpscConcurrentQueue) nuclei[i].__cbQueue;
            load = queue.size() * 100 / queue.getCapacity();
            if (load > res)
                res = load;
        }
        return res;
    }

    // FIXME: USE MPSCARRAYQUEUE
    /*

    public int getLoad() {
        int res = 0;
        final Nucleus actors[] = this.actors;
        for (int i = 0; i < actors.length; i++) {
            MpscArrayQueue queue = (MpscArrayQueue) actors[i].__mailbox;
            int load = queue.size() * 100 / actors[i].__mailboxCapacity;
            if ( load > res )
                res = load;
            queue = (MpscArrayQueue) actors[i].__cbQueue;
            load = queue.size() * 100 / actors[i].__mailboxCapacity;
            if ( load > res )
                res = load;
        }
        return res;
    }

    */

    /**
     * accumulated queue sizes of allOf actors
     * @return
     */
    public int getAccumulatedQSizes() {
        int res = 0;
        final Nucleus nuclei[] = this.nuclei;
        for (int i = 0; i < nuclei.length; i++) {
            res += nuclei[i].getQSizes();
        }
        return res;
    }

    /**
     * @return accumulated q size of allOf dispatched actors
     */
    public int getQSize() {
        int res = 0;
        final Nucleus nuclei[] = this.nuclei;
        for (int i = 0; i < nuclei.length; i++) {
            Nucleus a = nuclei[i];
            res += a.__mailbox.size();
            res += a.__cbQueue.size();
        }
        return res;
    }

    /**
     * @return true if DispatcherThread is not shut down
     */
    public boolean isShutDown() {
        return !shutDown;
    }

    /**
     * terminate operation after emptying Q
     */
    public void shutDown() {
        shutDown = true;
    }

    /**
     * terminate operation immediately. Pending messages in Q are lost
     */
    public void shutDownImmediate() {
        throw new RuntimeException("unimplemented");
    }

    public boolean isEmpty() {
        for (int i = 0; i < nuclei.length; i++) {
            Nucleus act = nuclei[i];
            if (!act.__mailbox.isEmpty() || !act.__cbQueue.isEmpty())
                return false;
        }
        return true;
    }

    /**
     * blocking method, use for debugging only.
     */
    public void waitEmpty(long nanos) {
        while (!isEmpty())
            LockSupport.parkNanos(nanos);
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * @return a copy of actors used
     */
    public Nucleus[] getNuclei() {
        Nucleus nuclei[] = this.nuclei;
        Nucleus res[] = new Nucleus[nuclei.length];
        System.arraycopy(nuclei, 0, res, 0, res.length);
        return res;
    }

    Nucleus[] getNucleiNoCopy() {
        return nuclei;
    }

    /**
     * can be called from the dispacther thread itself only
     * @param receiverRef
     * @return
     */
    public boolean schedules(Object receiverRef) {
        if (Thread.currentThread() != this) {
            throw new RuntimeException("cannot call from foreign thread");
        }
        if (receiverRef instanceof Nucleus) {
            // FIXME: think about visibility of scheduler var
            return ((Nucleus) receiverRef).__currentDispatcher == this;
//            for (int i = 0; i < actors.length; i++) {
//                Nucleus nuclei = actors[i];
//                if (nuclei == receiverRef)
//                    return true;
//            }
        }
        return false;
    }

	/**
	 * Returns the MXBean for monitoring of this Dispatcher.
	 * @return the MXBean for monitoring of this Dispatcher
	 */
	public DispatcherStatusMXBean dispatcherStatus() {
		return new DispatcherStatusMXBean.DispatcherStatus(getName(), nuclei.length, getLoad(), getAccumulatedQSizes());
	}
}
