package com.binoculars.nuclei.scheduler;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.Signal;

import java.util.concurrent.Callable;

/**
 *
 */
public class RemoteScheduler extends ElasticScheduler {

	// FIXME: dissimilar from source
    public RemoteScheduler() {
        super(1);
    }

    public RemoteScheduler(int defQSize) {
        super(1, defQSize);
    }

    @Override
    protected Dispatcher createDispatcherThread() {
        return new Dispatcher(this) {
            @Override
            public synchronized void start() {
                // fake thread, just don't start
            }
        };
    }

	@Override
	public void delayedCall(long millis, Runnable toRun) {
		throw new RuntimeException("cannot be used on a remote reference (no thread)");
	}

	@Override
	public <T> void runBlockingCall(Nucleus emitter, Callable<T> toCall, Signal<T> resultHandler) {
		throw new RuntimeException("cannot be used on a remote reference (no thread)");
	}

}
