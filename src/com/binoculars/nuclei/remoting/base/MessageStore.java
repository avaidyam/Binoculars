package com.binoculars.nuclei.remoting.base;

/**
 * interface defining a message store capable of storing/retrieving sequenced messages.
 * used e.g. as ringbuffer to support retransmission of lost messages (e.g. reliable long poll)
 *
 */
public interface MessageStore {

	Object getMessage( CharSequence queueId, long sequence );
	void   putMessage( CharSequence queueId, long sequence, Object message );
	void   confirmMessage( CharSequence queueId, long sequence );

	void killQueue( CharSequence queueId);

}

