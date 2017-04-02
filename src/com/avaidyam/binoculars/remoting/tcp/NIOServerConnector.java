
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

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.asyncio.ObjectAsyncSourceConnection;
import com.avaidyam.binoculars.remoting.base.*;
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.remoting.asyncio.AsyncServerSocket;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 *
 * Publishes an nuclei as a server using non-blocking IO backed TCP.
 * The number of threads does not increase with the number of clients.
 *
 */
public class NIOServerConnector extends AsyncServerSocket implements NucleusServerConnector {

    public static Future<NucleusServer> Publish(Nucleus facade, int port, Coding coding) {
        return Publish(facade,port,coding,null);
    }

    public static CompletableFuture<NucleusServer> Publish(Nucleus facade, int port, Coding coding, Consumer<Nucleus> disconnectHandler) {
        CompletableFuture finished = new CompletableFuture();
        try {
            NucleusServer publisher = new NucleusServer(new NIOServerConnector(port), facade, coding);
            facade.execute(() -> {
                try {
                    publisher.start(disconnectHandler);
                    finished.resolve(publisher);
                } catch (Exception e) {
                    finished.reject(e);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new CompletableFuture(null,e);
        }
        return finished;
    }

    int port;

    public NIOServerConnector(int port) {
        super();
        this.port = port;
    }

    @Override
    public void connect(Nucleus facade, Function<ObjectFlow.Source, ObjectFlow.Sink> factory) throws Exception {
        connect( port, (key,channel) -> {
            MyObjectAsyncSourceConnection sc = new MyObjectAsyncSourceConnection(key,channel);
            ObjectFlow.Sink sink = factory.apply(sc);
            sc.init(sink);
            return sc;
        });
    }

    @Override
    public Future closeServer() {
        try {
            super.close();
        } catch (IOException e) {
            return new CompletableFuture<>(null,e);
        }
        return new CompletableFuture<>(null);
    }

    static class MyObjectAsyncSourceConnection extends ObjectAsyncSourceConnection {

        ObjectFlow.Sink sink;

        public MyObjectAsyncSourceConnection(SelectionKey key, SocketChannel chan) {
            super(key, chan);
        }

        public void init( ObjectFlow.Sink sink ) { this.sink = sink; }

        @Override public void receivedObject(Object o) { sink.receiveObject(o, null); }

        @Override
        public void closed(Throwable ioe) {
            isClosed = true;
            sink.sinkClosed();
        }

        public void close() throws IOException {
            chan.close();
            sink.sinkClosed();
        }

    }
}
