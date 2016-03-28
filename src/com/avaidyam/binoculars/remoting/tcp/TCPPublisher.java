
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
import com.avaidyam.binoculars.remoting.base.NucleusServer;
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.remoting.encoding.SerializerType;
import com.avaidyam.binoculars.future.Future;

import java.util.function.Consumer;

/**
 *
 */
public class TCPPublisher extends TCPNIOPublisher {

    public TCPPublisher() {
        super();
    }

    public TCPPublisher(Nucleus facade, int port) {
        super(facade, port);
    }

    @Override
    public Future<NucleusServer> publish(Consumer<Nucleus> disconnectCallback) {
        return TCPServerConnector.Publish(facade,port,coding,disconnectCallback);
    }

    @Override
    public TCPPublisher serType(SerializerType type) {
        return (TCPPublisher) super.serType(type);
    }

    @Override
    public TCPPublisher facade(Nucleus facade) {
        return (TCPPublisher) super.facade(facade);
    }

    @Override
    public TCPPublisher port(int port) {
        return (TCPPublisher) super.port(port);
    }

    @Override
    public TCPPublisher coding(Coding coding) {
        return (TCPPublisher) super.coding(coding);
    }

    @Override
    public Nucleus getFacade() {
        return super.getFacade();
    }

    @Override
    public int getPort() {
        return super.getPort();
    }

    @Override
    public Coding getCoding() {
        return super.getCoding();
    }
}
