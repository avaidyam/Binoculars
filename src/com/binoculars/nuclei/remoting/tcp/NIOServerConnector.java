
package com.binoculars.nuclei.remoting.tcp;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.NucleusServer;
import com.binoculars.nuclei.remoting.base.NucleusServerConnector;
import com.binoculars.nuclei.remoting.base.ObjectSink;
import com.binoculars.nuclei.remoting.base.ObjectSocket;
import com.binoculars.nuclei.remoting.encoding.Coding;
import com.binoculars.asyncio.AsyncServerSocket;
import com.binoculars.asyncio.ObjectAsyncSocketConnection;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;

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
    public void connect(Nucleus facade, Function<ObjectSocket, ObjectSink> factory) throws Exception {
        connect( port, (key,channel) -> {
            MyObjectAsyncSocketConnection sc = new MyObjectAsyncSocketConnection(key,channel);
            ObjectSink sink = factory.apply(sc);
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

    static class MyObjectAsyncSocketConnection extends ObjectAsyncSocketConnection {

        ObjectSink sink;

        public MyObjectAsyncSocketConnection(SelectionKey key, SocketChannel chan) {
            super(key, chan);
        }

        public void init( ObjectSink sink ) { this.sink = sink; }

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
