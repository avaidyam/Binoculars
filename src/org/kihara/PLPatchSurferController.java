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
import org.kihara.util.MigrationVisitor;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.ProcessBuilder.Redirect.appendTo;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

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

        String plpsPath = tilde("~/PatchSurfer/");
        String workingPath = tilde("~/PatchSurferFiles/");
        String pdb2pqrPath = tilde("/apps/pdb2pqr/current/");
        String apbsPath = tilde("/apps/apbs/apbs-1.4.2/");
        String babelPath = tilde("/usr/bin");
        String xlogp3Path = tilde("~/PatchSurfer/XLOGP3/bin/");
        String omegaPath = tilde("~/PatchSurfer/openeye/arch/Ubuntu-12.04-x64/omega");
        String omegaLicense = tilde("~/PatchSurfer/openeye/oe_license.txt");
        String pdbSources = tilde("/net/kihara/yang942/compound_3d/compound_3d_pdb");
        HashMap<String, String> databaseSet = new HashMap<>();
        int n_conf = 50; // max n_conf for the database

        Configuration() {
            HashMap<String, String> m = this.databaseSet;
            m.put("zinc_druglike",  "/net/kihara/shin183/DATA-scratch/zinc_druglike_90/druglike.list");
            m.put("chembl_19",      "/net/kihara/shin183/DATA-scratch/chembl/chembl_19/chembl.list");
            m.put("debug_1",        "/net/kihara/avaidyam/PLPSSample/PLPSSample.list");
            m.put("debug_2",        "/net/kihara/avaidyam/PLPSSample2/PLPSSample2.list");
        }

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
                    ", pdbSources='" + pdbSources + '\'' +
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
            if (!pdbSources.equals(that.pdbSources)) return false;
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
            result = 31 * result + pdbSources.hashCode();
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
            /*X*/ FAILED, // TODO
            /*0*/ INITIALIZED,
            /*1*/ SPLIT_XTAL,
            /*2*/ PREPARE_RECEPTOR,
            /*3*/ COMPARE_SEEDS,
            /*4*/ COMPARE_LIGANDS,
            /*5*/ COMPLETE,
        }

        Stage stage = Stage.INITIALIZED;
        String path = "";
        String db = "";
        String email = "";
        String chainID = "";
        String ligandID = "";
        String inputFile = "";
        String outputFile = ""; //ex: lcs.rank

        @Override
        public String toString() {
            return "State{" +
                    "stage=" + stage +
                    ", path='" + path + '\'' +
                    ", db='" + db + '\'' +
                    ", email='" + email + '\'' +
                    ", chainID='" + chainID + '\'' +
                    ", ligandID='" + ligandID + '\'' +
                    ", inputFile='" + inputFile + '\'' +
                    ", outputFile='" + outputFile + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            if (!path.equals(state.path)) return false;
            if (!db.equals(state.db)) return false;
            if (!email.equals(state.email)) return false;
            if (!chainID.equals(state.chainID)) return false;
            if (!ligandID.equals(state.ligandID)) return false;
            if (!inputFile.equals(state.inputFile)) return false;
            return outputFile.equals(state.outputFile);
        }

        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + db.hashCode();
            result = 31 * result + email.hashCode();
            result = 31 * result + chainID.hashCode();
            result = 31 * result + ligandID.hashCode();
            result = 31 * result + inputFile.hashCode();
            result = 31 * result + outputFile.hashCode();
            return result;
        }
    }

    /* TODO */
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

    // FIXME
    /*
    @Override
    public void init() {
        Log.d(TAG, "Init executed!");
    }

    @Override
    public void deinit() {
        Log.d(TAG, "Deinit executed!");
    }
    //*/

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
        pb.environment().put("OE_LICENSE", self().configuration.omegaLicense);
        return pb;
    };
    private static String tilde(String path) {
        return path.replaceFirst("^~", System.getProperty("user.home"));
    }
    // --------------------------------------------------------------------

    /**
     *
     *
     * @param root
     * @param db
     * @param email
     * @param chainID
     * @param ligandID
     * @param pdb
     * @return
     * @throws Exception
     */
    public Future<Void> provideInputs(String root, String db, String email,
                                      String chainID, String ligandID, String pdb) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state != null && self().configuration != null) {
            return new CompletableFuture<>(new RuntimeException("Can't start another task!"));
        }

        // Initialize the configutation.
        Configuration conf = new Configuration();
        self().configuration = conf;

        // Initialize the state (job context).
        State st = new State();
        st.path = tilde(root);
        st.email = email;
        st.db = db;
        st.chainID = chainID;
        st.ligandID = ligandID;
        st.stage = State.Stage.INITIALIZED;

        // Rename the input file if it's "reserved" for us.
        if (pdb.equals("rec.pdb") || pdb.equals("xtal-lig.pdb")) {
            Path p = Paths.get(st.path + pdb);
            Files.move(p, p.resolveSibling("input.pdb"));

            st.inputFile = "input.pdb";
        } else st.inputFile = pdb;
        self().state = st;

        // Wrap up and exit.
        Log.d(TAG, "PLPS State = " + st.toString());
        promise.complete();
        return promise;
    }
    //st.receptorFilePDB.replace(".pdb", ".ssic");
    //"/net/kihara/avaidyam/PLPSSample2/PLPSSample2.list";
    //tilde(conf.workingPath) + st.uuid.toString().replaceAll("-", "") + "/";

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> generateInputs() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        Configuration conf = self().configuration;
        State st = self().state;

        // Expand all tildes once for effectiveness.
        String pdb2pqrPath = conf.pdb2pqrPath;
        String apbsPath = conf.apbsPath + "bin/";
        String babelPath = conf.babelPath;

        // Generate the input configuration files.
        String s1 = "PLPS_path\t" + conf.plpsPath + "\nPDB2PQR_path\t" + pdb2pqrPath + "\nAPBS_path\t" +
                apbsPath + "\nBABEL_path\t" + babelPath + "\nreceptor_file\t" + "rec.pdb" +
                "\nligand_file\t" + "xtal-lig.pdb\n";
        String s3 = "PLPS_path\t" + conf.plpsPath + "\nreceptor_file\t" + "rec.ssic" + "\n";
        String s4 = "receptor_file\t" + "rec.ssic" + "\noutput_file\tout.rank";

        // Map all the ligands from the selected database into the python input files.
        Path dbloc = Paths.get(conf.databaseSet.get(st.db));
        try (Stream<String> lines = Files.lines(dbloc)) {
            String set = lines
                    .map((lig) -> "\nligand_dir\t" + lig)
                    .reduce((r, s) -> r + s)
                    .orElse("");
            s4 += set;
        }; s4 += "\n";

        // Write all python input files.
        Files.write(Paths.get(st.path + "s1.in"), s1.getBytes(), CREATE_NEW);
        Files.write(Paths.get(st.path + "s3.in"), s3.getBytes(), CREATE_NEW);
        Files.write(Paths.get(st.path + "s4.in"), s4.getBytes(), CREATE_NEW);

        Log.d(TAG, "Generated input files at" + st.path + ".");
        promise.complete();
        return promise;
    }

    // Step 1:

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<Void> splitXtalLigand() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        self().state.stage = State.Stage.SPLIT_XTAL;

        // Run script on the generated input.
        State st = self().state;
        _plps.apply(new String[]{"python", self().configuration.plpsPath + "scripts/split_lig.py",
                st.path + st.inputFile, st.chainID, st.ligandID})
                .start().waitFor();

        promise.complete();
        return promise;
    }

    // Step 2:
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
        self().state.stage = State.Stage.PREPARE_RECEPTOR;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", self().configuration.plpsPath + "scripts/prepare_receptor.py", self().state.path + "s1.in"})
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
        self().state.stage = State.Stage.COMPARE_SEEDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", self().configuration.plpsPath + "scripts/compare_seeds.py", self().state.path + "s3.in"})
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
        self().state.stage = State.Stage.COMPARE_SEEDS;

        String db = self().configuration.databaseSet.get(self().state.db);
        String par = Paths.get(db).getParent().toString();
        Path seedsDir = Paths.get(self().state.path).resolve("seeds");
        Files.createDirectory(seedsDir);

        // Iterate every ligand in the database. Create a temp directory for the
        // conformation intermediate files and delete immediately after the summary
        // is generated (because the files are massive).
        try (Stream<String> lines = Files.lines(Paths.get(db))) {
            lines.forEach((s) -> {
                try {
                    Files.createDirectory(seedsDir.resolve(s));

                    // Run script on the generated input.
                    String loc = self().configuration.plpsPath + "scripts/compare_seeds_indiv.py";
                    _plps.apply(new String[]{"python", loc, self().state.path + "s3.in", par + "/" + s, "" + self().configuration.n_conf, self().state.path + "seeds"})
                            .start().waitFor();

                    MigrationVisitor.deleteAll(seedsDir.resolve(s));
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
    public Future<Void> compareLigands(int num_preview) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        self().state.stage = State.Stage.COMPARE_LIGANDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", self().configuration.plpsPath + "scripts/rank_ligands_indiv.py", self().state.path + "s4.in"})
                .start().waitFor();

        // Templating sources...
        final String a = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>PL-PatchSurfer Results</title><script src=\"http://3dmol.csb.pitt.edu/build/3Dmol-min.js\"></script></head><body>";
        final String b = "<script>$(document).ready(function() {";
        final String c = "});</script></body></html>";
        final StringBuilder htmls = new StringBuilder();
        final StringBuilder jss = new StringBuilder();

        // Iteration things over here.
        // Create the output directory and copy the rank file.
        // In ./output/: all *.pdb files for rendering.
        // In ./output/plps_XXXX/: index.html, out.rank.
        final int[] idx = {0};
        String dbDest = self().state.path + "/output";
        Files.createDirectory(Paths.get(dbDest));
        Path folder = Paths.get(self().state.path).getFileName();
        Files.createDirectory(Paths.get(dbDest).resolve(folder));
        String dbSource = self().configuration.pdbSources;
        String rank = self().state.path + "/out.rank";
        Files.copy(Paths.get(rank), Paths.get(dbDest).resolve(folder).resolve("out.rank"));

        // Iterate and transform each rank listing <mol2> <score> into a visualization.
        Files.lines(Paths.get(rank)).limit(num_preview).forEachOrdered((s) -> {
            String in1 = "<div id='m${NUM}' style=\"height: 512px; width: 512px;\" class='viewer_3Dmoljs' data-href='${PATH}' data-backgroundcolor='0xffffff' data-style='stick'></div>";
            String in2 = "$3Dmol.viewers['m${NUM}'].addLabel(\"Distance: ${SCORE}\", {position: {x:0, y:0, z:0}, backgroundColor: 0x000000, backgroundOpacity: 0.8});";
            String parts[] = s.split("\\s+");

            // Copy any PDB files we need (~600kB) to the outbox.
            String pdbSource = parts[0] + ".pdb";
            try {
                Files.copy(Paths.get(dbSource).resolve(pdbSource),
                           Paths.get(dbDest).resolve(pdbSource));
            } catch (Exception ignored) {}

            // Makeshift templating engine here...
            in1 = in1.replace("${NUM}", "" + idx[0]);
            in1 = in1.replace("${PATH}", "../" + pdbSource);
            in2 = in2.replace("${NUM}", "" + idx[0]);
            in2 = in2.replace("${SCORE}", parts[1]);
            htmls.append(in1);
            jss.append(in2);
            idx[0]++;
        });

        // Write the results HTML output.
        String out = a + htmls.toString() + b + jss.toString() + c;
        Files.write(Paths.get(dbDest).resolve(folder).resolve("index.html"), out.getBytes());

        self().state.stage = State.Stage.COMPLETE;
        promise.complete();
        return promise;
    }
}