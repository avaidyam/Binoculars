package com.binoculars.future;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.scheduler.Scheduler;
import com.binoculars.nuclei.scheduler.Dispatcher;
import com.binoculars.nuclei.scheduler.ElasticScheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** SELECT EXECUTION THREAD <-- VIA(EXECUTOR) */
/*
The first and most useful is via, which passes execution through an Executor, which usually has
the effect of running the callback in a new thread.

aFuture
  .then(x)
  .via(e1).then(y1).then(y2)
  .via(e2).then(z);
*/

/** CAPTURE EXCEPTIONS AUTOMATICALLY --> FAILURE */
/*
It's good practice to use setWith which takes a function and automatically captures exceptions, e.g.

CompletableFuture<int> p;
p.setWith([]{
  try {
    // do stuff that may throw
    return 42;
  } catch (MySpecialException const& e) {
    // handle it
    return 7;
  }
  // Any exceptions that we didn't catch, will be caught for us
});
*/

/** FINALLY VIA ENSURE() OPERATOR */
/*
Future<T>::ensure(F func) is similar to the finally block in languages like Java. That is, it takes a void
function and will execute regardless of whether the Future contains a value or an exception. The resultant
Future will contain the exception/value of the original Future, unless the function provided to ensure throws,
 in which case that exception will be caught and propagated instead.
*/

/** ALLOW ONERROR(EXCEPTION_CLASS, HANDLER) FOR SPECIFIC EXCEPTION TYPES */

public class CompletableFuture<T> implements Future<T> {
    // probably unnecessary, increases cost
    // of allocation. However for now stay safe and optimize
    // from a proven-working implementation
    // note: if removed some field must set to volatile
    final AtomicBoolean lock = new AtomicBoolean(false); // (AtomicFieldUpdater is slower!)

    // Main components of Future.
    protected T result = null;
    protected Object error = null;

    String id;
    Future nextFuture;
    protected Signal resultReceiver;

    // fixme: use bits
    protected volatile boolean hadResult;
    protected boolean hasFired;

    public static Void NULL = (Void)null;

    // FIXME: Separate allOf<List<Future>> as allOf<Void> and async
    /**
     * similar to es6 CompletableFuture.allOf method, however non-Future objects are not allowed
     * equiv Scala async-like code:
     *
     * <code>
     * async(
     *      () -> mutator.$init(table),
     *      () -> {
     *          client.$run(table);
     *          return mutator.$run(table, 1000, null);
     *          },
     *      () -> table.$sync(),
     *      () -> client.$unsubscribe(table),
     *      () -> client.$checkCorrectness(table)
     * ).then( (res, err) -> {
     *      if (err == null) {
     *          System.out.println("NEXT RUN");
     *          table.$sync();
     *          testRun(table, mutator, client);
     *      } else {
     *          System.out.println(err);
     *          System.exit(1);
     *      }
     * });
     * </code>
     *
     * <p>
     * returns a future which is settled once allOf futures provided are settled
     */
    @SafeVarargs
    public static <T> Future<List<Future<T>>> allOf(Future<T>... futures) {
        return allOf(Arrays.asList(futures));
    }

    /**
     * similar to es6 CompletableFuture.allOf method, however non-Future objects are not allowed
     * <p>
     * returns a future which is settled once allOf futures provided are settled
     */
    public static <T> Future<List<Future<T>>> allOf(List<Future<T>> futures) {
	    if(futures == null || futures.size() <= 0)
		    return new CompletableFuture<>(new ArrayList<>());

        CompletableFuture<List<Future<T>>> res = new CompletableFuture<>();
        //List<Future<T>> allRes = new ArrayList<>(futures.size());

	    _awaitSettle(futures, 0, res);

        //for(int i = 0; i < futures.size() - 1; i++)
        //    allRes.add(futures.get(i).then(futures.get(i)));
        //allRes.add(futures.get(futures.size() - 1).then((r, e) -> res.complete(allRes, null)));

        return res;
    }

