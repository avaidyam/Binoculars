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

package org.kihara.util;

import java.util.HashMap;
import java.util.Random;
import java.util.Set;

/**
 * Created by andrew on 11/14/16.
 */
public class TicketManager<T> {

    public static Set<Integer> idSet;

    public HashMap<Integer, T> fileHashMap;

    public TicketManager() {
        fileHashMap = new HashMap<>();
    }

    /**
     * Tells if a ticket has already been registered with the ID
     * @param id
     * @return
     */
    public boolean hasTicket(int id) {
        return idSet.contains(id);
    }

    /**
     * Generates new random int ID
     * @return
     */
    private int getNewId() {

        int newId;
        Random generator = new Random();

        do {
            newId = Math.abs(generator.nextInt());
        } while (hasTicket(newId));

        return newId;
    }


    /**
     * Registers a new ticket with a random ID
     * @return
     */
    public int getNewTicket() {
        int newId = getNewId();

        idSet.add(newId);

        return newId;
    }

    /**
     * Gets the file for the ID
     * @param id
     * @return
     */
    public T getFile(int id) {
        if (!hasTicket(id)) {
            return null;
        }
        return fileHashMap.get(id);
    }

    /**
     * Checks if the ticket exists and is set
     * @param id
     * @return
     */
    public boolean isSet(int id) {
        if (hasTicket(id)) {
            return fileHashMap.containsKey(id);
        }
        return false;
    }

    /**
     * Sets the file for the ticket if the ticket exists and is not set
     * @param id
     * @param s
     */
    public void set(int id, T s) {
        if ((!hasTicket(id)) || isSet(id)) {
            // TODO: Maybe throw exception or something?
            return;
        }

        fileHashMap.put(id, s);
    }

    /**
     * Removes the file with the id, or the id from the list of ids
     * @param id
     */
    public void removeFile(int id) {
        if (isSet(id)) {
            fileHashMap.remove(id);
        }
        else if (hasTicket(id)) {
            idSet.remove(id);
        }
    }
}
