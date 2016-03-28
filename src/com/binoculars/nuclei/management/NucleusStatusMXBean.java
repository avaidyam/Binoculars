package com.binoculars.nuclei.management;

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
