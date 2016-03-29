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

/**
 * Container for all exceptions that can be thrown by a Nucleus or its constituents.
 */
public class Exceptions {

	/**
	 * An InternalNucleusStoppedException is thrown when an nuclei
	 * cannot be stopped because has already been stopped.
	 *
	 * Note: avoid instantiating this class; use the INSTANCE provided.
	 * This Throwable is able to be quickly thrown since it does
	 * not implement the fillInStackTrace() method.
	 */
	public static class InternalNucleusStoppedException extends RuntimeException {
		public static InternalNucleusStoppedException INSTANCE = new InternalNucleusStoppedException();

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}

	/**
	 * The NucleusBlockedException is thrown when an nuclei is blocked
	 * because a receiving nuclei's queue is full.
	 *
	 * Note: avoid instantiating this class; use the INSTANCE provided.
	 * This Throwable is able to be quickly thrown since it does
	 * not implement the fillInStackTrace() method.
	 */
	public static class NucleusBlockedException extends RuntimeException {
		public static NucleusBlockedException INSTANCE = new NucleusBlockedException();

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}

	/**
	 * A MustBeRunFromNucleusThreadException is thrown if the invocation
	 * in question was not made from an Nucleus's thread.
	 */
	public static class MustBeRunFromNucleusThreadException extends RuntimeException {
		public MustBeRunFromNucleusThreadException() {
			super("RemoteInvocation must occur in an Nucleus thread.");
		}
	}

	/**
	 * An NucleusStoppedException occurs when an nuclei is messaged
	 * after it has already been stopped before.
	 *
	 * See also: InternalNucleusStoppedException.
	 */
	public static class NucleusStoppedException extends RuntimeException {

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
}
