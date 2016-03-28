
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

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import org.nustaq.serialization.util.*;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

/**
 * Baseclass for handling async io. Its strongly recommended to use QueuingAsyncSocketConnection as this
 * eases things.
 */
public abstract class AsyncSocketConnection {

    protected ByteBuffer readBuf = ByteBuffer.allocateDirect(4096);

    protected SelectionKey key;
    protected SocketChannel chan;

    protected CompletableFuture writeCompletableFuture;
    protected ByteBuffer writingBuffer;
    protected boolean isClosed;
    protected Executor myNucleus;

    public AsyncSocketConnection(SelectionKey key, SocketChannel chan) {
        this.key = key;
        this.chan = chan;
    }

    public abstract void closed(Throwable ioe);
//    {
//        Log.Lg.info(this,"connection closed " + ioe);
//        isClosed = true;
//    }

    public void close() throws IOException {
        chan.close();
    }

    /**
     * @return wether more reads are to expect
     * @throws IOException
     */
    boolean readData() throws IOException {
        checkThread();
        readBuf.position(0); readBuf.limit(readBuf.capacity());
        int read = chan.read(readBuf);
        if ( read == -1 )
            throw new EOFException("connection closed");
        readBuf.flip();
        if ( readBuf.limit() > 0 )
            dataReceived(readBuf);
        return read == readBuf.capacity();
    }

    protected void checkThread() {
        if ( theExecutingThread == null )
            theExecutingThread = Thread.currentThread();
        else if ( theExecutingThread != Thread.currentThread() ) {
            System.err.println("unexpected multithreading");
            Thread.dumpStack();
        }
    }

    /**
     * writes given buffer content. In case of partial write, another write is enqueued internally.
     * once the write is completed, the returned promise is fulfilled.
     * the next write has to wait until the future has completed, else write order might get mixed up.
     *
     * Better use write* methods of QueuingAsyncSocketConnection as these will write to a binary queue
     * which is read+sent behind the scenes in parallel.
     *
     * @param buf
     * @return
     */
    protected Thread theExecutingThread; // originall for debugging, but now used to reschedule ..
    protected Future directWrite(ByteBuffer buf) {
        checkThread();
        if ( myNucleus == null )
            myNucleus = Nucleus.current();
        if ( writeCompletableFuture != null )
            throw new RuntimeException("concurrent write con:"+chan.isConnected()+" open:"+chan.isOpen());
        writeCompletableFuture = new CompletableFuture();
        writingBuffer = buf;
        CompletableFuture res = writeCompletableFuture;
        try {
            int written = 0;
            written = chan.write(buf);
            if (written<0) {
                // TODO:closed
                writeFinished(new IOException("connection closed"));
            }
            if ( buf.remaining() > 0 ) {
//                key.interestOps(SelectionKey.OP_WRITE);
            } else {
                writeFinished(null);
            }
        } catch (Exception e) {
            res.reject(e);
            FSTUtil.rethrow(e);
        }
        return res;
    }

    ByteBuffer getWritingBuffer() {
        return writingBuffer;
    }

    public boolean canWrite() {
        return writeCompletableFuture == null;
    }


    // error = null => ok
    void writeFinished(Object error) {
        checkThread();
        writingBuffer = null;
        CompletableFuture wp = this.writeCompletableFuture;
        writeCompletableFuture = null;
        if ( ! wp.isComplete() ) {
            if ( error != null )
                wp.reject(error);
            else
                wp.complete();
        }
    }

    public abstract void dataReceived(ByteBuffer buf);

    public boolean isClosed() {
        return !chan.isOpen() || isClosed;
    }
}
