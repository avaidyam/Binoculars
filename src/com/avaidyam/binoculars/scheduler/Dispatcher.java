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
import com.avaidyam.binoculars.remoting.RemoteInvocation;
import com.avaidyam.binoculars.Log;
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
 * Implements the default dispatcher/scheduling of actors. For each nucleus created
 * "outside" (not from within another nuclei), a new Dispatcher is created automatically.
 * A nucleus created from within another nuclei inherits the dispatcher of the enclosing nucleus.
 *
 * Invocations from nuclei sharing the same Dispatcher are done directly without enqueueing.
 * Calls across nuclei in different Dispatchers are put to the Channel of the receiving
 * dispatcher. Note that cross-Dispatcher calls are much slower than inbound calls.
 *
 * Each Dispatcher owns exactly one single thread. Note that Dispatchers must be terminated
 * if not needed any longer, as a thread is associated with them, and it would be wasteful.
 *
 * For more sophisticated applications it might be appropriate to manually set up Dispatchers
 * using Scheduler.assignDispatcher(). The Nucleus.assign(...) method allows specifying a
 * dedicated Dispatcher on which to run the nuclei. This way it is possible to exactly balance
 * and control the number of threads created and which thread operates a set of nuclei.
 */
// FIXME Nucleus.assign(...)
public class Dispatcher extends Thread {
    private static final String TAG = "Dispatcher";

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

    /**
     * The Dispatcher's attached Scheduler.
     */
    private Scheduler scheduler;

    /**
     * The Dispatcher's assigned Nuclei.
     */
    private Nucleus nuclei[] = new Nucleus[0]; // always refs

    /**
     * Poll all nuclei in the queue in a round robin manner.
     */
    int currentPolledNucleus = 0;

    /**
     * Create a new Dispatcher bound to the given Scheduler.
     *
     * @param scheduler the Scheduler to bind to
     */
    public Dispatcher(Scheduler scheduler) {
        this.scheduler = scheduler;
        setName("Dispatcher" + dtcount.incrementAndGet());
    }

    /**
     * Create a new Dispatcher bound to the given Scheduler.
     *
     * @param scheduler the Scheduler to bind to
     * @param autoShutdown shut down when done
     */
    public Dispatcher(Scheduler scheduler, boolean autoShutdown) {
        this(scheduler);
        this.autoShutdown = autoShutdown;
    }

    /**
     * Returns the string representation of the Dispatcher.
     *
     * @return the string representation of the Dispatcher.
     */
    @Override
    public String toString() {
        return "Dispatcher{" +
                " name:" + getName() +
                '}';
    }

    /**
     * Returns whether this Dispatcher is isolated.
     *
     * @return whether this Dispatcher is isolated
     */
    public boolean isIsolated() {
        return isIsolated;
    }

    /**
     * Set whether this Dispatcher is isolated.
     *
     * @param isolated whether this Dispatcher is isolated
     */
    public void setIsolated(boolean isolated) {
        this.isIsolated = isolated;
    }

    /**
     * Returns whether this Dispatcher shuts down automatically.
     *
     * @return whether this Dispatcher shuts down automatically
     */
    public boolean isAutoShutdown() {
        return autoShutdown;
    }

    /**
     * Set whether this Dispatcher shuts down automatically.
     *
     * @param autoShutdown whether this Dispatcher shuts down automatically
     */
    public void setAutoShutdown(boolean autoShutdown) {
        this.autoShutdown = autoShutdown;
    }

    /**
     * Adds the Nucleus provided to the Dispatcher.
     *
     * @param nucleus the nucleus to add
     */
    public void addNucleus(Nucleus nucleus) {
        nucleus.getNucleusRef().__currentDispatcher = nucleus.getNucleus().__currentDispatcher = this;
        toAdd.offer(nucleus.getNucleusRef());
    }

    /**
     * Remove the given Nucleus immediately.
     *
     * NOTE: This MUST be called from the Dispatcher thread.
     *
     * @param nucleus the nucleus to remove
     */
    void removeNucleusImmediate(Nucleus nucleus) {
        if (Thread.currentThread() != this)
            throw new RuntimeException("Must invoke this method from the Dispatcher!");

        Nucleus newAct[] = new Nucleus[nuclei.length - 1];
        int idx = 0;
        for (Nucleus nucleus2 : nuclei) {
            if (nucleus2 != nucleus)
                newAct[idx++] = nucleus2;
        }

        if (idx != newAct.length)
            throw new RuntimeException("Could not remove nucleus!");
        nuclei = newAct;
    }

