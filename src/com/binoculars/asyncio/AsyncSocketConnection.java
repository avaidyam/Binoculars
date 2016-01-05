
package com.binoculars.asyncio;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
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
