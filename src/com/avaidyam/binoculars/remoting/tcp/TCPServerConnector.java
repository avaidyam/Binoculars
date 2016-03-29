
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
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.remoting.base.*;
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 *
 * Publishes an nuclei as a server via blocking TCP. Requires one thread for each client connecting.
 *
 */
public class TCPServerConnector implements NucleusServerConnector {

    public static int DELAY_MS_TILL_CLOSE = 2000;

    public static AtomicInteger numberOfThreads = new AtomicInteger(0);

    public static CompletableFuture<NucleusServer> Publish(Nucleus facade, int port, Coding coding) {
        return Publish(facade,port,coding,null);
    }

    public static CompletableFuture<NucleusServer> Publish(Nucleus facade, int port, Coding coding, Consumer<Nucleus> disconnectCB) {
        CompletableFuture finished = new CompletableFuture();
        try {
            NucleusServer publisher = new NucleusServer(new TCPServerConnector(port), facade, coding);
            facade.execute(() -> {
                try {
                    publisher.start(disconnectCB);
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
    protected ServerSocket acceptSocket;
    protected ConcurrentLinkedQueue<Socket> clientSockets = new ConcurrentLinkedQueue<>();

    public TCPServerConnector(int port) {
        super();
        this.port = port;
    }

    @Override
    public void connect(Nucleus facade, Function<ObjectFlow.Source, ObjectFlow.Sink> factory) throws Exception {
        CompletableFuture p = new CompletableFuture();
        new Thread( () -> acceptLoop(facade,port,factory,p), "acceptor thread "+port ).start();
        p.await();
    }

    protected CompletableFuture acceptLoop(Nucleus facade, int port, Function<ObjectFlow.Source, ObjectFlow.Sink> factory, CompletableFuture p) {
        try {
            numberOfThreads.incrementAndGet();
            acceptSocket = new ServerSocket(port);
            p.complete();
            while (!acceptSocket.isClosed()) {
                Socket clientSocket = acceptSocket.accept();
                clientSockets.add(clientSocket);
                MyTCPSource objectSocket = new MyTCPSource(clientSocket);
                facade.execute(() -> {
                    ObjectFlow.Sink sink = factory.apply(objectSocket);
                    new Thread(() -> {
                        try {
                            numberOfThreads.incrementAndGet();
                            while (!clientSocket.isClosed()) {
                                try {
                                    Object o = objectSocket.readObject();
                                    sink.receiveObject(o, null);
                                } catch (Exception e) {
                                    if (e instanceof EOFException == false && e instanceof SocketException == false)
                                        Log.w(this.toString(), "", e);
                                    try {
                                        clientSocket.close();
                                    } catch (IOException e1) {
                                        Log.w(this.toString(), "", e1);
                                    }
                                }
                            }
                            sink.sinkClosed();
                        } finally {
                            clientSockets.remove(clientSocket);
                            numberOfThreads.decrementAndGet();
                        }
                    }, "tcp receiver").start();
                });
            }
        } catch (Exception e) {
            Log.i(this.toString(), e.getMessage());
            if ( ! p.isComplete() )
                p.reject(e);
        } finally {
            if ( ! p.isComplete() )
                p.reject("conneciton failed");
            try {
                acceptSocket.close();
            } catch (IOException e) {
                Log.w(this.toString(), "", e);
            }
            numberOfThreads.decrementAndGet();
        }
        return p;
    }

    @Override
    public Future closeServer() {
        try {
            clientSockets.forEach( socket -> {
                // need to give time for flush. No way to determine wether buffers are out =>
                // risk of premature close + message loss
                Nucleus.submitDelayed(DELAY_MS_TILL_CLOSE, () -> {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.w(this.toString(), "", e);
                    }
                });
            });
            acceptSocket.close();
        } catch (IOException e) {
            return new CompletableFuture<>(null,e);
        }
        return new CompletableFuture<>(null);
    }

    static class MyTCPSource extends TCPObjectSocket implements ObjectFlow.Source {

        public MyTCPSource(Socket socket) throws IOException {
            super(socket,null);
        }

    }
}
