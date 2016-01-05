package com.binoculars.nuclei.exception;

/**
 * An NucleusStoppedException occurs when an nuclei is messaged
 * after it has already been stopped before.
 *
 * See also: InternalNucleusStoppedException.
 */
public class NucleusStoppedException extends RuntimeException {

    public NucleusStoppedException() {
	    this("");
    }

    public NucleusStoppedException(String message) {
        super("Nucleus is already stopped and cannot be messaged. " + message);
    }

	public NucleusStoppedException(Throwable cause) {
		this("", cause);
	}

    public NucleusStoppedException(String message, Throwable cause) {
        super("Nucleus is already stopped and cannot be messaged. " + message, cause);
    }

    public NucleusStoppedException(String message, Throwable cause,
                                   boolean enableSuppression, boolean writableStackTrace) {
        super("Nucleus is already stopped and cannot be messaged. " + message,
		        cause, enableSuppression, writableStackTrace);
    }
}
