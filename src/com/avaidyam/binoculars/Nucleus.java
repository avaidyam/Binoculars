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

import com.avaidyam.binoculars.asyncio.WrapperExecutorService;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.Spore;
import com.avaidyam.binoculars.remoting.RemoteConnection;
import com.avaidyam.binoculars.scheduler.Dispatcher;
import com.avaidyam.binoculars.scheduler.ElasticScheduler;
import com.avaidyam.binoculars.scheduler.Scheduler;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
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
    public Channel __channel;
    public Thread __dispatcher;
    public Scheduler __scheduler;
    public volatile boolean __stopped = false;
    public Nucleus __self; // the proxy
    public int __remoteId;
    public boolean __throwExAtBlock = false;
    // a list of connection required to be notified on close
    public volatile ConcurrentLinkedQueue<RemoteConnection> __connections;
    // register callbacks notified on stop
    ConcurrentLinkedQueue<Signal<SELF>> __stopHandlers;
    // remoteconnection this in case of remote ref
    public RemoteConnection __clientConnection;

    /**
     * A tagging interface to identify Nucleus proxies. {@link Nucleus#of(Class)}
     * internally generates an {@link Proxy} which translates each method
     * call into a message to be enqueued by the elastic scheduler to the
     * underlying nuclei instance.
     *
     * @param <T> the type of the Nucleus proxied
     */
    public interface Proxy<T extends Nucleus> {

        /**
         * Returns the underlying Nucleus behind this Proxy. Can be
         * used to verify if an Object is the real nuclei, or a proxy,
         * like so: {@code nuclei.getNucleus() == nuclei}.
         *
         * @return the nuclei under this proxy
         */
        Nucleus<T> getNucleus();
    }

    /**
     * The Timer used by Nuclei to schedule delayed invocations.
     */
    public static Timer delayedCalls = new Timer();

    /**
     * The default Nucleus Scheduler.
     */
    public static Supplier<Scheduler> defaultScheduler = () -> new ElasticScheduler(1);

    /**
     * Contains the sending Nucleus that the message came from (null if otherwise).
     */
    public static ThreadLocal<Nucleus> sender = new ThreadLocal<>();

    /**
     * Contains the remote connection if current message came from a remote.
     */
    public static ThreadLocal<RemoteConnection> connection = new ThreadLocal<>();

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
        T a = (T) Nucleus.newProxy(actorClazz, scheduler, qsize);
		a.init(); // queues a constructor call
		return a;
    }

    /**
     * Transforms the Nucleus into an ExecutorService such that all
     * Runnables in the ExecutorService are processed by the Nucleus's
     * Channel, Scheduler, and Dispatcher.
     *
     * @param nucleus
     * @param <T>
     * @return
     */
	public static <T extends Nucleus> ExecutorService toExecutor(final Nucleus<T> nucleus) {
		return new WrapperExecutorService() {
			final Nucleus _this = nucleus;
			@Override public void execute(Runnable command) {
				this._this.self().execute(command);
			}
		};
	}

    /**
     * Submit a Runnable to be invoked after a delay.
     *
     * @param millis
     * @param task
     */
    public static void submitDelayed(long millis, final Runnable task) {
        Nucleus.delayedCalls.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, millis);
    }

	/**
	 * processes messages from inbox/outbox until no messages are left
	 * NOP if called from non nuclei thread.
	 */
	public static void yield() {
		yield(0);
	}

	/**
	 * process messages on the inbox/outbox until timeout is reached. In case timeout is 0,
	 * process until inbox+callback queue is empty.
	 *
	 * If called from a non-nuclei thread, either sleep until timeout or (if timeout == 0) its a NOP.
	 *
	 * @param timeout
	 */
	public static void yield(long timeout) {
		long endtime = 0;
		if (timeout > 0)
			endtime = System.currentTimeMillis() + timeout;

        // If we're not in a Dispatcher, we can't defer()!
		if (!(Thread.currentThread() instanceof Dispatcher) && timeout > 0) {
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
		} else ((Dispatcher)Thread.currentThread()).defer(endtime);
	}

    /**
     * use this to call public methods using nuclei-dispatch instead of direct in-thread call.
     * Important: When passing references out of your nuclei, always pass 'self()' instead of this !
     *
     * @return
     */
    protected SELF self() {
        //noinspection unchecked
        return (SELF)__self;
    }

    /**
     * @return if this is an actorproxy, return the underlying nuclei instance, else return this
     */
    public SELF getNucleus() {
        //noinspection unchecked
        return (SELF)this;
    }

    /**
     * stop receiving events. If there are no actors left on the underlying dispatcher,
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
	 * just enqueue given runable to this actors inbox and execute on the nuclei's thread
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

////////////////////////////// internals ///////////////////////////////////////////////////////////////////

    protected boolean getThrowExWhenBlocked() {
        return __throwExAtBlock;
    }

    /**
     * tell the execution machinery to throw an NucleusBlockedException in case the nuclei is blocked trying to
     * put a message on an overloaded nuclei's inbox/queue. Useful e.g. when dealing with actors representing
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
        Log.w("NONE", s);
        Channel.deadLetters.add(s);
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

    public static <T extends Nucleus> T makeProxy(Class<T> clz, Dispatcher disp, int qs) {
        try {
            if (qs <= 100)
                qs = disp.getScheduler().getDefaultQSize();

            T realNucleus = clz.newInstance();
            T selfproxy = NucleusProxifier.instantiateProxy(realNucleus);

            realNucleus.__channel = new Channel(qs);
            realNucleus.__scheduler = disp.getScheduler();
            realNucleus.__dispatcher = disp;
            realNucleus.__self = selfproxy;

            selfproxy.__channel = realNucleus.__channel;
            selfproxy.__scheduler = disp.getScheduler();
            selfproxy.__dispatcher = disp;
            selfproxy.__self = selfproxy;

            disp.addNucleus(realNucleus);
            return selfproxy;
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }
    }

    public static <T extends Nucleus> T newProxy(Class<T> clz, Scheduler sched, int qsize) {
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
