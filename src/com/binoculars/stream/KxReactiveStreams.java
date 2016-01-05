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
import com.binoculars.nuclei.NucleusProxy;
import com.binoculars.nuclei.scheduler.Scheduler;
import com.binoculars.nuclei.scheduler.SimpleScheduler;
import com.binoculars.nuclei.annotation.CallerSideMethod;
import com.binoculars.nuclei.remoting.base.NucleusClientConnector;
import com.binoculars.nuclei.remoting.base.NucleusPublisher;
import com.binoculars.nuclei.remoting.base.ConnectableNucleus;
import com.binoculars.future.Signal;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
import com.binoculars.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.*;
import org.reactivestreams.*;

/**
 *
 * <p>
 * Factory class for reactive streams <=> Kontraktor interop.
 * <p>
 * Note you rarely need use this directly. To publish / start a stream, see EventSink (offer() like feed in point),
 * this also allows to publish it on network.
 * <p>
 * For connecting a remote stream, see ReaktiveStreams.connectXXX methods. Note that RxPublisher extends the original
 * Publisher interface.
 * <p>
 * for interop use asRxPublisher()
 */
public class KxReactiveStreams {

    // as for remoting the request(N) ack signals have network latency
    // request(N) is executed with large sizes to not let the sending side "dry"/stall.
    // in case of high latency connections (wifi, WAN) sizes below probably need to be raised
    public static final int MAX_BATCH_SIZE = 50_000;
    public static final int DEFQSIZE = 128_000;
    public static final int DEFBATCHSIZE = 50_000;


    public static int REQU_NEXT_DIVISOR = 1;

    protected static KxReactiveStreams instance = new KxReactiveStreams(true);
    protected int batchSize = 50_000;

    ////////// instance methods ////////////////////////////////////////////////
    SimpleScheduler scheduler;

    /**
     * each KxReactiveStreams instance has a dedicated thread which is used for all its processors.
     * Its recommended to use the singleton for normal use cases. Only start using multiple KxReactiveStreams
     * instances if you need to scale out for throughput.
     * <p>
     * Note that frequently increase of cache misses can lead to an effective degrade of throughput, so you
     * need to choose wisely which part of your processing pipeling shares a thread.
     * <p>
     * Because of Kontraktor's explicit scheduling model, load balancing cannot be done automatically currently.
     */
    public KxReactiveStreams() {
        this(false);
    }

    public KxReactiveStreams(boolean keepSchedulerAlive) {
        this(DEFBATCHSIZE, DEFQSIZE, keepSchedulerAlive);
    }

    public KxReactiveStreams(int batchSize, int queueSize, boolean keepSchedulerAlive) {
        if (batchSize * 2 > queueSize)
            throw new RuntimeException("queuesize must be >= 2 * batchSize");
        scheduler = new SimpleScheduler(queueSize, keepSchedulerAlive);
        this.batchSize = batchSize;
    }

