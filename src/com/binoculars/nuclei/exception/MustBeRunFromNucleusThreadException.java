package com.binoculars.nuclei.exception;

/**
 * A MustBeRunFromNucleusThreadException is thrown if the invocation
 * in question was not made from an Nucleus's thread.
 */
public class MustBeRunFromNucleusThreadException extends RuntimeException {
    public MustBeRunFromNucleusThreadException() {
        super("Invocation must occur in an Nucleus thread.");
    }
}
