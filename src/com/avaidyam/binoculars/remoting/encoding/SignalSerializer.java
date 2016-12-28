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

import com.avaidyam.binoculars.remoting.base.ObjectFlow;
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import com.avaidyam.binoculars.remoting.base.RemotedCallback;
import com.avaidyam.binoculars.future.Signal;
import com.avaidyam.binoculars.Log;
import org.nustaq.serialization.FSTBasicObjectSerializer;
import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;
import org.nustaq.serialization.util.FSTUtil;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class SignalSerializer extends FSTBasicObjectSerializer {

	RemoteRegistry reg;

	public SignalSerializer(RemoteRegistry reg) {
		this.reg = reg;
	}

	@Override
	public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) {

	}

	@Override
	public boolean alwaysCopy() {
		return super.alwaysCopy();
	}

	public class MyRemotedSignal implements Signal, RemotedCallback {
		AtomicReference<ObjectFlow.Source> chan;
		int id;

		public MyRemotedSignal(AtomicReference<ObjectFlow.Source> chan, int id) {
			this.chan = chan;
			this.id = id;
		}

		@Override
		public void complete(Object result, Throwable error) {
			try {
				reg.receiveCBResult(chan.get(),id,result,error);
			} catch (Exception e) {
				Log.w(this.toString(), "", e);
				FSTUtil.rethrow(e);
			}
		}

		@Override
		public boolean isTerminated() {
			boolean terminated = reg.isTerminated();
			if ( terminated )
				return true;
			boolean closed = chan.get().isClosed();
			if ( closed ) {
				Log.e(this.toString(), "registry alive, but socket closed");
			}
			return closed;
		}
	}

	@Override
	public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPositioin) throws IOException {
		// fixme: detect local actors returned from foreign
		int id = in.readInt();
		AtomicReference<ObjectFlow.Source> chan = reg.getWriteObjectSocket();
		MyRemotedSignal cb = new MyRemotedSignal(chan, id);
		in.registerObject(cb, streamPositioin, serializationInfo, referencee);
		return cb;
	}

	@Override
	public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
		// fixme: catch republish of foreign nuclei
		int id = reg.registerPublishedCallback((Signal) toWrite); // register published host side
		out.writeInt(id);
	}

}
