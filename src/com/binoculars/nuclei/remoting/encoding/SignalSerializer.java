package com.binoculars.nuclei.remoting.encoding;

import com.binoculars.nuclei.remoting.base.ObjectSocket;
import com.binoculars.nuclei.remoting.base.RemoteRegistry;
import com.binoculars.nuclei.remoting.base.RemotedCallback;
import com.binoculars.future.Signal;
import com.binoculars.util.Log;
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
		AtomicReference<ObjectSocket> chan;
		int id;

		public MyRemotedSignal(AtomicReference<ObjectSocket> chan, int id) {
			this.chan = chan;
			this.id = id;
		}

		@Override
		public void complete(Object result, Object error) {
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
		AtomicReference<ObjectSocket> chan = reg.getWriteObjectSocket();
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
