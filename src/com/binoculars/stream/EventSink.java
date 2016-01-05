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
import com.binoculars.nuclei.annotation.CallerSideMethod;

import java.util.concurrent.atomic.AtomicLong;
import org.reactivestreams.*;

/**
 *
 * <p>
 * "Inverts" stream logic from "pull" to "push" (offer-style).
 * Note this class is not remoteable. RxPublisher.serve automatically creates
 * an async identity processor before publishing.
 * <p>
 * For advanced use cases (see example RxStreamServer) this needs to be done explicitely
 */
public class EventSink<T> implements KxPublisher<T> {

    protected AtomicLong credits = new AtomicLong(0);
    protected Nucleus nucleusSubs;
    protected volatile Subscriber subs;
    protected volatile boolean canceled = false;
    protected KxReactiveStreams streams;

    public EventSink() {
        this(KxReactiveStreams.get());
    }

    public EventSink(KxReactiveStreams streams) {
        this.streams = streams;
    }

    public boolean offer(T event) {
        if (event == null)
            throw new RuntimeException("event cannot be null");
        if (canceled)
            throw CancelException.Instance;
        if (((nucleusSubs != null && !nucleusSubs.isMailboxPressured()) || nucleusSubs == null) &&
                credits.get() > 0 && subs != null) {
            subs.onNext(event);
            credits.decrementAndGet();
            return true;
        }
        return false;
    }

    public void complete() {
        subs.onComplete();
    }

    public void error(Throwable th) {
        subs.onError(th);
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if (subs != null) {
            throw new RuntimeException("only one subscription supported");
        }
        if (subscriber == null) {
            throw null;
        }
        subs = subscriber;
        if (subs instanceof Nucleus) {
            nucleusSubs = (Nucleus) subs;
        }
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long l) {
                if (l <= 0) {
                    subs.onError(new IllegalArgumentException("spec rule 3.9: request > 0 elements"));
                }
                credits.addAndGet(l);
            }

            @Override
            public void cancel() {
                subs = null;
                canceled = true;
            }
        });
    }

    @Override
    @CallerSideMethod
    public KxReactiveStreams getKxStreamsInstance() {
        if (streams == null)
            System.out.println("POK");
        return streams;
    }
}
