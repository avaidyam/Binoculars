package com.binoculars.nuclei.management;

public interface SchedulerStatusMXBean {

	int getNumberOfDispatchers();

	int getDefQueueSize();

	int getIsolatedThreadCount();

	class SchedulerStatus implements SchedulerStatusMXBean {

		private int _numberOfDispatchers;
		private int _defQueueSize;
		private int _isolatedThreadCount;

		public SchedulerStatus(int numberOfDispatchers, int defQueueSize, int isolatedThreadCount) {
			this._numberOfDispatchers = numberOfDispatchers;
			this._defQueueSize = defQueueSize;
			this._isolatedThreadCount = isolatedThreadCount;
		}

		public int getNumberOfDispatchers() {
			return _numberOfDispatchers;
		}

		public int getDefQueueSize() {
			return _defQueueSize;
		}

		public int getIsolatedThreadCount() {
			return _isolatedThreadCount;
		}
	}
}