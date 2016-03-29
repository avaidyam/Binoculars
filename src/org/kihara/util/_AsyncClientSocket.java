
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

package org.kihara.util;

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.asyncio.AsyncSocketConnection;
import com.avaidyam.binoculars.asyncio.QueuingAsyncSocketConnection;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.util.Log;

import org.nustaq.offheap.BinaryQueue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;

/**
 *
 *
 * ALPHA has serious issues.
 *
 */
public class _AsyncClientSocket implements Runnable {

    SocketChannel channel;
    Selector selector;
    BiFunction<SelectionKey,SocketChannel,AsyncSocketConnection> connectionFactory;
    AsyncSocketConnection con;
    CompletableFuture connectFuture;

    public Future connect(String host, int port, BiFunction<SelectionKey,SocketChannel,AsyncSocketConnection> connectionFactory) {
        if ( connectFuture != null ) {
            throw new RuntimeException("illegal state, connect is underway");
        }
        connectFuture = new CompletableFuture<>();
        this.connectionFactory = connectionFactory;
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            selector = Selector.open();
            channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE );
            channel.connect(new InetSocketAddress(host, port));
            Nucleus.current().execute(this);
        } catch (Exception e) {
            connectFuture.reject(e);
            connectFuture = null;
        }
        return connectFuture;
    }

    @Override
    public void run() {
        boolean hadStuff = false;
        try {
            selector.selectNow();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            for (Iterator<SelectionKey> iterator = selectionKeys.iterator(); iterator.hasNext(); ) {
                SelectionKey key = iterator.next();
                if (key.isConnectable() && connectFuture != null ) {
                    boolean connected = channel.finishConnect();
                    con = connectionFactory.apply(key,channel);
                    iterator.remove();
                    connectFuture.complete();
                    connectFuture = null;
                }
                if ( con != null ) {
                    boolean wrote = false;
                    if (key.isWritable()) {
                        ByteBuffer writingBuffer = con.getWritingBuffer();
                        if ( writingBuffer != null ) {
                            int written = channel.write(writingBuffer);
                            if (written<0) {
                                wrote = true;
                                iterator.remove();
                                key.cancel();
                                // closed
                                con.writeFinished(new IOException("disconnected"));
                            } else
                            if ( writingBuffer.remaining() == 0) {
                                wrote = true;
                                iterator.remove();
                                con.writeFinished(null);
                            }
                        }
                    }
                    if (!wrote && key.isReadable()) {
                        hadStuff = true;
                        try {
                            if ( ! con.readData() ) {
                                iterator.remove();
                            }
                        } catch (Exception ioe) {
                            ioe.printStackTrace();
                            con.closed(ioe);
                            key.cancel();
                            try {
                                channel.close();
                            } catch (IOException e) {
                                Log.w(this.toString(), "", e);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(this.toString(), "", e);
            new CompletableFuture<>(null, e); // FIXME: uh...
            try {
                close();
            } catch (IOException e1) {
                Log.w(this.toString(), "", e);
            }
        }
        if ( ! isClosed() ) {
            if ( hadStuff ) {
                Nucleus.current().execute(this);
            } else {
                Nucleus.current().delayed( 2, this );
            }
        } else
            System.out.println("loop terminated");
    }

    public boolean isClosed() {
        return !channel.isOpen();
    }

    public void close() throws IOException {
        channel.close();
    }

    public AsyncSocketConnection getConnection() {
        return con;
    }


    public static class CLSNucleus extends Nucleus<CLSNucleus> {
        _AsyncClientSocket sock;

        public void connect() {
            sock = new _AsyncClientSocket();
            sock.connect("localhost",8080, (key,channel) ->
                new QueuingAsyncSocketConnection( key, channel ) {
                    @Override
                    public void closed(Throwable ioe) {
                        isClosed = true;
                    }

                    @Override
                    protected void dataReceived(BinaryQueue queue) {
                        System.out.println("received:"+queue.remaining());
                    }
                }
            ).await();
            delayed( 1000, () -> loop() );
        }

        public void loop() {
            QueuingAsyncSocketConnection con = (QueuingAsyncSocketConnection) sock.getConnection();
            con.write("Hello\n".getBytes());
            con.tryFlush();
            delayed(1000, () -> loop());
        }
    }

	// FIXME: What is this doing here...
    public static void main(String a[]) throws InterruptedException {
        CLSNucleus act = Nucleus.of(CLSNucleus.class);
        act.connect();
        Thread.sleep(10000000l);
    }

}
