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

package com.avaidyam.binoculars.management;

/**
 *
 */
public interface NucleusStatusMXBean {

	/**
	 *
	 * @return
	 */
	String getNucleusClass();

	/**
	 *
	 * @return
	 */
	int getMessageQueueSize();

	/**
	 *
	 * @return
	 */
	int getCallbackQueueSize();

	/**
	 * A concrete implementation of NucleusStatusMXBean.
	 */
	final class NucleusStatus implements NucleusStatusMXBean {

		private String _nucleusClass;
		private int _messageQueueSize;
		private int _callbackQueueSize;

		public NucleusStatus(String nucleusClass, int messageQueueSize, int callbackQueueSize) {
			this._nucleusClass = nucleusClass;
			this._messageQueueSize = messageQueueSize;
			this._callbackQueueSize = callbackQueueSize;
		}

		public final String getNucleusClass() {
			return _nucleusClass;
		}

		public final int getMessageQueueSize() {
			return _messageQueueSize;
		}

		public final int getCallbackQueueSize() {
			return _callbackQueueSize;
		}
	}
}
