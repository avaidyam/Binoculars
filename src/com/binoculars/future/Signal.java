package com.binoculars.future;

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
    String CONT = "CNT";

	public static boolean isComplete(Object error) {
		return error == null;
	}

	public static boolean isCont(Object o) {
        return CONT.equals(o);
    }

	public static boolean isResult(Object error) {
		return isCont(error);
	}

	public static boolean isError(Object o) {
        return o != null && !CONT.equals(o);
    }

    void complete(T result, Object error);

    default void complete() {
        complete(null, null);
    }

    default void complete(T result) {
        complete(result, null);
    }

    default void completeExceptionally(Throwable throwable) {
        complete(null, throwable);
    }

    default void reject(Object error) {
        complete(null, error);
    }

    default void resolve(T result) {
        complete(result, null);
    }

    default void stream(T result) {
        complete(result, Signal.CONT);
    }
}
