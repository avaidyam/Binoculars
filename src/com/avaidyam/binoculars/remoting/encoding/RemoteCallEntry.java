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

package com.avaidyam.binoculars.remoting.encoding;

import java.io.Serializable;
import java.util.Arrays;

public class RemoteCallEntry implements Serializable {

	public static final int MAILBOX = 0;
	public static final int CBQ = 1;

	int receiverKey; // id of published nuclei in host, contains cbId in case of callbacks
	int futureKey; // id of future if any
	String method;
	Object args[];
	int queue;

	public RemoteCallEntry(int futureKey, int receiverKey, String method, Object[] args) {
		this.receiverKey = receiverKey;
		this.futureKey = futureKey;
		this.method = method;
		this.args = args;
	}

	public int getQueue() {
		return queue;
	}

	public void setQueue(int queue) {
		this.queue = queue;
	}

	public int getReceiverKey() {
		return receiverKey;
	}

	public void setReceiverKey(int receiverKey) {
		this.receiverKey = receiverKey;
	}

	public int getFutureKey() {
		return futureKey;
	}

	public void setFutureKey(int futureKey) {
		this.futureKey = futureKey;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public Object[] getArgs() {
		return args;
	}

	public void setArgs(Object[] args) {
		this.args = args;
	}

	@Override
	public String toString() {
		return "RemoteCallEntry{" +
				"receiverKey=" + receiverKey +
				", futureKey=" + futureKey +
				", method='" + method + '\'' +
				", args=" + Arrays.toString(args) +
				", queue=" + queue +
				'}';
	}
}

