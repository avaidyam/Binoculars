package com.binoculars.nuclei.management;

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