
package com.binoculars.nuclei.remoting.tcp;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.NucleusClientConnector;
import com.binoculars.nuclei.remoting.base.ObjectSink;
import com.binoculars.nuclei.remoting.base.ObjectSocket;
import com.binoculars.future.Signal;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
import com.binoculars.util.Log;
import org.nustaq.net.TCPObjectSocket;
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
public class TCPClientConnector implements NucleusClientConnector {

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
    protected MyTCPSocket socket;
    protected Signal<NucleusClientConnector> disconnectSignal;

    public TCPClientConnector(int port, String host, Signal<NucleusClientConnector> disconnectSignal) {
        this.port = port;
        this.host = host;
        this.disconnectSignal = disconnectSignal;
    }

    @Override
    public Future connect(Function<ObjectSocket, ObjectSink> factory) throws Exception {
        CompletableFuture res = new CompletableFuture();
        socket = new MyTCPSocket(host,port);
        ObjectSink sink = factory.apply(socket);
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
    public Future closeClient() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            return new CompletableFuture<>(e);
        }
        return new CompletableFuture<>();
    }

    static class MyTCPSocket extends TCPObjectSocket implements ObjectSocket {

        ArrayList objects = new ArrayList();

        public MyTCPSocket(String host, int port) throws IOException {
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
