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

import com.avaidyam.binoculars.Export;
import com.avaidyam.binoculars.Log;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import org.kihara.util.FileWatcher;
import org.kihara.util.Mailer;
import org.kihara.util.MigrationVisitor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.ProcessBuilder.Redirect.appendTo;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class LZerDController extends Nucleus<LZerDController> {
    //
    // CONTEXT:
    //

    // --------------------------------------------------------------------
    // Context for PFP application jobs, including FASTA and divisions.
    public static final String TAG = "[LZerD]";
    public static LinkedList<Map<String, String>> allJobs = new LinkedList<>();

    Configuration configuration = null;
    State state = null;

    public static class RemoteHeartbeat implements Serializable {
        public static List<String> remotes = Arrays.asList(
                "alien.bio.purdue.edu",
                "beluga.bio.purdue.edu",
                "dragon.bio.purdue.edu",
                "emu.bio.purdue.edu",
                "giraffe.bio.purdue.edu",
                "kiwi1.bio.purdue.edu",
                "kiwi2.bio.purdue.edu",
                "liger3.bio.purdue.edu",
                "lion.bio.purdue.edu",
                "maroon.bio.purdue.edu",
                "miffy.bio.purdue.edu",
                "owl.bio.purdue.edu",
                "panda.bio.purdue.edu",
                "poas.bio.purdue.edu",
                "puma.bio.purdue.edu",
                "qilin.bio.purdue.edu",
                "snake.bio.purdue.edu",
                "tiger.bio.purdue.edu");
    }

    public static class State implements Serializable {

        enum Stage implements Serializable {
            FAILED,
            INITIALIZED,
            PREP,
            LZERD,
            POSTPROCESSING,
            COMPLETE
        }

        enum PrepStage implements Serializable {
            FAILED,
            WAITING,
            INITIALIZED,
            MARK_SUR,
            GETPOINTS,
            LZD32,
            COMPLETE
        }

        enum PostStage implements Serializable {
            FAILED,
            WAITING,
            INITIALIZED,
            GREP,
            PDBGEN,
            CLUSTERING,
            DFIRE,
            GOAP,
            ITSCORE,
            COMPLETE
        }

        Stage stage = Stage.INITIALIZED;
        PrepStage receptorStage = PrepStage.WAITING;
        PrepStage ligandStage = PrepStage.WAITING;
        PostStage postStage = PostStage.WAITING;

        String path = "";

        String email = "";

        String receptorFile = "";
        String ligandFile = "";

        String recMarkSur = "";
        String ligMarkSur = "";

        String recGetPointsCP = "";
        String recGetPointsGTS = "";
        String ligGetPointsCP = "";
        String ligGetPointsGTS = "";

        String recLzd32 = "";
        String ligLzd32 = "";

        String recBaseName = "";
        String ligBaseName = "";

        String lzerdOutput = "";

        // TODO add variables for different result files

        @Override
        public String toString() {
            return "State{" +
                    "stage=" + stage +
                    ", receptorStage=" + receptorStage +
                    ", ligandStage=" + ligandStage +
                    ", postStage=" + postStage +
                    ", path=" + path +
                    ", email=" + email +
                    ", receptorFile=" + receptorFile +
                    ", ligandFile=" + ligandFile +
                    ", recMarkSur=" + recMarkSur +
                    ", ligMarkSur=" + ligMarkSur +
                    ", recGetPointsCP=" + recGetPointsCP +
                    ", recGetPointsGTS=" + recGetPointsGTS +
                    ", ligGetPointsCP=" + ligGetPointsCP +
                    ", ligGetPointsGTS=" + ligGetPointsGTS +
                    ", recLzd32=" + recLzd32 +
                    ", ligLzd32=" + ligLzd32 +
                    ", recBaseName=" + recBaseName +
                    ", ligBaseName=" + ligBaseName +
                    ", lzerdOutput=" + lzerdOutput +
                    "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            if (!stage.equals(state.stage)) return false;
            if (!receptorStage.equals(state.receptorStage)) return false;
            if (!ligandStage.equals(state.ligandStage)) return false;
            if (!postStage.equals(state.postStage)) return false;
            if (!path.equals(state.path)) return false;
            if (!email.equals(state.email)) return false;
            if (!receptorFile.equals(state.receptorFile)) return false;
            if (!ligandFile.equals(state.ligandFile)) return false;
            if (!recMarkSur.equals(state.recMarkSur)) return false;
            if (!ligMarkSur.equals(state.ligMarkSur)) return false;
            if (!recGetPointsCP.equals(state.recGetPointsCP)) return false;
            if (!recGetPointsGTS.equals(state.recGetPointsGTS)) return false;
            if (!ligGetPointsCP.equals(state.ligGetPointsCP)) return false;
            if (!ligGetPointsGTS.equals(state.ligGetPointsGTS)) return false;
            if (!recLzd32.equals(state.recLzd32)) return false;
            if (!ligLzd32.equals(state.ligLzd32)) return false;
            if (!lzerdOutput.equals(state.lzerdOutput)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = stage.hashCode();
            result = 31 * result + receptorStage.hashCode();
            result = 31 * result + ligandStage.hashCode();
            result = 31 * result + postStage.hashCode();
            result = 31 * result + path.hashCode();
            result = 31 * result + email.hashCode();
            result = 31 * result + receptorFile.hashCode();
            result = 31 * result + ligandFile.hashCode();
            result = 31 * result + recMarkSur.hashCode();
            result = 31 * result + ligMarkSur.hashCode();
            result = 31 * result + recGetPointsCP.hashCode();
            result = 31 * result + recGetPointsGTS.hashCode();
            result = 31 * result + ligGetPointsCP.hashCode();
            result = 31 * result + ligGetPointsGTS.hashCode();
            result = 31 * result + recLzd32.hashCode();
            result = 31 * result + ligLzd32.hashCode();
            result = 31 * result + recBaseName.hashCode();
            result = 31 * result + ligBaseName.hashCode();
            result = 31 * result + lzerdOutput.hashCode();
            return result;
        }
    }

    public static class Configuration implements Serializable {

        double rfmin = 4.0;
        double rfmax = 9.0;
        double rfpmax = 15.0;
        int nvotes = 8;
        double cor = 0.7;
        double dist = 2.0;
        double nrad = 2.5;

        String lzerdPath = tilde("~/LZerD");
        String workingPath = tilde("~/LZerDFiles");

        @Override
        public String toString() {
            return "Configuration{" +
                    "rfmin=" + rfmin +
                    ", rfmax=" + rfmax +
                    ", rfpmax=" + rfpmax +
                    ", nvotes=" + nvotes +
                    ", cor=" + cor +
                    ", dist=" + dist +
                    ", nrad=" + nrad +
                    ", lzerdPath='" + lzerdPath + '\'' +
                    ", workingPath='" + workingPath + '\'' +
                    "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Configuration that = (Configuration) o;
            if (rfmin != that.rfmin) return false;
            if (rfmax != that.rfmax) return false;
            if (rfpmax != that.rfpmax) return false;
            if (nvotes != that.nvotes) return false;
            if (cor != that.cor) return false;
            if (dist != that.dist) return false;
            if (nrad != that.nrad) return false;
            if (!lzerdPath.equals(that.lzerdPath)) return false;
            if (!workingPath.equals(that.workingPath)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = (new Double(rfmin)).hashCode();
            result = 31 * result + (new Double(rfmax)).hashCode();
            result = 31 * result + (new Double(rfpmax)).hashCode();
            result = 31 * result + (new Double(nvotes)).hashCode();
            result = 31 * result + (new Double(cor)).hashCode();
            result = 31 * result + (new Double(dist)).hashCode();
            result = 31 * result + (new Double(nrad)).hashCode();
            result = 31 * result + lzerdPath.hashCode();
            result = 31 * result + workingPath.hashCode();
            return result;
        }
    }

    @Export
    public Future<Configuration> getConfiguration() {
        return new CompletableFuture<>(configuration);
    }

    @Export
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Export
    public void setState(State state) {
        this.state = state;
    }

    @Export
    public Future<Boolean> hasState() {
        return new CompletableFuture<>((this.state != null));
    }

    @Export
    public void clearState() {
        this.state = null;
    }

    // --------------------------------------------------------------------
    // Helper lambda to concisely produce processes for PFP.
    Function<String[], ProcessBuilder> _lzerd = (String[] args) -> {
        System.out.println("Running " + Arrays.toString(args));
        String log = this.state.path + "log.txt";
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(new File(this.state.path))
                .redirectOutput(appendTo(Paths.get(log).toFile()))
                .redirectError(appendTo(Paths.get(log).toFile()));
        return pb;
    };

    public void printPB() {
        Log.d(TAG, "_lzerd: " + _lzerd.toString());
    }

    // --------------------------------------------------------------------
    private static String tilde(String path) {
        return path.replaceFirst("^~", System.getProperty("user.home"));
    }


    @Export
    public void runJob(Map<String, String> manifest) throws Exception {
        Log.d("MAIN", "Found the files! Starting the job");

        if (self().hasState().await() || manifest == null) {
            throw new RuntimeException("Can't do that!");
        }
        try {
            String path = manifest.get("_path");
            self().provideInputs(path, manifest.get("email"), manifest.get("receptor"), manifest.get("ligand"));
            self().prepareFiles().await();
            //self().runLzerd().await();
            //self().runGrep().await();
            //self().runPDBGEN().await();
            //self().runPostProcessing().await();
            self().reportCompletion(10).await();

            String outbox = "/bio/kihara-web/www/unified/outbox";
            Path start = Paths.get(manifest.get("_path")).resolve("output");
            Path end = Paths.get(outbox);
            MigrationVisitor.migrate(start, end, true, REPLACE_EXISTING);

            String jobName = Paths.get(manifest.get("_path")).getFileName().toString();
            Mailer.mail("Kihara Lab <sbit-admin@bio.purdue.edu>", manifest.get("email"), "LZerD Job Results",
                    "Your LZerD job results can be found at http://kiharalab.org/unified/outbox/" + jobName + "/ and will be available for the next six months. Please access and download your results as needed.");

            self().clearState();
            self().setConfiguration(null);
            self().notifyJob();
        } catch(Exception e2) {
            Log.e("MAIN", "Failed to complete task.", e2);
            StringWriter sw = new StringWriter();
            e2.printStackTrace(new PrintWriter(sw));

            // Send the job failed message as an email.
            String jobName = Paths.get(manifest.get("_path")).getFileName().toString();
            Mailer.mail("Kihara Lab <sbit-admin@bio.purdue.edu>", manifest.get("email"), "LZerD Job Failed",
                    "Your LZerD job (" + jobName + ") failed to complete. Please contact us for support and provide the below trace message.\n\n" + sw.toString() + "\n");

            self().clearState();
            self().setConfiguration(null);
            self().notifyJob();
        }
    }

    @Export(transport=false)
    public void notifyJob() throws Exception {
        if (!self().hasState().await() && allJobs.size() > 0)
            self().runJob(allJobs.pop());
    }

    public Future<Void> provideInputs(String root, String email, String receptorFile, String ligandFile) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state != null && this.configuration != null) {
            return new CompletableFuture<>();
        }

        Configuration conf = new Configuration();
        this.configuration = conf;

        State st = new State();

        st.receptorFile = receptorFile;
        String recBaseName = Paths.get(receptorFile).getFileName().toString();
        if (recBaseName.indexOf('.') > 0) recBaseName = recBaseName.substring(0, recBaseName.indexOf('.'));
        st.recBaseName = recBaseName;

        st.ligandFile = ligandFile;
        String ligBaseName = Paths.get(ligandFile).getFileName().toString();
        if (ligBaseName.indexOf('.') > 0) ligBaseName = ligBaseName.substring(0, ligBaseName.indexOf('.'));
        st.ligBaseName = ligBaseName;

        st.path = tilde(root);
        st.email = email;
        st.receptorFile = receptorFile;
        st.ligandFile = ligandFile;
        st.stage = State.Stage.INITIALIZED;
        this.state = st;

        promise.complete();
        return promise;
    }

    // Runs mark_sur
    // Returns output file abs. path as String
    public Future<String> runMarkSur(String inputFile) {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 1: Running mark_sur.");
        String outputFile = inputFile + ".ms";
        try {
            Log.d(TAG, "_lzerd: " + _lzerd.toString());
            //_lzerd.apply(new String[]{"./mark_sur", inputFile, outputFile})
            //        .start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            promise.completeExceptionally(e);
            return promise;
        }
        promise.complete(outputFile);
        return promise;
    }

    public Future<Void> runRecMarkSur() {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        Log.d("MAIN", "recMarkSur");
        state.receptorStage = State.PrepStage.MARK_SUR;

        self().runMarkSur(state.receptorFile).then((o, e) -> {
            state.recMarkSur = o;
            promise.complete();
        });
        return promise;
    }

    public Future<Void> runLigMarkSur() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "ligMarkSur");
        state.ligandStage = State.PrepStage.MARK_SUR;

        self().runMarkSur(state.ligandFile).then((o, e) -> {
            state.ligMarkSur = o;
            promise.complete();
        });
        return promise;
    }

    // Runs GETPOINTS
    // Returns output file abs. path as String
    public Future<HashMap<String, String>> runGetPoints(String inputFileBase) {
        CompletableFuture<HashMap<String, String>> promise = new CompletableFuture<>();

        Log.i(TAG, "Step 2: Running GETPOINTS.");

        double smooth = 0.35;
        String cut = "1e-04";

        String inputFile = "/tmp/" + inputFileBase + ".pdb.ms";

        try {
            Log.d(TAG, "lzerd: " + _lzerd.toString());
            Log.d(TAG, "inputFile: " + inputFile);
            Log.d(TAG, "smooth: " + String.valueOf(smooth));
            Log.d(TAG, "cut: " + cut);
            _lzerd.apply(new String[]{"./GETPOINTS", "-pdb", inputFile, "-smooth", String.valueOf(smooth), "-cut", cut})
                    .start().waitFor();
        } catch (Exception e) {
            Log.d(TAG, "Failed GETPOINTS");
            e.printStackTrace();
            promise.completeExceptionally(e);
            return promise;
        }

        HashMap<String, String> outputFiles = new HashMap<>();

        String cpTxt = inputFileBase + "_cp.txt";
        String gts = inputFileBase + ".gts";

        // TODO: Convert to temp files

        Log.d(TAG, "CP-TXT: " + cpTxt);
        outputFiles.put("cp-txt", cpTxt);
        Log.d(TAG, "GTS: " + gts);
        outputFiles.put("gts", gts);
        promise.complete(outputFiles);
        return promise;
    }

    public Future<Void> runRecGetPoints() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "recGetPoints");
        state.receptorStage = State.PrepStage.GETPOINTS;

        self().runGetPoints(state.recBaseName).then((o, e) -> {
            String cpTxt = o.get("cp-txt");
            Log.d(TAG, "CP-TXT: " + cpTxt);
            state.recGetPointsCP = cpTxt;
            String gts = o.get("gts");
            Log.d(TAG, "GTS: " + gts);
            state.recGetPointsGTS = gts;
            promise.complete();
        });
        return promise;
    }

    public Future<Void> runLigGetPoints() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "ligGetPoints");
        state.ligandStage = State.PrepStage.GETPOINTS;

        self().runGetPoints(state.ligBaseName).then((o, e) -> {
            state.ligGetPointsCP = o.get("cp-txt");
            state.ligGetPointsGTS = o.get("gts");
            promise.complete();
        });
        return promise;
    }

    // Runs LZD32
    // Returns output file abs. path as String
    public Future<String> runLzd32(String inputFileBase) {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 3: Running LZD32.");

        String gtsInput = inputFileBase + ".gts";
        String cpInput = inputFileBase + "_cp.txt";
        String outputFile = inputFileBase + "_01.inv";

        int dim = 161;
        double rad = 6;
        int ord = 10;

        try {
            _lzerd.apply(new String[]{"./LZD32", "-g", gtsInput, "-c", cpInput, "-o", inputFileBase,
                    "-dim", String.valueOf(dim), "-rad", String.valueOf(rad), "-ord", String.valueOf(ord) })
                    .start().waitFor();
        } catch (Exception e) {
            promise.completeExceptionally(e);
            return promise;
        }

        promise.complete(configuration.workingPath + "/" + outputFile);
        return promise;
    }

    public Future<Void> runRecLzd32() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "recLzd32");
        state.receptorStage = State.PrepStage.LZD32;

        self().runLzd32(state.recBaseName).then((o, e) -> {
            state.recLzd32 = o;
            promise.complete();
        });
        return promise;
    }

    public Future<Void> runLigLzd32() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "ligLzd32");
        state.ligandStage = State.PrepStage.LZD32;

        self().runLzd32(state.ligBaseName).then((o, e) -> {
            state.ligLzd32 = o;
            promise.complete();
        });
        return promise;
    }

    public Future<Void> prepareReceptorFiles() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        state.receptorStage = State.PrepStage.INITIALIZED;

        self().runRecMarkSur().await();
        //self().runRecGetPoints().await();
        // self().runRecLzd32().await();

        state.receptorStage = State.PrepStage.COMPLETE;

        promise.complete();
        return promise;
    }

    public Future<Void> prepareLigandFiles() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        state.ligandStage = State.PrepStage.INITIALIZED;

        self().runLigMarkSur().await();
        self().runLigGetPoints().await();
        self().runLigLzd32().await();

        state.ligandStage = State.PrepStage.COMPLETE;

        promise.complete();
        return promise;
    }

    public Future<Void> prepareFiles() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        state.stage = State.Stage.PREP;

        prepareReceptorFiles().await();
        // prepareLigandFiles().await();

        promise.complete();
        return promise;
    }

    // Runs LZerD
    // Returns output file abs. path as String
    public Future<Void> runLzerd() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "lzerd");
        state.stage = State.Stage.LZERD;

        Log.i(TAG, "Step 4: Running LZerD.");

        String rec_cp = state.recGetPointsCP;
        String lig_cp = state.ligGetPointsCP;

        String recBaseName = state.recBaseName;

        String ligBaseName = state.ligBaseName;

        String rec_ms = state.recMarkSur;
        String lig_ms = state.ligMarkSur;

        String rec_inv = state.recLzd32;
        String lig_inv = state.ligLzd32;

        double rfmin = configuration.rfmin;
        double rfmax = configuration.rfmax;
        double rfpmax = configuration.rfpmax;
        int nvotes = configuration.nvotes;
        double cor = configuration.cor;
        double dist = configuration.dist;
        double nrad = configuration.nrad;

        String outFile = recBaseName + "_" + ligBaseName + ".out";

        try {
            _lzerd.apply(new String[]{"./LZerD1.0", "-rec", rec_cp, "-lig", lig_cp,
                    "-prec", rec_ms, "-plig", lig_ms, "-zrec", rec_inv,
                    "-zlig", lig_inv, "-rfmin", String.valueOf(rfmin), "-rfmax", String.valueOf(rfmax),
                    "-rfpmax", String.valueOf(rfpmax), "-nvotes", String.valueOf(nvotes), "-cor", String.valueOf(cor),
                    "-dist", String.valueOf(dist), "-nrad", String.valueOf(nrad)})
                    .redirectOutput(new File(configuration.workingPath + "/" + outFile))
                    .start().waitFor();
        } catch (Exception e) {
            promise.completeExceptionally(e);
            return promise;
        }

        state.lzerdOutput = outFile;

        promise.complete();
        return promise;
    }

    public Future<Void> runGrep() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "grep");
        String inputFile = state.lzerdOutput;
        File tmpFile = new File(inputFile + ".tmp");
        File redirectFile = new File(inputFile + ".v.tmp");

        try {
            _lzerd.apply(new String[]{"grep", "^LIG", inputFile})
                    .redirectOutput(tmpFile)
                    .start().waitFor();

            _lzerd.apply(new String[]{"grep", "-v", "^LIG", inputFile})
                    .redirectOutput(redirectFile)
                    .start().waitFor();

            _lzerd.apply(new String[]{"sort", "-k", "13,13", "-nr"})
                    .redirectInput(redirectFile)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(tmpFile))
                    .start().waitFor();

            _lzerd.apply(new String[]{"mv", tmpFile.getAbsolutePath(), inputFile})
                    .start().waitFor();
        } catch (Exception e) {
            promise.completeExceptionally(e);
            return promise;
        }

        promise.complete();
        return promise;
    }

    public Future<Void> runPDBGEN() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        Log.d("MAIN", "pdbgen");
        String receptorFile = state.receptorFile;
        String ligandFile = state.ligandFile;
        String outFile = state.lzerdOutput;

        try {
            _lzerd.apply(new String[]{"./PDBGEN", receptorFile, ligandFile, outFile, "3"})
                    .start().waitFor();
        } catch (Exception e) {
            promise.completeExceptionally(e);
            return promise;
        }

        promise.complete();
        return promise;
    }

    public Future<Void> runClustering() {
        CompletableFuture<Void> promise = new CompletableFuture<>();


        Log.i(TAG, "Should perform clustering");

        promise.complete();
        return promise;
    }

    public Future<Void> runDFIRE() {
        CompletableFuture<Void> promise = new CompletableFuture<>();


        Log.i(TAG, "Should perform DFIRE scoring");

        promise.complete();
        return promise;
    }

    public Future<Void> runGOAP() {
        CompletableFuture<Void> promise = new CompletableFuture<>();


        Log.i(TAG, "Should perform GOAP scoring");

        promise.complete();
        return promise;
    }

    public Future<Void> runITScore() {
        CompletableFuture<Void> promise = new CompletableFuture<>();


        Log.i(TAG, "Should perform ITScore scoring");

        promise.complete();
        return promise;
    }

    public Future<Void> runScoring() {
        CompletableFuture<Void> promise = new CompletableFuture<>();


        Log.i(TAG, "Should start scoring");

        ArrayList<Future<Void>> futureQueue = new ArrayList<>();

        futureQueue.add(runGOAP());
        futureQueue.add(runITScore());
        futureQueue.add(runDFIRE());

        for (Future f : futureQueue) f.await();

        Log.i(TAG, "All scores done");

        promise.complete();
        return promise;
    }

    public Future<Void> runPostProcessing() {
        CompletableFuture<Void> promise = new CompletableFuture<>();


        Log.i(TAG, "Should start post processing");

        runClustering().then((r, e) -> {
            runScoring().then((r2, e2) -> {
                Log.i(TAG, "Should be done with post processing.");
                promise.complete();
            });
        });
        return promise;
    }

    @Export
    public Future<Void> reportCompletion(int num_preview) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        InputStream t = getClass().getResourceAsStream("/templates/visualizer_template.html");
        String template = new BufferedReader(new InputStreamReader(t))
                .lines().collect(Collectors.joining("\n"));
        final String inside = template.replaceAll("[\\s\\S]*(<!--START-->)|(<!--END-->)[\\s\\S]*", "");

        final int[] idx = {0};
        String output[] = {""};

        // Gather state and move things over.
        String dbDest = this.state.path + "/output";
        Path folder = Paths.get(this.state.path).getFileName();
        // String dbSource = this.configuration.pdbSources;
        String rank = this.state.lzerdOutput;

        //
        Files.createDirectory(Paths.get(dbDest));
        Files.createDirectory(Paths.get(dbDest).resolve(folder));
        Files.copy(Paths.get(rank), Paths.get(dbDest).resolve(folder).resolve("out.rank"));

        /*
        Files.lines(Paths.get(rank)).limit(num_preview).forEachOrdered((s) -> {
            String parts[] = s.split("\\s+");

            // Makeshift templating engine here...
            String temp = inside;
            temp = temp.replace("![NUM]", "" + idx[0]);
            temp = temp.replace("![NAME]", parts[0]);
            temp = temp.replace("![SCORE]", parts[1]);

            output[0] += temp;
            idx[0]++;
        });
        */
        // Write the results HTML output.
        String out = template.replaceFirst("(<!--START-->)[\\s\\S]*(<!--END-->)", output[0]);
        Files.write(Paths.get(dbDest).resolve(folder).resolve("index.html"), out.getBytes());

        promise.complete();
        return promise;
    }

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
        Log.d(TAG, "Should start job watcher");
        for (WatchEvent<?> event : events) {
            Path file = source.resolve((Path) event.context());
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                try {
                    Thread.sleep(500); // pls die already

                    Map<String, String> m = Files.lines(file.resolve("manifest.mf")).map(s -> {
                        String parts[] = s.split(": ");
                        return new AbstractMap.SimpleEntry<>(parts[0], parts[1]);
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (Objects.equals(m.get("service"), jobType)) {
                        Path end = jobDest.resolve(file.getFileName()).toAbsolutePath();
                        MigrationVisitor.migrate(file, end, true, REPLACE_EXISTING);
                        m.put("_path", end.toString() + "/");
                        Log.d("JOB", "Job Discovered [" + jobType + "] = " + m.toString());
                        jobs.add(m);
                    }
                    else {
                        Log.d(TAG, "Not the right job type: " + m.get("service"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return jobs;
    }

    /**
     * Initialize the LZerDController and watch for jobs.
     */
    public static void main(String[] args) throws Exception {
        Log.get().setSeverity(Log.Severity.DEBUG);
        LZerDController controller = Nucleus.of(LZerDController.class);

        FileWatcher watcher = FileWatcher.watch((p, e) -> {
            LZerDController.allJobs.addAll(LZerDController.jobWatcher(p, e, "lzerd", Paths.get("/net/kihara/waldena/LZerDFiles/")));

            try {
                controller.notifyJob();
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        }, "/bio/kihara-web/www/unified/inbox/");
    }
}
