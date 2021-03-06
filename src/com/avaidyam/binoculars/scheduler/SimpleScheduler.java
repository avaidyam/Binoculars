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
import com.avaidyam.binoculars.remoting.RemoteInvocation;
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import com.avaidyam.binoculars.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.*;

/**
 *
 */
public class SimpleScheduler implements Scheduler {

	public static final int MAX_EXTERNAL_THREADS_POOL_SIZE = 1000; // max threads used when externalizing blocking api
	public static ThreadPoolExecutor exec;
	static {
		exec = new ThreadPoolExecutor(
				MAX_EXTERNAL_THREADS_POOL_SIZE, MAX_EXTERNAL_THREADS_POOL_SIZE,
				1L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>()
		);
		exec.allowCoreThreadTimeOut(true);
	}

	/**
	 * time ms until a warning is printed once a sender is blocked by a full nuclei queue
	 */
	public static long BLOCKED_MS_TIL_WARN = 5000;
	public static int DEFQSIZE = 32768; // will be alligned to 2^x
	
	protected SchedulingStrategy schedulingStrategy = new SchedulingStrategy();
	protected Dispatcher myThread;
	int qsize = DEFQSIZE;
	
	protected SimpleScheduler(String dummy) {
	}
	
	public SimpleScheduler() {
		myThread = new Dispatcher(this, true);
		myThread.start();
	}
	
	public SimpleScheduler(int qsize) {
		this.qsize = qsize;
		myThread = new Dispatcher(this, true);
		myThread.start();
	}
	
	/**
	 * @param qsize
	 * @param keepAlive
	 * 		- keep thread idle even if no nuclei is scheduled. Required for assisted scheduling e.g. in servers
	 */
	public SimpleScheduler(int qsize, boolean keepAlive) {
		this.qsize = qsize;
		myThread = new Dispatcher(this, !keepAlive);
		myThread.start();
	}
	
	@Override
	public int getDefaultQSize() {
		return qsize;
	}
	
	@Override
	public void pollDelay(int count) {
		schedulingStrategy.yield(count);
	}
	
	@Override
	public void put2QueuePolling(Queue q, boolean isCBQ, Object o, Object receiver) {
		int count = 0;
		long sleepStart = 0;
		boolean warningPrinted = false;
		while(!q.offer(o)) {
			pollDelay(count++);
			if(schedulingStrategy.isYielding(count)) {
				Nucleus sendingNucleus = Nucleus.sender.get();
				if(receiver instanceof Nucleus && ((Nucleus) receiver).__stopped) {
					String dl;
					if(o instanceof RemoteInvocation) {
						dl = ((RemoteInvocation) o).getMethod().getName();
					} else {
						dl = "" + o;
					}
					if(sendingNucleus != null)
						sendingNucleus.__addDeadLetter((Nucleus) receiver, dl);
					throw new Exceptions.NucleusStoppedException(dl);
				}
				if(sendingNucleus != null && sendingNucleus.__throwExAtBlock)
					throw Exceptions.NucleusBlockedException.INSTANCE;
				if(schedulingStrategy.isSleeping(count)) {
					if(sleepStart == 0) {
						sleepStart = System.currentTimeMillis();
					} else if(!warningPrinted && System.currentTimeMillis() - sleepStart > BLOCKED_MS_TIL_WARN) {
						String receiverString;
						warningPrinted = true;
						if(receiver instanceof Nucleus) {
							if(q == ((Nucleus) receiver).__channel.outbox) {
								receiverString = receiver.getClass().getSimpleName() + " outbox";
							} else if(q == ((Nucleus) receiver).__channel.inbox) {
								receiverString = receiver.getClass().getSimpleName() + " inbox";
							} else {
								receiverString = receiver.getClass().getSimpleName() + " unknown queue";
							}
						} else {
							receiverString = "" + receiver;
						}
						String sender = "";
						if(sendingNucleus != null)
							sender = ", sender:" + sendingNucleus.getNucleus().getClass().getSimpleName();
						Log.w(this.toString(), "Warning: Thread " + Thread.currentThread().getName() + " blocked more than " + BLOCKED_MS_TIL_WARN + "ms trying to put message on " + receiverString + sender + " log:" + o);
					}
				}
			}
		}
	}
	
