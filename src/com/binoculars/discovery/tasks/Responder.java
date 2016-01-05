// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package com.binoculars.discovery.tasks;

import com.binoculars.discovery.*;
import com.binoculars.discovery.constants.DNSConstants;
import com.binoculars.discovery.DNSIncoming;
import com.binoculars.discovery.DNSOutgoing;
import com.binoculars.discovery.DNSQuestion;
import com.binoculars.discovery.DNSRecord;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Responder sends a single answer for the specified service infos and for the host name.
 */
public class Responder extends DNSTask {
    static Logger             logger = Logger.getLogger(Responder.class.getName());

    /**
     *
     */
    private final DNSIncoming _in;

    /**
     *
     */
    private final boolean     _unicast;

    public Responder(ZeroConf jmDNSImpl, DNSIncoming in, int port) {
        super(jmDNSImpl);
        this._in = in;
        this._unicast = (port != DNSConstants.MDNS_PORT);
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#getName()
     */
    @Override
    public String getName() {
        return "Responder(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return super.toString() + " incomming: " + _in;
    }

    /*
     * (non-Javadoc)
     * @see DNSTask#start(java.util.Timer)
     */
    @Override
    public void start(Timer timer) {
        // According to draft-cheshire-dnsext-multicastdns.txt chapter "7 Responding":
        // We respond immediately if we know for sure, that we are the only one who can respond to the query.
        // In allOf other cases, we respond within 20-120 ms.
        //
        // According to draft-cheshire-dnsext-multicastdns.txt chapter "6.2 Multi-Packet Known Answer Suppression":
        // We respond after 20-120 ms if the query is truncated.

        boolean iAmTheOnlyOne = true;
        for (DNSQuestion question : _in.getQuestions()) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest(this.getName() + "start() question=" + question);
            }
            iAmTheOnlyOne = question.iAmTheOnlyOne(this.getDns());
            if (!iAmTheOnlyOne) {
                break;
            }
        }
        int delay = (iAmTheOnlyOne && !_in.isTruncated()) ? 0 : DNSConstants.RESPONSE_MIN_WAIT_INTERVAL + ZeroConf.getRandom().nextInt(DNSConstants.RESPONSE_MAX_WAIT_INTERVAL - DNSConstants.RESPONSE_MIN_WAIT_INTERVAL + 1) - _in.elapseSinceArrival();
        if (delay < 0) {
            delay = 0;
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(this.getName() + "start() Responder chosen delay=" + delay);
        }
        if (!this.getDns().isCanceling() && !this.getDns().isCanceled()) {
            timer.schedule(this, delay);
        }
    }

    @Override
    public void run() {
        this.getDns().respondToQuery(_in);

        // We use these sets to prevent duplicate records
        Set<DNSQuestion> questions = new HashSet<DNSQuestion>();
        Set<DNSRecord> answers = new HashSet<DNSRecord>();

        if (this.getDns().isAnnounced()) {
            try {
                // Answer questions
                for (DNSQuestion question : _in.getQuestions()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() ZeroConf responding to: " + question);
                    }
                    // for unicast responses the question must be included
                    if (_unicast) {
                        // out.addQuestion(q);
                        questions.add(question);
                    }

                    question.addAnswers(this.getDns(), answers);
                }

                // remove known answers, if the ttl is at least half of the correct value. (See Draft Cheshire chapter 7.1.).
                long now = System.currentTimeMillis();
                for (DNSRecord knownAnswer : _in.getAnswers()) {
                    if (knownAnswer.isStale(now)) {
                        answers.remove(knownAnswer);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer(this.getName() + "ZeroConf Responder Known Answer Removed");
                        }
                    }
                }

                // respond if we have answers
                if (!answers.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer(this.getName() + "run() ZeroConf responding");
                    }
                    DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, !_unicast, _in.getSenderUDPPayload());
                    out.setId(_in.getId());
                    for (DNSQuestion question : questions) {
                        if (question != null) {
                            out = this.addQuestion(out, question);
                        }
                    }
                    for (DNSRecord answer : answers) {
                        if (answer != null) {
                            out = this.addAnswer(out, _in, answer);

                        }
                    }
                    if (!out.isEmpty()) this.getDns().send(out);
                }
                // this.cancel();
            } catch (Throwable e) {
                logger.log(Level.WARNING, this.getName() + "run() exception ", e);
                this.getDns().close();
            }
        }
    }
}