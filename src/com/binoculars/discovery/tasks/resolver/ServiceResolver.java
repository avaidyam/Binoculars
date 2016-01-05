// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package com.binoculars.discovery.tasks.resolver;

import java.io.IOException;

import com.binoculars.discovery.ServiceInfo;
import com.binoculars.discovery.DNSOutgoing;
import com.binoculars.discovery.DNSQuestion;
import com.binoculars.discovery.DNSRecord;
import com.binoculars.discovery.ZeroConf;
import com.binoculars.discovery.constants.DNSConstants;
import com.binoculars.discovery.constants.DNSRecordClass;
import com.binoculars.discovery.constants.DNSRecordType;

/**
 * The ServiceResolver queries three times consecutively for services of a given type, and then removes itself from the timer.
 * <p/>
 * The ServiceResolver will run only if ZeroConf is in state ANNOUNCED. REMIND: Prevent having multiple service resolvers for the same type in the timer queue.
 */
public class ServiceResolver extends DNSResolverTask {

    private final String _type;

    public ServiceResolver(ZeroConf jmDNSImpl, String type) {
        super(jmDNSImpl);
        this._type = type;
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#getName()
     */
    @Override
    public String getName() {
        return "ServiceResolver(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#addAnswers(DNSOutgoing)
     */
    @Override
    protected DNSOutgoing addAnswers(DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        long now = System.currentTimeMillis();
        for (ServiceInfo info : this.getDns().getServices().values()) {
            newOut = this.addAnswer(newOut, new DNSRecord.Pointer(info.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, info.getQualifiedName()), now);
            // newOut = this.addAnswer(newOut, new DNSRecord.Service(info.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, info.getPriority(), info.getWeight(), info.getPort(),
            // this.getDns().getLocalHost().getName()), now);
        }
        return newOut;
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#addQuestions(DNSOutgoing)
     */
    @Override
    protected DNSOutgoing addQuestions(DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        newOut = this.addQuestion(newOut, DNSQuestion.newQuestion(_type, DNSRecordType.TYPE_PTR, DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE));
        // newOut = this.addQuestion(newOut, DNSQuestion.newQuestion(_type, DNSRecordType.TYPE_SRV, DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE));
        return newOut;
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#description()
     */
    @Override
    protected String description() {
        return "querying service";
    }
}