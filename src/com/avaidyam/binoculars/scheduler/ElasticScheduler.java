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
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.SignalWrapper;
import com.avaidyam.binoculars.management.SchedulerStatusMXBean;
import com.avaidyam.binoculars.remoting.CallEntry;
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import com.avaidyam.binoculars.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scheduler implementing "vertical" scaling. Instead of distributing load amongst
 * a given set of threads, it increases the number of threads/cores with load.
 * This way an nuclei has an dedicated thread executing it instead of random thread hopping
 * when using an executor to schedule nuclei messages.
 * Additionally this way I can use spin locks without going to 800% CPU if threadmax = 8.
 */
public class ElasticScheduler implements Scheduler {

    public static final int MAX_STACK_ON_SYNC_CBDISPATCH = 100000;
    public static final int MAX_EXTERNAL_THREADS_POOL_SIZE = 1000; // max threads used when externalizing blocking api
    public static int DEFQSIZE = 32768; // will be alligned to 2^x

    public static boolean DEBUG_SCHEDULING = false;
    public static boolean REALLY_DEBUG_SCHEDULING = false; // logs any move and remove

    public static int RECURSE_ON_BLOCK_THRESHOLD = 2;
    public static Timer delayedCalls = new Timer();
    final Dispatcher threads[];
    final Object balanceLock = new Object();
    protected BackOffStrategy backOffStrategy = new BackOffStrategy();
    protected ExecutorService exec = Executors.newFixedThreadPool(MAX_EXTERNAL_THREADS_POOL_SIZE);
    int maxThread = Runtime.getRuntime().availableProcessors();
    int defQSize = DEFQSIZE;
    private AtomicInteger isolateCount = new AtomicInteger(0);

    public ElasticScheduler(int maxThreads) {
        this(maxThreads, DEFQSIZE);
    }

    public ElasticScheduler(int maxThreads, int defQSize) {
        this.maxThread = maxThreads;
        this.defQSize = defQSize;
        if (defQSize <= 1)
            this.defQSize = DEFQSIZE;
        threads = new Dispatcher[maxThreads];
    }

    public int getActiveThreads() {
        int res = 0;
        for (int i = 0; i < threads.length; i++) {
            if (threads[i] != null) {
                res++;
            }
        }
        return res;
    }

    @Override
    public int getDefaultQSize() {
        return defQSize;
    }

    //    @Override
    public Future put2QueuePolling(CallEntry e) {
        final Future fut;
        if (e.hasFutureResult() && !(e.getFutureCB() instanceof SignalWrapper)) {
            fut = new CompletableFuture();
            e.setFutureCB(new SignalWrapper(e.getSendingNucleus(), new Signal() {
                @Override
                public void complete(Object result, Object error) {
                    fut.complete(result, error);
                }
            }));
        } else
            fut = null;
        Nucleus targetNucleus = e.getTargetNucleus();
        put2QueuePolling(e.isCallback() ? targetNucleus.__cbQueue : targetNucleus.__mailbox, false, e, targetNucleus);
        return fut;
    }

    @Override
    public void pollDelay(int count) {
        backOffStrategy.yield(count);
    }

