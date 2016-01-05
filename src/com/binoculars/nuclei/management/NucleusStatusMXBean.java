package com.binoculars.nuclei.management;

public interface NucleusStatusMXBean {

	String getNucleusClass();

	int getMessageQueueSize();

	int getCallbackQueueSize();

	class NucleusStatus implements NucleusStatusMXBean {

		private String _nucleusClass;
		private int _messageQueueSize;
		private int _callbackQueueSize;

		public NucleusStatus(String nucleusClass, int messageQueueSize, int callbackQueueSize) {
			this._nucleusClass = nucleusClass;
			this._messageQueueSize = messageQueueSize;
			this._callbackQueueSize = callbackQueueSize;
		}

		public String getNucleusClass() {
			return _nucleusClass;
		}

		public int getMessageQueueSize() {
			return _messageQueueSize;
		}

		public int getCallbackQueueSize() {
			return _callbackQueueSize;
		}
	}
}
