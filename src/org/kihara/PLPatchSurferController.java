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
import com.avaidyam.binoculars.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.ProcessBuilder.Redirect.appendTo;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/*
-- 	algorithm:
	- distribution: tell(job.data) to enqueue
	- heartbeat: ask(queue.count, sys.stats) to analyze
	- stages: tell(job.stage) to advance
*/

/**
 * The PLPatchSurferController nucleus is designed to wrap the PLPatchSurfer
 * tools and execute them in a managed distributed manner with state safety.
 */
public class PLPatchSurferController extends Nucleus<PLPatchSurferController> {
    public static final String TAG = "[PLPatchSurferController]";

    Configuration configuration = null;
    State state = null;

    /**
     * The Configuration of a PLPatchSurferController defines its tool locations
     * and working directory; this rarely needs to be changed, and if it does,
     * it will invalidate the state of every instance of the controller with it.
     */
    static class Configuration implements Serializable {

        String plpsPath = "~/PatchSurfer/";
        String workingPath = "~/PatchSurferFiles/";
        String pdb2pqrPath = "/apps/pdb2pqr/current/";
        String apbsPath = "/apps/apbs/apbs-1.4.2/";
        String babelPath = "/usr/bin";
        String xlogp3Path = "~/PatchSurfer/XLOGP3/bin/";
        String omegaPath = "~/PatchSurfer/openeye/arch/Ubuntu-12.04-x64/omega";
        String omegaLicense = "~/PatchSurfer/openeye/oe_license.txt";
        String databaseSet = //"/net/kihara/shin183/DATA-scratch/zinc_druglike_90/druglike.list";
                            //"/net/kihara/shin183/DATA-scratch/chembl/chembl_19/chembl.list";
                            //"/net/kihara/avaidyam/PLPSSample/PLPSSample.list";
                            "/net/kihara/avaidyam/PLPSSample2/PLPSSample2.list";
        int n_conf = 50; // max n_conf for the database

        @Override
        public String toString() {
            return "Configuration{" +
                    "plpsPath='" + plpsPath + '\'' +
                    ", workingPath='" + workingPath + '\'' +
                    ", pdb2pqrPath='" + pdb2pqrPath + '\'' +
                    ", apbsPath='" + apbsPath + '\'' +
                    ", babelPath='" + babelPath + '\'' +
                    ", xlogp3Path='" + xlogp3Path + '\'' +
                    ", omegaPath='" + omegaPath + '\'' +
                    ", omegaLicense='" + omegaLicense + '\'' +
                    ", databaseSet='" + databaseSet + '\'' +
                    ", n_conf='" + n_conf + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Configuration that = (Configuration) o;
            if (!plpsPath.equals(that.plpsPath)) return false;
            if (!workingPath.equals(that.workingPath)) return false;
            if (!pdb2pqrPath.equals(that.pdb2pqrPath)) return false;
            if (!apbsPath.equals(that.apbsPath)) return false;
            if (!babelPath.equals(that.babelPath)) return false;
            if (!xlogp3Path.equals(that.xlogp3Path)) return false;
            if (!omegaPath.equals(that.omegaPath)) return false;
            if (!omegaLicense.equals(that.omegaLicense)) return false;
            if (!databaseSet.equals(that.databaseSet)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = plpsPath.hashCode();
            result = 31 * result + workingPath.hashCode();
            result = 31 * result + pdb2pqrPath.hashCode();
            result = 31 * result + apbsPath.hashCode();
            result = 31 * result + babelPath.hashCode();
            result = 31 * result + xlogp3Path.hashCode();
            result = 31 * result + omegaPath.hashCode();
            result = 31 * result + omegaLicense.hashCode();
            result = 31 * result + databaseSet.hashCode();
            return result;
        }
    }

    /**
     * The State of a PLPatchSurferController indicates its current job and
     * the completion of that job; it's designed to be capable of splitting
     * and redistributing the job amongst distant nodes and relies on an
     * underlying distributed file system (like MooseFS) to operate.
     */
    static class State implements Serializable {

        /**
         * The State.Stage describes the current step in processing that the
         * controller is in; this is useful to synchronize and steal work when
         * needed from distant nodes.
         */
        enum Stage implements Serializable {
            /*0*/ INITIALIZED,
            /*1*/ PREPARE_RECEPTOR,
            /*2*/ PREPARE_LIGANDS,
            /*3*/ COMPARE_SEEDS,
            /*4*/ COMPARE_LIGANDS,
            /*5*/ COMPLETE;
        }

        UUID uuid = null;
        String path = "";
        Stage stage = Stage.INITIALIZED;

        String receptorFilePDB = ""; //ex: rec.pdb
        String xtalLigand = ""; //ex: xtal-lig.pdb
        String outputFile = ""; //ex: lcs.rank

