package com.binoculars.future;

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
