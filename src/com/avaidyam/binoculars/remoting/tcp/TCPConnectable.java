
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
import com.avaidyam.binoculars.scheduler.ElasticScheduler;
import com.avaidyam.binoculars.remoting.base.NucleusClient;
import com.avaidyam.binoculars.remoting.base.NucleusClientConnector;
import com.avaidyam.binoculars.remoting.base.ConnectableNucleus;
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.remoting.encoding.SerializerType;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;

import java.util.function.Consumer;

/**
 *
 *
 * Describes a connectable remote nuclei
 *
 */
public class TCPConnectable implements ConnectableNucleus {

    String host;
    int port;
    Class nucleusClz;
    Coding coding = new Coding(SerializerType.FSTSer);
    int inboundQueueSize = ElasticScheduler.DEFQSIZE;

    public TCPConnectable() {
    }

    /**
     *
     * @param host - ip/host e.g. "192.168.4.5"
     * @param port - port
     * @param nucleusClz - nuclei clazz to connect to
     */
    public TCPConnectable(Class nucleusClz, String host, int port) {
        this.host = host;
        this.port = port;
        this.nucleusClz = nucleusClz;
    }

    @Override
    public <T> Future<T> connect(Signal<NucleusClientConnector> disconnectSignal, Consumer<Nucleus> nucleusDisconnecCB) {
        CompletableFuture result = new CompletableFuture();
        Runnable connect = () -> {
            TCPClientConnector client = new TCPClientConnector(port,host, disconnectSignal);
            NucleusClient connector = new NucleusClient(client,nucleusClz,coding);
            connector.connect(inboundQueueSize, nucleusDisconnecCB).then(result);
        };
        if ( ! Nucleus.inside() ) {
            TCPClientConnector.get().execute(() -> Thread.currentThread().setName("singleton remote client nuclei polling"));
            TCPClientConnector.get().execute(connect);
        }
        else
            connect.run();
        return result;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Class getNucleusClz() {
        return nucleusClz;
    }

    public TCPConnectable host(String host) {
        this.host = host;
        return this;
    }

    public TCPConnectable port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public TCPConnectable nucleusClass(Class nucleusClz) {
        this.nucleusClz = nucleusClz;
        return this;
    }

    public TCPConnectable coding(Coding coding) {
        this.coding = coding;
        return this;
    }

    public TCPConnectable serType(SerializerType sertype) {
        this.coding = new Coding(sertype);
        return this;
    }

    /**
     * default is 32k (SimpleScheduler.DEFQSIZE)
     * @param inboundQueueSize
     * @return
     */
    public TCPConnectable inboundQueueSize(final int inboundQueueSize) {
        this.inboundQueueSize = inboundQueueSize;
        return this;
    }

}
