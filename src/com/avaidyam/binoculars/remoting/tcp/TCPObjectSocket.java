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

import org.nustaq.serialization.FSTConfiguration;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * As socket allowing to send/receive serializable objects
 * see ./test/net for an example
 *
 * Note that by providing a Json configuration, it can be used cross language
 */
public class TCPObjectSocket {

	public static int BUFFER_SIZE = 512_000;

	InputStream in;
	OutputStream out;
	FSTConfiguration conf;
	Socket socket;
	Throwable lastErr;
	boolean stopped;

	AtomicBoolean readLock = new AtomicBoolean(false);
	AtomicBoolean writeLock = new AtomicBoolean(false);

	public TCPObjectSocket(String host, int port) throws IOException {
		this(new Socket(host, port), FSTConfiguration.createDefaultConfiguration());
	}

	public TCPObjectSocket(String host, int port, FSTConfiguration conf) throws IOException {
		this(new Socket(host, port),conf);
	}

	public TCPObjectSocket( Socket socket, FSTConfiguration conf) throws IOException {
		this.socket = socket;
//        socket.setSoLinger(true,0);
		this.out = new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE);
		this.in  = new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE);
		this.conf = conf;
	}

	public boolean isStopped() {
		return stopped;
	}

	public boolean isClosed() {
		return socket.isClosed();
	}

	/**
	 * enables reading raw bytes from socket
	 * @return
	 */
	public InputStream getIn() {
		return in;
	}

	public Object readObject() throws Exception {
		try {
			while ( !readLock.compareAndSet(false,true) );

			return conf.decodeFromStream(in);

		} finally {
			readLock.set(false);
		}
	}

	public void writeObject(Object toWrite) throws Exception {
		try {
			while ( !writeLock.compareAndSet(false,true) );
			conf.encodeToStream(out, toWrite);
		} finally {
			writeLock.set(false);
		}
	}

	public void flush() throws IOException {
		out.flush();
	}

	public void setLastError(Throwable ex) {
		stopped = true;
		lastErr = ex;
	}

	public Throwable getLastError() {
		return lastErr;
	}

	public void close() throws IOException {
		flush();
		socket.close();
	}

	public Socket getSocket() {
		return socket;
	}

	public FSTConfiguration getConf() {
		return conf;
	}

	public void setConf(FSTConfiguration conf) {
		this.conf = conf;
	}

}