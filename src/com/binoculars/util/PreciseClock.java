package com.binoculars.util;

public class PreciseClock {
    private static final long utcOrigin = System.currentTimeMillis();
    private static final long originNano = System.nanoTime();

    public static final PreciseClock INSTANCE = new PreciseClock();

    public PreciseDate now() {
        return new PreciseDate(1000000L * utcOrigin + (System.nanoTime() - originNano));
    }

    public static class TimeSpan {
        private final long nanos;

        public TimeSpan(long nanos) {
            this.nanos = nanos;
        }

        public double toSeconds() {
            return (1.0 * nanos) / 1.0e9;
        }

        public double toMillis() {
            return (1.0 * nanos) / 1.0e6;
        }

        public long toNanos() {
            return nanos;
        }

        public String toString() {
            return (1.0e-6*nanos) + "ms";
        }
    }
}
