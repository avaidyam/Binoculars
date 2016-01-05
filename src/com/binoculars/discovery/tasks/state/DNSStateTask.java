// Licensed under Apache License version 2.0
package com.binoculars.discovery.tasks.state;

import com.binoculars.discovery.ServiceInfo;
import com.binoculars.discovery.DNSOutgoing;
import com.binoculars.discovery.DNSStatefulObject;
import com.binoculars.discovery.ServiceInfoImpl;
import com.binoculars.discovery.ZeroConf;
import com.binoculars.discovery.constants.DNSConstants;
import com.binoculars.discovery.constants.DNSState;
import com.binoculars.discovery.tasks.DNSTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the root class for allOf state tasks. These tasks work with objects that implements the {@link DNSStatefulObject} interface and therefore participate in the state machine.
 * 
 * @author Pierre Frisch
 */
public abstract class DNSStateTask extends DNSTask {
    static Logger      logger1     = Logger.getLogger(DNSStateTask.class.getName());

    /**
     * By setting a 0 ttl we effectively expire the record.
     */
    private final int  _ttl;

    private static int _defaultTTL = DNSConstants.DNS_TTL;

    /**
     * The state of the task.
     */
    private DNSState _taskState  = null;

    public abstract String getTaskDescription();

    public static int defaultTTL() {
        return _defaultTTL;
    }

    /**
     * For testing only do not use in production.
     * 
     * @param value
     */
    public static void setDefaultTTL(int value) {
        _defaultTTL = value;
    }

    /**
     * @param jmDNSImpl
     * @param ttl
     */
    public DNSStateTask(ZeroConf jmDNSImpl, int ttl) {
        super(jmDNSImpl);
        _ttl = ttl;
    }

    /**
     * @return the ttl
     */
    public int getTTL() {
        return _ttl;
    }

    /**
     * Associate the DNS host and the service infos with this task if not already associated and in the same state.
     * 
     * @param state
     *            target state
     */
    protected void associate(DNSState state) {
        synchronized (this.getDns()) {
            this.getDns().associateWithTask(this, state);
        }
        for (ServiceInfo serviceInfo : this.getDns().getServices().values()) {
            ((ServiceInfoImpl) serviceInfo).associateWithTask(this, state);
        }
    }

    /**
     * Remove the DNS host and service info association with this task.
     */
    protected void removeAssociation() {
        // Remove association from host to this
        synchronized (this.getDns()) {
            this.getDns().removeAssociationWithTask(this);
        }

        // Remove associations from services to this
        for (ServiceInfo serviceInfo : this.getDns().getServices().values()) {
            ((ServiceInfoImpl) serviceInfo).removeAssociationWithTask(this);
        }
    }

    @Override
    public void run() {
        DNSOutgoing out = this.createOugoing();
        try {
            if (!this.checkRunCondition()) {
                this.cancel();
                return;
            }
            List<DNSStatefulObject> stateObjects = new ArrayList<DNSStatefulObject>();
            // send probes for ZeroConf itself
            synchronized (this.getDns()) {
                if (this.getDns().isAssociatedWithTask(this, this.getTaskState())) {
                    logger1.finer(this.getName() + ".run() ZeroConf " + this.getTaskDescription() + " " + this.getDns().getName());
                    stateObjects.add(this.getDns());
                    out = this.buildOutgoingForDNS(out);
                }
            }
            // send probes for services
            for (ServiceInfo serviceInfo : this.getDns().getServices().values()) {
                ServiceInfoImpl info = (ServiceInfoImpl) serviceInfo;

                synchronized (info) {
                    if (info.isAssociatedWithTask(this, this.getTaskState())) {
                        logger1.fine(this.getName() + ".run() ZeroConf " + this.getTaskDescription() + " " + info.getQualifiedName());
                        stateObjects.add(info);
                        out = this.buildOutgoingForInfo(info, out);
                    }
                }
            }
            if (!out.isEmpty()) {
                logger1.finer(this.getName() + ".run() ZeroConf " + this.getTaskDescription() + " #" + this.getTaskState());
                this.getDns().send(out);

                // Advance the state of objects.
                this.advanceObjectsState(stateObjects);
            } else {
                // Advance the state of objects.
                this.advanceObjectsState(stateObjects);

                // If we have nothing to send, another timer taskState ahead of us has done the job for us. We can cancel.
                cancel();
                return;
            }
        } catch (Throwable e) {
            logger1.log(Level.WARNING, this.getName() + ".run() exception ", e);
            this.recoverTask(e);
        }

        this.advanceTask();
    }

    protected abstract boolean checkRunCondition();

    protected abstract DNSOutgoing buildOutgoingForDNS(DNSOutgoing out) throws IOException;

    protected abstract DNSOutgoing buildOutgoingForInfo(ServiceInfoImpl info, DNSOutgoing out) throws IOException;

    protected abstract DNSOutgoing createOugoing();

    protected void advanceObjectsState(List<DNSStatefulObject> list) {
        if (list != null) {
            for (DNSStatefulObject object : list) {
                synchronized (object) {
                    object.advanceState(this);
                }
            }
        }
    }

    protected abstract void recoverTask(Throwable e);

    protected abstract void advanceTask();

    /**
     * @return the taskState
     */
    protected DNSState getTaskState() {
        return this._taskState;
    }

    /**
     * @param taskState
     *            the taskState to set
     */
    protected void setTaskState(DNSState taskState) {
        this._taskState = taskState;
    }

}
