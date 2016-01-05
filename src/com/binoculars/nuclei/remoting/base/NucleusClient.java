package com.binoculars.nuclei.remoting.base;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.scheduler.RemoteScheduler;
import com.binoculars.nuclei.remoting.encoding.Coding;
import com.binoculars.nuclei.remoting.encoding.SerializerType;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
import org.nustaq.serialization.util.FSTUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class NucleusClient<T extends Nucleus> {

	protected NucleusClientConnector client;
	protected Class<T> facadeClass;
	protected Coding coding;

	protected ThreadLocal<RemotePolling> poller = new ThreadLocal<RemotePolling>() {
		@Override
		protected RemotePolling initialValue() {
			return new RemotePolling();
		}
	};

	public NucleusClient(NucleusClientConnector client, Class<T> facadeClass, Coding coding) {
		this.facadeClass = facadeClass;
		this.client = client;
		this.coding = coding;
		if(this.coding == null) {
			this.coding = new Coding(SerializerType.FSTSer);
		}
	}

	public Future<T> connect() {
		return connect(RemoteScheduler.DEFQSIZE, null);
	}

	public Future<T> connect(int qsiz) {
		return connect(qsiz, null);
	}

	public Future<T> connect(int qsiz, Consumer<Nucleus> discon) {
		CompletableFuture<T> result = new CompletableFuture<>();
		try {
			client.connect(writesocket -> {
				Nucleus facadeProxy = Nucleus.of(facadeClass, new RemoteScheduler(qsiz));
				facadeProxy.__remoteId = 1;

				AtomicReference<ObjectSocket> socketRef = new AtomicReference<>(writesocket);
				RemoteRegistry reg = new RemoteRegistry(coding) {
					@Override
					public Nucleus getFacadeProxy() {
						return facadeProxy;
					}

					@Override
					public AtomicReference<ObjectSocket> getWriteObjectSocket() {
						return socketRef;
					}
				};
				reg.setDisconnectHandler(discon);
				if(coding.getCrossPlatformShortClazzNames() != null)
					reg.getConf().registerCrossPlatformClassMappingUseSimpleName(coding.getCrossPlatformShortClazzNames());
				writesocket.setConf(reg.getConf());

				Nucleus.current(); // ensure running in nuclei thread

				ObjectSink objectSink = new ObjectSink() {
					@Override
					public void receiveObject(ObjectSink sink, Object received, List<Future> createdFutures) {
						try {
							reg.receiveObject(socketRef.get(), sink, received, createdFutures);
						} catch(Exception e) {
							FSTUtil.rethrow(e);
						}
					}

					@Override
					public void sinkClosed() {
						reg.setTerminated(true);
						reg.cleanUp();
					}
				};
				reg.registerRemoteRefDirect(facadeProxy);
				poller.get().scheduleSendLoop(reg).then(objectSink::sinkClosed);
				result.resolve((T)facadeProxy);
				return objectSink;
			});
		} catch(Exception e) {
			if(!result.isComplete())
				result.reject(e);
			else
				e.printStackTrace();
		}
		return result;
	}

}
