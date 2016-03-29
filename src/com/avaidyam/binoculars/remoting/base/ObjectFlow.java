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

package com.avaidyam.binoculars.remoting.base;

import com.avaidyam.binoculars.future.Future;
import org.nustaq.serialization.FSTConfiguration;

import java.io.IOException;
import java.util.List;

/**
 * A pair of protocols that define the flow of an Object.
 * TODO: Needs refactoring.
 */
public interface ObjectFlow {

	/**
	 * A protocol defining the process of encoding outgoing messages.
	 */
	interface Source {

		/**
		 *
		 * @param toWrite
		 * @throws Exception
		 */
		void writeObject(Object toWrite) throws Exception;

		/**
		 *
		 * @throws Exception
		 */
		void flush() throws Exception;

		/**
		 *
		 * @param ex
		 */
		void setLastError(Throwable ex);

		/**
		 *
		 * @return
		 */
		Throwable getLastError();

		/**
		 * set by outer machinery
		 * @param conf
		 */
		void setConf(FSTConfiguration conf);

		/**
		 *
		 * @return
		 */
		FSTConfiguration getConf();

		/**
		 *
		 * @throws IOException
		 */
		void close() throws IOException;

		/**
		 *
		 * @return
		 */
		default boolean canWrite() {
			return true;
		}

		/**
		 *
		 * @return
		 */
		boolean isClosed();
	}

	/**
	 * A protocol defining the process of decoding incoming messages.
	 */
	interface Sink {

		/**
		 * @param sink - usually this or a wrapper of this
		 * @param received - decoded object(s)
		 * @param createdFutures - list of futures/callbacks contained in the decoded object remote calls (unused)
		 */
		void receiveObject(Sink sink, Object received, List<Future> createdFutures);

		/**
		 *
		 * @param received
		 * @param createdFutures
		 */
		default void receiveObject(Object received, List<Future> createdFutures) {
			receiveObject(this,received,createdFutures);
		}

		/**
		 *
		 */
		void sinkClosed();
	}
}


