// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package com.binoculars.discovery.tasks.state;

import com.binoculars.discovery.DNSOutgoing;

import java.io.IOException;
import java.util.Timer;
import java.util.logging.Logger;

import com.binoculars.discovery.DNSRecord;
import com.binoculars.discovery.ZeroConf;
import com.binoculars.discovery.ServiceInfoImpl;
import com.binoculars.discovery.constants.DNSConstants;
import com.binoculars.discovery.constants.DNSRecordClass;
import com.binoculars.discovery.constants.DNSState;

/**
 * The Renewer is there to send renewal announcement when the record expire for ours infos.
 */
public class Renewer extends DNSStateTask {
    static Logger logger = Logger.getLogger(Renewer.class.getName());

    public Renewer(ZeroConf jmDNSImpl) {
        super(jmDNSImpl, defaultTTL());

        this.setTaskState(DNSState.ANNOUNCED);
        this.associate(DNSState.ANNOUNCED);
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#getName()
     */
    @Override
    public String getName() {
        return "Renewer(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
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
            timer.schedule(this, DNSConstants.ANNOUNCED_RENEWAL_TTL_INTERVAL, DNSConstants.ANNOUNCED_RENEWAL_TTL_INTERVAL);
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
        return "renewing";
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
        if (!this.getTaskState().isAnnounced()) {
            cancel();
        }
    }
}