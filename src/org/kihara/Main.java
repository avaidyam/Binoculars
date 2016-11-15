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
import com.avaidyam.binoculars.util.Log;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.kihara.util.ParameterFilter;
import org.kihara.util.TicketManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Entry point for the sample application (PFP).
 */
public class Main {

    private static TicketManager<String> ticketManager;

    /**
     * Initialize the Cortex, and start the HTTP server and shell.
     */
    public static void main(String[] args) {
        Log.get().setSeverity(Log.Severity.DEBUG);
        // Cortex<PFPController> cortex = Cortex.of(PFPController.class);
        Cortex<LZerDController> cortex = Cortex.of(LZerDController.class);
        try {
            // PFPController main = cortex.getNodes().get(0);
            LZerDController main = cortex.getNodes().get(0);
            startHTTP(8080, main);
            startShell(cortex.getNodes());
        } catch (Exception e) {
            Log.e("Main", "Could not begin application.", e);
        }
    }

    /**
     * Custom handler for file upload.
     */
    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {

            for(Map.Entry<String, List<String>> header : t.getRequestHeaders().entrySet()) {
                System.err.println(header.getKey() + ":");
                for (String value : header.getValue()) {
                    System.err.println(value);
                }
            }

            DiskFileItemFactory d = new DiskFileItemFactory();

            try {
                ServletFileUpload up = new ServletFileUpload(d);
                new ServletFileUpload();
                List<FileItem> result = up.parseRequest(new RequestContext() {

                    @Override
                    public String getCharacterEncoding() {
                        return "UTF-8";
                    }

                    @Override
                    public int getContentLength() {
                        return 0; //tested to work with 0 as return
                    }

                    @Override
                    public String getContentType() {
                        return t.getRequestHeaders().getFirst("Content-type");
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return t.getRequestBody();
                    }

                });
                t.getResponseHeaders().add("Content-type", "text/plain");
                t.sendResponseHeaders(200, 0);

                OutputStream os = t.getResponseBody();
                HashMap<String, String> inputFiles = new HashMap<>();
                for(FileItem fi : result) {
                    try {
                        if (fi.getSize() > 0) {
                            String fileName = fi.getName();
                            int dotIndex = fileName.indexOf(".");
                            String prefix = fileName.substring(0, dotIndex);
                            String suffix = fileName.substring(dotIndex);
                            File temp = File.createTempFile(prefix, suffix);
                            temp.deleteOnExit();
                            fi.write(temp);
                            System.out.println("Temp file path: " + temp.getAbsolutePath());
                            inputFiles.put(fi.getFieldName(), temp.getAbsolutePath());
                        }
                        os.write(fi.getName().getBytes());
                        os.write("\r\n".getBytes());
                        System.out.println("File-Item: " + fi.getFieldName() + " = " + fi.getName());
                        System.out.println("Contents:");
                        System.out.println(fi.getString());
                    }
                    catch (NullPointerException e) {
                        continue;
                    }
                }
                os.close();
                if (inputFiles.containsKey("receptor") && inputFiles.containsKey("ligand")) {
                    LZerDController c = Cortex.of(LZerDController.class)
                            .getNodes().get(0);
                    c.runLzerdFlow(inputFiles.get("receptor"), inputFiles.get("ligand")).then((r, e) -> c.sendEmail());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Jumpstart a quick HTTPServer atop the PFPController
     * Yes! We can turn the PFPController into an Executor,
     * and then apply parallelism or any API onto it.
     *
     * http://stackoverflow.com/questions/33732110/file-upload-using-httphandler
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
            /*
            if (params.get("data") != null)
                Cortex.of(PFPController.class)
                        .getNodes().get(0)
                        .beginPFP(params.get("data"));
            */
            // Test LZerDController;
        });
        context.getFilters().add(new ParameterFilter());
        server.createContext("/fileupload", new UploadHandler());

        ticketManager = new TicketManager<>();
        int testId1 = ticketManager.getNewTicket();
        ticketManager.set(testId1, "The quick brown fox jumps over the lazy dog");
        Log.d("Main.java", "testId1: " + String.valueOf(testId1));
        int testId2 = ticketManager.getNewTicket();
        ticketManager.set(testId2, "The fish was delish and it made quite a dish");
        Log.d("Main.java", "testId2: " + String.valueOf(testId2));

        HttpContext ticketContext = server.createContext("/ticket", exchange -> {
            Map<String, String> params = (Map<String, String>)exchange.getAttribute("parameters");
            if (params.containsKey("id") && params.get("id") != null) {
                String idString = params.get("id");
                String response = "Ticket not found";
                try {
                    int id = Integer.parseInt(idString);
                    if (ticketManager.hasTicket(id) && ticketManager.isSet(id)) {
                        response = ticketManager.get(id);
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        });
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
    // public static void startShell(List<PFPController> nodes) throws Exception {
    public static void startShell(List<LZerDController> nodes) throws Exception {
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
        JavascriptEngine.script = () -> "var PFP = Java.type(\"org.kihara.PFPController\");\n" +
                "var LZerD = Java.type(\"org.kihara.LZerDController\")\n";
        JavascriptEngine.shell(System.in, System.out, System.err);
    }
}
