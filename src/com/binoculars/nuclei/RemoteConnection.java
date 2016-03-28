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
	 * Closes underlying network connection also. Can be dangerous as other clients might share it
	 *
	 * @return
	 */
	Future closeNetwork();
}
