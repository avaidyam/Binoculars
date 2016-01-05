package com.binoculars.nuclei;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class NucleusExecutorService implements ExecutorService {

	Nucleus nucleus;

	public NucleusExecutorService(Nucleus nucleus) {
		this.nucleus = nucleus;
	}

	@Override
	public void shutdown() {
	}

	@Override
	public List<Runnable> shutdownNow() {
		return null;
	}

	@Override
	public boolean isShutdown() {
		return false;
	}

	@Override
	public boolean isTerminated() {
		return false;
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return false;
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		throw new RuntimeException("not supported");
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		throw new RuntimeException("not supported");
	}

	@Override
	public Future<?> submit(Runnable task) {
		throw new RuntimeException("not supported");
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		throw new RuntimeException("not supported");
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		throw new RuntimeException("not supported");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		throw new RuntimeException("not supported");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		throw new RuntimeException("not supported");
	}

	@Override
	public void execute(Runnable command) {
		nucleus.execute(command);
	}
}
