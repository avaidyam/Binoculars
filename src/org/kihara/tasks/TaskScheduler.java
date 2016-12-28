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

package org.kihara.tasks;

import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.future.SignalWrapper;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.Log;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;

// TODO: can defer algorithm to TaskCoordinator interface
public class TaskScheduler extends Nucleus<TaskScheduler> {

    enum Signal {TASK_QUEUED, OUT_OF_WORK, TASK_STOLEN, SHUTDOWN}

    // submit task and return a future holding value

    /**
     * Arranges for (asynchronous) execution of the given task,
     * returning a <tt>Future</tt> that may be used to obtain results
     * upon completion.
     *
     * @param task the task
     * @return a Future that can be used to get the task's results.
     * @throws NullPointerException       if task is null
     * @throws RejectedExecutionException if the executor is
     *                                    not in a state that allows execution.
     */
    // TODO: single node single thread
    // FIXME: queue onto other nodes
    // FIXME: steal work from other nodes
    private static final long max_queued_tasks = 1_000_000_000_000L;
    private Deque<Task<?, ?>> _queuedTasks = new LinkedList<>();
    private HashMap<Long, Task.State> _registry = new HashMap<>();
    private HashMap<Long, Set<Long>> _dependencies = new HashMap<>();

    // 1. tasks submitted to queue sequentially
    // 2. tasks picked up automatically
    // 3.

    // submit task and return a future holding value
    public <O> Future<O> submit(Task<?, O> task) {
        CompletableFuture<O> future = new CompletableFuture<>();
        task.forEach(future::complete);

        if(_queuedTasks.size() > 0 && _queuedTasks.size() < max_queued_tasks)
            _queuedTasks.offer(task);
        else self().spore(task);

        Log.i(this.toString(), "Next task queued");

        return future.then((r, e) -> {
            self().spore(_queuedTasks.poll());
            Log.i(this.toString(), "Spooling for next task");
        });
    }

    // remove a task from the queue if it is queued.
    // if it is running, there is no hope for it now!
    public void removeTask(long taskTag) {
        for(Task<?, ?> t : _queuedTasks)
            if(t.getTag() == taskTag)
                _queuedTasks.remove(t);
    }

    // maintain a one to one map of states for tasks
    // if a taskId has no state, it is assumed complete
    // remove
    public void updateState(long taskId, Task.State newState) {
        _registry.put(taskId, newState);
    }

    // maintain a one to many map of dependencies for tasks
    public void updateDependency(long taskId, long dependentTaskId) {
        if(!_dependencies.containsKey(taskId))
            _dependencies.put(taskId, new HashSet<>());
        _dependencies.get(taskId).add(dependentTaskId);
    }

    public void _task(com.avaidyam.binoculars.future.Signal<?> cb) {
        Task<?, ?> task;
        if(cb instanceof SignalWrapper)
            task = (Task<?, ?>)((SignalWrapper)cb).getRealSignal();
        else task = (Task<?, ?>)cb;
        if(task == null || !(task instanceof Task))
            throw new RuntimeException("Cannot invoke a non-Task Callback!");

        // FIXME: hacky hack hack!
        task.remote(null);
    }

    // pop/peek the next possible task off
    public Future<Task<?, ?>> popTask() {
        return new CompletableFuture<>(_queuedTasks.poll());
    }

    public Future<Task<?, ?>> peekTask() {
        return new CompletableFuture<>(_queuedTasks.peek());
    }

    /*// cancel all pending tasks
    public List<Task<?,?>> cancelTasks() {
        LinkedList<Task<?, ?>> tasks = new LinkedList<>(_queuedTasks);
        _queuedTasks.clear();
        return tasks;
    }

    // get # tasks queued
    public long getQueuedCount() {
        return _queuedTasks.size();
    }

    // await termination (shutdown signal)
    public boolean awaitShutdown(long millis) {
        return false;
    }

    // shutdown after tasks processed,
    // return # tasks left
    public long shutdown() {
        return _queuedTasks.size();
    }

    // shutdown and eject tasks to a list
    public List<Task<?, ?>> shutdownNow() {
        shutdown();
        return cancelTasks();
    }

    // is idle?
    public boolean isIdle() {
        return _queuedTasks.size() == 0;
    }

    // is shut down?
    public boolean isShutdown() {
        return false;
    }//*/

    // cancel all pending tasks
    /*List<Task<?,?>> cancelTasks();

    // get # tasks queued
    long getQueuedCount();

    // await termination (shutdown signal)
    boolean awaitShutdown(long millis);

    // shutdown after tasks processed,
    // return # tasks left
    long shutdown();

    // shutdown and eject tasks to a list
    List<Task<?, ?>> shutdownNow();

    // is idle?
    boolean isIdle();

    // is shut down?
    boolean isShutdown();//*/

    // long getConcurrentWorkers();
}