        @Override
        public String toString() {
            return "State{" +
                    "uuid=" + uuid +
                    ", path='" + path + '\'' +
                    ", stage=" + stage +
                    ", receptorFilePDB='" + receptorFilePDB + '\'' +
                    ", xtalLigand='" + xtalLigand + '\'' +
                    ", outputFile='" + outputFile + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return uuid.equals(state.uuid);
        }

        @Override
        public int hashCode() {
            return uuid.hashCode();
        }
    }


    static class Task implements Serializable {
        UUID uuid = null;
        String path = "";
        State.Stage stage = State.Stage.INITIALIZED;
    }

    /**
     *
     * @return
     */
    public Future<Configuration> getConfiguration() {
        return new CompletableFuture<>(configuration);
    }

    /**
     *
     * @param configuration
     */
    public void setConfiguration(Configuration configuration) {
        self().configuration = configuration;
    }

    /**
     *
     * @param state
     */
    public void setState(State state) {
        self().state = state;
    }

    /**
     *
     * @return
     */
    public Future<Boolean> hasState() {
        return new CompletableFuture<>((self().state != null));
    }

    /**
     *
     */
    public void clearState() {
        self().state = null;
    }

    @Override
    public void deinit() {
        Log.d(TAG, "Deinit executed!");
    }

    // --------------------------------------------------------------------
    // Helper lambda to concisely produce processes for PLPatchSurfer.
    Function<String[], ProcessBuilder> _plps = (String ... args) -> {
        System.out.println("Running " + Arrays.toString(args));
        String log = self().state.path + "log.txt";
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(new File(self().state.path))
                .redirectOutput(appendTo(Paths.get(log).toFile()))//INHERIT)
                .redirectError(appendTo(Paths.get(log).toFile()));//INHERIT);
        pb.environment().put("LD_LIBRARY_PATH", self().configuration.apbsPath + "lib/");
        pb.environment().put("OE_LICENSE", tilde(self().configuration.omegaLicense));
        return pb;
    };
    private static String tilde(String path) {
        return path.replaceFirst("^~", System.getProperty("user.home"));
    }
    // --------------------------------------------------------------------

    /**
     *
     *
     * @param receptorFile
     * @param xtalLigand
     * @return
     * @throws Exception
     */
    public Future<Void> begin(String receptorFile, String xtalLigand) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state != null && self().configuration != null) {
            return new CompletableFuture<>(new RuntimeException("Can't start another task!"));
        }
        Configuration conf = new Configuration();
        self().configuration = conf;

        // Initialize the state (job context).
        State st = new State();
        st.uuid = UUID.randomUUID();
        st.path = tilde(conf.workingPath) + st.uuid.toString().replaceAll("-", "") + "/";

        // Create the directory and move input files over.
        Path path = Paths.get(st.path);
        Path rfPath = Paths.get(tilde(receptorFile)); // EXPECTS FILENAME
        Path xtalPath = Paths.get(tilde(xtalLigand)); // EXPECTS FILENAME
        Files.createDirectories(path);
        Files.copy(rfPath, path.resolve(rfPath.getFileName()),
                    REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS);
        Files.copy(xtalPath, path.resolve(xtalPath.getFileName()),
                    REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS);

        // Mark the updated paths instead of the input ones.
        st.receptorFilePDB = rfPath.getFileName().toString();
        st.xtalLigand = xtalPath.getFileName().toString();

