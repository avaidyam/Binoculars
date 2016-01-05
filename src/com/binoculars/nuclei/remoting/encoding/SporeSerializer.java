package com.binoculars.nuclei.remoting.encoding;

import org.nustaq.serialization.FSTBasicObjectSerializer;
import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.IOException;

/**
 *
 * <p>
 * FIXME: pobably not needed anymore because of @AnonymousTransient
 */
public class SporeSerializer extends FSTBasicObjectSerializer {

	@Override
	public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) {
		in.defaultReadObject(referencedBy,clzInfo,toRead);
	}

	@Override
	public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPositioin) {
		return null;
	}

	@Override
	public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
		out.defaultWriteObject(toWrite, clzInfo);
	}

}
