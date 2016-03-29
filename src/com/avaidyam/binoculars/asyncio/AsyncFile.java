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

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;
import org.nustaq.serialization.util.FSTUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class AsyncFile {

	/**
	 *
	 */
	public static class IOEvent {

		int read;
		long nextPosition;
		ByteBuffer buffer;

		public IOEvent(long position, int read, ByteBuffer buffer) {
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
			return "IOEvent{" +
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

	private static FileAttribute[] NO_ATTRIBUTES = new FileAttribute[0];

	byte tmp[];

    AsynchronousFileChannel fileChannel;

    IOEvent event = null;

    /**
     * create an unitialized AsyncFile. Use open to actually open a file
     */
    public AsyncFile() {}

	/**
	 * create an async file and open for read
	 * @param file
	 * @throws IOException
	 */
	public AsyncFile(String file) throws IOException {
		open(Paths.get(file), StandardOpenOption.READ);
	}

	/**
	 * create an async file and open with given options (e.g. StandardOptions.READ or 'StandardOpenOption.WRITE, StandardOpenOption.CREATE')
	 * @param file
	 * @throws IOException
	 */
	public AsyncFile(String file, OpenOption... options) throws IOException {
		open(Paths.get(file), options);
	}

	/**
	 *
	 * @param file
	 * @param options
	 * @throws IOException
	 */
	public AsyncFile(Path file, OpenOption... options) throws IOException {
		open(file, options);
	}

    /**
     * return a pseudo-blocking input stream. Note: due to limitations of the current await implementation (stack based),
     * when reading many files concurrently from a single nuclei thread don't mix high latency file locations (e.g. remote file systems vs. local)
     * with low latency ones. If this is required, fall back to the more basic read/write methods returning futures.
     *
	 * @return
	 */
    public InputStream asInputStream() {

        if ( tmp != null )
            throw new RuntimeException("can create Input/OutputStream only once");
        tmp = new byte[1];
        return new InputStream() {

            @Override
            public void close() throws IOException {
                AsyncFile.this.close();
            }


            @Override
            public int read() throws IOException {
                // should rarely be called, super slow
                int read = read(tmp, 0, 1);
                if ( read < 1 ) {
                    return -1;
                }
                return (tmp[0]+256)&0xff;
            }

            @Override
            public int read(byte b[], int off, int len) throws IOException {
                if ( event == null ) {
                    event = new IOEvent(0,0, ByteBuffer.allocate(len));
                }
                if ( event.getBuffer().capacity() < len ) {
                    event.buffer = ByteBuffer.allocate(len);
                }
                ByteBuffer buffer = event.buffer;
                event.reset();
                event = AsyncFile.this.read(event.getNextPosition(), len, buffer).await();
                int readlen = event.getRead();
                if ( readlen > 0 )
                    buffer.get(b,off,readlen);
                return readlen;
            }
        };
    }

    /**
     * return a pseudo-blocking output stream. Note: due to limitations of the current await implementation (stack based),
     * when writing many files concurrently from a single nuclei thread don't mix high latency file locations (e.g. remote file systems vs. local)
     * with low latency ones. If this is required, fall back to the more basic read/write methods returning futures.
     *
     * @return
     */
    public OutputStream asOutputStream() {
        if ( tmp != null )
            throw new RuntimeException("can create Input/OutputStream only once");
        tmp = new byte[1];
        return new OutputStream() {

            @Override
            public void write(int b) throws IOException {
                tmp[0] = (byte) b;
                write(tmp, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if ( event == null ) {
                    event = new IOEvent(0,0, ByteBuffer.allocate(len));
                }
                if ( event.getBuffer().capacity() < len ) {
                    event.buffer = ByteBuffer.allocate(len);
                }
                ByteBuffer buffer = event.buffer;
                event.reset();
                buffer.put(b,off,len);
                buffer.flip();
                event = AsyncFile.this.write(event.getNextPosition(), buffer).await();
                if ( event.getRead() != len )
                    throw new RuntimeException("unexpected. Pls report");
            }

            @Override
            public void close() throws IOException {
                AsyncFile.this.close();
            }

        };
    }

	/**
	 *
	 * @param file
	 * @param options
	 * @throws IOException
	 */
    public void open(Path file, OpenOption... options) throws IOException {
        if ( fileChannel != null )
            throw new RuntimeException("can only open once");
        Nucleus sender = Nucleus.current();
        Set<OpenOption> set = new HashSet<>(options.length);
        Collections.addAll(set, options);
        fileChannel = AsynchronousFileChannel.open(file, set, Nucleus.toExecutor(sender), NO_ATTRIBUTES);
    }

	/**
	 *
	 * @return
	 */
    public long length() {
        try {
            return fileChannel.size();
        } catch (IOException e) {
            FSTUtil.<RuntimeException>rethrow(e);
        }
        return -1;
    }

	/**
	 *
	 * @return
	 */
    public Future<IOEvent> readFully() {
        ByteBuffer buf = ByteBuffer.allocate((int) length());
        IOEvent ev = new IOEvent(0,0,buf);
        do {
            ev = read(ev.nextPosition, (int) ((int) length() - ev.nextPosition), buf).await();
        } while( buf.limit() != buf.capacity() && ev.getNextPosition() >= 0 );
        return new CompletableFuture<>(ev);
    }

	/**
	 *
	 * @param position
	 * @param chunkSize
	 * @param target
	 * @return
	 */
    public Future<IOEvent> read(long position, int chunkSize, ByteBuffer target) {
        if (fileChannel == null)
            throw new RuntimeException("file not opened");
        Nucleus sender = Nucleus.current();
        CompletableFuture p = new CompletableFuture();
        if (target == null) {
            target = ByteBuffer.allocate(chunkSize);
        }
        final long bufferStartPos = target.position();
        final ByteBuffer finalTarget = target;
        fileChannel.read(target, position, target, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                // FIXME: how to handle incomplete read. (currently burden on reader)
                long newPos = position + finalTarget.limit() - bufferStartPos;
                if (result < 0)
                    newPos = -1;
                attachment.flip();
                p.resolve(new IOEvent(newPos, result, finalTarget));
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                p.reject(exc);
            }
        });
        return p;
    }

	/**
	 *
	 * @param filePosition
	 * @param source
	 * @return
	 */
    public Future<IOEvent> write(long filePosition, ByteBuffer source) {
        if (fileChannel == null)
            throw new RuntimeException("file not opened");
        Nucleus sender = Nucleus.current();
        CompletableFuture p = new CompletableFuture();
        final long bufferStartPos = source.position();
        final ByteBuffer finalTarget = source;
        fileChannel.write(source, filePosition, source, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                if ( source.remaining() > 0 ) {
                    // just retry (will enqueue new message/job to nuclei mailbox)
                    fileChannel.write(source,filePosition,source,this);
                } else {
                    long newPos = filePosition + finalTarget.limit() - bufferStartPos;
                    if (result < 0)
                        newPos = -1;
                    attachment.flip();
                    p.resolve(new IOEvent(newPos, result, finalTarget));
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                p.reject(exc);
            }
        });
        return p;
    }

	/**
	 *
	 */
    public void close() {
        try {
            fileChannel.close();
            fileChannel = null;
        } catch (IOException e) {
            FSTUtil.<RuntimeException>rethrow(e);
        }
    }
}
