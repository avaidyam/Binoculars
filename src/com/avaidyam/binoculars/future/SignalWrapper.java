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

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.RemoteInvocation;
import com.avaidyam.binoculars.util.Log;
import com.avaidyam.binoculars.remoting.base.RemotedCallback;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ..
 */
public class SignalWrapper<T> implements Future<T>, Serializable {

    static Method receiveRes;
    static {
        try {
            receiveRes = Signal.class.getMethod("complete", Object.class, Throwable.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    final Nucleus targetNucleus;
    final Signal<T> realSignal;

    public SignalWrapper(Nucleus targetQ, Signal<T> realFuture) {
        this.realSignal = realFuture;
        this.targetNucleus = targetQ;
    }

    public Signal<T> getRealSignal() {
        return realSignal;
    }

    @Override
    public void complete(T result, Throwable error) {
        if (realSignal == null)
            return;
        if (targetNucleus == null) {
            // call came from outside the nuclei world => use current thread => blocking the callback blocks nuclei, dont't !
            try {
                receiveRes.invoke(realSignal, result, error);
            } catch (Exception e) {
                Log.w(this.toString(), "", e);
            }
        } else {
            RemoteInvocation ce = new RemoteInvocation<>(realSignal, receiveRes, new Object[]{result, error},
                                           Nucleus.sender.get(), targetNucleus, true);
            targetNucleus.__scheduler.put2QueuePolling(targetNucleus.__cbQueue, true, ce, targetNucleus);
        }
    }

    @Override
    public Future<T> then(Runnable result) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).then(result);
    }

    @Override
    public Future<T> then(Supplier<Future<T>> result) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).then(result);
    }

    @Override
    public Future<T> then(Signal<T> result) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).then(result);
    }

    @Override
    public Future<T> onResult(Consumer<T> resultHandler) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).onResult(resultHandler);
    }

    @Override
    public Future<T> onError(Consumer<Throwable> errorHandler) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).onError(errorHandler);
    }

    @Override
    public Future<T> onTimeout(Consumer timeoutHandler) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).onTimeout(timeoutHandler);
    }

    @Override
    public <OUT> Future<OUT> then(Function<T, Future<OUT>> function) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).then(function);
    }

    @Override
    public <OUT> Future<OUT> then(Consumer<T> function) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).then(function);
    }

    @Override
    public <OUT> Future<OUT> catchError(Function<Throwable, Future<OUT>> function) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).catchError(function);
    }

    @Override
    public <OUT> Future<OUT> catchError(Consumer<Throwable> function) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).catchError(function);
    }

    @Override
    public T get() {
        if (!(realSignal instanceof Future))
            return null;
        else return ((Future<T>) realSignal).get();
    }

    @Override
    public T await(long timeout, TimeUnit timeUnit) {
        if (!(realSignal instanceof Future))
            return null;
        else return ((Future<T>) realSignal).await(timeout, timeUnit);
    }

    @Override
    public Future<T> awaitFuture(long timeout, TimeUnit timeUnit) {
        if (!(realSignal instanceof Future))
            return null;
        else return ((Future<T>) realSignal).awaitFuture(timeout, timeUnit);
    }

    @Override
    public Throwable getError() {
        if (!(realSignal instanceof Future))
            return null;
        else return ((Future<T>) realSignal).getError();
    }

    @Override
    public Future timeoutIn(long timeout, TimeUnit timeUnit) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("currently supported for futures only");
        ((Future) realSignal).timeoutIn(timeout, timeUnit);
        return this;
    }

    @Override
    public boolean isComplete() {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("currently supported for futures only");
        else return ((Future) realSignal).isComplete();
    }

    public boolean isRemote() {
        return realSignal instanceof RemotedCallback;
    }

    /**
     * @return if the corresponding remote connection is closed if any
     */
    public boolean isTerminated() {
        if ( isRemote() ) {
            return ((RemotedCallback) realSignal).isTerminated();
        }
        return false;
    }
}