    public static KxReactiveStreams get() {
        return instance;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * tell the execution thread to stop as soon no actors are scheduled on it.
     */
    public void terminateScheduler() {
        scheduler.setKeepAlive(false);
    }

    /**
     * interop, obtain a RxPublisher from an arbitrary rxstreams publisher.
     * <p>
     * RxPublisher inherits rxtreams.Publisher and adds some sugar like map, network publish etc. via default methods
     *
     * @param p
     * @param <T>
     * @return
     */
    public <T> KxPublisher<T> asKxPublisher(Publisher<T> p) {
        if (p instanceof KxPublisher)
            return (KxPublisher<T>) p;
        return new KxPublisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> s) {
                p.subscribe(s);
            }

            @Override
            @CallerSideMethod
            public KxReactiveStreams getKxStreamsInstance() {
                return KxReactiveStreams.this;
            }
        };
    }

    /**
     * consuming endpoint. requests data from publisher immediately after
     * receiving onSubscribe callback. Maps from reactive streams Subscriber to Kontraktor Callback
     * <p>
     * all events are routed to the callback
     * e.g.
     * <pre>
     * subscriber( (event, err) -> {
     * if (Nuclei.isComplete(err)) {
     * System.out.println("complete");
     * } else if (Nuclei.isError(err)) {
     * System.out.println("ERROR");
     * } else {
     * // process event
     * }
     * }
     * </pre>
     *
     * @param <T>
     */
    public <T> Subscriber<T> subscriber(Signal<T> cb) {
        return subscriber(batchSize, cb);
    }

    /**
     * consuming endpoint. requests data from publisher immediately after
     * receiving onSubscribe callback. Maps from reactive streams Subscriber to Kontraktor Callback
     * <p>
     * all events are routed to the callback
     * e.g.
     * <pre>
     * subscriber( (event, err) -> {
     * if (Nuclei.isComplete(err)) {
     * System.out.println("complete");
     * } else if (Nuclei.isError(err)) {
     * System.out.println("ERROR");
     * } else {
     * // process event
     * }
     * }
     * </pre>
     *
     * @param <T>
     */
    public <T> Subscriber<T> subscriber(int batchSize, Signal<T> cb) {
        if (batchSize > MAX_BATCH_SIZE) {
            throw new RuntimeException("batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
        return new KxSubscriber<>(batchSize, cb);
    }

    public <T> KxPublisher<T> connect(Class<T> eventType, ConnectableNucleus connectable) {
        return (KxPublisher<T>) connect(eventType, connectable, null).await();
    }

    public <T> KxPublisher<T> produce(Stream<T> stream) {
        return produce(batchSize, stream.iterator());
    }

    public <T> KxPublisher<T> produce(Collection<T> collection) {
        return produce(batchSize, collection.iterator());
    }

    public KxPublisher<Integer> produce(IntStream stream) {
        return produce(batchSize, stream.mapToObj(i -> i).iterator());
    }

    public KxPublisher<Long> produce(LongStream stream) {
        return produce(batchSize, stream.mapToObj(i -> i).iterator());
    }

    public KxPublisher<Double> produce(DoubleStream stream) {
        return produce(batchSize, stream.mapToObj(i -> i).iterator());
    }

    public <T> KxPublisher<T> produce(int batchSize, Stream<T> stream) {
        return produce(batchSize, stream.iterator());
    }

    public <T> KxPublisher<T> produce(Iterator<T> iter) {
        return produce(batchSize, iter);
    }

    public <T> KxPublisher<T> produce(int batchSize, Iterator<T> iter) {
        if (batchSize > KxReactiveStreams.MAX_BATCH_SIZE) {
            throw new RuntimeException("batch size exceeds max of " + KxReactiveStreams.MAX_BATCH_SIZE);
        }
        KxPublisherNucleus pub = Nucleus.of(KxPublisherNucleus.class, scheduler);

        // actually init should provide a promise as return value to ensure streams instance is initialized.
        // do sync shortcut instead here
        pub._streams = this;
        ((KxPublisherNucleus) pub.getNucleus())._streams = this;

        pub.setBatchSize(batchSize);
        pub.setThrowExWhenBlocked(true);
        pub.initFromIterator(iter);
        return pub;
    }

    /**
     * warning: blocks the calling thread. Need to execute in a separate thread if called
     * from within a callback
     */
    public <T> Stream<T> stream(Publisher<T> pub) {
        return stream(pub, batchSize);
    }

    /**
     * warning: blocks the calling thread. Need to execute in a separate thread if called
     * from within a callback
     */
    public <T> Stream<T> stream(Publisher pub, int batchSize) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(pub, batchSize), Spliterator.IMMUTABLE | Spliterator.NONNULL), false);
    }

    /**
     * warning: blocks the calling thread. Need to execute in a separate thread if called
     * from within a callback
     */
    public <T> Iterator<T> iterator(Publisher<T> pub) {
        return iterator(pub, batchSize);
    }

    /**
     * warning: blocks the calling thread. Need to execute in a separate thread if called
     * from within a callback
     */
    public <T> Iterator<T> iterator(Publisher<T> pub, int batchSize) {
        if (batchSize > KxReactiveStreams.MAX_BATCH_SIZE) {
            throw new RuntimeException("batch size exceeds max of " + KxReactiveStreams.MAX_BATCH_SIZE);
        }
        KxSubscriber<T> subs = new KxSubscriber<T>(batchSize);
        pub.subscribe(subs);

        return subs;
    }

    /**
     * @param eventType
     * @param connectable
     * @param disconHandler - can be null
     * @param <T>
     * @return
     */
    public <T> Future<KxPublisher<T>> connect(Class<T> eventType, ConnectableNucleus connectable, Signal<NucleusClientConnector> disconHandler) {
        Signal<NucleusClientConnector> discon = (acc, err) -> {
            Log.i(this.toString(), "Client disconnected");
            acc.closeClient();
            if (disconHandler != null) {
                disconHandler.complete(acc, err);
            }
        };
        Future<KxPublisher<T>> connect = connectable.nucleusClass(KxPublisherNucleus.class).inboundQueueSize(scheduler.getDefaultQSize()).connect(discon, remoteref -> {
            if (((KxPublisherNucleus) remoteref)._callerSideSubscribers != null) {
                ((KxPublisherNucleus) remoteref)._callerSideSubscribers.forEach(subs -> {
                    ((Subscriber) subs).onError(new IOException("connection lost"));
                });
                ((KxPublisherNucleus) remoteref)._callerSideSubscribers = null;
            }
        });
        CompletableFuture<KxPublisher<T>> res = new CompletableFuture<>();
        connect.then((publisher, err) -> {
            if (publisher != null) {
                ((KxPublisherNucleus) publisher)._streams = this;
                res.resolve(publisher);
            } else {
                res.reject(err);
            }
        });
        return res;
    }

    /**
     * exposes a publisher on the network via kontraktor's generic remoting. Usually not called directly (see EventSink+RxPublisher)
     *
     * @param source
     * @param networRxPublisher - the appropriate network publisher (TCP,TCPNIO,WS)
     * @param disconCB          - called once a client disconnects/stops. can be null
     * @param <OUT>
     * @return
     */
    public <OUT> Future serve(Publisher<OUT> source, NucleusPublisher networRxPublisher, boolean closeConnectionOnCompleteOrError, Consumer<Nucleus> disconCB) {
        if (networRxPublisher.getClass().getSimpleName().equals("HttpPublisher")) {
            throw new RuntimeException("Http long poll cannot be supported. Use WebSockets instead.");
        }
        if (source instanceof KxPublisherNucleus == false || source instanceof NucleusProxy == false) {
            Processor<OUT, OUT> proc = newAsyncProcessor(a -> a); // we need a queue before going to network
            source.subscribe(proc);
            source = proc;
        }
        ((KxPublisherNucleus) source).setCloseOnComplete(closeConnectionOnCompleteOrError);
        return networRxPublisher.facade((Nucleus) source).publish(disconCB);
    }

    /**
     * create async processor. Usually not called directly (see EventSink+RxPublisher)
     *
     * @param processingFunction
     * @param <IN>
     * @param <OUT>
     * @return
     */
    public <IN, OUT> Processor<IN, OUT> newAsyncProcessor(Function<IN, OUT> processingFunction) {
        return newAsyncProcessor(processingFunction, scheduler, batchSize);
    }

    /**
     * create async processor. Usually not called directly (see EventSink+RxPublisher).
     * The resulting processor is Publisher allowing for multiple subscribers, having a
     * slowest-subscriber-dominates policy.
     *
     * @param processingFunction
     * @param batchSize
     * @param <IN>
     * @param <OUT>
     * @return
     */
    public <IN, OUT> Processor<IN, OUT> newAsyncProcessor(Function<IN, OUT> processingFunction, int batchSize) {
        return newAsyncProcessor(processingFunction, scheduler, batchSize);
    }

    /**
     * create async processor. Usually not called directly (see EventSink+RxPublisher)
     * The resulting processor is Publisher allowing for multiple subscribers, having a
     * slowest-subscriber-dominates policy.
     *
     * @param processingFunction
     * @param sched
     * @param batchSize
     * @param <IN>
     * @param <OUT>
     * @return
     */
    public <IN, OUT> Processor<IN, OUT> newAsyncProcessor(Function<IN, OUT> processingFunction, Scheduler sched, int batchSize) {
        if (batchSize > KxReactiveStreams.MAX_BATCH_SIZE) {
            throw new RuntimeException("batch size exceeds max of " + KxReactiveStreams.MAX_BATCH_SIZE);
        }
        KxPublisherNucleus pub = Nucleus.of(KxPublisherNucleus.class, sched);
        // actually init should provide a promise as return value to ensure streams instance is initialized.
        // do sync shortcut instead here
        pub._streams = this;
        ((KxPublisherNucleus) pub.getNucleus())._streams = this;
        pub.setBatchSize(batchSize);
        pub.setThrowExWhenBlocked(true);
        pub.init(processingFunction);
        return pub;
    }

    /**
     * create sync processor. Usually not called directly (see EventSink+RxPublisher)
     * does not support dropping elements by returning null from processor, does not support
     * multiple subscribers.
     * <p>
     * Prefer map() unless you are aware of the implications.
     *
     * @param processingFunction
     * @param <IN>
     * @param <OUT>
     * @return
     */
    public <IN, OUT> Processor<IN, OUT> newSyncProcessor(Function<IN, OUT> processingFunction) {
        SyncProcessor<IN, OUT> inoutSyncProcessor = new SyncProcessor<>(batchSize, processingFunction, this);
        return inoutSyncProcessor;
    }

    public <T, OUT> Processor<T, OUT> newLossyProcessor(Function<T, OUT> processingFunction, int batchSize) {
        if (batchSize > KxReactiveStreams.MAX_BATCH_SIZE) {
            throw new RuntimeException("batch size exceeds max of " + KxReactiveStreams.MAX_BATCH_SIZE);
        }
        KxPublisherNucleus pub = Nucleus.of(KxPublisherNucleus.class, scheduler);
        // actually init should provide a promise as return value to ensure streams instance is initialized.
        // do sync shortcut instead here
        pub._streams = this;
        ((KxPublisherNucleus) pub.getNucleus())._streams = this;
        pub.setBatchSize(batchSize);
        pub.setThrowExWhenBlocked(true);
        pub.setLossy(true);
        pub.init(processingFunction);
        return pub;
    }

}