	// Helper
	private static <T> void _awaitSettle(final List<Future<T>> futures, final int index,
	                                     final Future<List<Future<T>>> result) {
		if (index < futures.size())
			futures.get(index).then((r, e) -> _awaitSettle(futures, index + 1, result));
		else result.complete(futures, null);
	}

    /**
     * similar to es6 CompletableFuture.race method, however non-Future objects are not allowed
     * <p>
     * returns a future which is settled once one of the futures provided gets settled
     */
    @SafeVarargs
    public static <T> Future<T> anyOf(Future<T>... futures) {
        return anyOf(Arrays.asList(futures));
    }

    /**
     * similar to es6 CompletableFuture.race method, however non-Future objects are not allowed
     * <p>
     * returns a future which is settled once one of the futures provided gets settled
     */
    public static <T> Future<T> anyOf(Collection<Future<T>> futures) {
	    if(futures == null || futures.size() <= 0)
		    return new CompletableFuture<>();

	    CompletableFuture<T> p = new CompletableFuture<>();
        AtomicBoolean fin = new AtomicBoolean(false);

        futures.parallelStream().forEach(f -> f.then((r, e) -> {
            if (fin.compareAndSet(false, true))
                p.complete(r, e);
        }));
        return p;
    }

    /**
     * helper to stream settled futures unboxed. e.g. allOf(f1,f2,..).then( farr -> stream(farr).forEach( val -> process(val) );
     * Note this can be used only on "settled" or "completed" futures. If one of the futures has been rejected,
     * a null value is streamed.
     *
     * @param settledFutures
     * @param <T>
     * @return
     */
    /*@SafeVarargs
    private static <T> Stream<T> streamHelper(Future<T>... settledFutures) {
        return Arrays.stream(settledFutures).map(Future::get);
    }

    private static <T> Stream<T> streamHelper(List<Future<T>> settledFutures) {
        return settledFutures.stream().map(Future::get);
    }//*/

    /**
     * await until allOf futures are settled and stream them
     */
    /*@SafeVarargs
    public static <T> Stream<T> stream(Future<T>... futures) {
        return streamHelper(allOf(futures).await());
    }

    public static <T> Stream<T> stream(List<Future<T>> futures) {
        return streamHelper(allOf(futures).await());
    }//*/

    /**
     *
     *
     * @param result
     * @param error
     */
    public CompletableFuture(T result, Object error) {
        this.result = result;
        this.error = error;
        hadResult = true;
    }

    /**
     *
     * @param result
     */
    public CompletableFuture(T result) {
        this(result, null);
    }

    /**
     *
     * @param throwable
     */
    public CompletableFuture(Throwable throwable) {
        this(null, throwable);
    }

    /**
     *
     */
    public CompletableFuture() {
        //
    }

