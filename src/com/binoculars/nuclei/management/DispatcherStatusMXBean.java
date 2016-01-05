package com.binoculars.nuclei.management;

public interface DispatcherStatusMXBean {

	String getName();

	int getNumberOfNuclei();

	int getLoadPerc();

	int getQueueSize();

	class DispatcherStatus implements DispatcherStatusMXBean {

		private String _name;
		private int _numberOfNuclei;
		private int _loadPerc;
		private int _queueSize;

		public DispatcherStatus(String name, int numberOfNuclei, int loadPerc, int queueSize) {
			this._name = name;
			this._numberOfNuclei = numberOfNuclei;
			this._loadPerc = loadPerc;
			this._queueSize = queueSize;
		}

		public String getName() {
			return _name;
		}

		public int getNumberOfNuclei() {
			return _numberOfNuclei;
		}

		public int getLoadPerc() {
			return _loadPerc;
		}

		public int getQueueSize() {
			return _queueSize;
		}
	}
}
