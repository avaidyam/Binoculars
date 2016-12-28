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

package com.avaidyam.binoculars;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for all annotations to be applied to messages inside Nuclei.
 */
public @interface Domain {

	/**
	 * Nucleus methods explicitly marked with the @Export annotation will be
	 * transformed to allow queueing and remote invocation. A public void or
	 * Signal-returning method MUST declare this annotation to support it.
	 */
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface Export {

		/**
		 * Allow this method to be exposed and invoked via a remoting interface.
		 */
		boolean transport = true;

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
		boolean signalPriority = false;
	}

	// TODO DEPRECATE THESE
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface CallerSide {}
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface SignalPriority {}
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface Local {}

	/**
	 * parameters tagged as callback get wrapped by 'Nucleus.Callback' automatically. Calls on these object are
	 * executed in the callers thread (enqueued to the calling Nucleus queue)
	 * automatically applied for callback + futures
	 */
	@Target({ElementType.PARAMETER})
	@Retention(RetentionPolicy.RUNTIME)
	@interface InThread {}
}
