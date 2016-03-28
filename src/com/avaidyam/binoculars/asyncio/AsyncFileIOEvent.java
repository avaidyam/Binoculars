
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

package com.avaidyam.binoculars.asyncio;

import java.nio.ByteBuffer;

/**
 * Created by moelrue on 5/4/15.
 */
public class AsyncFileIOEvent {

    int read;
    long nextPosition;
    ByteBuffer buffer;

    public AsyncFileIOEvent(long position, int read, ByteBuffer buffer) {
        this.nextPosition = position;
        this.buffer = buffer;
        this.read = read;
    }

    public long getNextPosition() {
        return nextPosition;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public byte[] copyBytes() {
        byte b[] = new byte[buffer.limit() - buffer.position()];
        buffer.get(b);
        return b;
    }

    public int getRead() {
        return read;
    }

    @Override
    public String toString() {
        return "AsyncFileIOEvent{" +
                "read=" + read +
                ", nextPosition=" + nextPosition +
                ", buffer=" + buffer +
                '}';
    }


    public void reset() {
        buffer.position(0);
        buffer.limit(buffer.capacity());
    }
}
