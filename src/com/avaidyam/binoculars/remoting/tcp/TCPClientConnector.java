
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

package com.avaidyam.binoculars.remoting.tcp;

import com.avaidyam.binoculars.remoting.base.ConnectibleNucleus;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.base.ObjectFlow;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.util.Log;
import org.nustaq.serialization.util.FSTUtil;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 *
 */
public class TCPClientConnector implements ConnectibleNucleus.NucleusClientConnector {

    /**
     * used in most client and server connector implementations
     */
    static int OBJECT_MAX_BATCH_SIZE = 100;

    public static class RemotingHelper extends Nucleus<RemotingHelper> {}
    protected static AtomicReference<RemotingHelper> singleton =  new AtomicReference<>();

    /**
     * in case clients are connected from non nuclei world, provide a global nuclei(thread) for remote client processing
     * (=polling queues, encoding)
     */
    protected static RemotingHelper get() {
        synchronized (singleton) {
            if ( singleton.get() == null ) {
                singleton.set(Nucleus.of(RemotingHelper.class));
            }
            return singleton.get();
        }
    }

    protected int port;
    protected String host;
    protected MyTCPSource socket;
    protected Signal<ConnectibleNucleus.NucleusClientConnector> disconnectSignal;

    public TCPClientConnector(int port, String host, Signal<ConnectibleNucleus.NucleusClientConnector> disconnectSignal) {
        this.port = port;
        this.host = host;
        this.disconnectSignal = disconnectSignal;
    }

    @Override
    public Future connect(Function<ObjectFlow.Source, ObjectFlow.Sink> factory) throws Exception {
        CompletableFuture res = new CompletableFuture();
        socket = new MyTCPSource(host,port);
        ObjectFlow.Sink sink = factory.apply(socket);
        new Thread(() -> {
            res.complete();
            while (!socket.isClosed()) {
                try {
                    Object o = socket.readObject();
                    sink.receiveObject(o, null);
                } catch (Exception e) {
                    if (e instanceof EOFException == false && e instanceof SocketException == false )
                        Log.w(this.toString(), "", e);
                    else {
                        Log.w(this.toString(), e.getMessage());
                    }
                    try {
                        socket.close();
                    } catch (IOException e1) {
                        Log.w(this.toString(), e.getMessage());
                    }
                }
            }
            if ( disconnectSignal != null ) {
                disconnectSignal.complete(this,null);
            }
            sink.sinkClosed();
        }, "tcp client receiver").start();
        return res;
    }

	@Override
    public Future disconnect() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            return new CompletableFuture<>(e);
        }
        return new CompletableFuture<>();
    }

    static class MyTCPSource extends TCPObjectSocket implements ObjectFlow.Source {

        ArrayList objects = new ArrayList();

        public MyTCPSource(String host, int port) throws IOException {
            super(host, port);
        }

        @Override
        public void writeObject(Object toWrite) throws Exception {
            objects.add(toWrite);
            if (objects.size()>OBJECT_MAX_BATCH_SIZE) {
                flush();
            }
        }

        @Override
        public void flush() throws IOException {
            if ( objects.size() == 0 ) {
                return;
            }
            objects.add(0); // sequence
            Object[] objArr = objects.toArray();
            objects.clear();

            try {
                super.writeObject(objArr);
            } catch (Exception e) {
                FSTUtil.<RuntimeException>rethrow(e);
            }

            super.flush();
        }
    }

}
