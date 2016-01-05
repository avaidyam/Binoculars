
package com.binoculars.asyncio;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.tcp.TCPServerConnector;
import com.binoculars.future.CompletableFuture;
import com.binoculars.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;

/**
 *
 *
 * Implements NIO based TCP server
 *
 */
public class AsyncServerSocket {

    ServerSocketChannel socket;
    Selector selector;
    SelectionKey serverkey;
    BiFunction<SelectionKey,SocketChannel,AsyncSocketConnection> connectionFactory;

    public void connect( int port, BiFunction<SelectionKey,SocketChannel,AsyncSocketConnection> connectionFactory ) throws IOException {
        selector = Selector.open();
        socket = ServerSocketChannel.open();
        socket.configureBlocking(false);
        socket.socket().bind(new java.net.InetSocketAddress(port));
        serverkey = socket.register(selector, SelectionKey.OP_ACCEPT);
        this.connectionFactory = connectionFactory;
        receiveLoop();
    }

    Thread t = null;
    public void receiveLoop() {
        Nucleus nucleus = Nucleus.current();
        if ( t == null )
            t = Thread.currentThread();
        else {
            if ( t != Thread.currentThread() ) {
                System.out.println("FATAL");
                System.exit(-1);
            }
        }
        boolean hadStuff = false;
        int iterCount = 10;
        do {
            try {
                selector.selectNow();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (Iterator<SelectionKey> iterator = selectionKeys.iterator(); iterator.hasNext(); ) {
                    SelectionKey key = iterator.next();
                    try {
                        if (key == serverkey) {
                            if (key.isAcceptable()) {
                                SocketChannel accept = socket.accept();
                                if (accept != null) {
                                    hadStuff = true;
                                    accept.configureBlocking(false);
                                    SelectionKey newKey = accept.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE);
                                    AsyncSocketConnection con = connectionFactory.apply(key, accept);
                                    newKey.attach(con);
                                }
                            }
                        } else {
                            SocketChannel client = (SocketChannel) key.channel();
                            int written = 0;
                            if (key.isWritable()) {
                                AsyncSocketConnection con = (AsyncSocketConnection) key.attachment();
                                ByteBuffer writingBuffer = con.getWritingBuffer();
                                if ( writingBuffer != null ) {
                                    hadStuff = true;
                                    try {
                                        written = con.chan.write(writingBuffer);
                                        if (written<0) {
                                            iterator.remove();
                                            key.cancel();
                                            // closed
                                            con.writeFinished("disconnected");
                                        } else if ( writingBuffer.remaining() == 0) {
                                            iterator.remove();
                                            con.writeFinished(null);
                                        }
                                    } catch (IOException ioe) {
                                        iterator.remove();
                                        key.cancel();
                                        con.writeFinished("disconnected");
                                    }
                                }
                            }
                            if (key.isReadable() && written == 0) {
                                iterator.remove();
                                AsyncSocketConnection con = (AsyncSocketConnection) key.attachment();
                                if ( con == null || con.isClosed() ) {
                                    Log.w(this.toString(), "con is null " + key);
                                } else {
                                    hadStuff = true;
                                    try {
                                        if ( ! con.readData() ) {
                                            // yield ?
                                        }
                                    } catch (Exception ioe) {
//                                        ioe.printStackTrace();
                                        con.closed(ioe);
                                        key.cancel();
                                        try {
                                            client.close();
                                        } catch (IOException e) {
                                            Log.w(this.toString(), "", e);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        Log.w(this.toString(), "", e);
                    }
                }
            } catch (Throwable e) {
                Log.w(this.toString(), "", e);
	            new CompletableFuture<>(null, e); // FIXME: uh...
            }
        } while (iterCount-- > 0 && hadStuff );
        if ( ! isClosed() ) {
            if ( hadStuff ) {
                nucleus.execute( () -> receiveLoop() );
            } else {
                nucleus.delayed( 1, () -> receiveLoop() );
            }
        } else {
            // close open connections
            try {
                selector.selectNow();
                Nucleus.submitDelayed(TCPServerConnector.DELAY_MS_TILL_CLOSE, () -> {
	                selector.selectedKeys().forEach(key -> {
		                try {
			                key.channel().close();
		                } catch(IOException e) {
			                Log.w(this.toString(), "", e);
		                }
	                });
                });
            } catch (IOException e) {
                Log.w(this.toString(), "", e);
            }

        }
    }

    public boolean isClosed() {
        return !socket.isOpen();
    }

    public void close() throws IOException {
        socket.close();
    }
}
