package com.binoculars.nuclei.exception;

/**
 * The NucleusBlockedException is thrown when an nuclei is blocked
 * because a receiving nuclei's queue is full.
 *
 * Note: avoid instantiating this class; use the INSTANCE provided.
 * This Throwable is able to be quickly thrown since it does
 * not implement the fillInStackTrace() method.
 */
public class NucleusBlockedException extends RuntimeException {

    public static NucleusBlockedException INSTANCE = new NucleusBlockedException();

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
