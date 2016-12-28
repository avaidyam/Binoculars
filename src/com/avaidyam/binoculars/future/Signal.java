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

package com.avaidyam.binoculars.future;

import java.io.Serializable;

/**
 * Typically used to complete results from outside the nuclei.
 * The underlying mechanics scans method arguments and schedules calls on the call back into the calling actors thread.
 * Note that the callback invocation is added as a message to the end of the calling nuclei.
 * e.g. nuclei.method( arg, new Callbacl() { public void complete(T result, Object error ) { ..runs in caller thread.. } }
 * do not use interface, slows down instanceof significantly
 */
@FunctionalInterface
public interface Signal<T> extends Serializable {

    /**
     * use value as error to indicate more messages are to come (else remoting will close channel).
     */
	Error CONT = new Error("CNT");

	static boolean isComplete(Object error) {
		return error == null;
	}

	static boolean isCont(Object o) {
        return CONT.equals(o);
    }

	static boolean isResult(Object error) {
		return isCont(error);
	}

	static boolean isError(Object o) {
        return o != null && !CONT.equals(o);
    }

    void complete(T result, Throwable error);

    default void complete() {
        complete(null, null);
    }

    default void complete(T result) {
        complete(result, null);
    }

    default void completeExceptionally(Throwable throwable) {
        complete(null, throwable);
    }

    default void reject(Throwable error) {
        complete(null, error);
    }

    default void resolve(T result) {
        complete(result, null);
    }

    default void stream(T result) {
        complete(result, Signal.CONT);
    }
}