    /**
     * The main worker thread component of the Dispatcher.
     */
    public void run() {

        // Reset Dispatcher state.
        int emptyCount = 0;
        long scheduleTickTime = System.nanoTime();
        boolean isShutDown = false;
        activeDispatchers.incrementAndGet();

        try {
            while (!isShutDown) {
                try {
                    if (pollQs()) {

                        // Successful poll!
                        emptyCount = 0;
                        if (System.nanoTime() - scheduleTickTime > SCHEDULE_TICK_NANOS) {
                            if (emptySinceLastCheck == 0) // no idle during last interval
                                checkForSplit();
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

                        // See if the Scheduler wants us to stop the Dispatcher.
                        if (scheduler.getBackoffStrategy().isSleeping(emptyCount)) {
                            scheduleTickTime = 0;
                            schedulePendingAdds();
                            if (autoShutdown && System.currentTimeMillis() - created > 5000) {
                                if (nuclei.length == 0 && toAdd.peek() == null)
                                    shutDown();
                                else scheduler.tryStopThread(this);
                            }
                        }
                    }
                } catch (Throwable e) {
                    Log.w(TAG, "Exception from main poll loop: ", e);
                }
            }

            // The Dispatcher has been shut down.
            scheduler.threadStopped(this);
            LockSupport.parkNanos(1000 * 1000 * 1000);
            if (nuclei.length > 0 || toAdd.peek() != null) {
                if (ElasticScheduler.DEBUG_SCHEDULING)
                    Log.w(TAG, "Severe: zombie dispatcher thread detected, but can be a debugger artifact.");
                scheduler.tryStopThread(this);
            }

            if (ElasticScheduler.DEBUG_SCHEDULING)
                Log.i(TAG, "Dispatcher terminated: " + getName());
        } finally {
            activeDispatchers.decrementAndGet();
        }
    }

    /**
     * Add all Nuclei which have been marked to be scheduled on this Dispatcher.
     */
    public void schedulePendingAdds() {
        ArrayList<Nucleus> newOnes = new ArrayList<>();
        Nucleus a;
        while ((a = toAdd.poll()) != null)
            newOnes.add(a);

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

    /**
     * Returns the first RemoteInvocation available for the given Nuclei.
     *
     * @param nuclei the Nuclei to poll
     * @return an available RemoteInvocation
     */
    protected RemoteInvocation pollQueues(final Nucleus[] nuclei) {
        if (nuclei.length == 0)
            return null;

        int count = 0;
        RemoteInvocation res = null;
        while (res == null && count < nuclei.length) {
            if (currentPolledNucleus >= nuclei.length)
                currentPolledNucleus = 0;

            Nucleus nucleus2Poll = nuclei[currentPolledNucleus];
            res = (RemoteInvocation)nucleus2Poll.__cbQueue.poll();

            if (res == null)
                res = (RemoteInvocation)nucleus2Poll.__mailbox.poll();
            currentPolledNucleus++;
            count++;
        }
        return res;
    }

    /**
     * Returns whether messages exist for any Nuclei in the Dispatcher.
     *
     * @return whether messages exist
     */
    // TODO RENAME
    public boolean pollQs() {
        return pollQs(nuclei);
    }

    /**
     * Returns whether messages exist for any Nuclei given.
     *
     * @return whether messages exist
     */
    // TODO RENAME
    public boolean pollQs(Nucleus nuclei[]) {
        RemoteInvocation invocation = pollQueues(nuclei);
        if (invocation != null) {
            try {
                // Before calling the nuclei method, set current sender
                // to target, so for each method/callback invoked by the nuclei method,
                // sender has correct value.
                Nucleus targetNucleus = invocation.getTargetNucleus();
                Nucleus.sender.set(targetNucleus);
                Nucleus.connection.set(invocation.getRemoteRegistry());
                if (targetNucleus.__stopped) {
                    targetNucleus.__addDeadLetter(targetNucleus, invocation.getMethod().getName());
                    return true;
                }

                // Invoke the RemoteInvocation.
                Object invoke = null;
                try {
                    invoke = invoke(invocation);
                } catch (IllegalArgumentException e) {
                    System.err.println("Argument mismatch when invoking method " + invocation);
                    for (int i = 0; i < invocation.getArgs().length; i++) {
                        Object o = invocation.getArgs()[i];
                        System.err.println("arg" + i + "= " + o + (o != null ? o.getClass().getSimpleName() : "[null]") + ", ");
                    }
                    System.err.println();
                    throw e;
                }

                // Handle any attached Futures.
                // If the invocation returns null instead of a Future, handle it like a Future<Void>.
                if (invocation.getFutureCB() != null) {
                    final Future futureCB = invocation.getFutureCB(); // caller's future
                    final CompletableFuture<Object> invokeResult = (CompletableFuture<Object>)invoke;  // the future returned sync from call
                    if(invokeResult != null)
                        invokeResult.then((Signal<Object>)futureCB::complete);
                }

                return true;
            } catch (Throwable e) {

                // The target no longer exists; assume it's dead.
                if (e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException() == Exceptions.InternalNucleusStoppedException.INSTANCE) {
                    // FIXME: Sometimes ElasticScheduler causes a ClassCastException when stop() is called from a Signal.
                    Nucleus nucleus = (Nucleus) invocation.getTarget();
                    nucleus.__stopped = true;
                    removeNucleusImmediate(nucleus.getNucleusRef());
                    return true;
                }

                // If the invocation caused an exception, pass it to a Future if possible.
                if (e instanceof InvocationTargetException)
                    e = e.getCause();
                if (invocation.getFutureCB() != null) {
                    Log.w(TAG, "Unhandled exception in message: " + invocation + ".\nReturned caught exception to Future.\nEnable Dispatcher.DUMP_EXCEPTIONS to dump stacktrace.", e);
                    if (DUMP_EXCEPTIONS)
                        e.printStackTrace();
                    invocation.getFutureCB().complete(null, e);
                } else {
                    Log.w(TAG, "Invocation caused an exception: " + invocation + "\nargs: " + Arrays.toString(invocation.getArgs()), e);
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Invoke the given RemoteInvocation.
     *
     * @param invocation the invocation itself
     * @return the result of the invocation
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Object invoke(RemoteInvocation invocation) throws IllegalAccessException, InvocationTargetException {
        return invocation.getMethod().invoke(invocation.getTarget(), invocation.getArgs());
    }

    /**
     * Rebalance the Dispatcher from the Scheduler if the current load is over-limit.
     */
    private void checkForSplit() {
        if (getLoad() > QUEUE_PERCENTAGE_TRIGGERING_REBALANCE && nuclei.length > 1 &&
                System.currentTimeMillis() - created > MILLIS_AFTER_CREATION_BEFORE_REBALANCING)
            scheduler.rebalance(this);
    }

    /**
     * Returns the percentage of the queue filled per max nuclei.
     *
     * @return the percentage of the queue filled per max nuclei
     */
    public int getLoad() {
        int res = 0;
        final Nucleus nuclei[] = this.nuclei;
        for (Nucleus aNuclei : nuclei) {
            MpscConcurrentQueue queue = (MpscConcurrentQueue) aNuclei.__mailbox;
            int load = queue.size() * 100 / queue.getCapacity();
            if (load > res)
                res = load;

            queue = (MpscConcurrentQueue) aNuclei.__cbQueue;
            load = queue.size() * 100 / queue.getCapacity();
            if (load > res)
                res = load;
        }
        return res;
    }

    /**
     * Returns the accumulated queue sizes of all Nuclei.
     *
     * @return the accumulated queue sizes of all Nuclei
     */
    public int getAccumulatedQSizes() {
        int res = 0;
        final Nucleus nuclei[] = this.nuclei;
        for (Nucleus aNuclei : nuclei) {
            res += aNuclei.getQSizes();
        }
        return res;
    }

    /**
     * Returns the accumulated queue sizes of all dispatched Nuclei.
     *
     * @return the accumulated queue sizes of all dispatched Nuclei
     */
    public int getQSize() {
        int res = 0;
        final Nucleus nuclei[] = this.nuclei;
        for (Nucleus a : nuclei) {
            res += a.__mailbox.size();
            res += a.__cbQueue.size();
        }
        return res;
    }

    /**
     * Returns whether the Dispatcher has been shut down.
     *
     * @return whether the Dispatcher has been shut down
     */
    public boolean isShutDown() {
        return shutDown;
    }

    /**
     * Terminate the Dispatcher after current messages are done processing.
     */
    public void shutDown() {
        shutDown = true;
    }

    /**
     * Returns whether there are no messages left in the Dispatcher.
     *
     * @return whether there are no messages left in the Dispatcher
     */
    public boolean isEmpty() {
        for (Nucleus n : nuclei) {
            if (!n.__mailbox.isEmpty() || !n.__cbQueue.isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Returns the Scheduler this Dispatcher is bound to.
     *
     * @return the Scheduler this Dispatcher is bound to.
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Returns a copy of the nuclei assigned to this Dispatcher.
     *
     * @return a copy of the nuclei assigned to this Dispatcher
     */
    public Nucleus[] copyNuclei() {
        Nucleus copied[] = new Nucleus[this.nuclei.length];
        System.arraycopy(this.nuclei, 0, copied, 0, copied.length);
        return copied;
    }

    /**
     * Returns a reference to the nuclei assigned to this Dispatcher.
     *
     * @return a reference to the nuclei assigned to this Dispatcher
     */
    Nucleus[] getNuclei() {
        return nuclei;
    }

    /**
     *
     *
     * @param receiver
     * @return
     */
    public boolean schedules(Object receiver) {
        if (Thread.currentThread() != this)
            throw new RuntimeException("Must invoke this method from the Dispatcher!");

        return (receiver instanceof Nucleus) &&
                (((Nucleus) receiver).__currentDispatcher == this);
    }
}
