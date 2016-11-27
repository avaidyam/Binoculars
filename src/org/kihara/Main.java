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

package org.kihara;

import com.avaidyam.binoculars.Cortex;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.util.Log;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import org.kihara.util.ParameterFilter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Entry point for the sample application (PFP).
 */
public class Main {

    /**
     * Initialize the Cortex, and start the HTTP server and shell.
     */
    public static void main(String[] args) throws Exception {
        Log.get().setSeverity(Log.Severity.DEBUG);
        PLPatchSurferController plps = Nucleus.of(PLPatchSurferController.class);
        plps.init().await();
        plps.begin("~/PatchSurfer/example/1_prepare_receptor/rec.pdb",
                "~/PatchSurfer/example/1_prepare_receptor/xtal-lig.pdb", 50).await();
        plps.generateInputs().await();
        /*
        plps.prepareReceptor().await();
        plps.prepareLigands().await();
        plps.compareSeeds().await();
        plps.compareLigands().then((r, e) -> {
            Log.d("Main", "Finished task!", e);
            e.printStackTrace();
        });
        //*/
    }

    /*public static void main(String[] args) {
        Log.get().setSeverity(Log.Severity.DEBUG);
        Cortex<PFPController> cortex = Cortex.of(PFPController.class);
        PFPController main = cortex.getNodes().get(0);

        // Safely bind the web interface, if possible.
        try {
            startHTTP(8080, main); // TODO: Don't use 8080.
        } catch (Exception e) {
            Log.w("Main", "Could not bind web interface.");
        }

        // Get the hostname.
        String host = "";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        Log.i("Main", "Resolving on host " + host + "...");

        // Manually connect the demo machine.
        if (host.equals("dragon"))
            cortex.manuallyConnect("miffy.bio.purdue.edu", 30003);
        else cortex.manuallyConnect("dragon.bio.purdue.edu", 30003);

        // Safely bind the shell, if possible.
        try {
            startShell(cortex.getNodes());
        } catch (Exception e) {
            Log.e("Main", "Could not begin application.", e);
            System.exit(-1);
        }
    }*/

    /**
     * Jumpstart a quick HTTPServer atop the PFPController
     * Yes! We can turn the PFPController into an Executor,
     * and then apply parallelism or any API onto it.
	 *
     * @param port
     *
     * @param executor
     *
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

            String responseURI = exchange.getRequestURI().toString();
            if (responseURI.startsWith("/")) {
                responseURI = responseURI.substring(1);
            }

            String response = "";
            if (!params.containsKey("data") || params.get("data") == null) {
                Path p = new File(responseURI).toPath();
                if (responseURI.length() == 0)
                    response = new String(Files.readAllBytes(new File("index.html").toPath()));
                else if (Files.exists(p))
                    response = new String(Files.readAllBytes(p));
                else
                    response = new String(Files.readAllBytes(new File("404.html").toPath()));
            }
            else
                response = "FASTA input received.";

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();

            // Begin the PFP processing from HTTP.
            if (params.get("data") != null)
                Cortex.of(PFPController.class)
                        .getNodes().get(0)
                        .beginPFP(params.get("data"));
        });
        context.getFilters().add(new ParameterFilter());
        server.setExecutor(executor);
        server.start();
    }

    /**
     * Setup and deploy the JS Runtime wrapper.
	 *
     * @param nodes
     *
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
        JavascriptEngine.script = () -> "var PFP = Java.type(\"org.kihara.PFPController\")\n";
        JavascriptEngine.shell(System.in, System.out, System.err);
    }
}
