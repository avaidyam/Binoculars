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
import com.avaidyam.binoculars.remoting.base.ObjectFlow;
import com.avaidyam.binoculars.util.Log;
import org.nustaq.offheap.BinaryQueue;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.util.FSTUtil;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

/**
 *
 */
public abstract class ObjectAsyncSourceConnection extends QueuingAsyncSocketConnection implements ObjectFlow.Source {

    FSTConfiguration conf;
    Throwable lastError;
    ArrayList objects = new ArrayList();

    public ObjectAsyncSourceConnection(SelectionKey key, SocketChannel chan) {
        super(key, chan);
    }

    public ObjectAsyncSourceConnection(FSTConfiguration conf, SelectionKey key, SocketChannel chan) {
        super(key, chan);
        setConf(conf);
    }

    public void setConf(FSTConfiguration conf) {this.conf = conf;}

    public FSTConfiguration getConf() {
        return conf;
    }

    @Override
    public void dataReceived(BinaryQueue q) {
        checkThread();
        while ( q.available() > 4 ) {
            int len = q.readInt();
            if ( len <= 0 )
            {
                System.out.println("object len ?? "+len);
                return;
            }
            if ( q.available() >= len ) {
                byte[] bytes = q.readByteArray(len);
                receivedObject(conf.asObject(bytes));
            } else {
                q.back(4);
                break;
            }
        }
    }

    public abstract void receivedObject(Object o);

    public void writeObject(Object o) {
        if ( myNucleus != null )
            myNucleus = Nucleus.current();
        checkThread();
        objects.add(o);
        if (objects.size()>100) {
            try {
                flush();
            } catch (Exception e) {
                FSTUtil.<RuntimeException>rethrow(e);
            }
        }
    }

    @Override
    public void flush() throws IOException, Exception {
        if ( theExecutingThread != Thread.currentThread() ) {
            if ( myNucleus == null )
                return;
            myNucleus.execute( () -> {
                try {
                    flush();
                } catch (Exception e) {
                    Log.w(this.toString(), "", e);
                }
            });
            return;
        }
        checkThread();
        if ( objects.size() == 0 ) {
            return;
        }
        objects.add(0); // sequence
        Object[] objArr = objects.toArray();
        objects.clear();

        byte[] bytes = conf.asByteArray(objArr);
        write(bytes.length);
        write(bytes);
        tryFlush();
    }

    public Throwable getLastError() {
        return lastError;
    }

    @Override
    public void setLastError(Throwable ex) {
        lastError = ex;
    }
}
