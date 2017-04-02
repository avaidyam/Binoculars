package com.avaidyam.binoculars;

import external.jaq.mpsc.MpscConcurrentQueue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Channel is a device for inbox/outbox style message-passing, where the
 * owner uses the inbox to receive "tell" messages and the outbox for "ask"
 * messages (i.e. ones that must respond with another message). It is intended
 * to be used as a multiple-producer, single-consumer unit.
 */
public class Channel {

    /**
     * Messages that have been dropped or have been sent to stopped targets.
     * Note: only strings are recorded to avoid accidental references.
     */
    public static final ConcurrentLinkedQueue<String> deadLetters = new ConcurrentLinkedQueue<>();

    /**
     * The letter queue used for messages that don't need to be responded to.
     */
    public Queue inbox;

    /**
     * The letter queue used for messages that require a reply (i.e. higher priority).
     */
    public Queue outbox;

    /**
     * The capacity of the inbox, useful for calculating pressure.
     */
    public int capacity;

    /**
     * Create a new Channel with a given queue size.
     *
     * @param queueSize initial inbox/outbox queue size.
     */
    public Channel(int queueSize) {
        this.inbox = new MpscConcurrentQueue(queueSize);
        this.outbox = new MpscConcurrentQueue(queueSize);
        this.capacity = ((MpscConcurrentQueue)this.inbox).getCapacity();
    }

    /**
     * An estimation of inbox queued messages. Useful for flow control.
     *
     * @return estimation of inbox queued messages
     */
    public int getMailboxSize() {
        return this.inbox.size();
    }

    /**
     * An estimation of outbox queued messages. Useful for flow control.
     *
     * @return estimation of outbox queued messages
     */
    public int getCallbackSize() {
        return this.outbox.size();
    }

    /**
     * An estimation of all queued messages. Useful for flow control.
     *
     * @return estimation of all queued messages
     */
    public int getQSizes() {
        return this.inbox.size() + this.outbox.size();
    }

    /**
     * Returns whether the inbox is currently under pressure.
     *
     * @return true if inbox fill size is half capacity
     */
    public boolean isInboxPressured() {
        return this.inbox.size() * 2 > this.capacity;
    }

    /**
     * Returns whether the outbox is currently under pressure.
     *
     * @return true if outbox fill size is half capacity
     */
    public boolean isOutboxPressured() {
        return this.outbox.size() * 2 > this.capacity;
    }
}
