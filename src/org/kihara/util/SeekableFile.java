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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SeekableFile {

    private final Path path;
    //private final SeekableByteChannel sbc;

    public SeekableFile(Path path) throws IOException {
		this.path = path;
        //this.sbc = Files.newByteChannel((this.path = path));
    }

    public SeekableFile(File file) throws IOException {
        this(file.toPath());
    }

    public SeekableFile(String string) throws IOException {
        this(Paths.get(string));
    }

    /*public char charAt(int index) {
        if(index < 0)
            return (char)-1;

        try {
            ByteBuffer buff = ByteBuffer.allocate(1);
            this.sbc.position(index);
            this.sbc.read(buff);

            buff.flip();
            CharBuffer cb = Charset.defaultCharset().decode(buff);
            return cb.charAt(0);
        } catch (IOException e) {
            return (char)-1;
        }
    }*/

    public int ordinalIndexOf(char item, int occurrence) {
		if(occurrence < 0 || path == null)
			return -1;

		int curr = 0;
		try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
			ByteBuffer buff = ByteBuffer.allocate(1 << 2); // size = 32, direct?
			buff.clear();

			for(int found = 0; sbc.read(buff) > 0; curr += buff.capacity()) {
				buff.flip();
				CharBuffer out = Charset.defaultCharset().decode(buff);

				char[] outa = out.array();
				for(int i = 0; i < out.length(); i++) {
					if((outa[i] == item) && (++found == occurrence + 1))
						return (curr + i);
				}
				buff.clear();
			}
		} catch (IOException e) {
			return -1;
		}
		return curr;
    }

    public int indexOf(char item) {
        return this.ordinalIndexOf(item, 0);
    }

    public String substring(int start, int end) {
		if(end < start || start < 0 || end < 0 || path == null)
			return null;

		try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
			ByteBuffer buff = ByteBuffer.allocate((end - start));
			sbc.position(start);
			sbc.read(buff);
			buff.flip();
			return Charset.defaultCharset().decode(buff).toString();
		} catch (IOException e) {
			return null;
		}
    }

    public String find(char item, int occurrence) {
        if(occurrence < 0 || path == null)
            return null;

        int start = this.ordinalIndexOf(item, occurrence);
        int end = this.ordinalIndexOf(item, occurrence + 1);
        if (start == end)
            return null;
        return this.substring(start, end);
    }


    /*@Override
    public String toString() {
        try {
            ByteBuffer buff = ByteBuffer.allocate((int)this.sbc.size());
            this.sbc.read(buff);

            buff.flip();
            CharBuffer cb = Charset.defaultCharset().decode(buff);
            return cb.toString();
        } catch (IOException e) {
            return null;
        }
    }*/

	public static int _ordinalIndexOf(String str, char c, int n) {
		int pos = str.indexOf(c, 0);
		while (n-- > 0 && pos != -1)
			pos = str.indexOf(c, pos+1);
		return pos;
	}

    /**
     *
     * @param source
     * @param zip
     * @throws IOException
     */
    public static void zip(String source, String zip) throws IOException {
        Path p = Files.createFile(Paths.get(zip));
        ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p));
        Path pp = Paths.get(source);

        Files.walk(pp)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(path.toAbsolutePath().toString()
                            .replace(pp.toAbsolutePath().toString(), "")
                            .replace(path.getFileName().toString(), "") +
                            "/" + path.getFileName().toString());

                    try {
                        zs.putNextEntry(zipEntry);
                        zs.write(Files.readAllBytes(path));
                        zs.closeEntry();
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                });
        zs.close();
    }

	public static String fromPath(Path path) throws IOException {
		return new String(Files.readAllBytes(path), "UTF-8");
	}

    public static String fromFile(String file) throws IOException {
		return fromPath(Paths.get(file));
	}
}
