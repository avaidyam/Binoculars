package test;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.scheduler.ElasticScheduler;
import com.binoculars.future.CompletableFuture;
import com.binoculars.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class PiCalc {

    public static class PiNucleus extends Nucleus<PiNucleus> {
        public void $calculatePiFor(int start, int nrOfElements, Adder adder) {
            double acc = 0.0;
            for (int i = start * nrOfElements; i <= ((start + 1) * nrOfElements - 1); i++) {
                acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1);
            }
            adder.$add(acc);
        }
    }

    public static class Adder extends Nucleus<Adder> {
        double pi = 0;
        public void $add(double d) {
            pi += d;
        }
        public void $printPi() {
            System.out.println("PI: "+pi);
        }
    }

    // blocking utility method
    static long calcPi(final int numMessages, int step, final int numNuclei) throws InterruptedException {
        long tim = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        Adder adder = Nucleus.of(Adder.class, 70000);

        List<PiNucleus> pies = new ArrayList<>(numNuclei);
        for (int i = 0; i < numNuclei; i++)
            pies.add(i, Nucleus.of(PiNucleus.class));

        for (int i = 0; i < numMessages; i += numNuclei) {
            final int finalI = i;
            pies.forEach(pia -> pia.$calculatePiFor(finalI + pies.indexOf(pia), step, adder));
        }

        // trigger latch once all actors have finished
        CompletableFuture.allOf(pies.stream().map(Nucleus::ping)
		        .toArray(CompletableFuture[]::new))
		        .then((r, e) -> latch.countDown());
        latch.await();
        long duration = System.currentTimeMillis() - tim;
        adder.$printPi();

        // clean up
        pies.forEach(Nucleus::stop);
        adder.stop();

        System.out.println("TIM ("+numNuclei+")"+duration);
        return duration;
    }

    public static void main( String arg[] ) throws InterruptedException {
        final int numMessages = 1000000;
        final int step = 100;
        final int MAX_ACT = 8;
        String results[] = new String[MAX_ACT];

        Log.get().setSeverity(Log.Severity.ERROR);
        ElasticScheduler.DEFQSIZE = 60000;

        for (int numNuclei = 1; numNuclei <= MAX_ACT; numNuclei += 1) {
            long sum = 0;
            for (int ii = 0; ii < 20; ii++)
                if (ii >= 10) sum += calcPi(numMessages, step, numNuclei);
            results[numNuclei-1] = "average "+numNuclei+" threads : "+sum/10;
        }

        for(String result : results)
            System.out.println(result);
    }
}