    @Override
    public void put2QueuePolling(Queue q, boolean isCBQ, Object o, Object receiver) {
        int count = 0;
        boolean warningPrinted = false;
        while (!q.offer(o)) {
            pollDelay(count++);
            if (count > RECURSE_ON_BLOCK_THRESHOLD && isCBQ) {
                // thread is blocked, try to schedule other actors on this dispatcher (only calbacks+futures!)
                if (Thread.currentThread() instanceof Dispatcher) {

                    // fixme: think about consequences in depth.
                    //

                    // if blocked trying to put a callback onto a callback queue:
                    // check wether receiving nuclei is also scheduled on current thread
                    // if so, poll its message queue. fixme: what happens if sender == receiver

//                    Nucleus sendingNucleus = Nucleus.sender.get();
                    Dispatcher dp = (Dispatcher) Thread.currentThread();
                    if (dp.__stack.size() < MAX_STACK_ON_SYNC_CBDISPATCH && dp.getNucleiNoCopy().length > 1) {
                        Nucleus recAct = (Nucleus) receiver;
                        recAct = recAct.getNucleusRef();
                        if (dp.schedules(recAct)) {
                            dp.__stack.add(null);
                            if (dp.pollQs(new Nucleus[]{recAct})) {
                                count = 0;
                            }
                            dp.__stack.remove(dp.__stack.size() - 1);
                        }
                    } else {
//                        System.out.println("max stack depth");
                    }
                }
            }
            if (backOffStrategy.isYielding(count)) {
                Nucleus sendingNucleus = Nucleus.sender.get();
                if (receiver instanceof Nucleus && ((Nucleus) receiver).__stopped) {
                    String dl;
                    if (o instanceof CallEntry) {
                        dl = ((CallEntry) o).getMethod().getName();
                    } else {
                        dl = "" + o;
                    }
                    if(sendingNucleus != null)
                        sendingNucleus.__addDeadLetter((Nucleus) receiver, dl);
                    throw new Exceptions.NucleusStoppedException(dl);
                }
                if (sendingNucleus != null && sendingNucleus.__throwExAtBlock)
                    throw Exceptions.NucleusBlockedException.INSTANCE;
                if (backOffStrategy.isSleeping(count)) {
                    if (!warningPrinted) {
                        warningPrinted = true;
                        String receiverString;
                        if (receiver instanceof Nucleus) {
                            if (q == ((Nucleus) receiver).__cbQueue) {
                                receiverString = receiver.getClass().getSimpleName() + " callbackQ";
                            } else if (q == ((Nucleus) receiver).__mailbox) {
                                receiverString = receiver.getClass().getSimpleName() + " mailbox";
                            } else {
                                receiverString = receiver.getClass().getSimpleName() + " unknown queue";
                            }
                        } else
                            receiverString = "" + receiver;
                        String sender = "";
                        if (sendingNucleus != null)
                            sender = ", sender:" + sendingNucleus.getNucleus().getClass().getSimpleName();
                        if (DEBUG_SCHEDULING)
                            Log.w(this.toString(), "Warning: Thread " + Thread.currentThread().getName() + " blocked trying to put message on " + receiverString + sender + " log:" + o);
                    }
                    // decouple current thread
                    if (sendingNucleus != null && Thread.currentThread() instanceof Dispatcher) {
                        Dispatcher dp = (Dispatcher) Thread.currentThread();
                        dp.schedulePendingAdds();
//                    if ( dp.getNuclei().length > 1 && dp.schedules( receiver ) )
                        if (dp.getNuclei().length > 1) // try isolating in any case
                        {
                            if (DEBUG_SCHEDULING)
                                Log.w(this.toString(), "  try unblock Thread " + Thread.currentThread().getName() + " actors:" + dp.getNuclei().length);
                            dp.getScheduler().tryIsolate(dp, sendingNucleus.getNucleusRef());
                            if (DEBUG_SCHEDULING)
                                Log.w(this.toString(), "  unblock done Thread " + Thread.currentThread().getName() + " actors:" + dp.getNuclei().length);
                        } else {
                            if (dp.getNuclei().length > 1) {
                                // this indicates there are at least two actors on different threads blocking each other
                                // only solution to unlock is increase the Q of one of the actors
//                            System.out.println("POK "+dp.schedules( receiver )+" "+sendingNucleus.__currentDispatcher+" "+ ((Nucleus) receiver).__currentDispatcher);
                            }
                        }
                    }
                }
            }
        }
        if (warningPrinted && DEBUG_SCHEDULING) {
            Log.w(this.toString(), "Thread " + Thread.currentThread().getName() + " continued");
        }
    }


    @Override
    public Object enqueueCall(Nucleus sendingNucleus, Nucleus receiver, String methodName, Object args[], boolean isCB) {
        return enqueueCallFromRemote(null, sendingNucleus, receiver, methodName, args, isCB);
    }

