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

package com.avaidyam.binoculars.asyncio;

import com.avaidyam.binoculars.Log;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.tcp.TCPServerConnector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Implements NIO based TCP server.
 */
public class AsyncServerSocket implements AutoCloseable {

	/**
	 *
	 */
    ServerSocketChannel socket;

	/**
	 *
	 */
    Selector selector;

	/**
	 *
	 */
    SelectionKey serverkey;

	/**
	 *
	 */
    BiFunction<SelectionKey, SocketChannel, AsyncSocketConnection> connectionFactory;

	/**
	 *
	 */
	Thread localThread = null;

	/**
	 *
	 * @param port
	 * @param connectionFactory
	 * @throws IOException
	 */
    public void connect(int port, BiFunction<SelectionKey, SocketChannel, AsyncSocketConnection> connectionFactory) throws IOException {
        this.selector = Selector.open();
        this.socket = ServerSocketChannel.open();
        this.socket.configureBlocking(false);

        this.socket.socket().bind(new InetSocketAddress(port));
        this.serverkey = this.socket.register(selector, SelectionKey.OP_ACCEPT);
        this.connectionFactory = connectionFactory;

        receiveLoop();
    }

    public void receiveLoop() {

		// Get the right thread installed.
        if (this.localThread == null)
			this.localThread = Thread.currentThread();
        else if (this.localThread != Thread.currentThread())
			throw new IllegalThreadStateException();

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
                                            con.writeFinished(new IOException("disconnected"));
                                        } else if ( writingBuffer.remaining() == 0) {
                                            iterator.remove();
                                            con.writeFinished(null);
                                        }
                                    } catch (IOException ioe) {
                                        iterator.remove();
                                        key.cancel();
                                        con.writeFinished(new IOException("disconnected"));
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
                                    } catch (Exception e) {
                                        con.closed(e);
                                        key.cancel();
                                        try {
                                            client.close();
                                        } catch (IOException ee) {
                                            Log.w(this.toString(), "", ee);
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
	            //new CompletableFuture<>(null, e); // FIXME: uh...
            }
        } while (iterCount-- > 0 && hadStuff );

        if (!isClosed()) {

			//
			Nucleus nucleus = Nucleus.current();
            if (hadStuff)
                nucleus.execute(this::receiveLoop);
            else nucleus.delayed(1, this::receiveLoop);
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

	/**
	 *
	 * @return
	 */
    public boolean isClosed() {
        return !socket.isOpen();
    }

	/**
	 *
	 * @throws IOException
	 */
    public void close() throws IOException {
        socket.close();
    }
}
