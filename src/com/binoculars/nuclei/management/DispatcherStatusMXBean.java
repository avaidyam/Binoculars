package com.binoculars.nuclei.management;

/**
 *
 */
public interface DispatcherStatusMXBean {

	/**
	 *
	 * @return
	 */
	String getName();

	/**
	 *
	 * @return
	 */
	int getNumberOfNuclei();

	/**
	 *
	 * @return
	 */
	int getLoadPerc();

	/**
	 *
	 * @return
	 */
	int getQueueSize();

	/**
	 * A concrete implementation of DispatcherStatusMXBean.
	 */
	final class DispatcherStatus implements DispatcherStatusMXBean {

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

		public final String getName() {
			return _name;
		}

		public final int getNumberOfNuclei() {
			return _numberOfNuclei;
		}

		public final int getLoadPerc() {
			return _loadPerc;
		}

		public final int getQueueSize() {
			return _queueSize;
		}
	}
}