	public Future put2QueuePolling(RemoteInvocation e) {
		final Future fut;
		if(e.hasFutureResult() && !(e.getFutureCB() instanceof SignalWrapper)) {
			fut = new CompletableFuture<>();
			e.setFutureCB(new SignalWrapper<>(e.getSendingNucleus(), (Signal<Object>) fut::complete));
		} else
			fut = null;
		Nucleus targetNucleus = e.getTargetNucleus();
		put2QueuePolling(e.isCallback() ? targetNucleus.__channel.outbox : targetNucleus.__channel.inbox, false, e, targetNucleus);
		return fut;
	}
	
	@Override
	public Object enqueueCall(RemoteRegistry reg, Nucleus sendingNucleus, Nucleus receiver, String methodName, Object[] args, boolean isCB) {
		Nucleus nucleus = receiver.getNucleus();
		Method method = nucleus.__getCachedMethod(methodName, nucleus);
		
		if(method == null)
			throw new RuntimeException("unknown method " + methodName + " on " + nucleus);
		// scan for callbacks in arguments ..
		for(int i = 0; i < args.length; i++) {
			Object arg = args[i];
			if(arg instanceof Signal) {
				args[i] = new SignalWrapper<>(sendingNucleus, (Signal<Object>) arg);
			}
		}
		
		RemoteInvocation e = new RemoteInvocation(
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
	
	@Override
	public void terminateIfIdle() {
		myThread.setAutoShutdown(true);
	}
	
	@Override
	public void threadStopped(Dispatcher th) {
	}
	
	public void setKeepAlive(boolean b) {
		if(myThread != null)
			myThread.setAutoShutdown(b);
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
			if(method.getDeclaringClass() == Object.class)
				return method.invoke(proxy, args); // toString, hashCode etc. invoke sync (DANGER if hashcode accesses mutable local state)
			if(target != null) {
				RemoteInvocation ce = new RemoteInvocation(target, method, args, Nucleus.sender.get(), targetNucleus, true);
				put2QueuePolling(targetNucleus.__channel.outbox, true, ce, targetNucleus);
			}
			return null;
		}
	}
	
	@Override
	public InvocationHandler getInvoker(Nucleus dispatcher, Object toWrap) {
		return new CallbackInvokeHandler(toWrap, dispatcher);
	}
	
	@Override
	public <T> T inThread(Nucleus nucleus, T callback) {
		Class<?>[] interfaces = callback.getClass().getInterfaces();
		InvocationHandler invoker = nucleus.__scheduler.getInvoker(nucleus, callback);
		if(invoker == null) // called from outside nuclei world
			return callback; // callback in callee thread
		//noinspection unchecked
		return (T) Proxy.newProxyInstance(callback.getClass().getClassLoader(), interfaces, invoker);
	}
	
	@Override
	public void delayedCall(long millis, Runnable toRun) {
		Nucleus.delayedCalls.schedule(new TimerTask() {
			@Override
			public void run() {
				toRun.run();
			}
		}, millis);
		
	}
	
	@Override
	public <T> void runBlockingCall(Nucleus emitter, Callable<T> toCall, Signal<T> resultHandler) {
		final SignalWrapper<T> resultWrapper = new SignalWrapper<>(emitter, resultHandler);
		SimpleScheduler.exec.execute(() -> {
			try {
				resultWrapper.complete(toCall.call(), null);
			} catch(Throwable th) {
				resultWrapper.complete(null, th);
			}
		});
	}
	
	@Override
	public Dispatcher assignDispatcher(int minLoadPerc) {
		return myThread;
	}
	
	@Override
	public void rebalance(Dispatcher dispatcher) {
	}
	
	@Override
	public SchedulingStrategy getBackoffStrategy() {
		return schedulingStrategy;
	}
	
	@Override
	public void tryStopThread(Dispatcher dispatcher) {
		
	}
	
	@Override
	public void tryIsolate(Dispatcher dp, Nucleus nucleusRef) {
		
	}
	
	@Override
	public int getNumNuclei() {
		return myThread.getNuclei().length;
	}


}

