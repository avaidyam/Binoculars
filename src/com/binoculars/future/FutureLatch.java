package com.binoculars.future;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A synchronization aid that allows the signalling of a future once
 * a set of operations being performed in other threads completes.
 *
 * <p>A {@code FutureLatch} is initialized with a given <em>count</em>
 * and a given <em>future</em> to be signaled. A {@code FutureLatch}
 * is a versatile synchronization tool and can be used for a number of
 * purposes. Initialized with a count of one, it serves as an on/off latch.
 *
 * The future bound to the latch will only be signaled when the count
 * reaches zero (by calling {@link #countDown countDown}. The future
 * will also be signaled with the result and error provided by the
 * most recent call to {@link #countDown(Object, Object)} countDown}.
 *
 * The {@code FutureLatch} is most usually used for pure signalling, so
 * the result and error signaled will be null. To predefine the result
 * and/or error to signal, call {@link #preset(Object, Object)}.
 *
 */
public class FutureLatch<T> {

    protected Future<T> future;
    protected AtomicInteger count;

    // For presetting the future signal when the count reaches zero.
    protected T _result = null;
    protected Object _error = null;

    /**
     * Constructs a {@code FutureLatch} initialized with the given count and future.
     *
     * @param future the future to be signaled when the latch releases
     * @param count the number of times {@link #countDown} must be invoked
     * @throws IllegalArgumentException if {@code count} is negative or zero
     */
    public FutureLatch(Future<T> future, int count) {
        if(count <= 0)
            throw new IllegalArgumentException("count <= 0");

        this.future = future;
        this.count = new AtomicInteger(count);
    }

    /**
     * Constructs a {@code FutureLatch} initialized with the given future.
     *
     * @param future the future to be signaled when the count reaches zero
     */
    public FutureLatch(Future<T> future) {
        this(future, 1);
    }

    /**
     * Returns the future bound to this {@code FutureLatch}.
     *
     * @return the future to be signaled when the count reaches zero
     */
    public Future<T> future() {
        return future;
    }

    /**
     * Presets the result and error of the latch, so if the future is pure
     * signaled, using {@link #countDown}, it will be signaled with 
     * the result and error supplied, rather than null for both parameters.
     * 
     * @param result the result to be signaled to the future
     * @param error the error to be signaled to the future
     */
    public void preset(T result, Object error) {
        this._result = result;
        this._error = error;
    }

    /**
     * Increments the count of the latch, preventing the future being called
     * for another cycle.
     *
     * <p>If the current count is greater than zero then it is incremented.
     * If the current count equals zero then nothing happens, since the
     * future has already been called.
     */
    public void countUp() {
        if(count.get() > 0)
            count.incrementAndGet();
    }

    /**
     * Decrements the count of the latch, calling the future if it reaches zero.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the current count equals zero then nothing happens, since the
     * future has already been called.
     */
    public void countDown() {
        this.countDown(_result, _error);
    }

    /**
     * Decrements the count of the latch, calling the future if it reaches zero.
     * The future will be signaled with the latest result and error.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the current count equals zero then nothing happens, since the
     * future has already been called.
     *
     * @param result the result to be signaled to the future
     * @param error the error to be signaled to the future
     */
    @SuppressWarnings("unchecked")
    public void countDown(T result, Object error) {
        if (count.decrementAndGet() == 0)
            future.complete(result, error);
    }

    /**
     * Returns the current count.
     *
     * <p>This method is typically used for debugging and testing purposes.
     *
     * @return the current count
     */
    public long getCount() {
        return count.get();
    }

    /**
     * Returns a string identifying this latch, as well as its state.
     * The state, in brackets, includes the String {@code "count ="}
     * followed by the current count, and the String {@code "future ="}
     * followed by the future to be signaled by this latch.
     *
     * @return a string identifying this latch, as well as its state
     */
    public String toString() {
        return super.toString() + "[count = " + count.get() +
                ", future = " + future.toString() + "]";
    }
}