    @Override
    public Object enqueueCallFromRemote(RemoteRegistry reg, Nucleus sendingNucleus, Nucleus receiver, String methodName, Object args[], boolean isCB) {
        // System.out.println("dispatch "+methodName+" "+Thread.currentThread());
        // here sender + receiver are known in a ST context
        Nucleus nucleus = receiver.getNucleus();
        Method method = nucleus.__getCachedMethod(methodName, nucleus);

        int count = 0;
        // scan for callbacks in arguments ..
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Signal<?>) {
                args[i] = new SignalWrapper<>(sendingNucleus, (Signal<Object>)arg);
            }
        }

        CallEntry<?> e = new CallEntry<>(
                nucleus, // target
                method,
                args,
                Nucleus.sender.get(), // enqueuer
                nucleus,
                isCB
        );
        e.setRemoteRegistry(reg);
        return put2QueuePolling(e);
    }

    public void threadStopped(Dispatcher th) {
        synchronized (threads) {
            for (int i = 0; i < threads.length; i++) {
                if (threads[i] == th) {
                    threads[i] = null;
                    return;
                }
            }
        }
        if (th.isIsolated()) {
            if (DEBUG_SCHEDULING)
                Log.i(this.toString(), "  was decoupled one.");
            isolateCount.decrementAndGet();
        }
//        throw new RuntimeException("Oops. Unknown Thread");
    }

	@Override
	public void terminateIfIdle() {
		for(int i = 0; i < threads.length; i++)
			threads[i].setAutoShutdown(true);
	}

	@Override
    public InvocationHandler getInvoker(Nucleus dispatcher, Object toWrap) {
        return new CallbackInvokeHandler(toWrap, dispatcher);
    }

    /**
     * Creates a wrapper on the given object enqueuing allOf calls to INTERFACE methods of the given object to the given actors's queue.
     * This is used to enable processing of resulting callback's in the callers thread.
     * see also @InThread annotation.
     *
     * @param callback
     * @param <T>
     * @return
     */
    @Override
    public <T> T inThread(Nucleus nucleus, T callback) {
        Class<?>[] interfaces = callback.getClass().getInterfaces();
        InvocationHandler invoker = nucleus.__scheduler.getInvoker(nucleus, callback);
        if (invoker == null) // called from outside nuclei world
            return callback; // callback in callee thread
        return (T)Proxy.newProxyInstance(callback.getClass().getClassLoader(), interfaces, invoker);
    }

    @Override
    public void delayedCall(long millis, final Runnable toRun) {
        delayedCalls.schedule(new TimerTask() {
            @Override
            public void run() {
                toRun.run();
            }
        }, millis);
    }

    @Override
    public <T> void runBlockingCall(Nucleus emitter, final Callable<T> toCall, Signal<T> resultHandler) {
        final SignalWrapper<T> resultWrapper = new SignalWrapper<>(emitter, resultHandler);
        exec.execute(() -> {
            try {
                resultWrapper.complete(toCall.call(), null);
            } catch (Throwable th) {
                resultWrapper.complete(null, th);
            }
        });
    }

    /**
     * if a low load thread is avaiable, return it. else try creation of new thread.
     * if this is not possible return thread with lowest load
     *
     * @return
     */
    @Override
    public Dispatcher assignDispatcher(int minLoadPerc) {
        synchronized (balanceLock) {
            Dispatcher minThread = findMinLoadThread(minLoadPerc, null);
            if (minThread != null) {
                return minThread;
            }
            Dispatcher newThreadIfPossible = createNewThreadIfPossible();
            if (newThreadIfPossible != null) {
                newThreadIfPossible.start();
                return newThreadIfPossible;
            } else {
                return findMinLoadThread(Integer.MIN_VALUE, null); // return thread with lowest load
            }
        }
    }

    private Dispatcher findMinLoadThread(int minLoad, Dispatcher dispatcher) {
        synchronized (balanceLock) {
            Dispatcher minThread = null;
            for (int i = 0; i < threads.length; i++) {
                Dispatcher thread = threads[i];
                if (thread != null && thread != dispatcher) {
                    int load = thread.getLoad();
                    if (load < minLoad) {
                        minLoad = load;
                        minThread = thread;
                    }
                }
            }
            return minThread;
        }
    }

    private Dispatcher createNewThreadIfPossible() {
        for (int i = 0; i < threads.length; i++) {
            Dispatcher thread = threads[i];
            if (thread == null) {
                Dispatcher th = createDispatcherThread();
                threads[i] = th;
                return th;
            }
        }
        return null;
    }

    /**
     * @return an UNSTARTED dispatcher thread
     */
    protected Dispatcher createDispatcherThread() {
        return new Dispatcher(this);
    }

    /**
     * called from inside overloaded thread.
     * allOf actors assigned to the calling thread therefore can be safely moved
     *
     * @param dispatcher
     */
    @Override
    public void rebalance(Dispatcher dispatcher) {
        synchronized (balanceLock) {
            Dispatcher minLoadThread = assignDispatcher(dispatcher.getLoad());
            if (minLoadThread == null || minLoadThread == dispatcher) {
                return;
            }
            int qSizes = dispatcher.getAccumulatedQSizes();
            // move actors
            Nucleus[] qList = dispatcher.getNuclei();
            long otherQSizes = minLoadThread.getAccumulatedQSizes();
            if (4 * otherQSizes / 3 > qSizes) {
                if (REALLY_DEBUG_SCHEDULING) {
                    Log.i(this.toString(), "no payoff, skip rebalance load:" + qSizes + " other:" + otherQSizes);
                }
                return;
            }
            for (int i = 0; i < qList.length; i++) {
                Nucleus nucleus = qList[i];
                if (otherQSizes + nucleus.getQSizes() < qSizes - nucleus.getQSizes()) {
                    otherQSizes += nucleus.getQSizes();
                    qSizes -= nucleus.getQSizes();
                    if (REALLY_DEBUG_SCHEDULING)
                        Log.i(this.toString(), "move " + nucleus.getQSizes() + " myload " + qSizes + " otherload " + otherQSizes + " from " + dispatcher.getName() + " to " + minLoadThread.getName());
                    dispatcher.removeNucleusImmediate(nucleus);
                    minLoadThread.addNucleus(nucleus);
                }
            }
            if (!minLoadThread.isAlive())
                minLoadThread.start();
        }
    }

    // fixme: use currentthread if this is a precondition anyway
    public void tryIsolate(Dispatcher dispatcher, Nucleus refToExclude /*implicitely indicates unblock*/) {
        if (dispatcher != Thread.currentThread())
            throw new RuntimeException("bad error");
        synchronized (balanceLock) {
            // move to nuclei with minimal load
            if (refToExclude == null) {
                throw new IllegalArgumentException("excluderef should not be null");
            }
            Nucleus qList[] = dispatcher.getNuclei();
            Dispatcher minLoadThread = findMinLoadThread(Integer.MAX_VALUE, dispatcher);
            for (int i = 0; i < threads.length; i++) { // avoid further dispatch
                if (threads[i] == dispatcher) {
                    threads[i] = createDispatcherThread();
                    dispatcher.setName(dispatcher.getName() + " (isolated)");
                    dispatcher.setIsolated(true);
                    isolateCount.incrementAndGet();
                    minLoadThread = threads[i];
                    minLoadThread.start();
                    if (DEBUG_SCHEDULING)
                        Log.i(this.toString(), "created new thread to unblock " + dispatcher.getName());
                }
            }
            if (minLoadThread == null) {
                // calling thread is already isolate
                // so no creation happened and no minloadthread was found
                minLoadThread = createDispatcherThread();
                minLoadThread.setName(dispatcher.getName() + " (isolated)");
                minLoadThread.setIsolated(true);
                isolateCount.incrementAndGet();
                if (DEBUG_SCHEDULING)
                    Log.i(this.toString(), "created new thread to unblock already isolated " + dispatcher.getName());
            }
            for (int i = 0; i < qList.length; i++) {
                Nucleus nucleus = qList[i];
                // sanity, remove me later
                if (nucleus.getNucleusRef() != nucleus)
                    throw new RuntimeException("this should not happen ever");
                if (refToExclude != null && refToExclude.getNucleusRef() != refToExclude) {
                    throw new RuntimeException("this also");
                }
                if (nucleus != refToExclude) {
                    dispatcher.removeNucleusImmediate(nucleus);
                    minLoadThread.addNucleus(nucleus);
                }
                if (REALLY_DEBUG_SCHEDULING)
                    Log.i(this.toString(), "move for unblock " + nucleus.getQSizes() + " myload " + dispatcher.getAccumulatedQSizes() + " actors " + qList.length);
            }
        }
    }

	@Override
	public int getNumNuclei() {
		int l = 0;
		for(int i = 0; i < threads.length; i++)
			l += threads[i].getNucleiNoCopy().length;
		return l;
	}

	/**
     * stepwise move actors onto other dispatchers. Note actual termination is not done here.
     * removes given dispatcher from the scheduling array, so this thread won't be visible to scheduling
     * anymore. In extreme this could lead to high thread numbers, however this behaviour was never observed
     * until now ..
     * <p>
     * FIXME: in case decoupled threads live forever, do a hard stop on them
     * FIXME: sort by load and spread load amongst allOf threads (current find min and put removedNuclei on it).
     *
     * @param dispatcher
     */
    public void tryStopThread(Dispatcher dispatcher) {
        if (dispatcher != Thread.currentThread())
            throw new RuntimeException("bad one");
        synchronized (balanceLock) {
            Dispatcher minLoadThread = findMinLoadThread(Integer.MAX_VALUE, dispatcher);
            if (minLoadThread == null)
                return;
            // move to nuclei with minimal load
            Nucleus qList[] = dispatcher.getNuclei();
            for (int i = 0; i < threads.length; i++) { // avoid further dispatch
                if (threads[i] == dispatcher) {
                    threads[i] = null;
                }
            }
            int maxNuclei2Remove = Math.min(qList.length, qList.length / 5 + 1); // do several steps to get better spread
            for (int i = 0; i < maxNuclei2Remove; i++) {
                Nucleus nucleus = qList[i];
                // sanity, remove me later
                if (nucleus.getNucleusRef() != nucleus)
                    throw new RuntimeException("this should not happen ever");
                dispatcher.removeNucleusImmediate(nucleus);
                minLoadThread.addNucleus(nucleus);
                if (REALLY_DEBUG_SCHEDULING)
                    Log.i(this.toString(), "move for idle " + nucleus.getQSizes() + " myload " + dispatcher.getAccumulatedQSizes() + " actors " + qList.length);
            }
        }
    }

    @Override
    public BackOffStrategy getBackoffStrategy() {
        return backOffStrategy;
    }

    class CallbackInvokeHandler implements InvocationHandler {

        final Object target;
        final Nucleus targetNucleus;

        public CallbackInvokeHandler(Object target, Nucleus act) {
            this.target = target;
            this.targetNucleus = act;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class)
                return method.invoke(proxy, args); // toString, hashCode etc. invoke sync (DANGER if hashcode accesses mutable local state)
            if (target != null) {
                CallEntry ce = new CallEntry(target, method, args, Nucleus.sender.get(), targetNucleus, true);
                put2QueuePolling(targetNucleus.__cbQueue, true, ce, targetNucleus);
            }
            return null;
        }
    }

	public SchedulerStatusMXBean schedulerStatus() {
		int count = 0;
		for (Dispatcher thread : threads)
			if(thread != null)
				count++;

		return new SchedulerStatusMXBean.SchedulerStatus(count, defQSize, isolateCount.get());
	}
}
