
package com.binoculars.nuclei.remoting.tcp;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.scheduler.ElasticScheduler;
import com.binoculars.nuclei.remoting.base.NucleusClient;
import com.binoculars.nuclei.remoting.base.NucleusClientConnector;
import com.binoculars.nuclei.remoting.base.ConnectableNucleus;
import com.binoculars.nuclei.remoting.encoding.Coding;
import com.binoculars.nuclei.remoting.encoding.SerializerType;
import com.binoculars.future.Signal;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;

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
