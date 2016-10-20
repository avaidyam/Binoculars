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
    String LZerDdir = storePath + "/bin/lzerd";
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

    public static void sendEmailTest() {
        Cortex.of(LZerDController.class)
                .getNodes().get(0)
                .sendEmail();
    }

    // --------------------------------------------------------------------
    // Helper lambda to concisely produce processes for PFP.
    Function<String[], ProcessBuilder> _lzerd = (String ... args) -> {
        System.out.println("Running " + Arrays.toString(args));
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(new File(LZerDdir))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.environment().put("BLASTMAT", storePath + "/bin/data");
        return pb;
    };
    // --------------------------------------------------------------------


    // Runs mark_sur
    // Returns output file abs. path as String
    public Future<String> runMarkSur(String inputFileBase) throws IOException, InterruptedException {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 1: Running mark_sur.");
        String inputFile = "/tmp/" + inputFileBase + ".pdb";
        String outputFile = inputFile + ".ms";
        _lzerd.apply(new String[]{"./mark_sur", inputFile, outputFile})
                .start().waitFor();
        promise.complete(outputFile);
        return promise;
    }

    // Runs GETPOINTS
    // Returns output file abs. path as String
    public Future<HashMap<String, String>> runGetPoints(String inputFileBase) throws IOException, InterruptedException {
        CompletableFuture<HashMap<String, String>> promise = new CompletableFuture<>();

        Log.i(TAG, "Step 2: Running GETPOINTS.");

        double smooth = 0.35;
        String cut = "1e-04";

        String inputFile = "/tmp/" + inputFileBase + ".pdb.ms";

        _lzerd.apply(new String[]{"./GETPOINTS", "-pdb", inputFile, "-smooth", String.valueOf(smooth), "-cut", cut})
                .start().waitFor();

        HashMap<String, String> outputFiles = new HashMap<>();

        String cpTxt = inputFileBase + "_cp.txt";
        String gts = inputFileBase + ".gts";

        // TODO: Convert to temp files

        outputFiles.put("cp-txt", cpTxt);
        outputFiles.put("gts", gts);
        promise.complete(outputFiles);
        return promise;
    }

    // Runs LZD32
    // Returns output file abs. path as String
    public Future<String> runLzd32(String inputFileBase) throws IOException, InterruptedException {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 3: Running LZD32.");

        String gtsInput = inputFileBase + ".gts";
        String cpInput = inputFileBase + "_cp.txt";
        String outputFile = inputFileBase + "_01.inv";

        int dim = 161;
        double rad = 6;
        int ord = 10;

        _lzerd.apply(new String[]{"./LZD32", "-g", gtsInput, "-c", cpInput, "-o", inputFileBase,
                "-dim", String.valueOf(dim), "-rad", String.valueOf(rad), "-ord", String.valueOf(ord) })
                .start().waitFor();

        promise.complete(LZerDdir + "/" + outputFile);
        return promise;
    }

    // Runs LZerD
    // Returns output file abs. path as String
    public Future<String> runLzerd(HashMap<String, String> inputFiles) throws IOException, InterruptedException {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 4: Running LZerD.");

        String rec_cp = inputFiles.get("receptor-getpoints-cp-txt");
        String lig_cp = inputFiles.get("ligand-getpoints-cp-txt");

        String recBaseName = inputFiles.get("receptor-base");

        String ligBaseName = inputFiles.get("ligand-base");

        String rec_ms = inputFiles.get("receptor-mark_sur");
        String lig_ms = inputFiles.get("ligand-mark_sur");

        String rec_inv = inputFiles.get("receptor-lzd32");
        String lig_inv = inputFiles.get("ligand-lzd32");

        double rfmin = 4.0;
        double rfmax = 9.0;
        double rfpmax = 15.0;
        int nvotes = 8;
        double cor = 0.7;
        double dist = 2.0;
        double nrad = 2.5;

        String outFile = recBaseName + "_" + ligBaseName + ".out";

        _lzerd.apply(new String[]{"./LZerD1.0", "-rec", rec_cp, "-lig", lig_cp,
                "-prec", rec_ms, "-plig", lig_ms, "-zrec", rec_inv,
                "-zlig", lig_inv, "-rfmin", String.valueOf(rfmin), "-rfmax", String.valueOf(rfmax),
                "-rfpmax", String.valueOf(rfpmax), "-nvotes", String.valueOf(nvotes), "-cor", String.valueOf(cor),
                "-dist", String.valueOf(dist), "-nrad", String.valueOf(nrad), ">", outFile})
                .start().waitFor();

        promise.complete(outFile);
        return promise;
    }

    public Future<HashMap<String, String>> prepareFile(String inputFileBase) {
        CompletableFuture<HashMap<String, String>> promise = new CompletableFuture<>();

        try {
            Log.i(TAG, "Initiating file preparation.");

            HashMap<String, String> outputFiles = new HashMap<>();
            outputFiles.put("base", inputFileBase);

            runMarkSur(inputFileBase).then((mso, mse) -> {
                outputFiles.put("mark_sur", mso);
                try {
                    runGetPoints(inputFileBase).then((gpo, gpe) -> {
                        outputFiles.put("getpoints-cp-txt", gpo.get("cp-txt"));
                        outputFiles.put("getpoints-gts", gpo.get("gts"));
                        try {
                            runLzd32(inputFileBase).then((lzo, lze) -> {
                                outputFiles.put("lzd32", lzo);
                                promise.complete(outputFiles);
                            });
                        } catch (IOException | InterruptedException e) {
                            promise.completeExceptionally(e);
                        }
                    });
                } catch (IOException | InterruptedException e) {
                    promise.completeExceptionally(e);
                }
            });
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

    // Deletes a file by its path
    public Future<Void> deleteFile(String filePath) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        Path thePath = Paths.get(filePath);
        try {
            Files.delete(thePath);
            promise.complete();
        } catch (IOException e) {
            promise.completeExceptionally(e);
        }
        return promise;
    }

    // Removes all the intermediate files
    public Future<Void> cleanOutFiles(HashMap<String, String> outFiles) {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        String[] fileKeys = {"receptor-mark_sur", "ligand-mark_sur",
                "receptor-getpoints-cp-txt", "ligand-getpoints-cp-txt",
                "receptor-getpoints-gts", "ligand-getpoints-gts",
                "receptor-lzd32", "ligand-lzd32"
               // , "lzerd-out"
        };

        for (String key : fileKeys) {
            deleteFile(outFiles.get(key));
        }

        promise.complete();
        return promise;
    }

    public Future<Void> sendEmail() {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        try {
            /*
            ProcessBuilder mailProcessBuilder = _lzerd.apply(new String[]{"mail", "-s", "\"LZerD job complete!\"", "waldena@purdue.edu"});
            ProcessBuilder echoProcessBuilder = _lzerd.apply(new String[]{"echo", "\"The LZerD job you started is complete!\""});

            ProcessBuilder.Redirect mailInput = mailProcessBuilder.redirectInput();
            ProcessBuilder.Redirect echoOutput = echoProcessBuilder.redirectOutput();

            mailProcessBuilder.redirectInput(echoOutput);
            echoProcessBuilder.redirectOutput(mailInput);

            Process echoProcess = echoProcessBuilder.start();
            Process mailProcess = mailProcessBuilder.start();

            echoProcess.waitFor();
            mailProcess.waitFor();

            echoProcessBuilder.redirectOutput(echoOutput);
            mailProcessBuilder.redirectInput(mailInput);
            */

            _lzerd.apply(new String[]{"echo \"The LZerD job you started is complete!\" | mail -s \"LZerD job complete\" waldena@purdue.edu"})
                    .start().waitFor();
            promise.complete();
        } catch (InterruptedException | IOException e) {
            promise.completeExceptionally(e);
        }
        return promise;
    }

    // Runs LZerD pipeline
    // Returns output file abs. path as String
    public Future<String> runLzerdFlow(String receptorFile, String ligandFile) {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Initating LZerD.");

        String recBaseName = Paths.get(receptorFile).getFileName().toString();
        if (recBaseName.indexOf('.') > 0) recBaseName = recBaseName.substring(0, recBaseName.indexOf('.'));
        String ligBaseName = Paths.get(ligandFile).getFileName().toString();
        if (ligBaseName.indexOf('.') > 0) ligBaseName = ligBaseName.substring(0, ligBaseName.indexOf('.'));

        // Convert to .pdb.ms (mark_sur)
        // Get CP (GETPOINTS)
        // Get ZINV (LZD32)
        // Run LZerD (LZerD1.0)
        // Sort output (grep and stuff)
        // Output top ranked results (PDBGEN)

        List<LZerDController> lzerd = Cortex.of(LZerDController.class).getNodes();

        boolean whichFile = true;
        HashMap<String, String> inputFiles = new HashMap<>();

        ArrayList<Future> futureQueue = new ArrayList<>();
        for (LZerDController c : lzerd) {
            if (c.hasJobContext().await())
                continue;

            futureQueue.add(c.prepareFile(recBaseName).then((ro, re) -> {
                appendInputFiles("receptor", ro, inputFiles);
            }));
            futureQueue.add(c.prepareFile(ligBaseName).then((lo, le) -> {
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
            try {
                c.runLzerd(inputFiles).then((lo, le) -> {
                    Log.i(TAG, "Finished LzerD.");
                    cleanOutFiles(inputFiles);
                    inputFiles.put("lzerd-out", lo);
                    promise.complete(lo);
                    sendEmail();
                });
            } catch (IOException | InterruptedException e) {
                promise.completeExceptionally(e);
                break;
            }
        }
        return promise;
    }
}