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

import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.util.Log;
import org.nustaq.offheap.BinaryQueue;
import org.nustaq.offheap.bytez.niobuffers.ByteBufferBasicBytez;
import org.nustaq.offheap.bytez.onheap.HeapBytez;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;

/**
 * A server socket connection which buffers incoming/outgoing data in a binary queue so
 * an application can easily parse and process data async in chunks without having
 * to maintain complex state machines.
 */
public abstract class QueuingAsyncSocketConnection extends AsyncSocketConnection {

    public static long MAX_Q_SIZE_BYTES = 10_000_000;

    protected BinaryQueue readQueue = new BinaryQueue();
    protected BinaryQueue writeQueue = new BinaryQueue();

    protected ByteBufferBasicBytez wrapper = new ByteBufferBasicBytez(null);

    public QueuingAsyncSocketConnection(SelectionKey key, SocketChannel chan) {
        super(key, chan);
    }

    ByteBufferBasicBytez tmp = new ByteBufferBasicBytez(null);
    HeapBytez tmpBA = new HeapBytez(new byte[0]);

    protected void checkQSize() {
        if ( writeQueue.available() > MAX_Q_SIZE_BYTES ) {
            LockSupport.parkNanos(1); // poor man's backpressure in case buffer is too large
        }
    }

    public void write( ByteBuffer buf ) {
        checkThread();
        checkQSize();
        tmp.setBuffer(buf);
        writeQueue.add(tmp);
    }

    public void write( byte b[] ) {
        checkThread();
        checkQSize();
        write(b, 0, b.length);
    }

    public void write( byte b[], int off, int len ) {
        checkThread();
        checkQSize();
        tmpBA.setBase(b, off, len);
        writeQueue.add(tmpBA);
    }

    public void write( int val ) {
        checkThread();
        checkQSize();
        writeQueue.addInt(val);
    }

    // quite some fiddling required to deal with various byte abstractions

    ByteBuffer qWriteTmp = ByteBuffer.allocateDirect(128000);
    public void tryFlush() {
        checkThread();
        if ( canWrite() ) {
            qWriteTmp.position(0);
            qWriteTmp.limit(qWriteTmp.capacity());
            tmp.setBuffer(qWriteTmp);
            long poll = writeQueue.poll(tmp, 0, tmp.length());
//            System.out.println("try write "+poll+" avail:"+writeQueue.available()+" cap:"+writeQueue.capacity());
            if (poll > 0) {
                qWriteTmp.limit((int) poll);
                Future queueDataAvailableCompletableFuture = directWrite(qWriteTmp);
                queueDataAvailableCompletableFuture.then((Signal)(res, err) -> {
                    if ( err != null ) {
                        if (err instanceof Throwable ) {
                            Log.e(this.toString(), "write failure",  (Throwable) err);
                            closed((Throwable) err);
                        } else {
	                        Log.e(this.toString(), "write failure:"+err);
                            closed( new IOException(""+err));
                        }
                    } else {
                        tryFlush();
                    }
                });
            }
        }
    }

    @Override
    public void dataReceived(ByteBuffer buf) {
        wrapper.setBuffer(buf);
        readQueue.add(wrapper, buf.position(), buf.limit());
        dataReceived(readQueue);
    }

    protected abstract void dataReceived(BinaryQueue queue);

//    public void closed(Exception ioe) {
//    }

}
