package com.binoculars.nuclei.remoting.encoding;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.RemoteRegistry;
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
