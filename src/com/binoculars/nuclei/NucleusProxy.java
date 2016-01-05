package com.binoculars.nuclei;

/**
 * A tagging interface to identify Nucleus proxies. {@link Nucleus#of(Class)}
 * internally generates an {@link NucleusProxy} which translates each method
 * call into a message to be enqueued by the elastic scheduler to the
 * underlying nuclei instance.
 *
 * @param <T> the type of the Nucleus proxied
 */
public interface NucleusProxy<T extends Nucleus> {

    /**
     * Returns the underlying Nucleus behind this NucleusProxy. Can be
     * used to verify if an Object is the real nuclei, or a proxy,
     * like so: {@code nuclei.getNucleus() == nuclei}.
     *
     * @return the nuclei under this proxy
     */
    Nucleus<T> getNucleus();
}
