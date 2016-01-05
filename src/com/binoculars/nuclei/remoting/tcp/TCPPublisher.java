
package com.binoculars.nuclei.remoting.tcp;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.NucleusServer;
import com.binoculars.nuclei.remoting.encoding.Coding;
import com.binoculars.nuclei.remoting.encoding.SerializerType;
import com.binoculars.future.Future;

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
