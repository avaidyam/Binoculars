// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package external.zeroconf.discovery.tasks.resolver;

import external.zeroconf.discovery.DNSOutgoing;
import external.zeroconf.discovery.DNSRecord;
import external.zeroconf.discovery.ZeroConf;
import external.zeroconf.discovery.constants.DNSConstants;
import external.zeroconf.discovery.constants.DNSRecordClass;

import java.io.IOException;

import external.zeroconf.discovery.DNSQuestion;
import external.zeroconf.discovery.constants.DNSRecordType;

/**
 * Helper class to resolve service types.
 * <p/>
 * The TypeResolver queries three times consecutively for service types, and then removes itself from the timer.
 * <p/>
 * The TypeResolver will run only if ZeroConf is in state ANNOUNCED.
 */
public class TypeResolver extends DNSResolverTask {

    /**
     * @param jmDNSImpl
     */
    public TypeResolver(ZeroConf jmDNSImpl) {
        super(jmDNSImpl);
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#getName()
     */
    @Override
    public String getName() {
        return "TypeResolver(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#addAnswers(DNSOutgoing)
     */
    @Override
    protected DNSOutgoing addAnswers(DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        long now = System.currentTimeMillis();
        for (String type : this.getDns().getServiceTypes().keySet()) {
            ZeroConf.ServiceTypeEntry typeEntry = this.getDns().getServiceTypes().get(type);
            newOut = this.addAnswer(newOut, new DNSRecord.Pointer("_services._dns-sd._udp.local.", DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, typeEntry.getType()), now);
        }
        return newOut;
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#addQuestions(DNSOutgoing)
     */
    @Override
    protected DNSOutgoing addQuestions(DNSOutgoing out) throws IOException {
        return this.addQuestion(out, DNSQuestion.newQuestion("_services._dns-sd._udp.local.", DNSRecordType.TYPE_PTR, DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE));
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#description()
     */
    @Override
    protected String description() {
        return "querying type";
    }
}