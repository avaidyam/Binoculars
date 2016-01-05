package com.binoculars.future;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.CallEntry;
import com.binoculars.nuclei.remoting.base.RemotedCallback;
import com.binoculars.util.Log;

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
            receiveRes = Signal.class.getMethod("complete", Object.class, Object.class);
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
    public void complete(T result, Object error) {
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
            CallEntry ce = new CallEntry<>(realSignal, receiveRes, new Object[]{result, error},
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
    public Future<T> onError(Consumer<Object> errorHandler) {
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
    public <OUT> Future<OUT> catchError(Function<Object, Future<OUT>> function) {
        if (!(realSignal instanceof Future))
            throw new RuntimeException("Cannot invoke Future method on non-Future CallbackWrapper.");
        else return ((Future<T>) realSignal).catchError(function);
    }

    @Override
    public <OUT> Future<OUT> catchError(Consumer<Object> function) {
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
    public Object getError() {
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
