package com.binoculars.nuclei;

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
     * not the NucleusProxy, but the instance of the Nucleus itself.
     *
     * @return the target of this message
     */
    T getTarget();

    /**
     * Returns the method to call on the target. For Nuclei, this is
     * not the NucleusProxy, but the instance of the Nucleus itself.
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
