package com.binoculars.nuclei;

import com.binoculars.future.Future;

/**
 * A RemoteConnection facilitates a registry mapping of NucleusProxy to IDs
 * and can have a custom ClassLoader.
 */
public interface RemoteConnection {

    /**
     * Closes the remote connection and clears its registry.
     */
    void close();

    /**
     * Assign a custom {@link ClassLoader} to the remote connection.
     *
     * @param classLoader the ClassLoader to set
     */
    void setClassLoader(ClassLoader classLoader);

    /**
     * Returns the remote registry reference ID for the given Nucleus.
     *
     * @param nucleus the nuclei to return the reference ID of
     * @return the ID of the nuclei as a remote reference
     */
    int getRemoteId(Nucleus nucleus);

    /**
     * Unpublishes the Nucleus from the registry, but does not close the connection.
     *
     * @param self the nuclei to unpublish
     */
    void unpublishNucleus(Nucleus self);


	/**
	 * closes underlying network connection also. Can be dangerous as other clients might share it
	 */
	Future closeNetwork();
}
