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

package com.avaidyam.binoculars;

import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.future.*;
import com.avaidyam.binoculars.remoting.RemoteConnection;
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import com.avaidyam.binoculars.scheduler.Dispatcher;
import com.avaidyam.binoculars.scheduler.ElasticScheduler;
import com.avaidyam.binoculars.scheduler.Scheduler;
import com.avaidyam.binoculars.asyncio.WrapperExecutorService;
import external.jaq.mpsc.MpscConcurrentQueue;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base-class for nuclei implementations. Note that actors are not created using constructors.
 * Use Nucleus.of(..) to instantiate an nuclei instance. To pass initialization parameter,
 * define an init method in your implementation and call it from the instantiating instance.
 * <p>
 * The init method then will be executed in the thread of the dispatcher associated with your
 * nuclei avoiding problems raised by state visibility inconsistency amongst threads.
 * <p>
 * Inside an nuclei, everything is executed single threaded. You don't have to worry about synchronization.
 * <p>
 * All 'messages' of an nuclei are defined by 'public void' methods.
 * Nucleus methods are not allowed to return values. They must be of type void. Pass a Callback as argument to a call
 * in order to complete results from other actors/threads.
 * Non public methods can be called from inside the nuclei, but not outside as a message.
 * <p>
 * Note that you have to pass immutable objects as arguments, else you'll get unpredictable behaviour.
 * <p>
 * Code inside an nuclei is not allowed to ever block the current thread (networking etc.).
 * Use Nucleus.Exec in case you need to do blocking calls (e.g. synchronous requests)
 * --
 * Note that there is no supervision or monitoring; to do so, you can introspect qualities yourself.
 * --
 * Define as in: `public class Test extends Nucleus<Test> { ... }`
 * Don't define a constructor! Use the init() and deinit() functions instead.
 * --
 * You can also inherit from other Nuclei, and if generics throw a fit, include the following:
 * `@Override Nucleus self() { return super.self() }`
 * --
 * Only the following are free async methods: void xyz(...), Signal<...> xyz(...)
 * METHOD OVERLOADING IS NOT SUPPORTED FOR NUCLEI!
 * USE this FOR VARIABLES AND SYNC METHODS, USE self() for ASYNC METHODS!
 *
 */
public class Nucleus<SELF extends Nucleus> implements Serializable, Executor, AutoCloseable {

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
    public static NucleiImpl instance = new NucleiImpl(); // public for testing
    public static Timer delayedCalls = new Timer();
    public static Supplier<Scheduler> defaultScheduler = () -> new ElasticScheduler(1);//new SimpleScheduler();

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // static API

	/**
	 * A tagging interface to identify Nucleus proxies. {@link Nucleus#of(Class)}
	 * internally generates an {@link NucleusProxy} which translates each method
	 * call into a message to be enqueued by the elastic scheduler to the
	 * underlying nuclei instance.
	 *
	 * @param <T> the type of the Nucleus proxied
	 */
	public interface NucleusProxy<T extends Nucleus> {

		/**
		 * Returns the underlying Nucleus behind this NucleusProxy. Can be
		 * used to verify if an Object is the real nuclei, or a proxy,
		 * like so: {@code nuclei.getNucleus() == nuclei}.
		 *
		 * @return the nuclei under this proxy
		 */
		Nucleus<T> getNucleus();
	}

    public static void addDeadLetter(String s) {
        Log.w(null, s);
        deadLetters().add(s);
    }

    /**
     * in case called from an nuclei, wraps the given interface instance into a proxy such that
     * a calls on the interface get scheduled on the actors thread (avoids accidental multithreading
     * when handing out callback/listener interfaces from an nuclei)
     *
     * @param anInterface
     * @param <T>
     * @return
     */
    public static <T> T inThread(T anInterface) {
        Nucleus sender = Nucleus.sender.get();
        if (sender != null)
            return sender.getScheduler().inThread(sender.getNucleus(), anInterface);
        else return anInterface;
    }

