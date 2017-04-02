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

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * A representation of a method call on an object, but deconstructed
 * into a packet-style object that can be queued or serialized for
 * scheduling and remote method invocation.
 *
 * @param <T> the target type of this message
 */
public interface Message<T> extends Serializable {

    /**
     * Returns the target of the message. For Nuclei, this is
     * not the Proxy, but the instance of the Nucleus itself.
     *
     * @return the target of this message
     */
    T getTarget();

    /**
     * Returns the method to call on the target. For Nuclei, this is
     * not the Proxy, but the instance of the Nucleus itself.
     *
     * @return the method of this message
     */
    Method getMethod();

    /**
     * Returns the arguments to be passed to the method invoked on
     * the target. If this array is modified, copy before sending.
     *
     * @return a direct reference to the arguments of this message
     */
    Object[] getArgs();

    /**
     * Returns the nuclei sending the message. In the case of a remote
     * connection, this is a remote nuclei reference/proxy.
     *
     * @return the nuclei sending the message
     */
    Nucleus getSendingNucleus();

    //
    // UNAVAILABLE METHODS
    //
    // @return the same message, but with copied argument array.
    // I arguments are modified, always use copy before sending, else
    // unpredictable side effects will happen: e.g. log.copy().send();
    //
    // public Message copy();
    //
    // @param newTarget
    // @return a shallow copy of this message with a new target set.
    // In case an actorProxy is passed, it is automatically resolved to the underlying nuclei
    //
    // public Message withTarget(T newTarget);
    // public Message withTarget(T newTarget, boolean copyArgs);
}
