
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
import com.avaidyam.binoculars.remoting.base.ConnectibleNucleus;
import com.avaidyam.binoculars.remoting.encoding.Coding;
import com.avaidyam.binoculars.remoting.encoding.SerializerType;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;

import java.util.function.Consumer;

/**
 * Describes a ConnectibleNucleus over the TCP/IP protocol.
 */
public class TCPConnectible<T extends Nucleus> implements ConnectibleNucleus<T> {

	/**
	 * The inbound queue size. (Default == ElasticScheduler.DEFQSIZE)
	 */
	/*package*/ static int inboundQueueSize = ElasticScheduler.DEFQSIZE;

	/**
	 * The ConnectibleNucleus class.
	 */
    /*package*/ Class<T> nucleusClass = null;

	/**
	 * The TCP/IP host address. (i.e. "10.0.0.5")
	 */
    /*package*/ String host = "";

	/**
	 * The TCP/IP host port. (i.e. "8080")
	 */
    /*package*/ int port = 0;

    /**
     * Create a new TCPConnectible with the provided parameters.
	 *
	 * @param nucleusClass the ConnectibleNucleus class
     * @param host the TCP/IP host address
     * @param port the TCP/IP host port
     */
    public TCPConnectible(Class<T> nucleusClass, String host, int port) {
        this.host = host;
        this.port = port;
        this.nucleusClass = nucleusClass;
    }

	/**
	 * Connects to the remote Nucleus with provided disconnection Signals.
	 *
	 * @apiNote disconnectHandler is rarely used or needed.
	 *
	 * @param disconnectSignal called on disconnect
	 * @param disconnectHandler called on disconnect, with the RemoteNucleus.
	 * @return a Future containing the Nucleus reference
	 */
    @Override
    public Future<T> connect(Signal<NucleusClientConnector> disconnectSignal, Consumer<T> disconnectHandler) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable connect = () -> {
            TCPClientConnector client = new TCPClientConnector(this.port, this.host, disconnectSignal);
            NucleusClient<T> connector = new NucleusClient(client, this.nucleusClass, new Coding(SerializerType.FSTSer));
            connector.connect(TCPConnectible.inboundQueueSize, disconnectHandler).then(result);
        };

        if (!Nucleus.inside()) {
            TCPClientConnector.get().execute(() -> Thread.currentThread().setName("singleton remote client nuclei polling"));
            TCPClientConnector.get().execute(connect);
        } else connect.run();
        return result;
    }

	/**
	 * Returns the Nucleus class.
	 *
	 * @return the Nucleus class
	 */
	public Class<T> getNucleusClass() {
		return nucleusClass;
	}

	/**
	 * Sets the Nucleus class.
	 *
	 * @param nucleusClass the Nucleus class
	 */
	public void setNucleusClass(Class<T> nucleusClass) {
		this.nucleusClass = nucleusClass;
	}

	/**
	 * Returns the TCP/IP host.
	 *
	 * @return the TCP/IP host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Sets the TCP/IP host.
	 *
	 * @param host the TCP/IP host
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * Returns the TCP/IP port.
	 *
	 * @return the TCP/IP port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Sets the TCP/IP port.
	 *
	 * @param port the TCP/IP port
	 */
	public void setPort(int port) {
		this.port = port;
	}
}