    public CompletableFuture<T> setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }

    @Override
    public Future<T> then(Runnable result) {
        return then((r, e) -> result.run());
    }

    @Override
    public Future<T> onResult(Consumer<T> resultHandler) {
        return then((r, e) -> {
            if (e == null)
                resultHandler.accept(r);
        });
    }

    @Override
    public Future<T> onError(Consumer<Object> errorHandler) {
        return then((r, e) -> {
            if (e != null && e != CompletableFuture.Timeout.INSTANCE)
                errorHandler.accept(e);
        });
    }

    @Override
    public Future<T> onTimeout(Consumer<Object> timeoutHandler) {
        return then((r, e) -> {
            if (e == CompletableFuture.Timeout.INSTANCE)
                timeoutHandler.accept(e);
        });
    }

    @Override
    public <OUT> Future<OUT> then(final Function<T, Future<OUT>> function) {
        CompletableFuture<OUT> res = new CompletableFuture<>();
        then((r, e) -> {
            if (Signal.isError(e))
                res.complete(null, e);
            else function.apply(r).then(res);
        });
        return res;
    }

    @Override
    public <OUT> Future<OUT> then(Consumer<T> function) {
        CompletableFuture<OUT> res = new CompletableFuture<>();
        then((r, e) -> {
            if (Signal.isError(e)) {
                res.complete(null, e);
            } else {
                function.accept(r);
                res.complete();
            }
        });
        return res;
    }

    @Override
    public Future<T> then(Supplier<Future<T>> callable) {
        CompletableFuture<T> res = new CompletableFuture<>();
        then((r, e) -> {
            if (Signal.isError(e)) {
                res.complete(null, e);
            } else {
                Future<T> call = callable.get().then(res);
            }
        });
        return res;
    }

    @Override
    public <OUT> Future<OUT> catchError(final Function<Object, Future<OUT>> function) {
        CompletableFuture<OUT> res = new CompletableFuture<>();
        then((r, e) -> {
            if (!Signal.isError(e))
                res.complete(null, e);
            else function.apply(e).then(res);
        });
        return res;
    }

    @Override
    public <OUT> Future<OUT> catchError(Consumer<Object> function) {
        CompletableFuture<OUT> res = new CompletableFuture<>();
        then((r, e) -> {
            if (!Signal.isError(e))
                res.complete(null, e);
            else {
                function.accept(e);
                res.complete();
            }
        });
        return res;
    }

    public void timedOut(Timeout to) {
        if (!hadResult)
            complete(null, to);
    }

    @Override
    public Future<T> then(Signal<T> resultCB) {
        // FIXME: this can be implemented more efficient
        while (!lock.compareAndSet(false, true));

        try {
            if (resultReceiver != null)
                throw new RuntimeException("Double register of future listener");
            resultReceiver = resultCB;
            if (hadResult) {
                hasFired = true;
                if (nextFuture == null) {
                    nextFuture = new CompletableFuture(result, error);
                    lock.set(false);
                    resultCB.complete(result, error);
                } else {
                    lock.set(false);
                    resultCB.complete(result, error);
                    nextFuture.complete(result, error);
                    return nextFuture;
                }
            }
            if (resultCB instanceof Future)
                return (Future<T>)resultCB;
            lock.set(false);
            while (!lock.compareAndSet(false, true));

            if (nextFuture == null) {
                return nextFuture = new CompletableFuture();
            } else {
                return nextFuture;
            }
        } finally {
            lock.set(false);
        }
    }

    /**
     * special method for tricky things. Creates a nextFuture or returns it.
     * current
     *
     * @return
     */
    public CompletableFuture getNext() {
        while (!lock.compareAndSet(false, true)) {
        }
        try {
            if (nextFuture == null)
                return new CompletableFuture();
            else
                return (CompletableFuture) nextFuture;
        } finally {
            lock.set(false);
        }
    }

    public CompletableFuture getLast() {
        while (!lock.compareAndSet(false, true)) {
        }
        try {
            if (nextFuture == null)
                return this;
            else
                return ((CompletableFuture) nextFuture).getLast();
        } finally {
            lock.set(false);
        }
    }

    /**
     * same as then, but avoid creation of new future
     *
     * @param resultCB
     */
    public void finallyDo(Signal resultCB) {
        // FIXME: this can be implemented more efficient
        while (!lock.compareAndSet(false, true)) {
        }
        try {
            if (resultReceiver != null)
                throw new RuntimeException("Double register of future listener");
            resultReceiver = resultCB;
            if (hadResult) {
                hasFired = true;
                lock.set(false);
                resultCB.complete(result, error);
            }
        } finally {
            lock.set(false);
        }
    }

    /**
     *
     * @param res
     * @param error
     */
    @Override
    public final void complete(T res, Object error) {
	    this.result = res;
        Object prevErr = this.error;
        this.error = error;

        while (!lock.compareAndSet(false, true));

        try {
            if (hadResult) {
                if (prevErr instanceof Timeout) {
                    this.error = prevErr;
                    lock.set(false);
                    return;
                }
                lock.set(false);
                throw new RuntimeException("Double result received on future " + prevErr);
            }
            hadResult = true;
            if (resultReceiver != null) {
                if (hasFired) {
                    lock.set(false);
                    throw new RuntimeException("Double fire on callback");
                }
                hasFired = true;
                lock.set(false);
                resultReceiver.complete(result, error);
                resultReceiver = null;
                while (!lock.compareAndSet(false, true)) {
                }
                if (nextFuture != null) {
                    lock.set(false);
                    nextFuture.complete(result, error);
                }
                return;
            }
        } finally {
            lock.set(false);
        }
    }

    /**
     * force values to align on get() without calling callbacks
     * @param result
     * @param error
     */
    public final void obtrude(T result, Object error) {
        this.result = result;
        this.error = error;
    }

    public void obtrude(T result) {
        this.result = result;
    }

    public void obtrudeExceptionally(Throwable throwable) {
        this.error = throwable;
    }

    /**
     *
     * @return
     */
    @Override
    public T get() {
        return result;
    }

    @Override
    public T await(long timeout, TimeUnit timeUnit) {
        awaitFuture(timeout, timeUnit);
        if (!Signal.isError(getError()))
            return get();

        if (getError() instanceof Throwable)
            Nucleus.throwException((Throwable) getError());
        else if (getError() == Timeout.INSTANCE)
            throw new TimeoutException();
        else
            throw new ExecutionException(getError().toString());
        return null; // never reached
    }

    @Override
    public Future<T> awaitFuture(long timeout, TimeUnit timeUnit) {
        long endtime = 0;
        if ( timeUnit.toMillis(timeout) > 0 )
            endtime = System.currentTimeMillis() + timeUnit.toMillis(timeout);

        if (Thread.currentThread() instanceof Dispatcher) {
            Dispatcher dt = (Dispatcher)Thread.currentThread();
            Scheduler scheduler = dt.getScheduler();

            int idleCount = 0;
            dt.__stack.add(this);
            while (!isComplete()) {
                if (!dt.pollQs()) {
                    idleCount++;
                    scheduler.pollDelay(idleCount);
                } else idleCount = 0;

                if ( endtime != 0 && System.currentTimeMillis() > endtime ) {
                    timedOut(Timeout.INSTANCE);
                    break;
                }
            }

            dt.__stack.remove(dt.__stack.size() - 1);
            return this;
        } else { // if outside of nuclei machinery, just block (warning actually polls)
            // FIXME: Switch to CountDownLatch
            while (!isComplete()) {
                LockSupport.parkNanos(1000 * 500);
                if ( endtime != 0 && System.currentTimeMillis() > endtime ) {
                    timedOut(Timeout.INSTANCE);
                    break;
                }
            }
        }
        return this;
    }

    @Override
    public Future timeoutIn(long timeout, TimeUnit timeUnit) {
        final Nucleus nucleus = Nucleus.sender.get();
        if (nucleus != null) {
            try {
                Method m = nucleus.getClass().getDeclaredMethod("delayed", Long.TYPE, Runnable.class);
                m.setAccessible(true);
                m.invoke(nucleus, timeUnit.toMillis(timeout), (Runnable)() -> timedOut(Timeout.INSTANCE));
                return this;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                // silent... sshhh....
                // should forward directly to ElasticScheduler
            }

            // old method: but we're not protected-privileged!
            //nuclei.delayed(millis, () -> timedOut(Timeout.INSTANCE));
        }

        ElasticScheduler.delayedCalls.schedule(new TimerTask() {
            @Override
            public void run() {
                timedOut(Timeout.INSTANCE);
            }
        }, timeUnit.toMillis(timeout));
        return this;
    }

    public Object getError() {
        return error;
    }

    public boolean isComplete() {
        return hadResult;
    }

    // debug
    public boolean _isHadResult() {
        return hadResult;
    }

    // debug
    public boolean _isHasFired() {
        return hasFired;
    }

    @Override
    public String toString() {
        if(isComplete())
            return "CompletableFuture{" +  "result=" + result + ", error=" + error + '}';
        else return "CompletableFuture{}";
    }

    /**
     * A yield failed in the execution of the future.
     */
    public static class ExecutionException extends RuntimeException {
        public ExecutionException(String message) {
            super(message);
        }
    }

    /**
     * A dummy class only used to signal a TimeoutException
     */
    public static final class Timeout {
        public static Timeout INSTANCE = new Timeout();
        private Timeout() {}
    }

    /**
     * A timeout occurred in the execution of the Future.
     */
    public static class TimeoutException extends RuntimeException {
        public TimeoutException() {
            super("Timeout");
        }
    }
}
