/*
Kontraktor-reactivestreams Copyright (c) Ruediger Moeller, All rights reserved.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3.0 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

See https://www.gnu.org/licenses/lgpl.txt
*/
package com.binoculars.stream;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.scheduler.BackOffStrategy;
import com.binoculars.future.Signal;
import org.nustaq.serialization.util.FSTUtil;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.reactivestreams.*;

/**
 * consuming endpoint. requests data from publisher immediately after
 * receiving onSubscribe callback
 *
 * @param <T>
 */
public class KxSubscriber<T> implements Subscriber<T>, Serializable, Iterator<T> {
    public static final String COMPLETE = "COMPLETE";
    public static BackOffStrategy strat = new BackOffStrategy(100, 2, 5); // backoff when acting as iterator/stream
    public static ThreadLocal<Subscription> subsToCancel = new ThreadLocal<>();
    protected long batchSize;
    protected Signal<T> cb;
    protected long credits;
    protected Subscription subs;
    protected boolean autoRequestOnSubs;
    protected ConcurrentLinkedQueue buffer;
    Object next;

    /**
     * iterator mode constructor, spawns a thread
     *
     * @param batchSize
     */
    public KxSubscriber(long batchSize) {
        this.batchSize = batchSize;
        this.autoRequestOnSubs = true;
        credits = 0;
        this.cb = (res, err) -> {
            if (buffer == null) {
                buffer = new ConcurrentLinkedQueue();
            }
            if (Signal.isResult(err)) {
                buffer.add(res);
            } else if (Signal.isError(err)) {
                buffer.add(err);
            } else if (Signal.isComplete(err)) {
                buffer.add(COMPLETE);
            }
        };
    }

    public KxSubscriber(long batchSize, Signal<T> cb) {
        this(batchSize, cb, true);
    }

    public KxSubscriber(long batchSize, Signal<T> cb, boolean autoRequestOnSubs) {
        this.batchSize = batchSize;
        this.cb = cb;
        this.autoRequestOnSubs = autoRequestOnSubs;
        credits = 0;
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (subs != null) {
            s.cancel();
            return;
        }

        subs = s;
        if (autoRequestOnSubs)
            s.request(batchSize);
        credits += batchSize;
        if (KxPublisherNucleus.CRED_DEBUG)
            System.out.println("credits:" + credits);
    }

    @Override
    public void onNext(T t) {
        if (t == null)
            throw null;
        credits--;
        if (credits < batchSize / KxReactiveStreams.REQU_NEXT_DIVISOR) {
            subs.request(batchSize);
            credits += batchSize;
            if (KxPublisherNucleus.CRED_DEBUG)
                System.out.println("credits:" + credits);
        }
        nextAction(t);
    }

    protected void nextAction(T t) {
        try {
            cb.stream(t);
        } catch (CancelException c) {
            subs.cancel();
        }
    }

    /////////////////////////////////////// iterator

    @Override
    public void onError(Throwable t) {
        if (t == null)
            throw null;
        cb.reject(t);
    }

    @Override
    public void onComplete() {
        cb.complete();
    }

    @Override
    public boolean hasNext() {
        subsToCancel.set(subs);
        int count = 0;
        while ((buffer == null || buffer.peek() == null)) {
            if (Nucleus.inside()) {
                count++;
                if (count < 1)
                    Nucleus.yield();
                else {
                    if (count < 5)
                        Nucleus.yield(1);
                    else
                        Nucleus.yield(5);
                }
            } else {
                strat.yield(count++);
            }
        }
        Object poll = buffer.poll();
        next = poll;
        return next != COMPLETE && next instanceof Throwable == false;
    }

    @Override
    public T next() {
        if (next == COMPLETE) {
            throw new RuntimeException("no further elements in iterator");
        }
        if (next instanceof Throwable) {
            subs.cancel();
            FSTUtil.<RuntimeException>rethrow((Throwable) next);
        }
        return (T) next;
    }
}
