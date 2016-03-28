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
import com.avaidyam.binoculars.future.Spore;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.scheduler.ElasticScheduler;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 *  - A list of partitions
 *  - A function for computing each split
 *  - Optionally, a Partitioner for key-value RDDs (e.g. to say that the RDD is hash-partitioned)
 *  - Optionally, a list of preferred locations to compute each split on (e.g. block locations for
 *    an HDFS file)
 *
 *    FIXME: needs to support predefined partitions!
 *    FIXME: allow reduce style joins
 *    FIXME: Add CountedCompleter-style TaskLatch
 *
 * An object with an operational state, plus asynchronous {@link #start()} and
 * {@link #stop()} lifecycle methods to transition between states. Example services include
 * webservers, RPC servers and timers.
 *
 * <p>The normal lifecycle of a service is:
 * <ul>
 *   <li>{@linkplain State#STARTING NEW} -&gt;
 *   <li>{@linkplain State#STARTING STARTING} -&gt;
 *   <li>{@linkplain State#RUNNING RUNNING} -&gt;
 *   <li>{@linkplain State#STOPPING STOPPING} -&gt;
 *   <li>{@linkplain State#COMPLETED COMPLETED}
 * </ul>
 *
 * <p>There are deviations from this if there are failures or if {@link Task#stop} is called
 * before the {@link Task} reaches the {@linkplain State#RUNNING RUNNING} state. The set of legal
 * transitions form a <a href="http://en.wikipedia.org/wiki/Directed_acyclic_graph">DAG</a>,
 * therefore every method of the listener will be called at most once. N.B. The {@link State#FAILED}
 * and {@link State#COMPLETED} states are terminal states, once a service enters either of these
 * states it cannot ever leave them.
 */
public abstract class Task<I, O> extends Spore<I, O> implements Serializable {

    /**
     * The lifecycle states of a Task. The ordering of the {@link State} enum is such that
     * if there is a transition from {@code A -> B}, then {@code A.compareTo(B) < 0}. The converse,
     * however, is not true, since there is guarantee for a valid state transition {@code B -> A}.
     */
    public enum State {

        /**
         * The Task has not yet been run, and is using minimal resources.
         */
        INACTIVE,

        /**
         * The Task has been queued by a TaskScheduler, location notwithstanding.
         */
        QUEUED,

        /**
         * The Task is operational and is currently undergoing computation.
         */
        RUNNING,

        /**
         * The Task has completed and yielded a computational value and optionally an error.
         */
        COMPLETED,

        /**
         * The Task was cancelled and has been either completed partially or dequeued.
         */
        CANCELLED,

        /**
         * The Task failed and yielded no computational value, and an error indicating failure.
         */
        FAILED
    }

    public static class Pair<A,B> {
        public final A first;
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public static <A,B> Comparator<Pair<A,B>> compareOnSecond(final Comparator<B> comparator) {
            return ((x, y) -> comparator.compare(x.second, x.second));
        }

        @Override
        public String toString() {
            return "Pair{" + "first=" + first + ", second=" + second + '}';
        }
    }

    /**
     * Adapts a {@link BiFunction} to a Task that can be acted on, and
     * returns its results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param biFunction the function to adapt
     * @param <A> the type of the first argument
     * @param <B> the type of the second argument
     * @param <O> the return type of the task
     * @return the task
     */
    public static <A, B, O> Task<Pair<A, B>, O> adapt(final BiFunction<A, B, O> biFunction) {
        return new Task<Pair<A, B>, O>() {
            @Override
            public O exec(Pair<A, B> input) {
                try {
                    return biFunction.apply(input.first, input.second);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link BiConsumer} to a Task that can be acted on, and
     * returns no results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param biConsumer the function to adapt
     * @param <A> the type of the first argument
     * @param <B> the type of the second argument
     * @return the task
     */
    public static <A, B> Task<Pair<A, B>, Void> adapt(final BiConsumer<A, B> biConsumer) {
        return new Task<Pair<A, B>, Void>() {
            @Override
            public Void exec(Pair<A, B> input) {
                try {
                    biConsumer.accept(input.first, input.second);
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }


    /**
     * Adapts a {@link BiConsumer} to a Task that can be acted on, and
     * returns the passed results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param biConsumer the function to adapt
     * @param result the result of the adapted task
     * @param <A> the type of the first argument
     * @param <B> the type of the second argument
     * @param <O> the return type of the task
     * @return the task
     */
    public static <A, B, O> Task<Pair<A, B>, O> adapt(final BiConsumer<A, B> biConsumer, final O result) {
        return new Task<Pair<A, B>, O>() {
            @Override
            public O exec(Pair<A, B> input) {
                try {
                    biConsumer.accept(input.first, input.second);
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Function} to a Task that can be acted on, and
     * returns its results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param function the function to adapt
     * @param <I> the type of the argument
     * @param <O> the return type of the task
     * @return the task
     */
    public static <I, O> Task<I, O> adapt(final Function<I, O> function) {
        return new Task<I, O>() {
            @Override
            public O exec(I input) {
                try {
                    return function.apply(input);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Supplier} to a Task that can be acted on, and
     * returns its results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param supplier the function to adapt
     * @param <O> the return type of the task
     * @return the task
     */
    public static <O> Task<Void, O> adapt(final Supplier<O> supplier) {
        return new Task<Void, O>() {
            @Override
            public O exec(Void input) {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Consumer} to a Task that can be acted on, and
     * returns no results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param consumer the function to adapt
     * @param <I> the type of the first argument
     * @return the task
     */
    public static <I> Task<I, Void> adapt(final Consumer<I> consumer) {
        return new Task<I, Void>() {
            @Override
            public Void exec(I input) {
                try {
                    consumer.accept(input);
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Runnable} to a Task that can be acted on, and
     * returns its results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param runnable the runnable to adapt
     * @return the task
     */
    public static Task<Void, Void> adapt(final Runnable runnable) {
        return new Task<Void, Void>() {
            @Override
            public Void exec(Void input) {
                try {
                    runnable.run();
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Callable} to a Task that can be acted on, and
     * returns its results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param callable the callable to adapt
     * @param <O> the return type of the task
     * @return the task
     */
    public static <O> Task<Void, O> adapt(final Callable<O> callable) {
        return new Task<Void, O>() {
            @Override
            public O exec(Void input) {
                try {
                    return callable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Consumer} to a Task that can be acted on, and
     * returns the passed results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param consumer the function to adapt
     * @param result the result of the adapted task
     * @param <I> the type of the argument
     * @param <O> the return type of the task
     * @return the task
     */
    public static <I, O> Task<I, O> adapt(final Consumer<I> consumer, final O result) {
        return new Task<I, O>() {
            @Override
            public O exec(I input) {
                try {
                    consumer.accept(input);
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Adapts a {@link Runnable} to a Task that can be acted on, and
     * returns the passed results on join(), translating any checked exceptions
     * into a RuntimeException.
     *
     * Note: the function cannot directly access the Task enclosing it.
     *
     * @param runnable the runnable to adapt
     * @param result the result of the adapted task
     * @param <O> the return type of the task
     * @return the task
     */
    public static <O> Task<Void, O> adapt(final Runnable runnable, final O result) {
        return new Task<Void, O>() {
            @Override
            public O exec(Void input) {
                try {
                    runnable.run();
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Returns the tags of a group of Tasks.
     *
     * @param tasks the tasks to retrieve the tags of
     * @return the tags of the tasks passed
     */
    public static long[] tagsOf(Task<?, ?>... tasks) {
        return tagsOf(Arrays.asList(tasks));
    }

    /**
     * Returns the tags of a group of Tasks.
     *
     * @param tasks the tasks to retrieve the tags of
     * @return the tags of the tasks passed
     */
    public static long[] tagsOf(List<Task<?, ?>> tasks) {
        return tasks.stream().mapToLong(Task::getTag).toArray();
    }

    // task id + name
    private String name = "";
    private final long tag = UUID.randomUUID().getLeastSignificantBits();

    // state + dependencies
    private State state = State.INACTIVE;
    private Set<Long> dependencies = new LinkedHashSet<>();

    // scheduler reference
    private transient TaskScheduler scheduler;

    // get rid of these?
    private O result;
    private Object error;

    /**
     * Allows the Task to have a friendly name identifiable to the user.
     *
     * @param name the new friendly name of the Task
     * @return the task itself
     */
    public Task<I, O> setName(String name) {
        this.name = name != null ? name : "";
        return this;
    }

    /**
     * Returns the friendly name of the Task, which is user-settable.
     *
     * @return the task's friendly name
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Returns the internal tag used by the TaskScheduler system.
     *
     * @return the task's internal tag
     */
    public final long getTag() {
        return this.tag;
    }

    /**
     * Utility method to chain Task functions and still process the name and
     * tag. Passes the Task's friendly name, which is user-settable to the Consumer.
     *
     * @param consumer the consumer to process the friendly name
     * @return the task itself
     */
    public final Task<I, O> getName(Consumer<String> consumer) {
        if (consumer != null)
            consumer.accept(this.name);
        return this;
    }

    /**
     * Utility method to chain Task functions and still process the name and
     * tag. Passes the Task's internal tag to the Consumer.
     *
     * @param consumer the consumer to process the internal tag
     * @return the task itself
     */
    public final Task<I, O> getTag(Consumer<Long> consumer) {
        if (consumer != null)
            consumer.accept(this.tag);
        return this;
    }

    /**
     * Applies a dependency on the Task with the given tag. This tag must be registered
     * and mapped to a Task with the local TaskScheduler, otherwise, the dependency
     * cannot be established. A Task is said to be dependent on another Task(s), if
     * the completion status or resultant value of that Task is crucial to the
     * input or execution of this Task. Cyclic dependencies are illegal and cannot be
     * made, as the TaskScheduler will block the Task from doing so. By default,
     * if the given tag has no match in the TaskScheduler's registry, it will be
     * assumed that the prerequisite Task has completed normally without exception.
     *
     * @param tag the internal tag of the Task to become dependent on
     * @return the task itself
     */
    public final Task<I, O> applyDependency(long tag) {
        this.dependencies.add(tag);
        this.scheduler.updateDependency(this.getTag(), tag);
        return this;
    }

    /**
     * Applies a dependency on the Task with the given tags. These tags must be registered
     * and mapped to Tasks with the local TaskScheduler, otherwise, the dependencies
     * cannot be established. A Task is said to be dependent on another Task(s), if
     * the completion status or resultant value of that Task is crucial to the
     * input or execution of this Task. Cyclic dependencies are illegal and cannot be
     * made, as the TaskScheduler will block the Task from doing so. By default,
     * if the given tag has no match in the TaskScheduler's registry, it will be
     * assumed that the prerequisite Task has completed normally without exception.
     *
     * @param tags the internal tags of the Tasks to become dependent on
     * @return the task itself
     */
    public final Task<I, O> applyDependencies(long... tags) {
        for (long tag : tags)
            this.applyDependency(tag);
        return this;
    }

    /**
     * Implement this method to provide the facility to execute the Task.
     * Usually this will be done via an anonymous subclass or using
     * {@link Task#adapt} to transform a function to a Task. Any exception
     * thrown will be translated into a RuntimeException and forwarded
     * as the error of the Future.
     *
     * @param input the input required for the computation done by this Task
     * @return the output value for the computation done by this Task
     * @throws Exception the exception optionally to be caught and forwarded
     */
    protected abstract O exec(I input) throws Exception;

    /**
     * .
     *
     * @return the task itself
     */
    public final Task<I, O> fork() {
        // FIXME: Does nothing...
        throw new UnsupportedOperationException();
    }

    /**
     * .
     *
     * @return the task itself
     */
    public final void join() {
        // FIXME: Does nothing...
        throw new UnsupportedOperationException();
    }

    /**
     * Expires the Task in a given frame of time, causing it to complete with
     * a TimeoutException. The Task will have reached State.FAILED upon timeout.
     * Note that if this Task times out, it may also trigger a cascading
     * failure of all Tasks with this Task as a dependency.
     *
     * @param timeout the maximum time to wait
     * @param timeUnit the unit of the timeout argument
     * @return the task itself
     */
    public final Task<I, O> timeout(long timeout, TimeUnit timeUnit) {
        final Nucleus nucleus = Nucleus.sender.get();
        final Runnable timeoutHandler = () -> {
            if(this.state() == State.RUNNING | this.state() == State.QUEUED || this.state() == State.INACTIVE)
                this.streamError(new CompletableFuture.TimeoutException());
        };

        if (nucleus != null) {
            try {
                Method m = nucleus.getClass().getDeclaredMethod("delayed", Long.TYPE, Runnable.class);
                m.setAccessible(true);
                m.invoke(nucleus, timeUnit.toMillis(timeout), timeoutHandler);
                return this;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                // forward to timer method.
            }
        }

        ElasticScheduler.delayedCalls.schedule(new TimerTask() {
            public void run() {
                timeoutHandler.run();
            }
        }, timeUnit.toMillis(timeout));
        return this;
    }

    /**
     * Cancel a Task from current or future execution. This means Tasks
     * with dependencies on this Task may not continue to execute.
     *
     * @param mayInterruptIfRunning allow interruption of the Task
     * @return true if the Task is cancelled
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        this.registerState(State.CANCELLED);
        if(this.state() == State.INACTIVE)
            return true;
        else if(this.state() == State.QUEUED ||
                (this.state() == State.RUNNING && mayInterruptIfRunning)) {
            this.scheduler.removeTask(this.getTag());
            return true;
        }
        return false;
    }

    /**
     * Signals completion of the Task with the given result and error.
     * Usually invoked from within {@code Task#exec}, but can also
     * be used from outside the Task to obtrude and force the results
     * of the Task to align with the passed arguments.
     *
     * This method applies a combination of stream() and finished().
     *
     * @param result the result to complete the Task with
     * @param error the error to complete the Task with
     */
    public final void complete(O result, Object error) {
        this.registerState(error != null ? State.FAILED : State.COMPLETED);
        if (result != null)
            stream((this.result = result));
        if (error != null)
            streamError((this.error = error));
        finished();
    }

    /**
     * Signals completion of the Task with the given result.
     * Usually invoked from within {@code Task#exec}, but can also
     * be used from outside the Task to obtrude and force the results
     * of the Task to align with the passed argument.
     *
     * @param result the result to complete the Task with
     */
    public final void complete(O result) {
        this.registerState(State.COMPLETED);
        if (result != null)
            stream((this.result = result));
        finished();
    }

    /**
     * Signals completion of the Task with the given throwable error.
     * Usually invoked from within {@code Task#exec}, but can also
     * be used from outside the Task to obtrude and force the results
     * of the Task to align with the passed throwable.
     *
     * @param throwable the error to complete the Task with
     */
    public final void completeExceptionally(Throwable throwable) {
        this.registerState(State.FAILED);
        if (throwable != null)
            streamError((this.error = throwable));
        finished();
    }

    /**
     * Returns true if this Task was completed, cancelled, or failed,
     * and is no longer running or queued by the scheduler.
     *
     * @return true if this Task is no longer running
     */
    public final boolean isDone() {
        return this.state() == State.COMPLETED ||
                this.state() == State.CANCELLED ||
                this.state() == State.FAILED;
    }

    /**
     * Returns the informational state of the Task, which goes beyond
     * the granularity of the {@link Task#isDone()} method.
     *
     * @return the current state of the Task
     */
    public final State state() {
        return state;
    }

    //
    // IMPLEMENTATION
    //

    // Remote invocation source.
    @Override
    public final void remote(I input) {
        this.registerState(State.RUNNING);
        try {
            complete(exec(input));
        } catch(Exception e) {
            completeExceptionally(e);
        }
    }

    // give the scheduler signals about the Task
    // the scheduler can also transparently fill remote registries too
    private void registerState(State newState) {
        Objects.requireNonNull(this.scheduler, "TaskScheduler must be bound first!");
        this.scheduler.updateState(this.getTag(), (this.state = newState));
    }

    // nothing can really happen asynchronously without this...
    private void registerScheduler(TaskScheduler scheduler) {
        Objects.requireNonNull(this.scheduler, "TaskScheduler cannot be null!");
        (this.scheduler = scheduler).updateState(this.getTag(), this.state);
    }

}
