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

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.future.FutureLatch;
import com.avaidyam.binoculars.Cortex;
import com.avaidyam.binoculars.util.Eponym;
import com.avaidyam.binoculars.util.Log;
import org.kihara.util.SeekableFile;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LZerDController extends Nucleus<LZerDController> {

    //
    // CONTEXT:
    //

    // --------------------------------------------------------------------
    // Context for PFP application jobs, including FASTA and divisions.
    public static final String TAG = "[LZerD]";
    static class JobContext implements Serializable {
        PrintWriter writer = null;
        FutureLatch<Void> latch = null;
        String path = "";
        List<Integer> divisions = new ArrayList<>();
        String name = "";
        int total = 0;
    }
    // --------------------------------------------------------------------

    //
    // SET + GET
    //

    // --------------------------------------------------------------------
    // Set + Get for current execution context.
    JobContext context = null;
    public void startJobContext(String path, String name, int total, List<Integer> divisions) {
        JobContext ctx = new JobContext();
        ctx.path = path;
        ctx.divisions = divisions;
        ctx.name = name;
        ctx.total = total;
        self().context = ctx;
    }
    public Future<Boolean> hasJobContext() {
        return new CompletableFuture<>((self().context != null));
    }
    public void clearJobContext() {
        self().context = null;
    }
    // --------------------------------------------------------------------

    // --------------------------------------------------------------------
    // Set + Get for current store path.
    String storePath = System.getProperty("user.dir");
    public Future<String> getStorePath() {
        return new CompletableFuture<>(storePath);
    }
    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }
    // --------------------------------------------------------------------

    // --------------------------------------------------------------------
    // Handy JS function to begin PFP without messing with Cortex.
    public static void go(String receptorFile, String ligandFile) throws Exception {
        Cortex.of(LZerDController.class)
                .getNodes().get(0)
                .runLzerdFlow(receptorFile, ligandFile);
    }
    // --------------------------------------------------------------------


    // --------------------------------------------------------------------
    // Helper lambda to concisely produce processes for PFP.
    Function<String[], ProcessBuilder> _lzerd = (String ... args) -> {
        System.out.println("Running " + Arrays.toString(args));
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(new File(storePath))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.environment().put("BLASTMAT", storePath + "/bin/data");
        return pb;
    };
    // --------------------------------------------------------------------


    // Runs mark_sur
    // Returns output file abs. path as String
    public String runMarkSur(String inputFile) throws IOException, InterruptedException {
        Log.i(TAG, "Step 1: Running mark_sur.");
        _lzerd.apply(new String[]{"echo", "test1"})
                .start().waitFor();
        _lzerd.apply(new String[]{"echo", "test2"})
                .start().waitFor();
        _lzerd.apply(new String[]{"echo", "test3"})
                .start().waitFor();
        _lzerd.apply(new String[]{"echo", "test4"})
                .start().waitFor();

        return "";
    }

    // Runs GETPOINTS
    // Returns output file abs. path as String
    public HashMap<String, String> runGetPoints(String inputFile) throws IOException, InterruptedException {
        Log.i(TAG, "Step 2: Running GETPOINTS.");
        HashMap<String, String> outputFiles = new HashMap<>();
        outputFiles.put("cp-txt", "");
        outputFiles.put("gts", "");
        return outputFiles;
    }

    // Runs LZD32
    // Returns output file abs. path as String
    public String runLzd32(String inputFile) throws IOException, InterruptedException {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 3: Running LZD32.");
        return "";
    }

    // Runs LZerD
    // Returns output file abs. path as String
    public Future<String> runLzerd(HashMap<String, String> inputFiles) {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 4: Running LZerD.");
        promise.complete("");
        return promise;
    }

    public Future<HashMap<String, String>> prepareFile(String inputFile) {
        CompletableFuture<HashMap<String, String>> promise = new CompletableFuture<>();

        try {

            Log.i(TAG, "Initiating file preparation.");

            HashMap<String, String> outputFiles = new HashMap<>();

            String mso = runMarkSur(inputFile);
            outputFiles.put("mark_sur", mso);

            HashMap<String, String> gpo = runGetPoints(mso);
            outputFiles.put("getpoints-cp-txt", gpo.get("cp-txt"));
            outputFiles.put("getpoints-gts", gpo.get("gts"));

            String lzo = runLzd32(gpo.get("cp-txt"));
            outputFiles.put("lzd32", lzo);
            promise.complete();
        } catch (IOException | InterruptedException e) {
            promise.completeExceptionally(e);
        }

        return promise;
    }

    public void appendInputFiles(String prefix, HashMap<String, String> inputFiles, HashMap<String, String> appendTo) {
        for (String key : inputFiles.keySet()) {
            appendTo.put(prefix + "-" + key, inputFiles.get(key));
        }
    }

    // Runs LZerD pipeline
    // Returns output file abs. path as String
    public void runLzerdFlow(String receptorFile, String ligandFile) {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Initating LZerD.");

        List<LZerDController> lzerd = Cortex.of(LZerDController.class).getNodes();

        boolean whichFile = true;
        HashMap<String, String> inputFiles = new HashMap<>();

        ArrayList<Future> futureQueue = new ArrayList<>();
        for (LZerDController c : lzerd) {
            if (c.hasJobContext().await())
                continue;

            futureQueue.add(c.prepareFile(receptorFile).then((ro, re) -> {
                appendInputFiles("receptor", ro, inputFiles);
            }));
            futureQueue.add(c.prepareFile(ligandFile).then((lo, le) -> {
                appendInputFiles("ligand", lo, inputFiles);
            }));
            break;
        }

        // Wait for all of the futures to complete
        for (Future f : futureQueue) f.await();
        futureQueue.clear();

        Log.i(TAG, "Should be done with file preparation.");
        Log.i(TAG, "Results:");
        for (String key : inputFiles.keySet()) {
            Log.i(TAG, key + " : " + inputFiles.get(key));
        }

        for (LZerDController c : lzerd) {
            if (c.hasJobContext().await())
                continue;

            c.runLzerd(inputFiles).then((lo, le) -> {
                Log.i(TAG, "Finished LzerD.");
            });
        }
    }
}