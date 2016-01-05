package com.binoculars.util;

import java.util.Comparator;

public class Pair<A,B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public static <A,B> Comparator<Pair<A,B>> compareOnSecond(final Comparator<B> comparator) {
        return ((x, y) -> comparator.compare(x.second, x.second));
    }

    @Override
    public String toString() {
        return "Pair{" + "first=" + first + ", second=" + second + '}';
    }
}
