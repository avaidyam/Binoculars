
package com.binoculars.nuclei.remoting.tcp;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.NucleusPublisher;
import com.binoculars.nuclei.remoting.base.NucleusServer;
import com.binoculars.nuclei.remoting.encoding.Coding;
import com.binoculars.nuclei.remoting.encoding.SerializerType;
import com.binoculars.future.Future;

import java.util.function.Consumer;

/**
 *
 *
 * Publishes actors on TCP (NIO based)
 *
 */
public class TCPNIOPublisher implements NucleusPublisher {

    Nucleus facade;
    int port = 6543;
    Coding coding = new Coding( SerializerType.FSTSer );

    public TCPNIOPublisher() {
    }

    public TCPNIOPublisher(Nucleus facade, int port) {
        this.facade = facade;
        this.port = port;
    }

    @Override
    public Future<NucleusServer> publish(Consumer<Nucleus> disconnectHandler) {
        return NIOServerConnector.Publish(facade,port,coding,disconnectHandler);
    }

    public TCPNIOPublisher serType( SerializerType type ) {
        coding = new Coding(type);
        return this;
    }

    public TCPNIOPublisher facade(final Nucleus facade) {
        this.facade = facade;
        return this;
    }

    public TCPNIOPublisher port(final int port) {
        this.port = port;
        return this;
    }

    public TCPNIOPublisher coding(final Coding coding) {
        this.coding = coding;
        return this;
    }

    public Nucleus getFacade() {
        return facade;
    }

    public int getPort() {
        return port;
    }

    public Coding getCoding() {
        return coding;
    }

    @Override
    public String toString() {
        return "TCPNIOPublisher{" +
                   "facade=" + facade.getClass().getSimpleName() +
                   ", port=" + port +
                   ", coding=" + coding +
                   '}';
    }
}
