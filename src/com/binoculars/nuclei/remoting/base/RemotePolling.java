package com.binoculars.nuclei.remoting.base;


import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
import com.binoculars.util.Log;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * polls queues of remote nuclei proxies and serializes messages to their associated object sockets.
 *
 * Note for transparent websocket/longpoll reconnect:
 * Terminated / Disconnected remote actors (registries) are removed from the entry list,
 * so regular nuclei messages sent to a terminated remote nuclei queue up in its mailbox.
 * Callbacks/Future results from exported callbacks/futures still reach the object socket
 * as these are redirected directly inside serializers. Those queue up in the webobjectsocket's list,
 * as flush is not called anymore because of removement from SendLoop list.
 *
 * In case of TCP remoting, an NucleusStopped Exception is thrown if an attempt is made to send a message to a
 * disconnected remote nuclei.
 *
 * in short: regular messages to disconected remote actors queue up in mailbox, callbacks in object socket buffer
 *
 * For typical client/server alike use cases, there are never remote references as most client api's do
 * not support handing out client remoterefs to servers (javascript, kontraktor bare)
 *
 */
public class RemotePolling implements Runnable {
	
	ArrayList<ScheduleEntry> sendJobs = new ArrayList<>();
	
	AtomicInteger instanceCount = new AtomicInteger(0);
	public RemotePolling() {
		instanceCount.incrementAndGet();
	}
	
	/**
	 * return a future which is completed upon connection close
	 *
	 * @param reg
	 *
	 * @return future completed upon termination of scheduling (disconnect)
	 *
	 */
	public Future scheduleSendLoop(RemoteRegistry reg) {
		CompletableFuture promise = new CompletableFuture();
		sendJobs.add(new ScheduleEntry(reg, promise));
		synchronized (this) {
			if ( ! loopStarted ) {
				loopStarted = true;
				Nucleus.current().execute(this);
			}
		}
		return promise;
	}
	
	boolean loopStarted = false;
	boolean underway = false;
	static AtomicInteger scansPersec = new AtomicInteger(0);
	Thread pollThread;
	
	int remoteRefCounter = 0; // counts active remote refs, if none backoff remoteref polling massively
	public void run() {
		pollThread = Thread.currentThread();
		if ( underway )
			return;
		underway = true;
		try {
			int count = 1;
			while( count > 0 ) {
				count = onePoll();
				if ( sendJobs.size() > 0 ) {
					if ( count > 0 ) {
//                        Nucleus.current().yield();
					}
					else {
						if ( remoteRefCounter == 0 ) // no remote actors registered
						{
							Nucleus.current().delayed(100, this); // backoff massively
						} else {
							Nucleus.current().delayed(1, this); // backoff a bit (remoteactors present, no messages)
						}
					}
				} else {
					// no schedule entries (== no clients)
					Nucleus.current().delayed(100, this );
				}
			}
		} finally {
			underway = false;
		}
	}
	
	protected int onePoll() {
		int count = 1;
		int maxit = 1;
		remoteRefCounter = 0;
		//while ( maxit > 0 && count > 0)
		{
			count = 0;
			scansPersec.incrementAndGet();
			for (int i = 0; i < sendJobs.size(); i++) {
				ScheduleEntry entry = sendJobs.get(i);
				if ( entry.reg.getRemoteNucleusSize() > 0 ) {
					remoteRefCounter++;
				}
				if ( entry.reg.isTerminated() ) {
					terminateEntry(i, entry, "terminated", null );
					i--;
					continue;
				}
				try {
					if (entry.reg.pollAndSend2Remote(entry.reg.getWriteObjectSocket())) {
						count++;
					}
				} catch (Throwable e) {
					Log.d(this.toString(), e.toString());
					terminateEntry(i, entry, null, e);
					i--;
				}
			}
			maxit--;
		}
		
		return count;
	}
	
	protected void terminateEntry(int i, ScheduleEntry entry, Object res, Throwable e) {
		entry.reg.stopRemoteRefs();
		sendJobs.remove(i);
		entry.promise.complete(res,e);
	}
	
	public static class ScheduleEntry {
		public ScheduleEntry( RemoteRegistry reg, CompletableFuture promise) {
			this.reg = reg;
			this.promise = promise;
		}
		
		RemoteRegistry reg;
		Future promise;
	}
}
