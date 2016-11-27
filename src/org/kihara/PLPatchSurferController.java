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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static java.lang.ProcessBuilder.Redirect.appendTo;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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
        List<String> databaseSet = Arrays.asList(
                "/net/kihara/shin183/DATA-scratch/zinc_druglike_90/druglike.list",
                "/net/kihara/shin183/DATA-scratch/chembl/chembl_19/chembl.list"
        );

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
                    ", databaseSet=" + databaseSet +
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

        // FIXME REMOVE THIS:
        String receptorFilePDB = ""; //ex: rec.pdb
        String xtalLigand = ""; //ex: xtal-lig.pdb
        List<String> ligandFiles = new ArrayList<>(); //ex: [ZINC03833861, ZINC03815630] (mol2 is appended)
        int n_conf = 0; //ex: 50
        boolean useLCSinsteadOfBS = false; //ex: bs.rank
        String outputFile = ""; //ex: lcs.rank

        @Override
        public String toString() {
            return "State{" +
                    "uuid=" + uuid +
                    ", path='" + path + '\'' +
                    ", stage=" + stage +
                    ", receptorFilePDB='" + receptorFilePDB + '\'' +
                    ", xtalLigand='" + xtalLigand + '\'' +
                    ", ligandFiles=" + ligandFiles +
                    ", n_conf=" + n_conf +
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

    /**
     * Initializes the PLPatchSurferController with a global configuration.
     */
    public Future<Void> init() {
        self().setConfiguration(new Configuration());
        return new CompletableFuture<>((Void)null);
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
     * @param ligandFiles
     * @param nconf
     * @return
     * @throws Exception
     */
    public Future<Void> begin(String receptorFile, String xtalLigand, List<String> ligandFiles, int nconf) throws Exception {
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
        st.n_conf = nconf;

        // Create the directory and move input files over.
        Path path = Paths.get(st.path);
        Path rfPath = Paths.get(tilde(receptorFile)); // EXPECTS FILENAME
        Path xtalPath = Paths.get(tilde(xtalLigand)); // EXPECTS FILENAME
        Files.createDirectories(path);
        Files.copy(rfPath, path.resolve(rfPath.getFileName()),
                    REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS);
        Files.copy(xtalPath, path.resolve(xtalPath.getFileName()),
                    REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS);
        ArrayList<String> updateLigands = new ArrayList<>();
        for (String s : ligandFiles) {
            Path sPath = Paths.get(tilde(s));
            Files.copy(sPath, path.resolve(sPath.getFileName()),
                        REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS);
            String ss = sPath.getFileName().toString();
            ss = ss.substring(0, ss.lastIndexOf('.')); // strip .mol2
            updateLigands.add(ss);
        }

        // Mark the updated paths instead of the input ones.
        st.receptorFilePDB = rfPath.getFileName().toString();
        st.xtalLigand = xtalPath.getFileName().toString();
        st.ligandFiles = updateLigands;

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
                "\nBABEL_path\t" + babelPath +"\nn_conf\t" + st.n_conf;
        String s3 = "PLPS_path\t" + conf.plpsPath + "\nreceptor_file\t" + rfSSIC + "\nn_conf\t" + st.n_conf;
        String s4 = "receptor_file\t" + rfSSIC + "\noutput_file\tout.rank";
        for (String lig : st.ligandFiles) {
            s2 += "\nligand_file\t" + lig + ".mol2";
            s3 += "\nligand_dir\t" + lig;
            s4 += "\nligand_dir\t" + lig;
        }

        // Log progress.
        Log.d(TAG, "Generated " + st.path + "s1.in: \n" + s1 + "\n");
        Log.d(TAG, "Generated " + st.path + "s2.in: \n" + s2 + "\n");
        Log.d(TAG, "Generated " + st.path + "s3.in: \n" + s3 + "\n");
        Log.d(TAG, "Generated " + st.path + "s4.in: \n" + s4 + "\n");

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
            self().state.stage = State.Stage.PREPARE_RECEPTOR;
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

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/prepare_receptor.py", self().state.path + "s1.in"})
                .start().waitFor();
        self().state.stage = State.Stage.PREPARE_LIGANDS;
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

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/prepare_ligands.py", self().state.path + "s2.in"})
                .start().waitFor();
        self().state.stage = State.Stage.COMPARE_SEEDS;
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

        // Run script on the generated input.
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/compare_seeds.py", self().state.path + "s3.in"})
                .start().waitFor();
        self().state.stage = State.Stage.COMPARE_LIGANDS;
        promise.complete();
        return promise;
    }

    // Step 4:

    /**
     *
     * @return
     * @throws Exception
     */
    public Future<String> compareLigands() throws Exception {
        CompletableFuture<String> promise = new CompletableFuture<>();
        if (self().state == null || self().configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }

        // Run script on the generated input.
        String select = self().state.useLCSinsteadOfBS ? "lcs" : "bs";
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/rank_ligands_" + select + ".py", self().state.path + "s4.in"})
                .start().waitFor();
        self().state.stage = State.Stage.COMPLETE;
        promise.complete(self().state.outputFile);
        return promise;
    }

    // Optional:

    /**
     *
     * @param ssicFile
     * @return
     * @throws Exception
     */
    public Future<String> convertSSICtoPDB(String ssicFile) throws Exception {
        _plps.apply(new String[]{"python", tilde(self().configuration.plpsPath) + "scripts/convert_ssic_to_pdb.py", ssicFile, "pdbOut.pdb"})
                .start().waitFor();
        return new CompletableFuture<>("pdbOut.pdb");
    }
}