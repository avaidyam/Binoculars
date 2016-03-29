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

package com.avaidyam.binoculars;

import com.avaidyam.binoculars.remoting.tcp.TCPConnectible;
import org.kihara.tasks.TaskScheduler;
import com.avaidyam.binoculars.remoting.tcp.TCPPublisher;
import com.avaidyam.binoculars.util.Eponym;
import external.zeroconf.discovery.*;
import com.avaidyam.binoculars.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class Cortex<T extends Nucleus> {

    private static ZeroConf zeroConf;
    static {
        try {
            zeroConf = ZeroConf.create();
            //zeroConf = ZeroConfMulti.Factory.getInstance();
        } catch(Exception e) {
            Log.i(Runtime.getRuntime().toString(), "ZeroConf disabled. Error details: " + e.getMessage() + ".");
        }

        // Prepare for cleanup as soon as
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.i(Runtime.getRuntime().toString(), "ZeroConf shutting down.");

            zeroConf.unregisterAllServices();
            //zeroConf.removeAllServiceListeners();
            try {
                zeroConf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    private static HashMap<Class<? extends Nucleus>, Cortex<? extends Nucleus>> _endpoints = new HashMap<>();

    private BiConsumer<String, Integer> connectListener = (h, p) -> {};
    private BiConsumer<String, Integer> disconnectListener = (h, p) -> {};

    // Hold a private reference to the service listener, since we will need to remove it on cleanup.
    // Automate the service resolution process by forcing a resolution when a service is added.
    // On resolve and removal, call the BiConsumer functions as provided, for an abstracted callback.
    private final ServiceListener listener = new ServiceListener() {
        public void serviceResolved(ServiceEvent ev) {
			Log.i("ZeroConf", "Service added: " + ev.getName());
            if(connectListener != null)
                connectListener.accept(ev.getInfo().getHostAddresses()[0], ev.getInfo().getPort());
        }

        public void serviceRemoved(ServiceEvent ev) {
            Log.i("ZeroConf", "Service removed: " + ev.getName());
            if(disconnectListener != null)
                disconnectListener.accept(ev.getInfo().getHostAddresses()[0], ev.getInfo().getPort());
        }

        public void serviceAdded(ServiceEvent ev) {
            if(!ev.getName().equals(broadcastName))
				ev.getDNS().requestServiceInfo(ev.getType(), ev.getName(), 5000);
        }
    };

    private final String broadcastType;
    private final String broadcastName = "endpoint-" + Eponym.eponymate("-", 4);
    private final int broadcastPort = getLocalPort();

    private final Class<T> actorClass;
    private final List<T> nodes = new ArrayList<>();

    private static final List<Heartbeat> nodeHealth = new ArrayList<>();
    private static final long HEARTBEAT_INTERVAL = 60 * 1000; // each minute
    private static final TaskScheduler scheduler = new TaskScheduler();

    /*public void monitorHeartbeat() {
        Heartbeat current = new Heartbeat();
        //Log.Info(this, "Retrieving metrics: " + Metrics.collect());
        Endpoint.stream(this).forEach((n) -> n.recieveHeartbeat(current));
        delayed(HEARTBEAT_INTERVAL, this::monitorHeartbeat);
    }

    public void recieveHeartbeat(Heartbeat h) {
        // Currently doing nothing with it.
        //Log.Info(this, "" + h);
    }//*/

    @SuppressWarnings("unchecked")
    public static <T extends Nucleus> Cortex<T> of(Class<T> clazz) {
        Cortex end = _endpoints.get(clazz);
        if(end != null)
            return end;

        try {
            end = new Cortex<>(clazz);
            _endpoints.put(clazz, end);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return end;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Nucleus> List<T> nodes(T node) {
        return (List<T>) Cortex.of(node.getClass()).getNodes();
    }

    public static <T extends Nucleus> Stream<T> stream(T node) {
        return Cortex.nodes(node).stream().parallel();
    }

    private Cortex(Class<T> clazz) throws Exception {
        if("".equals(clazz.getSimpleName()))
            throw new IllegalArgumentException("Anonymous classes and array references not allowed.");

        this.actorClass = clazz;
        this.broadcastType = "_" + clazz.getSimpleName().toLowerCase() + "._tcp.local.";
        nodes.add(0, Nucleus.of(this.actorClass));

        try {
            new TCPPublisher(nodes.get(0), getBroadcastPort()).publish((n) -> {
	            Log.i(clazz.toGenericString(), "Disconnecting...");
	            //
            });
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            publish();
            discover((h, p) -> {
                try {
	                new TCPConnectible(this.actorClass, h, p).connect().then((node, error) -> {
	                    this.nodes.add((T)node);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, (h, p) -> {
                Log.i("[Cortex]", "Node dropped at " + h + ":" + p);
                //
            });
        }
    }

    public void stop() {
        this.nodes.stream().forEach(Nucleus::stop);
    }

    public List<T> getNodes() {
        return Collections.unmodifiableList(this.nodes);
    }

    /**
     * Establish a node as a server, and connect to allOf nodes available
     * on the network. As new nodes come online, a dimensional network is
     * formed where, for a network of N nodes, each node has N - 1 links.
     *
     * To disconnect from the dimensional network, see dimensionalDisconnect.
     *
     * @throws Exception
     */
    /*public static void dimensionalConnect() throws Exception {
        nodes.add(0, Nucleus.of(Node.class));
        try {
            TCPNucleusServer.Publish(nodes.get(0), Endpoint.getBroadcastPort(), (n) -> {
                Log.Info(Node.class, "Disconnecting...");
            });
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Endpoint.publish();
            Endpoint.discover((h, p) -> {
                try {
                    TCPNucleusClient.Connect(Node.class, h, p).then((node, error) -> {
                        Endpoint.nodes.add(node);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, (h, p) -> {
                Log.Info(Node.class, "Node dropped at " + h + ":" + p);
            });
        }
    }//*/

    // ---

    /**
     * Remove allOf local node proxies, and then disconnect local node from
     * the dimensional network. This causes a somewhat expensive task
     * and management recalculation via consensus on the remaining nodes.
     */
    /*public static void dimensionalDisconnect() {
        Endpoint.nodes.stream().forEach(Node::$stop);
    }//*/

    /**
     * As ZeroConf services are published, they hold a unique broadcast name.
     *
     * @return The current ZeroConf broadcast name.
     */
    public String getBroadcastName() {
        return broadcastName;
    }

    /**
     * As ZeroConf services are published, they are bound to a local port.
     *
     * @return The current ZeroConf broadcast port.
     */
    public int getBroadcastPort() {
        return broadcastPort;
    }

    /**
     * Publish the network stack as available to other Nodes. This allows the
     * dimensional connection system to connect to this stack as a Node.
     *
     * @throws IOException
     */
    public void publish() throws IOException {
        if(zeroConf == null) return;
        zeroConf.registerService(ServiceInfo.create(broadcastType,
                broadcastName, broadcastPort, "TXT RECORD"));
		Log.i("[Cortex]", "Publishing service " + broadcastName +
				" on port " + broadcastPort + ".");
    }

    /**
     * Discover other network stacks made available by other Nodes. This triggers
     * the dimensional connection system to connect to these remote stacks as Nodes.
     *
     * @param connectListener Listener to be invoked when a Node is connected.
     * @param disconnectListener Listener to be invoked when a Node is disconnected.
     * @throws IOException
     */
    public void discover(BiConsumer<String, Integer> connectListener,
                         BiConsumer<String, Integer> disconnectListener) throws IOException {
        if(zeroConf == null) return;
        this.connectListener = connectListener;
        this.disconnectListener = disconnectListener;
        zeroConf.addServiceListener(broadcastType, listener);
    }

    /**
     * Evaluates a local port that is open and available.
     *
     * @return An available port for use.
     */
    public static int getLocalPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch(IOException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
