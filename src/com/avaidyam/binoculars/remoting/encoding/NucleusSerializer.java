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

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import org.nustaq.serialization.FSTBasicObjectSerializer;
import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.IOException;

/**
 *
 */
public class NucleusSerializer extends FSTBasicObjectSerializer {

    RemoteRegistry reg;

    public NucleusSerializer(RemoteRegistry reg) {
        this.reg = reg;
    }

    @Override
    public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    }

    @Override
    public boolean alwaysCopy() {
        return super.alwaysCopy();
    }

    @Override
    public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPositioin) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        // fixme: detect local actors returned from foreign
        int id = in.readInt();
        String clzName = in.readStringUTF();
        if (clzName.endsWith("_NucleusProxy")) {
            clzName = clzName.substring(0, clzName.length() - "_NucleusProxy".length());
        }
        Class nucleusClz = Class.forName(clzName, true, reg.getConf().getClassLoader());
        Nucleus nucleusRef = reg.registerRemoteNucleusRef(nucleusClz, id, null);
        in.registerObject(nucleusRef, streamPositioin, serializationInfo, referencee);
        return nucleusRef;
    }

    @Override
    public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
        // fixme: catch republish of foreign nuclei
        Nucleus act = (Nucleus) toWrite;
        int id = reg.publishNucleus(act); // register published host side FIXME: if ref is foreign ref, scnd id is required see javascript impl
        out.writeInt(id);
        out.writeStringUTF(act.getNucleusRef().getClass().getName());
    }
}