        // Wrap up and exit.
        Log.d(TAG, "Generated state " + st.toString());
        self().state = st;
        promise.complete();
        return promise;
    }

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> generateInputs() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        Configuration conf = self().configuration;
        State st = self().state;
        self().state.stage = State.Stage.PREPARE_RECEPTOR;

        // Expand all tildes once for effectiveness.
        //String plpsPath = tilde(conf.plpsPath);
        String pdb2pqrPath = tilde(conf.pdb2pqrPath);
        String apbsPath = tilde(conf.apbsPath) + "bin/";
        String babelPath = conf.babelPath;
        String rfSSIC = tilde(st.receptorFilePDB.replace(".pdb", ".ssic"));

        // Generate the input configuration files.
        String s1 = "PLPS_path\t" + conf.plpsPath + "\nPDB2PQR_path\t" + pdb2pqrPath + "\nAPBS_path\t" +
                apbsPath + "\nBABEL_path\t" + babelPath + "\nreceptor_file\t" + tilde(st.receptorFilePDB) +
                "\nligand_file\t" + tilde(st.xtalLigand);
        String s2 = "PLPS_path\t" + conf.plpsPath + "\nPDB2PQR_path\t" + pdb2pqrPath + "\nAPBS_path\t" +
                apbsPath + "\nXLOGP3_path\t" + tilde(conf.xlogp3Path) + "\nOMEGA_path\t" + tilde(conf.omegaPath) +
                "\nBABEL_path\t" + babelPath +"\nn_conf\t" + conf.n_conf;
        String s3 = "PLPS_path\t" + conf.plpsPath + "\nreceptor_file\t" + rfSSIC;
        String s4 = "receptor_file\t" + rfSSIC + "\noutput_file\tout.rank";

        try (Stream<String> lines = Files.lines(Paths.get(conf.databaseSet))) {
            String set = lines
                    .map((lig) -> "\nligand_dir\t" + lig)
                    .reduce((r, s) -> r + s)
                    .orElse("");

            //s2 += "\nligand_file\t" + lig + ".mol2";
            //s3 += set;
            s4 += set;
        }

        // Export the configuration files.
        try (
                PrintWriter out1 = new PrintWriter(st.path + "s1.in");
                PrintWriter out2 = new PrintWriter(st.path + "s2.in");
                PrintWriter out3 = new PrintWriter(st.path + "s3.in");
                PrintWriter out4 = new PrintWriter(st.path + "s4.in");
        ) {
            out1.println(s1);
            out2.println(s2);
            out3.println(s3);
            out4.println(s4);

            // Notify completion or error status here.
            Log.d(TAG, "Generated input files at" + st.path + ".");
            promise.complete();
        } catch (Exception e) {
            promise.completeExceptionally(e);
        }
        return promise;
    }

    // Step 1:
    // Receptor PDB: PDB format of receptor structure.
    // Ligand PDB: PL-PatchSurfer2 defines receptor pocket by ray-casting method from
    //             co-crystalized ligand. It should be in PDB format.

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> prepareReceptor() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        self().state.stage = State.Stage.PREPARE_LIGANDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/prepare_receptor.py", self().state.path + "s1.in"})
                .start().waitFor();

        promise.complete();
        return promise;
    }

    // Step 2:
    // Ligand MOL2: ligand files to be screened. they should be prepared in MOL2 format.
    // n_conf: number of maximum conformations to be generated for each ligand.

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> prepareLigands() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        self().state.stage = State.Stage.COMPARE_SEEDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/prepare_ligands.py", self().state.path + "s2.in"})
                .start().waitFor();

        promise.complete();
        return promise;
    }

    // Step 3:

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> compareSeeds() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        self().state.stage = State.Stage.COMPARE_LIGANDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/compare_seeds.py", self().state.path + "s3.in"})
                .start().waitFor();

        promise.complete();
        return promise;
    }

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> compareSeedsDB() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        self().state.stage = State.Stage.COMPARE_LIGANDS;

        String db = self().configuration.databaseSet;
        String par = Paths.get(db).getParent().toString();
        try (Stream<String> lines = Files.lines(Paths.get(db))) {
            lines.forEach((s) -> {
                try {

                    // Run script on the generated input.
                    String loc = tilde(self().configuration.plpsPath) + "scripts/compare_seeds_indiv.py";
                    _plps.apply(new String[]{"python", loc, self().state.path + "s3.in", par + "/" + s, "" + self().configuration.n_conf})
                            .start().waitFor();
                } catch(Exception ignored) {}
            });
        }

        promise.complete();
        return promise;
    }

    // Step 4:

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> compareLigands() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/rank_ligands_indiv.py", self().state.path + "s4.in"})
                .start().waitFor();

        // Templating sources...
        final String a = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>PL-PatchSurfer Results</title><script src=\"http://3dmol.csb.pitt.edu/build/3Dmol-min.js\"></script></head><body>";
        final String b = "<script>$(document).ready(function() {";
        final String c = "});</script></body></html>";
        final StringBuilder htmls = new StringBuilder();
        final StringBuilder jss = new StringBuilder();

        // Iteration things over here.
        final int[] idx = {0};
        String db = self().configuration.databaseSet;
        String dbSource = Paths.get(db).getParent().toString();
        String rank = tilde(self().configuration.plpsPath) + "/out.rank";

        // Iterate and transform each rank listing <mol2> <score> into a visualization.
        Files.lines(Paths.get(rank)).limit(5).forEachOrdered((s) -> {
            String in1 = "<div id='m${NUM}' style=\"height: 512px; width: 512px;\" class='viewer_3Dmoljs' data-href='${PATH}' data-backgroundcolor='0xffffff' data-style='stick'></div>";
            String in2 = "3Dmol.viewers['m${NUM}'].addLabel(\"Score: ${SCORE}\", {position: {x:0, y:0, z:0}, backgroundColor: 0x000000, backgroundOpacity: 0.8});";
            String parts[] = s.split("\\s+");

            // Makeshift templating engine here...
            in1 = in1.replace("${NUM}", "" + idx[0]);
            in1 = in1.replace("${PATH}", dbSource + "/" + parts[0]);
            in2 = in2.replace("${NUM}", "" + idx[0]);
            in2 = in2.replace("${SCORE}", parts[1]);
            htmls.append(in1);
            jss.append(in1);
            idx[0]++;
        });

        // Write the results HTML output.
        String out = a + htmls.toString() + b + jss.toString() + c;
        Files.write(Paths.get(tilde(self().configuration.plpsPath) + "/results.html"), out.getBytes());

        self().state.stage = State.Stage.COMPLETE;
        promise.complete();
        return promise;
    }
}