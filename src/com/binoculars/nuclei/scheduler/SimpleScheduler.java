package com.binoculars.nuclei.scheduler;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.Exceptions;
import com.binoculars.nuclei.management.SchedulerStatusMXBean;
import com.binoculars.nuclei.remoting.CallEntry;
import com.binoculars.nuclei.remoting.base.RemoteRegistry;
import com.binoculars.future.Signal;
import com.binoculars.future.SignalWrapper;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
import com.binoculars.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.Callable;

public class SimpleScheduler implements Scheduler {
	
	public static final boolean DEBUG_SCHEDULING = true;
	/**
	 * time ms until a warning is printed once a sender is blocked by a full nuclei queue
	 */
	public static long BLOCKED_MS_TIL_WARN = 5000;
	public static int DEFQSIZE = 32768; // will be alligned to 2^x
	
	protected BackOffStrategy backOffStrategy = new BackOffStrategy();
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
		backOffStrategy.yield(count);
	}
	
	@Override
	public void put2QueuePolling(Queue q, boolean isCBQ, Object o, Object receiver) {
		int count = 0;
		long sleepStart = 0;
		boolean warningPrinted = false;
		while(!q.offer(o)) {
			pollDelay(count++);
			if(backOffStrategy.isYielding(count)) {
				Nucleus sendingNucleus = Nucleus.sender.get();
				if(receiver instanceof Nucleus && ((Nucleus) receiver).__stopped) {
					String dl;
					if(o instanceof CallEntry) {
						dl = ((CallEntry) o).getMethod().getName();
					} else {
						dl = "" + o;
					}
					if(sendingNucleus != null)
						sendingNucleus.__addDeadLetter((Nucleus) receiver, dl);
					throw new Exceptions.NucleusStoppedException(dl);
				}
				if(sendingNucleus != null && sendingNucleus.__throwExAtBlock)
					throw Exceptions.NucleusBlockedException.INSTANCE;
				if(backOffStrategy.isSleeping(count)) {
					if(sleepStart == 0) {
						sleepStart = System.currentTimeMillis();
					} else if(!warningPrinted && System.currentTimeMillis() - sleepStart > BLOCKED_MS_TIL_WARN) {
						String receiverString;
						warningPrinted = true;
						if(receiver instanceof Nucleus) {
							if(q == ((Nucleus) receiver).__cbQueue) {
								receiverString = receiver.getClass().getSimpleName() + " callbackQ";
							} else if(q == ((Nucleus) receiver).__mailbox) {
								receiverString = receiver.getClass().getSimpleName() + " mailbox";
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
	
	public Future put2QueuePolling(CallEntry e) {
		final Future fut;
		if(e.hasFutureResult() && !(e.getFutureCB() instanceof SignalWrapper)) {
			fut = new CompletableFuture<>();
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
	public Object enqueueCall(Nucleus sendingNucleus, Nucleus receiver, String methodName, Object[] args, boolean isCB) {
		return enqueueCallFromRemote(null, sendingNucleus, receiver, methodName, args, isCB);
	}
	
	@Override
	public Object enqueueCallFromRemote(RemoteRegistry reg, Nucleus sendingNucleus, Nucleus receiver, String methodName, Object[] args, boolean isCB) {
		// System.out.println("dispatch "+methodName+" "+Thread.currentThread());
		// here sender + receiver are known in a ST context
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
		
		CallEntry e = new CallEntry(
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
				CallEntry ce = new CallEntry(target, method, args, Nucleus.sender.get(), targetNucleus, true);
				put2QueuePolling(targetNucleus.__cbQueue, true, ce, targetNucleus);
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
		{
			return callback; // callback in callee thread
		}
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
		Nucleus.exec.execute(() -> {
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
	public BackOffStrategy getBackoffStrategy() {
		return backOffStrategy;
	}
	
	@Override
	public void tryStopThread(Dispatcher dispatcher) {
		
	}
	
	@Override
	public void tryIsolate(Dispatcher dp, Nucleus nucleusRef) {
		
	}
	
	@Override
	public int getNumNuclei() {
		return myThread.getNucleiNoCopy().length;
	}

	public SchedulerStatusMXBean schedulerStatus() {
		return new SchedulerStatusMXBean.SchedulerStatus(1, getDefaultQSize(), 0);
	}
}

