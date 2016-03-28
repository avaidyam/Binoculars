package com.binoculars.nuclei.remoting.base;

import com.binoculars.nuclei.*;
import com.binoculars.nuclei.Exceptions;
import com.binoculars.nuclei.scheduler.*;
import com.binoculars.nuclei.remoting.CallEntry;
import com.binoculars.nuclei.remoting.encoding.*;
import com.binoculars.future.*;
import com.binoculars.util.Log;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.util.FSTUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 
 * Manages mapping of remote refs and callbacks. E.g. if an nuclei ref or callback or spore is sent to
 * a remote nuclei, during serialization RemoteRegistry generates and maps the callback id's required
 * to later on route incoming messages from remote to the appropriate local instances
 */
public abstract class RemoteRegistry implements RemoteConnection {
	
	public static final Object OUT_OF_ORDER_SEQ = "OOOS";
	public static int MAX_BATCH_CALLS = 500;
	private NucleusServer server;
	
	public static void registerDefaultClassMappings(FSTConfiguration conf) {
		conf.registerCrossPlatformClassMapping(new String[][]{
				{"call", RemoteCallEntry.class.getName()},
				{"cbw", SignalWrapper.class.getName()}
		});
	}
	
	protected FSTConfiguration conf;
	protected RemoteScheduler scheduler = new RemoteScheduler(); // unstarted thread dummy
	// holds published actors, futures and callbacks of this process
	protected AtomicInteger nucleiIdCount = new AtomicInteger(0);
	protected ConcurrentHashMap<Integer, Object> publishedNucleusMapping = new ConcurrentHashMap<>();
	protected ConcurrentHashMap<Object, Integer> publishedNucleusMappingReverse = new ConcurrentHashMap<>();
	// have disabled dispacther thread
	protected ConcurrentLinkedQueue<Nucleus> remoteNuclei = new ConcurrentLinkedQueue<>();
	protected ConcurrentHashMap<Integer,Nucleus> remoteNucleusSet = new ConcurrentHashMap<>();
	protected volatile boolean terminated = false;
	protected BiFunction<Nucleus,String,Boolean> remoteCallInterceptor =
			(a,methodName) -> {
				Method method = a.__getCachedMethod(methodName, a);
				if ( method == null || method.getAnnotation(Domain.Local.class) != null ) {
					return false;
				}
				return true;
			};
	protected Consumer<Nucleus> disconnectHandler;
	protected boolean isObsolete;
	private Nucleus facadeNucleus;
	
	public RemoteRegistry(FSTConfiguration conf, Coding coding) {
		this.conf = conf;
		configureSerialization(coding);
	}
	
	public RemoteRegistry(Coding code) {
		if ( code == null )
			code = new Coding(SerializerType.FSTSer);
		conf = code.createConf();
		registerDefaultClassMappings(conf);
		configureSerialization(code);
	}
	
	public BiFunction<Nucleus, String, Boolean> getRemoteCallInterceptor() {
		return remoteCallInterceptor;
	}
	
	public void setRemoteCallInterceptor(BiFunction<Nucleus, String, Boolean> remoteCallInterceptor) {
		this.remoteCallInterceptor = remoteCallInterceptor;
	}
	
	protected void configureSerialization(Coding code) {
		conf.registerSerializer(Nucleus.class,new NucleusSerializer(this),true);
		conf.registerSerializer(SignalWrapper.class, new SignalSerializer(this), true);
		conf.registerSerializer(Spore.class, new SporeSerializer(), true);
		conf.registerClass(RemoteCallEntry.class);
		conf.registerSerializer(CompletableFuture.Timeout.class, new TimeoutSerializer(), false);
	}
	
	public Nucleus getPublishedNucleus(int id) {
		return (Nucleus) publishedNucleusMapping.get(id);
	}
	
	public Signal getPublishedCallback(int id) {
		return (Signal) publishedNucleusMapping.get(id);
	}
	
	public RemoteScheduler getScheduler() {
		return scheduler;
	}
	
	public ConcurrentLinkedQueue<Nucleus> getRemoteNuclei() {
		return remoteNuclei;
	}
	
