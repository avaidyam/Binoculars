package com.binoculars.nuclei.scheduler;

import java.util.concurrent.locks.LockSupport;

/**
 * <p>
 * Kontraktor uses spinlocking. By adjusting the backofstrategy values one can define the tradeoff
 * regarding latency/idle CPU load.
 * <p>
 * if a message queue is empty, first busy spin is used for N iterations, then Thread.allOf, then LockSupport.park, then sleep(nanosToPark)
 */
public class BackOffStrategy {

    public static int SLEEP_NANOS = 1000 * 1000; // could be 1000 * 300
    public static int SPIN_UNTIL_YIELD = 100;
    public static int YIELD_UNTIL_PARK = 100;
    public static int PARK_UNTIL_SLEEP = 10;

    int yieldCount;
    int parkCount;
    int sleepCount;
    int nanosToPark = SLEEP_NANOS; // 1 milli (=latency peak on burst ..)

    public BackOffStrategy() {
        setCounters(SPIN_UNTIL_YIELD, YIELD_UNTIL_PARK, PARK_UNTIL_SLEEP);
    }

    /**
     * @param spinUntilYield - number of busy spins until Thread.yield is used
     * @param yieldUntilPark - number of Thread.all iterations until parkNanos(1) is used
     * @param parkUntilSleep - number of parkNanos(1) is used until park(nanosToPark) is used. Default for nanosToPark is 0.5 milliseconds
     */
    public BackOffStrategy(int spinUntilYield, int yieldUntilPark, int parkUntilSleep) {
        setCounters(spinUntilYield, yieldUntilPark, parkUntilSleep);
    }

    public void setCounters(int spinUntilYield, int yieldUntilPark, int parkUntilSleep) {
        yieldCount = spinUntilYield;
        parkCount = spinUntilYield + yieldUntilPark;
        sleepCount = spinUntilYield + yieldUntilPark + parkUntilSleep;
    }

    public int getNanosToPark() {
        return nanosToPark;
    }

    public BackOffStrategy setNanosToPark(int nanosToPark) {
        this.nanosToPark = nanosToPark;
        return this;
    }

    public void yield(int count) {
        if (count > sleepCount || count < 0) {
            LockSupport.parkNanos(nanosToPark);
        } else if (count > parkCount) {
            LockSupport.parkNanos(1);
        } else {
            if (count > yieldCount)
                Thread.yield();
        }
    }

    public boolean isSleeping(int yieldCount) {
        return yieldCount > sleepCount;
    }

    public boolean isYielding(int count) {
        return count > yieldCount;
    }

}
