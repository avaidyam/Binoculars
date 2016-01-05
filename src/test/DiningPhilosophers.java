package test;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.tcp.TCPConnectable;
import com.binoculars.nuclei.remoting.tcp.TCPServerConnector;
import com.binoculars.future.Signal;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;

import java.util.ArrayList;
import java.util.concurrent.locks.LockSupport;

public class DiningPhilosophers {

	public static class Table extends Nucleus<Table> {
		ArrayList<CompletableFuture> forks[] = new ArrayList[5];

		public Table() {
			for (int i = 0; i < forks.length; i++)
				forks[i] = new ArrayList<>();
		}

		public Future getFork(int num) {
			num %= 5;
			CompletableFuture res = forks[num].size() == 0 ? new CompletableFuture("void") : new CompletableFuture();
			forks[num].add(res);
			return res;
		}

		public void returnFork(int num) {
			num %= 5;
			forks[num].remove(0);
			if ( forks[num].size() > 0 )
				forks[num].get(0).complete();
		}
	}

	public static class Philosopher extends Nucleus<Philosopher> {
		String name;
		int nr;
		Table table;

		String state;
		int eatCount;

		public void start(String name, int nr, Table table) {
			this.name = name;
			this.nr = nr;
			this.table = table;

			live();
		}

		public void live() {
			state = "Think";
			long thinkTime = randomTimeMS();

			delayed(thinkTime, () -> {
				state = "Hungry";
				// avoid deadlock:
				// even numbered philosophers take left then right fork,
				// odd numbered vice versa

				int firstFork =  nr+(nr&1);
				int secondFork = nr+(1-(nr&1));
				table.getFork(firstFork).then((Signal)(r, e) ->
								table.getFork(secondFork).then((Signal)(r1, e1) -> {
									state = "Eat";
									long eatTime = randomTimeMS();
									delayed( eatTime, () -> {
										eatCount++;
										table.returnFork(firstFork);
										table.returnFork(secondFork);
										self().live();
									});
								})
				);
			});
		}

		public Future<String> getState() {
			return new CompletableFuture<>(name + " " + state + " eaten:" + eatCount);
		}

		private long randomTimeMS() {
			return (long)(100 * Math.random() + 1);
		}
	}

	static void runPhilosophers(Table coord) {
		ArrayList<Philosopher> phils = new ArrayList<>();
		for(int i = 0; i < 5; i++) {
			phils.add(Nucleus.of(Philosopher.class));
			phils.get(i).start("Philosopher " + i, i, coord);
		}
		System.out.println("got " + phils);
		startReportingThread(phils);
	}

	public static void runServer() throws Exception {
		TCPServerConnector.Publish(Nucleus.of(Table.class), 6789, null);
	}

	static void runClient() throws Exception {
		new TCPConnectable(Table.class, "localhost", 6789)
				.connect()
				.then((table, error) -> {
					if (table != null) { // connection failure
						runPhilosophers((Table) table);
					} else {
						System.out.println("error:" + error);
					}
				});
	}

	public static void main(String[] args) throws Exception {
		switch (args.length) {
			//case 0: runServer(); break;
			//case 1: runClient(); break;
			default:
				// run them in process
				runPhilosophers(Nucleus.of(Table.class));
		}
	}

	// start a thread reporting state each second
	private static void startReportingThread(ArrayList<Philosopher> phils) {
		new Thread(() -> {
			while(true) {
				LockSupport.parkNanos(1000 * 1000l * 1000);

				// Convert to states: could use map() instead.
				ArrayList<Future<String>> bunch = new ArrayList<>();
				for(Philosopher p : phils)
					bunch.add(p.getState());

				//System.out.println("got " + bunch);
				CompletableFuture.allOf(bunch).then((f, e) -> {
					for(Future<String> aF : f)
						System.out.print(aF.get() + ", ");
					System.out.println();
				});
			}
		}).start();
	}
}