package com.avaidyam.binoculars;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nucleus methods explicitly marked with the @Export annotation will be
 * transformed to allow queueing and remote invocation. A public void or
 * Signal-returning method MUST declare this annotation to support it.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Export {

    /**
     * Allow this method to be exposed and invoked via a remoting interface.
     */
    boolean transport() default true;

    /**
     * Force this method to have the same priority as one that returns a Signal.
     * Note: this flag has no effect on a method already returning a Signal.
     *
     * For a Signal-returning method, the scheduler is allowed to execute the method
     * synchronously if the sender and receiver happen to be scheduled on the same thread.
     *
     * Besides performance improvements, this also enables some scheduling
     * tweaks to automatically prevent deadlocks.
     */
    boolean signalPriority() default false;

	/**
	 * parameters tagged as callback get wrapped by 'Nucleus.Callback' automatically. Calls on these object are
	 * executed in the callers thread (enqueued to the calling Nucleus queue)
	 * automatically applied for callback + futures
	 */
	@Target({ElementType.PARAMETER})
	@Retention(RetentionPolicy.RUNTIME)
	@interface InThread {}
}
