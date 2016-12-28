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
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import com.avaidyam.binoculars.Log;
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

/*
-- 	algorithm:
	- distribution: tell(job.data) to enqueue
	- heartbeat: ask(queue.count, sys.stats) to analyze
	- stages: tell(job.stage) to advance
*/

/**
 * The PLPSController nucleus is designed to wrap the PLPatchSurfer
 * tools and execute them in a managed distributed manner with state safety.
 */
public class PLPSController extends Nucleus<PLPSController> {
    public static final String TAG = "[PLPSController]";
    public static LinkedList<Map<String, String>> allJobs = new LinkedList<>();

    Configuration configuration = null;
    State state = null;

    /**
     * The Configuration of a PLPSController defines its tool locations
     * and working directory; this rarely needs to be changed, and if it does,
     * it will invalidate the state of every instance of the controller with it.
     */
    public static class Configuration implements Serializable {

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
            HashMap<String, String> m = databaseSet;
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
     * The State of a PLPSController indicates its current job and
     * the completion of that job; it's designed to be capable of splitting
     * and redistributing the job amongst distant nodes and relies on an
     * underlying distributed file system (like MooseFS) to operate.
     */
    public static class State implements Serializable {

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
        int size = 0; // counts number of ligands in db
        String path = "";
        String db = "";
        String email = "";
        String chainID = "";
        String ligandID = "";
        String inputFile = "";

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
            return inputFile.equals(state.inputFile);
        }

        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + db.hashCode();
            result = 31 * result + email.hashCode();
            result = 31 * result + chainID.hashCode();
            result = 31 * result + ligandID.hashCode();
            result = 31 * result + inputFile.hashCode();
            return result;
        }
    }

    /* // TODO
    static class Task implements Serializable {
        UUID uuid = null;
        String path = "";
        State.Stage stage = State.Stage.INITIALIZED;
    }
    //*/

    /**
     *
     * @return
     */
    @Export
    public Future<Configuration> getConfiguration() {
        return new CompletableFuture<>(configuration);
    }

    /**
     *
     * @param configuration
     */
    @Export
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     *
     * @param state
     */
    @Export
    public void setState(State state) {
        this.state = state;
    }

    /**
     *
     * @return
     */
    @Export
    public Future<Boolean> hasState() {
        return new CompletableFuture<>((this.state != null));
    }

    /**
     *
     */
    @Export
    public void clearState() {
        this.state = null;
    }

    /* // FIXME
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
        String log = this.state.path + "log.txt";
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(new File(this.state.path))
                .redirectOutput(appendTo(Paths.get(log).toFile()))//INHERIT)
                .redirectError(appendTo(Paths.get(log).toFile()));//INHERIT);
        pb.environment().put("LD_LIBRARY_PATH", this.configuration.apbsPath + "lib/");
        pb.environment().put("OE_LICENSE", this.configuration.omegaLicense);
        return pb;
    };
    private static String tilde(String path) {
        return path.replaceFirst("^~", System.getProperty("user.home"));
    }
    // --------------------------------------------------------------------

    /**
     *
     *
     * @throws Exception
     */
    @Export
    public void runJob(Map<String, String> manifest) throws Exception {
        if (self().hasState().await() || manifest == null) {
            throw new RuntimeException("Can't do that!");
        }
        try {

            // Queue and run the job on the daemon.
            String path = manifest.get("_path");
            self().provideInputs(path, manifest.get("db"), manifest.get("email"), manifest.get("chain"),
                    manifest.get("ligand"), manifest.get("receptor")).await();
            self().generateInputs().await();
            self().splitXtalLigand().await();
            self().prepareReceptor().await();
            self().compareSeedsDB().await();
            self().compareLigands().await();
            self().reportCompletion(10).await();

            // Notify the job results and pull from the queue if possible.
            Log.d("MAIN", "Finished task, sending results.");

            // Move the results to the outbox.
            String outbox = "/bio/kihara-web/www/binoculars/outbox";
            Path start = Paths.get(manifest.get("_path")).resolve("output");
            Path end = Paths.get(outbox);
            MigrationVisitor.migrate(start, end, true, REPLACE_EXISTING);

            // Send the results being available as an email.
            String jobName = Paths.get(manifest.get("_path")).getFileName().toString();
            Mailer.mail("Kihara Lab <sbit-admin@bio.purdue.edu>", manifest.get("email"), "PL-PatchSurfer2 Job Results",
                    "Your PL-PatchSurfer job results can be found at http://kiharalab.org/binoculars/outbox/" + jobName + "/ and will be available for the next six months. Please access and download your results as needed.");

            self().clearState();
            self().setConfiguration(null);
            self().notifyJob();
        } catch(Exception e2) {
            Log.e("MAIN", "Failed to complete task.", e2);
            StringWriter sw = new StringWriter();
            e2.printStackTrace(new PrintWriter(sw));

            // Send the job failed message as an email.
            String jobName = Paths.get(manifest.get("_path")).getFileName().toString();
            Mailer.mail("Kihara Lab <sbit-admin@bio.purdue.edu>", manifest.get("email"), "PL-PatchSurfer2 Job Failed",
                    "Your PL-PatchSurfer job (" + jobName + ") failed to complete. Please contact us for support and provide the below trace message.\n\n" + sw.toString() + "\n");

            self().clearState();
            self().setConfiguration(null);
            self().notifyJob();
        }
    }

    /**
     *
     */
    @Export(transport=false)
    public void notifyJob() throws Exception {
        if (!self().hasState().await() && allJobs.size() > 0)
            self().runJob(allJobs.pop());
    }

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
    @Export
    public Future<Void> provideInputs(String root, String db, String email,
                                      String chainID, String ligandID, String pdb) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state != null && this.configuration != null) {
            return new CompletableFuture<>(new RuntimeException("Can't start another task!"));
        }

        // Initialize the configutation.
        Configuration conf = new Configuration();
        this.configuration = conf;

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
        this.state = st;

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
    @Export
    public Future<Void> generateInputs() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        Configuration conf = this.configuration;
        State st = this.state;

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
            AtomicInteger it = new AtomicInteger(0);
            String set = lines
                    .map((lig) -> {
                        it.getAndIncrement(); // keep track of number of db items
                        return "\nligand_dir\t" + lig;
                    })
                    .reduce((r, s) -> r + s)
                    .orElse("");

            this.state.size = it.get();
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
    @Export
    public Future<Void> splitXtalLigand() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state == null || this.configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        this.state.stage = State.Stage.SPLIT_XTAL;

        // Run script on the generated input.
        State st = this.state;
        _plps.apply(new String[]{"python", this.configuration.plpsPath + "scripts/split_lig.py",
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
    @Export
    public Future<Void> prepareReceptor() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state == null || this.configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        this.state.stage = State.Stage.PREPARE_RECEPTOR;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", this.configuration.plpsPath + "scripts/prepare_receptor.py", this.state.path + "s1.in"})
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
    @Export
    public Future<Void> compareSeeds() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state == null || this.configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        this.state.stage = State.Stage.COMPARE_SEEDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", this.configuration.plpsPath + "scripts/compare_seeds.py", this.state.path + "s3.in"})
                .start().waitFor();

        promise.complete();
        return promise;
    }

    /**
     *
     * @return
     * @throws Exception
     */
    @Export
    public Future<Void> compareSeedsDB() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state == null || this.configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        this.state.stage = State.Stage.COMPARE_SEEDS;

        String db = this.configuration.databaseSet.get(this.state.db);
        String par = Paths.get(db).getParent().toString();
        Path seedsDir = Paths.get(this.state.path).resolve("seeds");
        Files.createDirectory(seedsDir);
        AtomicInteger current = new AtomicInteger(0);

        // Iterate every ligand in the database. Create a temp directory for the
        // conformation intermediate files and delete immediately after the summary
        // is generated (because the files are massive).
        try (Stream<String> lines = Files.lines(Paths.get(db))) {
            lines.forEach((s) -> {
                try {
                    Files.createDirectory(seedsDir.resolve(s));

                    // Run script on the generated input.
                    String loc = this.configuration.plpsPath + "scripts/compare_seeds_indiv.py";
                    _plps.apply(new String[]{"python", loc, this.state.path + "s3.in", par + "/" + s, "" + this.configuration.n_conf, this.state.path + "seeds"})
                            .start().waitFor();

                    current.getAndIncrement();
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
    @Export
    public Future<Void> compareLigands() throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (this.state == null || this.configuration == null) {
            return new CompletableFuture<>(new RuntimeException("No task in progress!"));
        }
        this.state.stage = State.Stage.COMPARE_LIGANDS;

        // Run script on the generated input.
        _plps.apply(new String[]{"python", this.configuration.plpsPath + "scripts/rank_ligands_indiv.py", this.state.path + "s4.in"})
                .start().waitFor();


        this.state.stage = State.Stage.COMPLETE;
        promise.complete();
        return promise;
    }

    /**
     *
     * @param current
     * @param total
     * @return
     * @throws Exception
     */
    @Export
    public Future<Void> reportProgress(int current, int total, boolean failed) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        promise.complete();
        return promise;
    }

    /**
     *
     *
     * @param num_preview
     * @return
     * @throws Exception
     */
    @Export
    public Future<Void> reportCompletion(int num_preview) throws Exception {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        //
        InputStream t = getClass().getResourceAsStream("/templates/visualizer_template.html");
        String template = new BufferedReader(new InputStreamReader(t))
                .lines().collect(Collectors.joining("\n"));
        final String inside = template.replaceAll("[\\s\\S]*(<!--START-->)|(<!--END-->)[\\s\\S]*", "");
        Log.d(TAG, "Formed template inner: " + inside);

        final int[] idx = {0};
        String output[] = {""};

        // Gather state and move things over.
        String dbDest = this.state.path + "/output";
        Path folder = Paths.get(this.state.path).getFileName();
        String dbSource = this.configuration.pdbSources;
        String rank = this.state.path + "/out.rank";

        //
        Files.createDirectory(Paths.get(dbDest));
        Files.createDirectory(Paths.get(dbDest).resolve(folder));
        Files.copy(Paths.get(rank), Paths.get(dbDest).resolve(folder).resolve("out.rank"));

        // Iterate and transform each rank listing <mol2> <score> into a visualization.
        Files.lines(Paths.get(rank)).limit(num_preview).forEachOrdered((s) -> {
            String parts[] = s.split("\\s+");

            // Copy any PDB files we need (~600kB) to the outbox.
            String pdbSource = parts[0] + ".pdb";
            Path source = Paths.get(dbSource).resolve(pdbSource);
            Path dest = Paths.get(dbDest).resolve(pdbSource);
            try { Files.copy(source, dest);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Copy from " + source + " to " + dest + " failed because source was not found.");
            } catch (Exception ignored) {}

            // Makeshift templating engine here...
            String temp = inside;
            temp = temp.replace("![NUM]", "" + idx[0]);
            temp = temp.replace("![NAME]", parts[0]);
            temp = temp.replace("![SCORE]", parts[1]);
            temp = temp.replace("![PATH]", "../" + pdbSource);

            output[0] += temp;
            idx[0]++;
        });

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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return jobs;
    }

    /**
     * Initialize the PLPSController and watch for jobs.
     */
    public static void main(String[] args) throws Exception {
        Log.get().setSeverity(Log.Severity.DEBUG);
        PLPSController controller = Nucleus.of(PLPSController.class);

        controller.delayed(1000, () -> {
            Log.d("TEST", "One second later...");
            controller.exec(() -> {
                Log.w("INSIDE", "Test");
                return "test";
            }).then((r, e) -> {
                Log.i("DONE", "YAY");
            });
        });

        FileWatcher watcher = FileWatcher.watch((p, e) -> {
            PLPSController.allJobs.addAll(PLPSController.jobWatcher(p, e, "plps", Paths.get("/net/kihara/avaidyam/PatchSurferFiles/")));

            try {
                controller.notifyJob();
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        }, "/bio/kihara-web/www/binoculars/upload/");
    }
}