	public boolean isTerminated() {
		return terminated;
	}
	
	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}
	
	public int publishNucleus(Nucleus act) {
		Integer integer = publishedNucleusMappingReverse.get(act.getNucleusRef());
		if ( integer == null ) {
			integer = nucleiIdCount.incrementAndGet();
			publishNucleusDirect(integer, act);
		}
		return integer;
	}
	
	private void publishNucleusDirect(Integer integer, Nucleus act) {
		publishedNucleusMapping.put(integer, act.getNucleusRef());
		publishedNucleusMappingReverse.put(act.getNucleusRef(), integer);
		act.__addRemoteConnection(this);
	}
	
	/**
	 * remove current <remoteId,nuclei> mappings if present.
	 * return map containing removed mappings (for reconnection)
	 *  @param act
	 *
	 */
	public void unpublishNucleus(Nucleus act) {
		Integer integer = publishedNucleusMappingReverse.get(act.getNucleusRef());
		if ( integer != null ) {
			Log.d(this.toString(), "" + act.getClass().getSimpleName() + " unpublished");
			publishedNucleusMapping.remove(integer);
			publishedNucleusMappingReverse.remove(act.getNucleusRef());
			act.__removeRemoteConnection(this);
			if ( act instanceof RemotedNucleus) {
				((RemotedNucleus) act).hasBeenUnpublished();
			}
		}
	}
	
	public int registerPublishedCallback(Signal cb) {
		Integer integer = publishedNucleusMappingReverse.get(cb);
		if ( integer == null ) {
			integer = nucleiIdCount.incrementAndGet();
			publishedNucleusMapping.put(integer, cb);
			publishedNucleusMappingReverse.put(cb, integer);
		}
		return integer;
	}
	
	public void removePublishedObject(int receiverKey) {
		Object remove = publishedNucleusMapping.remove(receiverKey);
		if ( remove != null ) {
			publishedNucleusMappingReverse.remove(remove);
		}
	}
	
	public void registerRemoteRefDirect(Nucleus act) {
		act = act.getNucleusRef();
		remoteNucleusSet.put(act.__remoteId,act);
		remoteNuclei.add(act);
		act.__clientConnection = this;
		act.__addStopHandler((a, err) -> {
			remoteRefStopped((Nucleus) a);
		});
	}
	
	public Nucleus registerRemoteNucleusRef(Class nucleusClz, int remoteId, Object client) {
		Nucleus nucleusRef = remoteNucleusSet.get(remoteId);
		if ( nucleusRef == null ) {
			Nucleus res = Nucleus.of(nucleusClz, getScheduler());
			res.__remoteId = remoteId;
			remoteNucleusSet.put(remoteId,res);
			remoteNuclei.add(res);
			res.__addStopHandler((a, err) -> {
				remoteRefStopped((Nucleus) a);
			});
			res.__clientConnection = this;
			return res;
		}
		return nucleusRef;
	}
	
	/**
	 * warning: MThreaded
	 * @param nucleus
	 */
	protected void remoteRefStopped(Nucleus nucleus) {
		removeRemoteNucleus(nucleus);
		nucleus.getNucleusRef().__stopped = true;
		nucleus.getNucleus().__stopped = true;
	}
	
	public void stopRemoteRefs() {
		new ArrayList<>(remoteNuclei).forEach((a) -> {
			if (disconnectHandler != null)
				disconnectHandler.accept(a);
			//don't call remoteRefStopped here as its designed to be overridden
			try {
				removeRemoteNucleus(a);
			} catch (Exception e) {
				Log.w(this.toString(), "", e);
			}
			a.getNucleusRef().__stopped = true;
			if (a.getNucleus() != null)
				a.getNucleus().__stopped = true;
		});
	}
	
	protected void removeRemoteNucleus(Nucleus act) {
		remoteNucleusSet.remove(act.__remoteId);
		remoteNuclei.remove(act);
		try {
			act.__stop();
		} catch (Exceptions.InternalNucleusStoppedException ase) {}
	}
	
	/**
	 * process a remote call entry or an array of remote call entries.
	 *
	 * @param responseChannel - writer required to route callback messages
	 * @param response
	 * @param createdFutures - can be null. Contains futures created by the submitted callsequence
	 * @return
	 * @throws Exception
	 */
	public boolean receiveObject(ObjectSocket responseChannel, ObjectSink receiver, Object response, List<Future> 
			createdFutures) throws Exception {
		if ( response == RemoteRegistry.OUT_OF_ORDER_SEQ )
			return false;
		if ( response instanceof Object[] ) { // bundling. last element contains sequence
			Object arr[] = (Object[]) response;
			boolean hadResp = false;
			int max = arr.length - 1;
			int inSequence = 0;
			if (arr[max] instanceof Number == false) // no sequencing
				max++;
			else
				inSequence = ((Number) arr[max]).intValue();
			
			for (int i = 0; i < max; i++) {
				Object resp = arr[i];
				if (resp instanceof RemoteCallEntry == false) {
					if ( resp != null && ! "SP".equals(resp) ) // FIXME: hack for short polling
						Log.e(this.toString(), "unexpected response:" + resp); // fixme
					hadResp = true;
				} else if (processRemoteCallEntry(responseChannel, (RemoteCallEntry) resp, createdFutures))
					hadResp = true;
			}
			return hadResp;
		} else {
			if (response instanceof RemoteCallEntry == false) {
				if ( response != null && ! "SP".equals(response))
					Log.e(this.toString(), "unexpected response:" + response); // fixme
				return true;
			}
			if (processRemoteCallEntry(responseChannel, (RemoteCallEntry) response, createdFutures)) return true;
		}
		return false;
	}
	
	// dispatch incoming remotecalls
	protected boolean processRemoteCallEntry(ObjectSocket objSocket, RemoteCallEntry response, List<Future> createdFutures
	) throws Exception {
		RemoteCallEntry read = response;
		boolean isContinue = read.getArgs().length > 1 && Signal.CONT.equals(read.getArgs()[1]);
		if ( isContinue )
			read.getArgs()[1] = Signal.CONT; // enable ==
		if (read.getQueue() == read.MAILBOX) {
			Nucleus targetNucleus = getPublishedNucleus(read.getReceiverKey());
			if (targetNucleus ==null) {
				Log.e(this.toString(), "registry:" + System.identityHashCode(this) + " no nuclei found for key " + read);
				return true;
			}
			if (targetNucleus.isStopped() || targetNucleus.getScheduler() == null ) {
				Log.e(this.toString(), "nuclei found for key " + read + " is stopped and/or has no scheduler set");
				receiveCBResult(objSocket, read.getFutureKey(), null, Exceptions.InternalNucleusStoppedException.INSTANCE);
				return true;
			}
			if (remoteCallInterceptor != null && !remoteCallInterceptor.apply(targetNucleus,read.getMethod())) {
				Log.w(this.toString(), "remote message blocked by securityinterceptor " + targetNucleus.getClass().getName() +
						" " + read.getMethod());
				return false;
			}
			try {
				Object future = targetNucleus.getScheduler().enqueueCallFromRemote(this, null, targetNucleus, read.getMethod(), read.getArgs(), false);
				if ( future instanceof Future) {
					CompletableFuture p = null;
					if ( createdFutures != null ) {
						p = new CompletableFuture();
						createdFutures.add(p);
					}
					final CompletableFuture finalP = p;
					Thread debug = Thread.currentThread();
					((Future) future).then( (Signal)(r, e) -> {
						try {
							Thread debug1 = Thread.currentThread();
							Thread debug2 = debug;
							receiveCBResult(objSocket, read.getFutureKey(), r, e);
							if ( finalP != null )
								finalP.complete();
						} catch (Exception ex) {
							Log.w(this.toString(), "", ex);
						}
					});
				}
			} catch (Throwable th) {
				Log.w(this.toString(), "", th);
				if ( read.getFutureKey() > 0 ) {
					receiveCBResult(objSocket, read.getFutureKey(), null, FSTUtil.toString(th));
				} else {
					FSTUtil.<RuntimeException>rethrow(th);
				}
			}
		} else if (read.getQueue() == read.CBQ) {
			Signal publishedSignal = getPublishedCallback(read.getReceiverKey());
			if ( publishedSignal == null ) {
				if ( read.getArgs() != null && read.getArgs().length == 2 && read.getArgs()[1] instanceof Exceptions.InternalNucleusStoppedException) {
					// FIXME: this might happen frequently as messages are in flight.
					// FIXME: need a better way to handle this. Frequently it is not an error.
					Log.w(this.toString(), "call to stopped remote nuclei");
				} else
					Log.w(this.toString(),
							"Publisher already deregistered, set error to 'Nucleus.CONT' in order to signal more messages will be sent");
			} else {
				publishedSignal.complete(read.getArgs()[0], read.getArgs()[1]); // is a wrapper enqueuing in caller
				if (!isContinue)
					removePublishedObject(read.getReceiverKey());
			}
		}
		return createdFutures != null && createdFutures.size() > 0;
	}
	
	/**
	 * cleanup after (virtual) connection close
	 */
	public void cleanUp() {
		conf.clearCaches();
		stopRemoteRefs();
		publishedNucleusMappingReverse.keySet().forEach((act) -> {
			if (act instanceof Nucleus)
				unpublishNucleus((Nucleus) act);
		});
		getFacadeProxy().__removeRemoteConnection(this);
	}
	
	protected void closeRef(CallEntry ce, ObjectSocket chan) throws IOException {
		if (ce.getTargetNucleus().getNucleusRef() == getFacadeProxy().getNucleusRef() ) {
			// invalidating connections should cleanup all refs
			chan.close();
		} else {
			removeRemoteNucleus(ce.getTargetNucleus());
		}
	}
	
	protected void writeObject(ObjectSocket chan, RemoteCallEntry rce) throws Exception {
		try {
			chan.writeObject(rce);
		} catch (Exception e) {
			Log.d(this.toString(), "a connection closed '" + e.getMessage() + "', terminating registry");
			setTerminated(true);
			cleanUp();
		}
	}
	
	public void receiveCBResult(ObjectSocket chan, int id, Object result, Object error) throws Exception {
		if (facadeNucleus !=null) {
			Thread debug = facadeNucleus.getCurrentDispatcher();
			if ( Thread.currentThread() != facadeNucleus.getCurrentDispatcher() ) {
				facadeNucleus.execute( () -> {
					try {
						if ( Thread.currentThread() != debug )
							System.out.println("??");
						receiveCBResult(chan,id,result, error);
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
				return;
			}
		}
		RemoteCallEntry rce = new RemoteCallEntry(0, id, null, new Object[] {result,error});
		rce.setQueue(rce.CBQ);
		writeObject(chan, rce);
	}
	
	public void close() {
		try {
			getWriteObjectSocket().get().flush();
		} catch (Exception e) {
			Log.w(this.toString(), "", e);
		}
		cleanUp();
	}
	
	public FSTConfiguration getConf() {
		return conf;
	}
	
	public abstract Nucleus getFacadeProxy();
	
	public void setDisconnectHandler(Consumer<Nucleus> disconnectHandler) {
		this.disconnectHandler = disconnectHandler;
	}
	
	public Consumer<Nucleus> getDisconnectHandler() {
		return disconnectHandler;
	}
	
	public void setClassLoader(ClassLoader l) {
		conf.setClassLoader(l);
	}
	
	public int getRemoteId(Nucleus act) {
		Integer integer = publishedNucleusMappingReverse.get(act.getNucleusRef());
		return integer == null ? -1 : integer;
	}
	
	/**
	 * poll remote nuclei proxies and send. return true if there was at least one message
	 * @param chanHolder
	 */
	public boolean pollAndSend2Remote(AtomicReference<ObjectSocket> chanHolder) throws Exception {
		ObjectSocket chan = chanHolder.get();
		if ( chan == null || ! chan.canWrite() )
			return false;
		boolean hadAnyMsg = false;
		ArrayList<Nucleus> toRemove = null;
		int sumQueued;
		int fullqueued = 0;
		do {
			sumQueued = 0;
			for (Iterator<Nucleus> iterator = remoteNuclei.iterator(); iterator.hasNext(); ) {
				Nucleus remoteNucleus = iterator.next();
				boolean cb = false; // true; FIXME
				CallEntry ce = (CallEntry) remoteNucleus.__cbQueue.poll();
				if ( ce == null ) {
					cb = false;
					ce = (CallEntry) remoteNucleus.__mailbox.poll();
				}
				if ( ce != null) {
					if ( ce.getMethod().getName().equals("close") ) {
						closeRef(ce,chan);
					} else
					if ( ce.getMethod().getName().equals("asyncstop") ) {
						Log.e(this.toString(), "cannot stop remote actors");
					} else {
						int futId = 0;
						if (ce.hasFutureResult()) {
							futId = registerPublishedCallback(ce.getFutureCB());
						}
						try {
							RemoteCallEntry rce = new RemoteCallEntry(futId, remoteNucleus.__remoteId, ce.getMethod().getName(), ce.getArgs());
							rce.setQueue(cb ? rce.CBQ : rce.MAILBOX);
							writeObject(chan, rce);
							sumQueued++;
							hadAnyMsg = true;
						} catch (Exception ex) {
							chan.setLastError(ex);
							if (toRemove == null)
								toRemove = new ArrayList();
							toRemove.add(remoteNucleus);
							remoteNucleus.__stop();
							Log.i(this.toString(), "connection closed", ex);
							break;
						}
					}
				}
			}
			if (toRemove!=null) {
				toRemove.forEach( (act) -> removeRemoteNucleus(act) );
			}
			fullqueued += sumQueued;
		} while ( sumQueued > 0 && fullqueued < MAX_BATCH_CALLS);
		chan.flush();
		return hadAnyMsg;
	}
	
	public abstract AtomicReference<ObjectSocket> getWriteObjectSocket();
	
	public boolean isObsolete() {
		return isObsolete;
	}
	
	/**
	 * give the application a way to explecitely flag a connection as obsolete
	 *
	 */
	public void setIsObsolete(boolean isObsolete) {
		this.isObsolete = isObsolete;
	}
	
	
	public int getRemoteNucleusSize() {
		return remoteNucleusSet.size();
	}
	
	public void setFacadeNucleus(Nucleus facadeNucleus) {
		this.facadeNucleus = facadeNucleus;
	}
	
	public Nucleus getFacadeNucleus() {
		return facadeNucleus;
	}
	
	public void setServer(NucleusServer server) {
		this.server = server;
	}
	
	public NucleusServer getServer() {
		return server;
	}
	
	@Override
	public Future closeNetwork() {
		if ( server != null )
			return server.close();
		else {
			Log.w(null, "failed closing underlying network connection as server is null");
			return new CompletableFuture<>(null,"server is null");
		}
	}
}
