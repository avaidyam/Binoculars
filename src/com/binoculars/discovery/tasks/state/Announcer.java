// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package com.binoculars.discovery.tasks.state;

import com.binoculars.discovery.DNSOutgoing;
import com.binoculars.discovery.DNSRecord;
import com.binoculars.discovery.ServiceInfoImpl;
import com.binoculars.discovery.ZeroConf;
import com.binoculars.discovery.constants.DNSConstants;
import com.binoculars.discovery.constants.DNSRecordClass;
import com.binoculars.discovery.constants.DNSState;

import java.io.IOException;
import java.util.Timer;
import java.util.logging.Logger;

/**
 * The Announcer sends an accumulated query of allOf announces, and advances the state of allOf serviceInfos, for which it has sent an announce. The Announcer also sends announcements and advances the state of ZeroConf itself.
 * <p/>
 * When the announcer has run two times, it finishes.
 */
public class Announcer extends DNSStateTask {
    static Logger logger = Logger.getLogger(Announcer.class.getName());

    public Announcer(ZeroConf jmDNSImpl) {
        super(jmDNSImpl, defaultTTL());

        this.setTaskState(DNSState.ANNOUNCING_1);
        this.associate(DNSState.ANNOUNCING_1);
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#getName()
     */
    @Override
    public String getName() {
        return "Announcer(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return super.toString() + " state: " + this.getTaskState();
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#start(java.util.Timer)
     */
    @Override
    public void start(Timer timer) {
        if (!this.getDns().isCanceling() && !this.getDns().isCanceled()) {
            timer.schedule(this, DNSConstants.ANNOUNCE_WAIT_INTERVAL, DNSConstants.ANNOUNCE_WAIT_INTERVAL);
        }
    }

    @Override
    public boolean cancel() {
        this.removeAssociation();

        return super.cancel();
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#getTaskDescription()
     */
    @Override
    public String getTaskDescription() {
        return "announcing";
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#checkRunCondition()
     */
    @Override
    protected boolean checkRunCondition() {
        return !this.getDns().isCanceling() && !this.getDns().isCanceled();
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#createOugoing()
     */
    @Override
    protected DNSOutgoing createOugoing() {
        return new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA);
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#buildOutgoingForDNS(DNSOutgoing)
     */
    @Override
    protected DNSOutgoing buildOutgoingForDNS(DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        for (DNSRecord answer : this.getDns().getLocalHost().answers(DNSRecordClass.UNIQUE, this.getTTL())) {
            newOut = this.addAnswer(newOut, null, answer);
        }
        return newOut;
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#buildOutgoingForInfo(ServiceInfoImpl, DNSOutgoing)
     */
    @Override
    protected DNSOutgoing buildOutgoingForInfo(ServiceInfoImpl info, DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        for (DNSRecord answer : info.answers(DNSRecordClass.UNIQUE, this.getTTL(), this.getDns().getLocalHost())) {
            newOut = this.addAnswer(newOut, null, answer);
        }
        return newOut;
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#recoverTask(java.lang.Throwable)
     */
    @Override
    protected void recoverTask(Throwable e) {
        this.getDns().recover();
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#advanceTask()
     */
    @Override
    protected void advanceTask() {
        this.setTaskState(this.getTaskState().advance());
        if (!this.getTaskState().isAnnouncing()) {
            this.cancel();

            this.getDns().startRenewer();
        }
    }

}