    /**
     * messages that have been dropped or have been sent to stopped actors
     *
     * @return queue of dead letters. Note: only strings are recorded to avoid accidental references.
     */
    @SuppressWarnings("unchecked")
    public static ConcurrentLinkedQueue<String> deadLetters() {
        return instance.getDeadLetters();
    }

    /**
     * create an new nuclei. If this is called outside an nuclei, a new DispatcherThread will be scheduled. If
     * called from inside nuclei code, the new nuclei will share the thread+queue with the caller.
     *
     * @param actorClazz Class of nuclei
     * @param <T> Implementation type
     * @return Nucleus instance to use
     */
    @SuppressWarnings("unchecked")
    public static <T extends Nucleus> T of(Class<T> actorClazz) {
        return of(actorClazz, defaultScheduler.get(), -1);
    }

    /**
     * create an new nuclei. If this is called outside an nuclei, a new DispatcherThread will be scheduled. If
     * called from inside nuclei code, the new nuclei will share the thread+queue with the caller.
     *
     * @param actorClazz
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Nucleus> T of(Class<T> actorClazz, int qSize) {
        return of(actorClazz, defaultScheduler.get(), qSize);
    }

    /**
     * create an new nuclei dispatched in the given DispatcherThread
     *
     * @param actorClazz
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Nucleus> T of(Class<T> actorClazz, Scheduler scheduler) {
        return of(actorClazz, scheduler, -1);
    }

    /**
     * create an new nuclei dispatched in the given DispatcherThread
     *
     * @param actorClazz
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Nucleus> T of(Class<T> actorClazz, Scheduler scheduler, int qsize) {
        T a = (T) instance.newProxy(actorClazz, scheduler, qsize);
		a.init(); // queues a constructor call
		return a;
    }

	public static <T extends Nucleus> ExecutorService toExecutor(final Nucleus<T> nucleus) {
		return new WrapperExecutorService() {
			final Nucleus _this = nucleus;
			@Override public void execute(Runnable command) {
				this._this.self().execute(command);
			}
		};
	}

    public static void submitDelayed(long millis, final Runnable task) {
        Nucleus.delayedCalls.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, millis);
    }

    public static void submitPeriodic(long startMillis, final Function<Long, Long> task) {
        Nucleus.delayedCalls.schedule(new TimerTask() {
            @Override
            public void run() {
                long tim = task.apply(startMillis);
                if (tim > 0)
                    submitPeriodic(tim, task::apply);
            }
        }, startMillis);
    }

	/**
	 * processes messages from mailbox / callbackqueue until no messages are left
	 * NOP if called from non nuclei thread.
	 */
	public static void yield() {
		yield(0);
	}

