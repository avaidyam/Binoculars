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
public interface SchedulerStatusMXBean {

	/**
	 *
	 * @return
	 */
	int getNumberOfDispatchers();

	/**
	 *
	 * @return
	 */
	int getDefQueueSize();

	/**
	 *
	 * @return
	 */
	int getIsolatedThreadCount();

	/**
	 * A concrete implementation of SchedulerStatusMXBean.
	 */
	final class SchedulerStatus implements SchedulerStatusMXBean {

		private int _numberOfDispatchers;
		private int _defQueueSize;
		private int _isolatedThreadCount;

		public SchedulerStatus(int numberOfDispatchers, int defQueueSize, int isolatedThreadCount) {
			this._numberOfDispatchers = numberOfDispatchers;
			this._defQueueSize = defQueueSize;
			this._isolatedThreadCount = isolatedThreadCount;
		}

		public final int getNumberOfDispatchers() {
			return _numberOfDispatchers;
		}

		public final int getDefQueueSize() {
			return _defQueueSize;
		}

		public final int getIsolatedThreadCount() {
			return _isolatedThreadCount;
		}
	}
}