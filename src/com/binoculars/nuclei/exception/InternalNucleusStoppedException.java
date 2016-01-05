package com.binoculars.nuclei.exception;

/**
 * An InternalNucleusStoppedException is thrown when an nuclei
 * cannot be stopped because has already been stopped.
 *
 * Note: avoid instantiating this class; use the INSTANCE provided.
 * This Throwable is able to be quickly thrown since it does
 * not implement the fillInStackTrace() method.
 */
public class InternalNucleusStoppedException extends RuntimeException {

    public static InternalNucleusStoppedException INSTANCE = new InternalNucleusStoppedException();

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
