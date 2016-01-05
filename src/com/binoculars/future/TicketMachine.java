package com.binoculars.future;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        signalFin.then(new Signal() {
            @Override
            public void complete(Object result, Object error) {
//                System.out.println("rec "+channelKey+" do remove+checknext");
                boolean remove = finalFutures.remove(ticket);
                if (!remove)
                    System.err.println("Error failed to remove " + channelKey);
                checkNext(channelKey, finalFutures, ticket);
            }

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

    static class Ticket {
        Future signalProcessingStart;
        Future signalProcessingFinished;
        Ticket(Future signalProcessingStart, Future signalProcessingFinished) {
            this.signalProcessingStart = signalProcessingStart;
            this.signalProcessingFinished = signalProcessingFinished;
        }
    }
}
