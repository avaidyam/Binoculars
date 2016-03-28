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
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.remoting.encoding.SerializerType;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.util.Log;
import org.nustaq.serialization.FSTConfiguration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class NucleusServer {
	
	protected NucleusServerConnector connector;
	protected Nucleus facade;
	AtomicInteger pollerCount = new AtomicInteger(0);
	protected ThreadLocal<RemotePolling> poller = new ThreadLocal<RemotePolling>() {
		@Override
		protected RemotePolling initialValue() {
			if ( pollerCount.get() > 0 ) {
				System.out.println("more than one poller started. used poller from wrong thread ?");
				Thread.dumpStack();
			}
			pollerCount.incrementAndGet();
			return new RemotePolling();
		}
	};
	
	protected Coding coding;
	protected FSTConfiguration conf; // parent conf
	
	public NucleusServerConnector getConnector() {
		return connector;
	}
	
	public NucleusServer(NucleusServerConnector connector, Nucleus facade, Coding coding) throws Exception {
		this.facade = facade;
		this.connector = connector;
		if ( coding == null )
			coding = new Coding(SerializerType.FSTSer);
		this.coding = coding;
		conf = coding.createConf();
		conf.setName("MAINCONFIG");
		RemoteRegistry.registerDefaultClassMappings(conf);
		if ( coding.getCrossPlatformShortClazzNames() != null ) {
			conf.registerCrossPlatformClassMappingUseSimpleName(coding.getCrossPlatformShortClazzNames());
		}
	}
	
	public void start() throws Exception {
		start(null);
	}
	
	public void start(Consumer<Nucleus> disconnectHandler) throws Exception {
		connector.connect(facade, writesocket -> {
			AtomicReference<ObjectSocket> socketRef = new AtomicReference<>(writesocket);
			RemoteRegistry reg = new RemoteRegistry( conf.deriveConfiguration(), coding) {
				@Override
				public Nucleus getFacadeProxy() {
					return facade;
				}
				
				@Override
				public AtomicReference<ObjectSocket> getWriteObjectSocket() {
					return socketRef;
				}
			};
			reg.setDisconnectHandler(disconnectHandler);
			writesocket.setConf(reg.getConf());
			Nucleus.current(); // ensure running in nuclei thread
			poller.get().scheduleSendLoop(reg);
			reg.setFacadeNucleus(facade);
			reg.publishNucleus(facade);
			reg.setServer(this);
			Log.i(this.toString(), "connected a client with registry " + System.identityHashCode(reg));
			return new ObjectSink() {
				
				@Override
				public void receiveObject(ObjectSink sink, Object received, List<Future> createdFutures) {
					try {
						reg.receiveObject(socketRef.get(), sink, received, createdFutures);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				@Override
				public void sinkClosed() {
					reg.setTerminated(true);
					reg.cleanUp();
				}
			};
		});
	}
	
	public Future close() {
		return connector.closeServer();
	}
	
	public Nucleus getFacade() {
		return facade;
	}
	
}