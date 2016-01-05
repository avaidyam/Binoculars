package org.kihara;

import com.binoculars.system.Endpoint;
import com.binoculars.system.JavascriptEngine;
import com.binoculars.system.PFPController;
import com.binoculars.util.Log;
import com.binoculars.util.ParameterFilter;
import com.sun.deploy.net.HttpResponse;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * TODO: virus protocol:
 * - snapshot of node state
 * - for any N endpoints
 * - acknowledge receipt
 * - no ACK = node lost
 * - synchronized timestamp
 * - vector clock
 * - includes cpu coefficient
 * - openjdk benchmarking
 * - includes current load info
 * - part of snapshot

 * TODO: nucleus additions:
 * - startup() upon system start
 * - shutdown() upon system shutdown
 * - start() upon alloc + proxy
 * - could be spawn()
 * - stop() upon deproxy + GC
 * - could be destroy()

 * TODO: future modifications:
 * - then: apply, compose, combine
 * - accept(both, either)

 * TODO: uncategorized issues:
 * - Tasks[] per node
 * - Task progress (Queue, InProgress, Success, Failure)
 * - Task progress listener ^^
 * - Turn endpoint into an nuclei
 * - endpoint can do heartbeat
 * - endpoint registers allOf "plugins"
 * - vends services to other endpoints connected
 * - Guava ServiceManager + Service
 * - ALSO ALLOW HTTP SERVER!! -- com.sun.net.httpserver
 * - see vert.x eventbus (send/recv)
 * - vert.x deploymentoptions, vertxoptions
 * - pfp should fix distribution
 * - use round robbin setup
 * - factor cpu strength

 * TODO: actor spawning modifications:
 * - .Unique, .PerCPU, .PerCore values
 * - select between Elastic or Simple based on this

 * TODO: visual output:
 * - show subsequences upon submission
 * - show more info upon completion
 * - allow output xml download
 * - show xml data summary
 * - show output visualizer
 * - test results with samples
 * - Arabidopsis Thalania
 * - Yeast
 */

/// scp Binoculars.jar dragon:.

/*
    // Imports Arrays and Collectors, and aliases the Arrays.stream() function.
    var Arrays = Java.type("java.util.Arrays")
    var Collectors = Java.type("java.util.stream.Collectors")
    var ArrayStream = Arrays["stream(java.lang.Object[])"]

    // Use cURL to download OpenFDA Adverse Events for "Morphine" as JSON.
    var drugs = JSON.parse(`curl -k https://api.fda.gov/drug/event.json?skip=0&limit=5&search=morphine`)
    drugs = ArrayStream(drugs.results)

    // Map each event to all the medicinal products involved and collect as a string.
    drugs = drugs.flatMap(function(a) ArrayStream(a.patient.drug))
    drugs = drugs.map(function(a) a.medicinalproduct)
    drugs = drugs.collect(Collectors.joining("\n"))
*/

public class Main {

    /**
     * Initialize the Endpoint, and start the HTTP server and shell.
     */
    public static void main(String[] args) {
        Log.get().setSeverity(Log.DEBUG);
        Endpoint<PFPController> endpoint = Endpoint.of(PFPController.class);
        try {
            PFPController main = endpoint.getNodes().get(0);
            startHTTP(8080, main);
            startShell(endpoint.getNodes());
        } catch (Exception e) {
            Log.e("Main", "Could not begin application.", e);
        }

    }

    /**
     * Jumpstart a quick HTTPServer atop the PFPController
     * Yes! We can turn the PFPController into an Executor,
     * and then apply parallelism or any API onto it.

     * @param port
     * *
     * @param executor
     * *
     * @throws IOException
     */
    public static void startHTTP(int port, Executor executor) throws Exception {
        if (port <= 0) port = 8080;
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 256);
        HttpContext context = server.createContext("/", exchange -> {

            // If params exists, then we're doing a response
            // otherwise, if it's empty, we're requesting.
            @SuppressWarnings("unchecked")
			Map<String, String> params = (Map<String, String>)exchange.getAttribute("parameters");

            String response = "";
            if (!params.containsKey("data") || params.get("data") == null)
                response = new String(Files.readAllBytes(new File("index.html").toPath()));
            else
                response = "FASTA input received.";

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();

            // Begin the PFP processing from HTTP.
            if (params.get("data") != null)
                Endpoint.of(PFPController.class)
                        .getNodes().get(0)
                        .beginPFP(params.get("data"));
        });
        context.getFilters().add(new ParameterFilter());
        server.setExecutor(executor);
        server.start();
    }

    /**
     * Setup and deploy the JS Runtime wrapper.

     * @param nodes
     * *
     * @throws Exception
     */
    public static void startShell(List<PFPController> nodes) throws Exception {
        System.err.println("Binoculars Runtime Environment (ver. b1.0.0)");
        System.err.println("For help, enter \"help()\" and press enter.");
        JavascriptEngine.bindings = bindings -> {
            bindings.put("$nodes", nodes);
            bindings.put("help", (Runnable)() -> {
                System.err.println("\nWelcome to the Binoculars Runtime Environment.");
                System.err.println("This is a JavaScript read-print-eval-loop (REPL) shell.");
                System.err.println("Through bidirectional Java to JavaScript communication,");
                System.err.println("the distributed node system may accessed with ease.\n");
                System.err.println("To access nodes, use \'$NODES\'. The root node is at index 0.");
                System.err.println("For information about a function or method, see its JavaDoc.\n");
            });
            bindings.put("info", (Consumer)(s) -> System.err.println("Command Info: " + s));
            bindings.put("time", (Supplier) System::currentTimeMillis);
        };
        JavascriptEngine.script = () -> "var PFP = Java.type(\"com.binoculars.system.PFPController\")\n";
        JavascriptEngine.shell(System.in, System.out, System.err);
    }
}
