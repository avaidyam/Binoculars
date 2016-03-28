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

package org.kihara.tasks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;

public abstract class Dataset<T extends Serializable, V> implements Serializable  {

    // is it cloned or remotely proxied?
    // proxy has network access latency issues and allOf
    // cloned would require a peer-peer "download" system
    //public boolean cloned = false;

    // the source of the dataset
    // i.e if in JS, create a new URLDataset("url here")
    // then set cloned = true, so the URL goes to allOf nodes
    // when a node needs to access it, it could do:
    // var str = `curl ${dataset.get()}`
    // now str contains the downloaded info from curl
    // also could use Runtime.exec to do the same thing
    // etc etc
    //public String type = ""; // url, collection, hdfs, etc

    private T value;

    public Dataset(T value) {
        this.value = value;
    }

    public V get() {
        StringBuffer output = new StringBuffer();
        try {
            Process p = Runtime.getRuntime().exec("curl " + value);
            p.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine())!= null)
                output.append(line + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return (V)output.toString();
    }
}
