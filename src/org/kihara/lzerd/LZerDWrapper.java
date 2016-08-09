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

package org.kihara.lzerd;

/**
 * Created by andrew on 8/3/16.
 */
public class LZerDWrapper {

    static {
        System.setProperty("java.library.path", "./lib");
        System.loadLibrary("LZerDWrapper");
    }

    public native void callLZerD(String rec, String lig, String rzec, String zlig,
                                 String prec, String plig, String irec, String ilig,
                                 double corr, double rfmin, double rfmax, double rfdist,
                                 double dcut, int votes, double nrad, boolean applyrandommotion,
                                 String output_filename);

    public static void runLZerD(String rec, String lig, String rzec, String zlig,
                                String prec, String plig, String irec, String ilig,
                                double corr, double rfmin, double rfmax, double rfdist,
                                double dcut, int votes, double nrad, boolean applyrandommotion,
                                String output_filename) {

        LZerDWrapper wrapper = new LZerDWrapper();

        wrapper.callLZerD(rec, lig, rzec, zlig,
                prec, plig, irec, ilig,
                corr, rfmin, rfmax, rfdist,
                dcut, votes, nrad, applyrandommotion,
                output_filename);

    }

}
