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
import org.kihara.util.FileWatcher;
import org.kihara.util.MigrationVisitor;
import org.kihara.util.ParameterFilter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Entry point for the sample application (PFP).
 */
public class Main {

    private static FileWatcher watcher;
    private static LinkedList<Map<String, String>> allJobs;
    private static PLPatchSurferController plps;

    /**
     * For every added folder, grab the manifest.mf file and translate it
     * into a map (which contains POST and FILES data for the job.
     *
     * @param source
     * @param events
     * @return
     */
    private static List<Map<String, String>> jobWatcher(Path source, List<WatchEvent<?>> events, String jobType, Path jobDest) {
        List<Map<String, String>> jobs = new LinkedList<>();
        for (WatchEvent<?> event : events) {
            Path file = source.resolve((Path) event.context());
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                try {
                    Map<String, String> m = Files.lines(file.resolve("manifest.mf")).map(s -> {
                        String parts[] = s.split(": ");
                        return new AbstractMap.SimpleEntry<>(parts[0], parts[1]);
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (Objects.equals(m.get("service"), jobType)) {
                        Path end = jobDest.resolve(file.getFileName()).toAbsolutePath();
                        MigrationVisitor.migrate(file, end, true, REPLACE_EXISTING);
                        m.put("_path", end.toString());
                        Log.d("JOB", "Job Discovered [" + jobType + "] = " + m.toString());
                        jobs.add(m);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return jobs;
    }

    /**
     *
     *
     * @throws Exception
     */
    private static void runJob(Map<String, String> manifest) throws Exception {
        if (Main.plps.hasState().await() || manifest == null) {
            throw new RuntimeException("Can't do that!");
        }

        String path = manifest.get("_path");
        Main.plps.provideInputs(path, "PLPSSample2", path + manifest.get("receptor"),
                                path + manifest.get("ligand"), manifest.get("email")).await();
        Main.plps.generateInputs().await();
        Main.plps.prepareReceptor().await();
        //Main.plps.prepareLigands().await();
        Main.plps.compareSeedsDB().await();
        Main.plps.compareLigands().then((r, e) -> {
            try {
                if (e != null) {
                    e.printStackTrace();
                } else {
                    Log.d("Main", "Finished task!");
                    Runtime.getRuntime().exec("echo \"JOB DONE\" | mail -s \"Job Results\" avaidyam@purdue.edu");
                    Main.plps.clearState();
                    Main.plps.setConfiguration(null);
                    notifyJob();
                }
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        });
    }

    private static void notifyJob() {
        if (!Main.plps.hasState().await()) {
            try {
                Map<String, String> job = allJobs.pop();
                if (job != null) {
                    runJob(job);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    /**
     * Initialize the Cortex, and start the HTTP server and shell.
     */
    public static void main(String[] args) throws Exception {
        Log.get().setSeverity(Log.Severity.DEBUG);
        Main.allJobs = new LinkedList<>();
        Main.plps = Nucleus.of(PLPatchSurferController.class);
        Main.watcher = FileWatcher.watch((p, e) -> {
            allJobs.addAll(Main.jobWatcher(p, e, "plps", Paths.get("/net/kihara/avaidyam/PatchSurferFiles/")));
            notifyJob();
        }, "/bio/kihara-web/www/binoculars/upload/");
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
