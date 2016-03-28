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

package com.avaidyam.binoculars.future;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 */
public interface Future<T> extends Signal<T> {

    /**
     * called when any result of a future becomes available
     * Can be used in case a sender is not interested in the actual result
     * but when a remote method has finished processing.
     *
     * @param result
     * @return
     */
    Future<T> then(Runnable result);

    /**
     * called when any result of a future becomes available
     *
     * @param result
     * @return
     */
    Future<T> then(Signal<T> result);

    Future<T> then(Supplier<Future<T>> result);

    <OUT> Future<OUT> then(final Function<T, Future<OUT>> function);

    <OUT> Future<OUT> then(final Consumer<T> function);

    <OUT> Future<OUT> catchError(final Function<Object, Future<OUT>> function);

    <OUT> Future<OUT> catchError(final Consumer<Object> function);

    /**
     * called when a valid result of a future becomes available.
     * forwards to (new) "then" variant.
     *
     * @return
     */
    default Future<T> onResult(Consumer<T> resultHandler) {
        return then(resultHandler);
    }

    /**
     * called when an error is set as the result
     * forwards to (new) "catchError" variant.
     *
     * @return
     */
    default Future<T> onError(Consumer<Object> errorHandler) {
        return catchError(errorHandler);
    }

    /**
     * called when the async call times out. see 'timeOutIn'
     *
     * @param timeoutHandler
     * @return
     */
    Future<T> onTimeout(Consumer<Object> timeoutHandler);

    /**
     * Warning: this is different to JDK's BLOCKING future
     *
     * @return result if avaiable.
     */
    T get();

    /**
     * schedule other events/messages until future is resolved/settled (Nonblocking delay).
     * <p>
     * In case this is called from a non-nuclei thread, the current thread is blocked
     * until the result is avaiable.
     * <p>
     * If the future is rejected (resolves to an error) an exception is raised.
     *
     * @return the futures result or throw exception in case of error
     */
    default T await() {
        return await(0, TimeUnit.NANOSECONDS);
    }

    T await(long timeout, TimeUnit timeUnit);

    /**
     * schedule other events/messages until future is resolved/settled (Nonblocking delay).
     * <p>
     * In case this is called from a non-nuclei thread, the current thread is blocked
     * until the result is avaiable.
     *
     * @return the settled future. No Exception is thrown, but the exception can be obtained by Future.getError()
     */
    default Future<T> awaitFuture() {
        return awaitFuture(0, TimeUnit.NANOSECONDS);
    }

    Future<T> awaitFuture(long timeout, TimeUnit timeUnit);

    /**
     * @return error if avaiable
     */
    Object getError();

    /**
     * tell the future to call the onTimeout callback in N milliseconds if future is not settled until then
     *
     * @param timeout
     * @param timeUnit
     * @return this for chaining
     */
    Future timeoutIn(long timeout, TimeUnit timeUnit);

    boolean isComplete();

}
