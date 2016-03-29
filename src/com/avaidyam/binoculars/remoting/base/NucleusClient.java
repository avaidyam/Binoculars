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

package com.avaidyam.binoculars.remoting.base;

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.remoting.encoding.SerializerType;
import com.avaidyam.binoculars.scheduler.RemoteScheduler;
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.future.Future;
import org.nustaq.serialization.util.FSTUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class NucleusClient<T extends Nucleus> {

	protected ConnectibleNucleus.NucleusClientConnector client;
	protected Class<T> facadeClass;
	protected Coding coding;

	protected ThreadLocal<RemotePolling> poller = new ThreadLocal<RemotePolling>() {
		@Override
		protected RemotePolling initialValue() {
			return new RemotePolling();
		}
	};

	public NucleusClient(ConnectibleNucleus.NucleusClientConnector client, Class<T> facadeClass, Coding coding) {
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

	public Future<T> connect(int qsiz, Consumer<T> discon) {
		CompletableFuture<T> result = new CompletableFuture<>();
		try {
			client.connect(writesocket -> {
				Nucleus facadeProxy = Nucleus.of(facadeClass, new RemoteScheduler(qsiz));
				facadeProxy.__remoteId = 1;

				AtomicReference<ObjectFlow.Source> socketRef = new AtomicReference<>(writesocket);
				RemoteRegistry reg = new RemoteRegistry(coding) {
					@Override
					public Nucleus getFacadeProxy() {
						return facadeProxy;
					}

					@Override
					public AtomicReference<ObjectFlow.Source> getWriteObjectSocket() {
						return socketRef;
					}
				};
				reg.setDisconnectHandler((Consumer<Nucleus>)discon);
				if(coding.getCrossPlatformShortClazzNames() != null)
					reg.getConf().registerCrossPlatformClassMappingUseSimpleName(coding.getCrossPlatformShortClazzNames());
				writesocket.setConf(reg.getConf());

				Nucleus.current(); // ensure running in nuclei thread

				ObjectFlow.Sink objectSink = new ObjectFlow.Sink() {
					@Override
					public void receiveObject(ObjectFlow.Sink sink, Object received, List<Future> createdFutures) {
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