	/**
	 * process messages on the mailbox/callback queue until timeout is reached. In case timeout is 0,
	 * process until mailbox+callback queue is empty.
	 *
	 * If called from a non-nuclei thread, either sleep until timeout or (if timeout == 0) its a NOP.
	 *
	 * @param timeout
	 */
	public static void yield(long timeout) {
		long endtime = 0;
		if ( timeout > 0 ) {
			endtime = System.currentTimeMillis() + timeout;
		}
		if ( Thread.currentThread() instanceof Dispatcher) {
			Dispatcher dt = (Dispatcher) Thread.currentThread();
			Scheduler scheduler = dt.getScheduler();
			boolean term = false;
			int idleCount = 0;
			while ( ! term ) {
				boolean hadSome = dt.pollQs();
				if ( ! hadSome ) {
					idleCount++;
					scheduler.pollDelay(idleCount);
					if ( endtime == 0 ) {
						term = true;
					}
				} else {
					idleCount = 0;
				}
				if ( endtime != 0 && System.currentTimeMillis() > endtime ) {
					term = true;
				}
			}
		} else {
			if ( timeout > 0 ) {
				try {
					Thread.sleep(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

    // end static API
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // helper

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void throwException(Throwable exception) throws T {
        throw (T) exception;
    }


    public static class NucleiImpl {

        protected ConcurrentLinkedQueue deadLetters = new ConcurrentLinkedQueue();

        public ConcurrentLinkedQueue getDeadLetters() {
            return deadLetters;
        }

        public Nucleus makeProxy(Class<? extends Nucleus> clz, Dispatcher disp, int qs) {
            try {
                if (qs <= 100)
                    qs = disp.getScheduler().getDefaultQSize();

                Nucleus realNucleus = clz.newInstance();
                realNucleus.__mailbox = createQueue(qs);
                realNucleus.__mbCapacity = ((MpscConcurrentQueue) realNucleus.__mailbox).getCapacity();
                realNucleus.__cbQueue = createQueue(qs);

                Nucleus selfproxy = NucleusProxifier.instantiateProxy(realNucleus);
                realNucleus.__self = selfproxy;
                selfproxy.__self = selfproxy;

                selfproxy.__mailbox = realNucleus.__mailbox;
                selfproxy.__mbCapacity = realNucleus.__mbCapacity;
                selfproxy.__cbQueue = realNucleus.__cbQueue;

                realNucleus.__scheduler = disp.getScheduler();
                selfproxy.__scheduler = disp.getScheduler();

                realNucleus.__currentDispatcher = disp;
                selfproxy.__currentDispatcher = disp;

                disp.addNucleus(realNucleus);
                return selfproxy;
            } catch (Exception e) {
                if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                throw new RuntimeException(e);
            }
        }

        public Queue createQueue(int qSize) {
            return new MpscConcurrentQueue(qSize);
        }

        public Nucleus newProxy(Class<? extends Nucleus> clz, Scheduler sched, int qsize) {
            if (sched == null && Thread.currentThread() instanceof Dispatcher)
                    sched = ((Dispatcher) Thread.currentThread()).getScheduler();

            try {
                if (sched == null)
                    sched = new ElasticScheduler(1, qsize);
                if (qsize < 1)
                    qsize = sched.getDefaultQSize();
                return makeProxy(clz, sched.assignDispatcher(70), qsize);
            } catch (RuntimeException e) {
                throw e;
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static ThreadLocal<Nucleus> sender = new ThreadLocal<>();
    public static ThreadLocal<RemoteRegistry> registry = new ThreadLocal<>();

    // contains remote connection if current message came from remote
    public static ThreadLocal<RemoteConnection> connection = new ThreadLocal<>();

    // internal ->
    public Queue __mailbox;
    public int __mbCapacity;
    public Queue __cbQueue;
    public Thread __currentDispatcher;
    public Scheduler __scheduler;
    public volatile boolean __stopped = false;
    public Nucleus __self; // the proxy
    public int __remoteId;
    public boolean __throwExAtBlock = false;
    public volatile ConcurrentLinkedQueue<RemoteConnection> __connections; // a list of connection required to be notified on close
    // register callbacks notified on stop
    ConcurrentLinkedQueue<Signal<SELF>> __stopHandlers;
    public RemoteConnection __clientConnection; // remoteconnection this in case of remote ref

	Thread _t; // debug
	protected TicketMachine __ticketMachine;


	/**
     * required by bytecode magic. Use Nucleus.Channel(..) to construct nuclei instances
     */
    public Nucleus() {}
    // <- internal

    /**
     * use this to call public methods using nuclei-dispatch instead of direct in-thread call.
     * Important: When passing references out of your nuclei, always pass 'self()' instead of this !
     *
     * @return
     */
    protected SELF self() {
        return (SELF)__self;
    }

    /**
     * @return if this is an actorproxy, return the underlying nuclei instance, else return this
     */
    public SELF getNucleus() {
        return (SELF)this;
    }

    /**
     * $$stop receiving events. If there are no actors left on the underlying dispatcher,
     * the dispatching thread will be terminated.
     */

    public void stop() {
        if(isRemote())
            throw new RuntimeException("Cannot stop a remote nuclei!");
        self().ping().then(() -> self().asyncStop());
    }

	// internal. tweak to check for remote ref before sending
    // Don't actually use!
    @Export(transport=false)
    public void asyncStop() {
		deinit(); // IS NOT QUEUED! locally executed
        __stop();
    }

	/**
	 * Called upon construction of Nucleus, and should be used instead of a constructor.
	 */
    @Export(transport=false)
	public void init() {
		// Unimplemented.
	}

	/**
	 * Called upon destruction of a Nucleus, and should be used as a destructor.
	 */
    @Export(transport=false)
	public void deinit() {
        // Unimplemented.
    }


    public boolean isStopped() {
        return __stopped;
    }


    public boolean isProxy() {
        return getNucleus() != this;
    }

    /**
     * @return current nuclei thread or throw an exception if not running inside an nuclei thread.
     */
    public static Nucleus current() {
        Nucleus nucleus = sender.get();
        if ( nucleus == null )
            throw new Exceptions.MustBeRunFromNucleusThreadException();
        return nucleus;
    }

    public static boolean inside() {
        return sender.get() != null;
    }

	/**
	 * just enqueue given runable to this actors mailbox and execute on the nuclei's thread
	 *
	 * @param command
	 */
    @Export(transport=false)
	@Override
	public void execute(Runnable command) {
		self().__submit(command);
	}

    @Export
	public void __submit(Runnable toRun) {
		toRun.run();
	}

    /**
     * execute a callable asynchronously (in a different thread) and return a future
     * of the result (delivered in caller thread). Can be used to isolate blocking operations
     * <p>
     * WARNING: do not access local nuclei state (instance fields) from within the callable (=hidden parallelism).
     *
     * <code>
     *     exec( () -> {
     *          try {
     *              return new CompletableFuture(new URL("http://www.google.com").getContent());
     *          } catch (IOException e) {
     *              return new CompletableFuture(null, e);
     *          }
     *     }).then( (content, error) -> {
     *          // handle result
     *     });
     * </code>
     *
     *
     * @param callable
     * @param <T>
     * @return
     */
    @Export
    public <T> Future<T> exec(Callable<T> callable) {
        CompletableFuture<T> prom = new CompletableFuture<>();
        __scheduler.runBlockingCall(self(), callable, prom);
        return prom;
    }

	/**
	 * can be used to wait for all messages having been processed and get a signal from the returned future once this is complete
	 * @return
	 */
    @Export(transport=false)
	public Future<Void> ping() {
		return new CompletableFuture<>();
	}

    /**
     * execute a Runnable on the actors thread, similar to invokeLater in Swing
     * <p>
     * self().$run( () -> .. ) - run the runnable after the current message has been processed
     * this.$run( () -> .. )   - runs the runnable synchronous (actually useless, could call it directly)
     */
    @Export
    public <I, O> void spore(Spore<I, O> spore) {
        if(spore != null)
            spore.doRemote(null);
    }

    /**
     * schedule an action or call delayed.
     * typical use case:
     * delayed( 100, () -> { self().doAction( x, y,  ); } );
     */

	@Export(transport=false)
    public void delayed(long millis, final Runnable toRun) {
        __scheduler.delayedCall(millis, inThread(self(), toRun));
    }

    /**
     * @return true if mailbox fill size is ~half capacity
     */

    public boolean isMailboxPressured() {
        return __mailbox.size() * 2 > __mbCapacity;
    }


    public Scheduler getScheduler() {
        return __scheduler;
    }


    public boolean isCallbackQPressured() {
        return __cbQueue.size() * 2 > __mbCapacity;
    }

    /**
     * @return an estimation on the queued up entries in the mailbox. Can be used for bogus flow control
     */

    public int getMailboxSize() {
        return __mailbox.size();
    }


    public int getQSizes() {
        return getCallbackSize() + getMailboxSize();
    }

    /**
     * @return an estimation on the queued up callback entries. Can be used for bogus flow control.
     */

    public int getCallbackSize() {
        return __cbQueue.size();
    }

	/**
	 * wraps an interface into a proxy of that interface. The proxy object automatically enqueues all
	 * calls made on the interface onto the callback queue.
	 * Can be used incase one needs to pass other callback interfaces then built in Callback object.
	 * Stick to using 'Callback' class if possible.
	 *
	 * @param proxy
	 * @param cbInterface
	 * @param <T>
	 * @return
	 */

    protected <T> T inThread(Nucleus proxy, T cbInterface) {
        return __scheduler.inThread(proxy, cbInterface);
    }


    public Nucleus getNucleusRef() {
        return __self;
    }


    public boolean isRemote() {
        return __remoteId != 0;
    }

    /**
     * generic method for untyped messages.
     */
    @Export
    public Future ask(String interest, Object contents) {
        return new CompletableFuture();
    }

    /**
     * generic method for untyped messages.
     */
    @Export
    public void tell(String interest, Object contents) {
        //
    }

	/**
	 * Debug method.
	 * can be called to check nuclei code is actually single threaded.
	 * By using custom callbacks/other threading bugs accidental multi threading
	 * can be detected that way.
	 *
	 * WARNING: only feasable when running in dev on single threaded scheduler (ElasticScheduler with a single thread),
	 * else false alarm might occur
	 */
	protected final void checkThread() {
		if (_t == null) {
			_t = Thread.currentThread();
		} else {
			if (_t != Thread.currentThread()) {
				throw new RuntimeException("Wrong Thread");
			}
		}
	}

	/**
	 * closes associated remote connection(s) if present. NOP otherwise.
	 * Close refers to "unmapping" the nuclei, underlying network connections will not be
	 * closed (else server down on single client disconnect)
	 */
    @Export(transport=false)
    public void close() {
        if (__connections != null) {
            final ConcurrentLinkedQueue<RemoteConnection> prevCon = getNucleusRef().__connections;
            getNucleusRef().__connections = null;
            getNucleus().__connections = null;
            prevCon.forEach(RemoteConnection::close);
        }
    }

    /**
     * closes the connection to the remote client which has sent currently executing message.
     * If current message was not sent by a client, NOP.
     */
    protected void closeCurrentClient() {
        RemoteConnection remoteConnection = connection.get();
        if ( remoteConnection != null ) {
            delayed(1000, () -> remoteConnection.close() );
        }
    }

    /**
     * avoids exception when closing an nuclei after stop has been called.
     */

    public void stopSafeClose() {
        if (isStopped()) {
            getNucleus().close(); // is threadsafe
        } else {
            self().close();
        }
    }


    public boolean isPublished() {
        return __connections != null && __connections.peek() != null;
    }


    /**
     * enforce serial execution of asynchronous tasks. The 'toRun' closure must call '.signal()' on the given future
     * to signal his processing has finished and the next item locked on 'transactionKey' can be processed.
     *
     * @param transactionKey
     * @param toRun
     */
    protected void serialOn(Object transactionKey, Consumer<Future> toRun) {
        if (isProxy())
            throw new RuntimeException("cannot call on nuclei proxy object");
        if (__ticketMachine == null) {
            __ticketMachine = new TicketMachine();
        }
        __ticketMachine.getTicket(transactionKey).onResult(finSig -> {
            try {
                toRun.accept(finSig);
            } catch (Throwable th) {
                Log.w(Nucleus.this.toString(), "", th);
            }
        });
    }

	public Dispatcher getCurrentDispatcher() {
		return (Dispatcher) __currentDispatcher;
	}

	protected ConcurrentLinkedQueue<RemoteConnection> getConnections() {
		return __connections;
	}

////////////////////////////// internals ///////////////////////////////////////////////////////////////////

    protected boolean getThrowExWhenBlocked() {
        return __throwExAtBlock;
    }

    /**
     * tell the execution machinery to throw an NucleusBlockedException in case the nuclei is blocked trying to
     * put a message on an overloaded nuclei's mailbox/queue. Useful e.g. when dealing with actors representing
     * a remote client (might block or lag due to connection issues).
     *
     * @param b
     * @return
     */

    public SELF setThrowExWhenBlocked(boolean b) {
        getNucleusRef().__throwExAtBlock = b;
        getNucleus().__throwExAtBlock = b;
        return (SELF) this;
    }


    public void __addStopHandler(Signal<SELF> cb) {
        if (__stopHandlers == null) {
            getNucleusRef().__stopHandlers = new ConcurrentLinkedQueue();
            getNucleus().__stopHandlers = getNucleusRef().__stopHandlers;
        }

        if(!__stopHandlers.contains(cb))
            __stopHandlers.add(cb);
    }


    public void __addRemoteConnection(RemoteConnection con) {
        if (__connections == null) {
            getNucleusRef().__connections = new ConcurrentLinkedQueue<RemoteConnection>();
            getNucleus().__connections = getNucleusRef().__connections;
        }
	    if ( ! __connections.contains(con) ) {
		    __connections.add(con);
	    }
    }


    public void __removeRemoteConnection(RemoteConnection con) {
        if (__connections != null) {
            __connections.remove(con);
        }
    }


    public void __stop() {
	    Log.d(this.toString(), "stopping nuclei " + getClass().getSimpleName());
        Nucleus self = __self;
        if (self == null || getNucleus() == null || (self.isStopped() && getNucleus().isStopped()))
            return;

        getNucleusRef().__stopped = true;
        getNucleus().__stopped = true;
        getNucleusRef().__throwExAtBlock = true;
        getNucleus().__throwExAtBlock = true;
        if (__stopHandlers != null) {
            __stopHandlers.forEach((cb) -> cb.complete(self(), null));
            __stopHandlers.clear();
        }

        // FIXME: causes NullPointerException instead of DeadLetter addition
        // remove ref to real nuclei as ref might still be referenced in threadlocals and queues
        //try {
        //    getNucleusRef().getClass().getField("__target").set(getNucleusRef(), null);
        //} catch (IllegalAccessException | NoSuchFieldException e) {
        //    e.printStackTrace();
        //}
        throw Exceptions.InternalNucleusStoppedException.INSTANCE;
    }

    // dispatch an outgoing call to the target nuclei queue. Runs in Caller Thread

    public Object __enqueueCall(Nucleus receiver, String methodName, Object args[], boolean isCB) {
        //System.out.println("INVOKE " + methodName + " (" + args.length + ") ON " + receiver);
        if (__stopped) {
            if (methodName.equals("stop")) // ignore double stop
                return null;
            __addDeadLetter(receiver, methodName);
            //throw new RuntimeException("Nucleus " + this + " received message after being stopped " + methodName);
        }
        return __scheduler.enqueueCall(sender.get(), receiver, methodName, args, isCB);
    }


    public void __addDeadLetter(Nucleus receiver, String methodName) {
        String senderString = sender.get() == null ? "null" : sender.get().getClass().getName();
        String s = "DEAD LETTER: sender:" + senderString + " receiver::msg:" + receiver.getClass().getSimpleName() + "::" + methodName;
        s = s.replace("_NucleusProxy", "");
        addDeadLetter(s);
    }

    // FIXME: would be much better to do lookup at method invoke time INSIDE nuclei thread instead of doing it on callside (contended)
    ConcurrentHashMap<String, Method> methodCache;

    public Method __getCachedMethod(String methodName, Nucleus nucleus) {
	    if ( methodCache == null ) {
		    methodCache = new ConcurrentHashMap<>(7);
	    }
        Method method = methodCache.get(methodName);
        if (method == null) {
            Method[] methods = nucleus.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if (m.getName().equals(methodName)) {
                    methodCache.put(methodName, m);
                    method = m;
                    break;
                }
            }
        }
        return method;
    }
}
