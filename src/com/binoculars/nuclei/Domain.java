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

package com.binoculars.nuclei;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for all annotations to be applied to messages inside Nuclei.
 */
public @interface Domain {

	/**
	 * Ignored by the runtime message producer -- will not become async.
	 */
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface Ignore {}

	/**
	 * Specifies this method is a utility processed on client side / inside sender thread:
	 *
	 * class .. extends Nucleus {
	 *     public void message( long timeStamp, String stuff ) {..}
	 *
	 *     // just an utility executed inside calling thread
	 *     @CallerSide public void message( String stuff ) {
	 *         message( System.currentTimeMillis(), stuff );
	 *     }
	 *
	 *     @CallerSide public int getId() {
	 *         // get "real" nuclei impl
	 *         return getNucleus().id; // concurrent access !! final, volatile and locks might be required
	 *     }
	 * }
	 *
	 * Note those method cannot access local state of the nuclei, they just might invoke methods as they
	 * are called on the proxy object (Nucleus Ref).
	 *
	 * If one urgently needs to access local nuclei state synchronous, its possible to obtain the real nuclei instance by calling getNucleus().
	 * Note that multithreading primitives might required then, as internal nuclei state is accessed concurrently
	 * this way.
	 *
	 * WARNING: @CallerSide's cannot be invoked from remote (via network)
	 */
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface CallerSide {}

	/**
	 * handle this method like a callback method. The contract for callbacks is weaker than for regular
	 * nuclei methods. For a callback method, the scheduler is allowed to execute the method synchronous
	 * if the sender and receiver happen to be scheduled on the same thread.
	 *
	 * Additionally callback messages have higher priority compared to regualt nuclei messages. A dispatcher thread
	 * will always first check the callback queue befor looking for messages on the actors mailbox.
	 *
	 * Besides performance improvements, this also enables some scheduling tweaks to automatically prevent deadlocks.
	 */
	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface SignalPriority {}

	/**
	 * method modifier to signal this method should not be exposed via an remoting interface (process local message)
	 */
	@Target({ElementType.METHOD, ElementType.TYPE})
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
