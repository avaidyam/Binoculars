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

package com.binoculars.util;

import java.io.File;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import com.sun.management.OperatingSystemMXBean;

public class Metrics implements Serializable {
    public static class CPU extends Metrics {
        // CPU_IDLE
        // CPU_SYSTEM
        // CPU_USER
        // CPU_NICE
        // CPU_WAIT_IO

        public final int CORES = os().getAvailableProcessors();
        public final String ARCH = os().getArch();
        public final String KERNEL = os().getVersion();

        public static Metrics.CPU measure() {
            return new Metrics.CPU();
        }
        public String toString() {
            return "CPU{" + "CORES=" + CORES + ", ARCH=" + ARCH + ", KERNEL=" + KERNEL + "}";
        }
    }

    public static class Disk extends Metrics {
        public final int FREE_SPACE = (int)(fs().getFreeSpace() / 1024 / 1024);
        public final int TOTAL_SPACE = (int)(fs().getTotalSpace() / 1024 / 1024);

        public static Metrics.Disk measure() {
            return new Metrics.Disk();
        }
        public String toString() {
            return "Disk{" + "FREE_SPACE=" + FREE_SPACE + ", TOTAL_SPACE=" + TOTAL_SPACE + '}';
        }
    }

    public static class Load extends Metrics {
        public final int UPTIME = (int)(vm().getUptime() / 1000);
        public final float LOAD_AVERAGE = (float)os().getSystemCpuLoad();

        public static Metrics.Load measure() {
            return new Metrics.Load();
        }
        public String toString() {
            return "Load{" + "UPTIME=" + UPTIME + ", LOAD_AVERAGE=" + LOAD_AVERAGE + '}';
        }
    }

    public static class Memory extends Metrics {
        public final int FREE_MEMORY = (int)(os().getFreePhysicalMemorySize() / 1024 / 1024);
        public final int TOTAL_MEMORY = (int)(os().getTotalPhysicalMemorySize() / 1024 / 1024);

        public static Metrics.Memory measure() {
            return new Metrics.Memory();
        }
        public String toString() {
            return "Memory{" + "FREE_MEMORY=" + FREE_MEMORY + ", TOTAL_MEMORY=" + TOTAL_MEMORY + '}';
        }
    }

    public static class Network extends Metrics {
        // BYTES_IN
        // BYTES_OUT
        // PACKETS_IN
        // PACKETS_OUT

        public static Metrics.Network measure() {
            return new Metrics.Network();
        }
        public String toString() {
            return "Network{}";
        }
    }

    public static class Process extends Metrics {
        // TOTAL_PROCESSES
        // RUNNING_PROCESSES

        public static Metrics.Process measure() {
            return new Metrics.Process();
        }
        public String toString() {
            return "Process{}";
        }
    }

    public Metrics.CPU CPU;
    public Metrics.Disk Disk;
    public Metrics.Load Load;
    public Metrics.Memory Memory;
    public Metrics.Network Network;
    public Metrics.Process Process;

    // Factory pattern, for future expansion.
    private Metrics() {}
    public static Metrics collect() {
        Metrics metrics = new Metrics();
        metrics.CPU = metrics.CPU.measure();
        metrics.Disk = metrics.Disk.measure();
        metrics.Load = metrics.Load.measure();
        metrics.Memory = metrics.Memory.measure();
        metrics.Network = metrics.Network.measure();
        metrics.Process = metrics.Process.measure();
        return metrics;
    }

    public String toString() {
        return "Metrics{" + CPU + ", " + Disk + ", " + Load + ", " +
                Memory + ", " + Network + ", " + Process + '}';
    }

    // Utility Methods
    // TODO: CPU L1/L2/L3 Cache, Bus + Clock Speed, Graphics Cards
    private static OperatingSystemMXBean os() {
        return (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    private static RuntimeMXBean vm() {
        return ManagementFactory.getRuntimeMXBean();
    }

    private static File fs() {
        return File.listRoots()[0];
    }
}
