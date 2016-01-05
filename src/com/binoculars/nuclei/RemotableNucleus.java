package com.binoculars.nuclei;

/**
 * An optional interface specifying that an Nucleus can receive
 * certain messages pertaining to RemoteConnections.
 */
public interface RemotableNucleus {

    /**
     * The Nucleus has been unpublished from the registry it
     * was remoted from.
     */
    void unpublished();
}
