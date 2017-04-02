/*
 * Copyright (c) 2016 Aditya Vaidyam
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.avaidyam.binoculars.future;

import com.avaidyam.binoculars.Log;
import com.avaidyam.binoculars.Nucleus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>
 * Single threaded, can be used from within one nuclei only !
 * <p>
 * Problem:
 * you complete a stream of events related to some items (e.g. UserSessions or Trades)
 * you need to do some asynchronous lookups during processing (e.g. query data async)
 * now you want to process parallel, but need to process events related to a single item
 * in order (e.g. want to process trades related to say BMW in the order they come in).
 * <p>
 * usage:
 * <p>
 * ticketMachine.getTicket( "BMW" ).then(
 * (endSignalfuture,e) -> {
 * <p>
 * .. wild async processing ..
 * <p>
 * endsignalFuture.complete("done",null); // will execute next event on bmw if present
 * });
 */
public class TicketMachine {

    HashMap<Object, List<Ticket>> tickets = new HashMap<>();

    public Future<Future> getTicket(final Object channelKey) {
        List<Ticket> futures = tickets.get(channelKey);
        if (futures == null) {
            futures = new ArrayList<>(3);
            tickets.put(channelKey, futures);
        }

        CompletableFuture<Object> signalFin = new CompletableFuture<>();
        Future signalStart = new CompletableFuture();
        final Ticket ticket = new Ticket(signalStart, signalFin);
        futures.add(ticket);

//        System.out.println("get ticket "+ticket+" "+Thread.currentThread().getName());

        final List<Ticket> finalFutures = futures;
        signalFin.then((Signal<Object>) (result, error) -> {
//                System.out.println("rec "+channelKey+" do remove+checknext");
			boolean remove = finalFutures.remove(ticket);
			if (!remove)
				System.err.println("Error failed to remove " + channelKey);
			checkNext(channelKey, finalFutures, ticket);
		});
        if (futures.size() == 1) { // this is the one and only call, start immediately
            signalStart.complete(signalFin, null);
        }
        return signalStart;
    }

    private void checkNext(Object channelKey, List<Ticket> futures, Ticket ticket) {
        if (futures.size() == 0) {
            tickets.remove(channelKey);
        } else {
            Ticket nextTicket = futures.get(0);
            nextTicket.signalProcessingStart.complete(nextTicket.signalProcessingFinished, null);
        }
    }

    public HashMap<Object, List<Ticket>> getTickets() {
        return tickets;
    }

    /**
     * enforce serial execution of asynchronous tasks. The 'toRun' closure must call '.signal()' on the given future
     * to signal his processing has finished and the next item locked on 'transactionKey' can be processed.
     *
     * @param transactionKey
     * @param toRun
     */
    public void serialOn(Nucleus n, Object transactionKey, Consumer<Future> toRun) {
        if (n.isProxy())
            throw new RuntimeException("cannot call on nuclei proxy object");
        this.getTicket(transactionKey).onResult(finSig -> {
            try {
                toRun.accept(finSig);
            } catch (Throwable th) {
                Log.w("TicketMachine", "", th);
            }
        });
    }

    static class Ticket {
        Future signalProcessingStart;
        Future signalProcessingFinished;
        Ticket(Future signalProcessingStart, Future signalProcessingFinished) {
            this.signalProcessingStart = signalProcessingStart;
            this.signalProcessingFinished = signalProcessingFinished;
        }
    }
}
