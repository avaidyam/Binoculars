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

import com.binoculars.nuclei.*;
import com.binoculars.future.*;
import com.binoculars.nuclei.Cortex;
import com.binoculars.util.Eponym;
import com.binoculars.util.Log;
import org.kihara.util.SeekableFile;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PFPController extends Nucleus<PFPController> {

	//
	// CONTEXT:
	//

	// --------------------------------------------------------------------
	// Context for PFP application jobs, including FASTA and divisions.
	public static final String TAG = "[PFP]";
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
	String storePath = "/net/kihara/avaidyam/binclr";
    public Future<String> getStorePath() {
        return new CompletableFuture<>(storePath);
    }
    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }
	// --------------------------------------------------------------------

	// --------------------------------------------------------------------
	// Handy JS function to begin PFP without messing with Cortex.
	public static void go(String filename) throws Exception {
		Cortex.of(PFPController.class)
				.getNodes().get(0)
				.beginPFP(SeekableFile.fromFile(filename));
	}
	// --------------------------------------------------------------------

	//
	// UTILITY
	//

	// --------------------------------------------------------------------
	// Assign per core a number of tasks, as a general algorithm.
	private HashMap<Integer, List<Integer>> _distributePFP(int cores, int total) {
		HashMap<Integer, List<Integer>> map = new HashMap<>();
		for(int i = 0; i < cores; i++)
			map.put(i, new ArrayList<>());

		// Assign PFP subunits to controllers based on leveled distribution;
		// the leftover subunits will be randomly distributed to all.
		int assigned = 1; // start at job 1
		for(int i = 0; i < cores; i++)
			for(int j = 0, __count = (total / cores); j < __count; j++)
				map.get(i).add(assigned++);
		for(int j = 0, __count = (total % cores); j < __count; j++)
			map.get((int)(Math.random() * cores)).add(assigned++);

		return map;
	}
	// --------------------------------------------------------------------

	// --------------------------------------------------------------------
	// Helper lambda to concisely produce processes for PFP.
	Function<String[], ProcessBuilder> _pfp = (String ... args) -> {
		System.out.println("Running " + Arrays.toString(args));
		ProcessBuilder pb = new ProcessBuilder(args)
				.directory(new File(storePath))
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT);
		pb.environment().put("BLASTMAT", storePath + "/bin/data");
		return pb;
	};
	// --------------------------------------------------------------------





	// Begin the stream processor and execute PFP.
    public void beginPFP(String fasta) {

		Log.i(TAG, "Identifying tasks by FASTA sequence markers (>).");
        int count = 0;
        for (Matcher m = Pattern.compile(">").matcher(fasta); m.find(); count++);
        if(count <= 0)
			return;

        Log.i(TAG, "Assigning subsequences via segmented distribution.");
        List<PFPController> pfp = Cortex.of(PFPController.class).getNodes();
        HashMap<Integer, List<Integer>> div = _distributePFP(pfp.size(), count);
        Log.d(TAG, "Distribution: " + count + " -> " + div);

		final int _count = count;
		final String id = Eponym.randomNumber(5);

        Log.i(TAG, "Beginning stream processor.");
		for (PFPController c : pfp) {
			if(c.hasJobContext().await())
				continue;

			Log.i(TAG, "Opening new Job Context for current stream division.");
			c.startJobContext(c.downloadFASTA(fasta).await(),
					id, _count, div.get(pfp.indexOf(c)));
			c.setupXML().then((r, e) -> {
				Log.i(TAG, "Node finished PFP process.");
			});
		}
    }

	// Download and locally cache FASTA files from the origin node.
    public Future<String> downloadFASTA(String fasta) {
        CompletableFuture<String> promise = new CompletableFuture<>();
        Log.i(TAG, "Step 1: Downloading FASTA data from origin.");

        try {
            Path path = Files.createTempFile("PFP", ".xml");
            Files.write(path, fasta.getBytes());
            promise.complete(path.toAbsolutePath().toString());
        } catch (IOException e) {
			Log.e(TAG, "Could not download FASTA data.", e);
			promise.completeExceptionally(e);
		}
        return promise;
    }

	// Setup XML intro and outro for PFP output file.
    public Future<Void> setupXML() {
		CompletableFuture<Void> promise = new CompletableFuture<>();
        String name = self().context.name;
        int total = self().context.total;

		// Get the PrintWriter for the XML file.
        Log.i(TAG, "Step 2: Writing XML intro to file \"" + (name + ".xml") + "\".");
        PrintWriter writer;
        try {
            writer = new PrintWriter(new FileOutputStream("PFP_" + name + ".xml"));
			self().context.writer = writer;
        } catch(IOException e) {
            Log.e(TAG, "Could not open XML file for writing sequence.", e);
			promise.completeExceptionally(e);
            return promise;
        }

		// Write the intro stanza and flush for later.
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<" + "PFP_Job>\n");
        writer.write("\t<job_id>" + name + "</job_id>\n");
        writer.write("\t<date_created>" + date + "</date_created>\n");
        writer.write("\t<numSequences>" + total + "</numSequences>\n");
        writer.write("\t<sequences>\n");
        writer.flush();

        // Latch to finish the XML writing.
        FutureLatch<Void> latch = new FutureLatch<>(new CompletableFuture<>(), total);
		self().context.latch = latch;
        latch.future().then((r, e) -> {
            Log.i(TAG, "Step 7: Subsequences completed; finalizing job.");
            writer.write("\t</sequences>\n</PFP_Job>\n");
            writer.close();

			// We're done here, clean the context and complete.
            self().clearJobContext();
			promise.complete();
        });

		// Bootstrap the last phase and take care of the results.
		for(int i : self().context.divisions) {
			self().executeSubsequence(i);
			// FIXME: here it should re-cast into another node.
			// if promise returns error!
					/*.onResult(v -> latch.countDown())
					.onError(e -> {
						Log.e(TAG, "Encountered an error with sequence " + i + "!", (Throwable)e);
						// IXME: restart processing here
					});*/
		}

        return promise;
    }

	public Future<Void> executeSubsequence(int i) {
		CompletableFuture<Void> promise = new CompletableFuture<>();
		PrintWriter writer = self().context.writer;
		File _fasta = new File(self().context.path);
		String name = self().context.name;

		Path path = Paths.get(storePath + "/tmp/" + name + "/" + name + "_" + i + "_part.fasta");
		Log.i(TAG, "Step 3: Creating temporary FASTA sequence " + i + " XML.");
		String f; Path temp;

		// First: Create the temp file and any directories.
		try {
			final Path tmp = path.getParent();
			if (tmp != null) // null will be returned if the path has no parent
				Files.createDirectories(tmp);
			temp = Files.createFile(path);
		} catch (IOException e) {
			Log.e(TAG, "Could not create temp file for subsequence #" + i + ".", e);
			promise.completeExceptionally(e);
			return promise;
		}

		// Second: open and seek the origin FASTA and grab the string.
		try {
			f = new SeekableFile(_fasta).find('>', i - 1);
			if(f == null) throw new IOException("SeekableFile failed.");
		} catch (IOException e) {
			Log.e(TAG, "Could not read for subsequence #" + i + ".", e);
			promise.completeExceptionally(e);
			return promise;
		}

		// Third: Write the subsequence to the temp file.
		try {
			Files.write(temp, f.getBytes());
		} catch (IOException e) {
			Log.e(TAG, "Could not write to temp file for subsequence #" + i + ".", e);
			promise.completeExceptionally(e);
			return promise;
		}

		// Message to process the PFP subunit.
		// Once the XML file has been written, signal completion.
		self().processScripts(name + "_" + i, temp.toAbsolutePath().toString()).onResult(n -> {
			//self().receive(i, temp).then(promise);
			List<PFPController> pfp = Cortex.of(PFPController.class).getNodes();
			pfp.stream().forEach(c -> {
				//
				c.receiveResults(i, temp.toAbsolutePath().toString());
			});
			promise.complete();
		}).onError(t -> {
			Log.e(TAG, "Encountered error!", (Throwable)t);
			promise.completeExceptionally((Throwable)t);
		});//.then(promise);
		return promise;
	}

	// Execute the equivalent of the pfp_run.pl script.
    public Future<Void> processScripts(String name, String path) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
		Log.i(TAG, "Step 4: Executing scripts for subsequence.");

		try {
			// Set up PFP temporary directories.
			Files.createDirectories(Paths.get(storePath + "/job_bin/"));
			Files.createDirectories(Paths.get(storePath + "/res/" + name));
			Files.createDirectories(Paths.get(storePath + "/tmp/" + name));
			Files.createDirectories(Paths.get(storePath + "/tmp/" + name + "/blast"));
			Files.createDirectories(Paths.get(storePath + "/tmp/" + name + "/out"));
			Files.createDirectories(Paths.get(storePath + "/tmp/" + name + "/out_exp_acc"));

			// Run the following commands as perl scripts:
			// blast_sequences, pfp_pfpdb, add_definitions_wrapper, convert_to_xml
			_pfp.apply(new String[]{"perl", storePath + "/bin/blast_sequences.pl", path, name})
					.start().waitFor();
			_pfp.apply(new String[]{"perl", storePath + "/bin/pfp_pfpdb.pl", storePath + "/tmp/" + name + "/blast/", name})
					.start().waitFor();
			//_pfp.apply(new String[]{"perl", storePath + "/bin/add_expected_accuracy.pl", storePath + "/tmp/" + name + "/out/",
			//        storePath + "/tmp/" + name + "/out_exp_acc/"})
			//        .start().waitFor();
			_pfp.apply(new String[]{"perl", storePath + "/bin/add_definitions_wrapper.pl", storePath + "/tmp/" + name + "/out/", name})
					.start().waitFor();
			_pfp.apply(new String[]{"perl", storePath + "/bin/convert_to_xml.pl", storePath + "/res/" + name})
					.start().waitFor();
        } catch(IOException | InterruptedException e) {
            Log.e(TAG, "Could not process scripts for subsequence.", e);
            promise.completeExceptionally(e);
        }

		self().moveFiles(name)
				.onResult(v -> promise.complete())
				.onError(e -> promise.completeExceptionally((Throwable)e));
		return promise;
    }

	// Move XML output files to the proper location by streaming.
	public Future<Void> moveFiles(String name) {
		CompletableFuture<Void> promise = new CompletableFuture<>();
		Log.i(TAG, "Step 5: Completed subsequence operation; moving output files.");

		// Set source and destination for moving files.
		Path source = Paths.get(storePath + "/res/" + name);
		Path destination = Paths.get(storePath + "/job_bin/");

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
			for (Path entry : stream) {
				if(!entry.toString().toLowerCase().endsWith(".xml"))
					continue;
				Files.move(entry, destination.resolve(entry.getFileName()),
						StandardCopyOption.REPLACE_EXISTING);
			}
		} catch(IOException e) {
			Log.e(TAG, "Could not move files for subsequence.", e);
			promise.completeExceptionally(e);
		}

		promise.complete();
		return promise;
	}

	public Future<Void> receiveResults(int i, String temp) {
		CompletableFuture<Void> promise = new CompletableFuture<>();

		// Fail-safe to wait until ready to write.
		while (!(self().context.writer != null && self().context.latch != null))
			LockSupport.parkNanos(500);
		PrintWriter writer = self().context.writer;
		FutureLatch<Void> latch = self().context.latch;
		String name = self().context.name;

		String d, s, r;

		// Prepare all the strings for writing the XML file.
		try {
			Log.i(TAG, "Step 6: Writing XML for subsequence #" + i + ".");
			String curr = SeekableFile.fromPath(Paths.get(temp));
			d = curr.substring(1, curr.indexOf("\n")).trim();
			s = curr.replace(d, "").replace("\n", "").trim();
			r = storePath + "/job_bin/" + "PFP_" + name + "_" + i + "_1_part.xml";
		} catch (IOException e) {
			Log.e(TAG, "Could not get XML data for subsequence #" + i + ".", e);
			promise.completeExceptionally(e);
			return promise;
		}

		// Write sequence information to the overall file.
		try {
			writer.write("\t\t<sequence id=\"" + i + "\">\n");
			writer.write("\t\t\t<protein_sequence>" + s + "</protein_sequence>\n");
			writer.write("\t\t\t<description>" + d + "</description>\n");
			writer.write(SeekableFile.fromFile(r));
			writer.write("\t\t</sequence>\n");
			Log.i(TAG, "Subsequence #" + i + " written.");
		} catch (IOException e) {
			Log.e(TAG, "Could not write XML file for subsequence #" + i + ".", e);
			promise.completeExceptionally(e);
			return promise;
		}

		latch.countDown();
		promise.complete();
		return promise;
	}
}