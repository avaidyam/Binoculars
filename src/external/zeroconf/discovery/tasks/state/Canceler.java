// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package external.zeroconf.discovery.tasks.state;

import external.zeroconf.discovery.DNSOutgoing;
import external.zeroconf.discovery.DNSRecord;
import external.zeroconf.discovery.ServiceInfoImpl;
import external.zeroconf.discovery.ZeroConf;
import external.zeroconf.discovery.constants.DNSConstants;
import external.zeroconf.discovery.constants.DNSRecordClass;
import external.zeroconf.discovery.constants.DNSState;

import java.io.IOException;
import java.util.Timer;
import java.util.logging.Logger;

/**
 * The Canceler sends two announces with TTL=0 for the specified services.
 */
public class Canceler extends DNSStateTask {
    static Logger logger = Logger.getLogger(Canceler.class.getName());

    public Canceler(ZeroConf jmDNSImpl) {
        super(jmDNSImpl, 0);

        this.setTaskState(DNSState.CANCELING_1);
        this.associate(DNSState.CANCELING_1);
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#getName()
     */
    @Override
    public String getName() {
        return "Canceler(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
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
        timer.schedule(this, 0, DNSConstants.ANNOUNCE_WAIT_INTERVAL);
    }

    /*
     * (non-Javadoc)
     * @see java.util.TimerTask#cancel()
     */
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
        return "canceling";
    }

    /*
     * (non-Javadoc)
     * @see DNSStateTask#checkRunCondition()
     */
    @Override
    protected boolean checkRunCondition() {
        return true;
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
        if (!this.getTaskState().isCanceling()) {
            cancel();
        }
    }
}