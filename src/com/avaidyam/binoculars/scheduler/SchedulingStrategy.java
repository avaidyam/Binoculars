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

package com.avaidyam.binoculars.scheduler;

import java.util.concurrent.locks.LockSupport;

/**
 * Schedulers apply the SchedulingStrategy to know when and how often to spinlock.
 * By adjusting these values, a tradeoff between latency and CPU load is made.
 */
public class SchedulingStrategy {

    /**
     * The default sleep (in nanoseconds) duration until parking the thread.
     */
    public static final int NANOS_TO_PARK = 1000 * 1000; // could be 1000 * 300

    /**
     * The default number of spins until yielding the thread.
     */
    public static final int SPIN_UNTIL_YIELD = 100;

    /**
     * The default number of yields until parking the thread.
     */
    public static final int YIELD_UNTIL_PARK = 100;

    /**
     * The default number of parks until sleeping the thread.
     */
    public static final int PARK_UNTIL_SLEEP = 10;

    private int yieldCount;
    private int parkCount;
    private int sleepCount;
    private int nanosToPark = NANOS_TO_PARK;

    /**
     * Initialize the default SchedulingStrategy.
     */
    public SchedulingStrategy() {
        setCounters(SPIN_UNTIL_YIELD, YIELD_UNTIL_PARK, PARK_UNTIL_SLEEP);
    }

    /**
     * Initialize a custom SchedulingStrategy.
     *
     * @param spinUntilYield number of busy spins until Thread.yield is used
     * @param yieldUntilPark number of Thread.all iterations until parkNanos(1) is used
     * @param parkUntilSleep number of parkNanos(1) is used until park(nanosToPark) is used.
     *                       the default for nanosToPark is 0.5 milliseconds.
     */
    public SchedulingStrategy(int spinUntilYield, int yieldUntilPark, int parkUntilSleep) {
        setCounters(spinUntilYield, yieldUntilPark, parkUntilSleep);
    }

    /**
     * Set the counters used in calculating the SchedulingStrategy.
     *
     * @param spinUntilYield number of busy spins until Thread.yield is used
     * @param yieldUntilPark number of Thread.all iterations until parkNanos(1) is used
     * @param parkUntilSleep number of parkNanos(1) is used until park(nanosToPark) is used.
     *                       the default for nanosToPark is 0.5 milliseconds.
     */
    public void setCounters(int spinUntilYield, int yieldUntilPark, int parkUntilSleep) {
        yieldCount = spinUntilYield;
        parkCount = spinUntilYield + yieldUntilPark;
        sleepCount = spinUntilYield + yieldUntilPark + parkUntilSleep;
    }

    /**
     * Get the duration until parking the thread.
     *
     * @return duration until parking the thread
     */
    public int getNanosToPark() {
        return nanosToPark;
    }

    /**
     * Set the duration until parking the thread.
     *
     * @param nanosToPark duration until parking the thread
     */
    public void setNanosToPark(int nanosToPark) {
        this.nanosToPark = nanosToPark;
    }

    /**
     * Yield the thread given the current counter.
     *
     * @param count the current counter
     */
    public void yield(int count) {
        if (count > sleepCount || count < 0)
            LockSupport.parkNanos(nanosToPark);
        else if (count > parkCount)
            LockSupport.parkNanos(1);
        else if (count > yieldCount)
            Thread.yield();
    }

    /**
     *
     * @param yieldCount
     * @return
     */
    public boolean isSleeping(int yieldCount) {
        return yieldCount > sleepCount;
    }

    /**
     *
     * @param count
     * @return
     */
    public boolean isYielding(int count) {
        return count > yieldCount;
    }
}
