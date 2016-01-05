package com.binoculars.future;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class Source<I, O> implements Serializable {

    private I _source;
    private Transformer<I, Integer, Integer, O> _function;

    public Source(I input, Transformer<I, Integer, Integer, O> transformer) {
        Objects.requireNonNull(input, "The source cannot have a null input!");
        Objects.requireNonNull(transformer, "The source cannot have a null transformer!");

        this._source = input;
        this._function = transformer;
    }

    public O get(int idx, int total) {
        return this._function.apply(this._source, idx, total);
    }

    @FunctionalInterface
    public interface Transformer<T, U, V, R> extends Serializable {
        R apply(T t, U u, V v);

        default <X> Transformer<T, U, V, X> andThen(Function<? super R, ? extends X> after) {
            Objects.requireNonNull(after);
            return (T t, U u, V v) -> after.apply(apply(t, u, v));
        }
    }

    public static final Transformer<String, Integer, Integer, String> FILE_SPLITTER = (path, idx, total) -> {
        ByteBuffer buffer = null;
        try (SeekableByteChannel channel = Files.newByteChannel(Paths.get(path), StandardOpenOption.READ)) {
            buffer = ByteBuffer.allocate((int)(channel.size() / total));
            channel.position(idx * (channel.size() / total));
            channel.read(buffer);
            buffer.flip();

            return Charset.forName(System.getProperty("file.encoding")).decode(buffer).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } finally {
            if(buffer != null)
                buffer.clear();
        }
    };

    public static class FileSpliterator implements Spliterator<String> {
        private Path path;
        private SeekableByteChannel channel;
        private ByteBuffer buffer;

        // Split Constructs
        private long idx, offset, length, size;

        public FileSpliterator(Path path, long offset, long length, long size) {
            this.path = path;

            this.size = size;
            this.length = length;
            this.offset = offset;

            try {
                this.channel = Files.newByteChannel(path, StandardOpenOption.READ);
                this.buffer = ByteBuffer.allocate((int) (this.channel.size() / this.size));
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        public FileSpliterator(Path path, long offset, long length) {
            this(path, offset, length, length);
        }

        @Override
        public Spliterator<String> trySplit() {
            if(this.length < 2)
                return null;

            // size   = 15 --> size   = 15 | size   = 15
            // length = 15 --> length = 7  | length = 8
            // offset = 0  --> offset = 0  | offset = 7

            long div = this.length / 2;
            this.length -= div;
            this.offset = div;
            this.idx = 0;

            return new FileSpliterator(this.path, 0, div, this.size);
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            //System.out.println("Advanced as: [" + this.offset + " + " + this.idx +
            //                   " --> " + this.length + " | " + this.size + "].");
            //
            // offset = 6, length = 5, total = 25
            //        idx --v
            // [ I I I I I I#I#I#I#I#I I I I I I I I I I I I I I ]

            if(this.idx == this.length)
                return false;

            try {
                this.channel.position((this.offset + this.idx++) * (this.channel.size() / this.size));
                this.channel.read(this.buffer);
                this.buffer.flip();

                String encoding = System.getProperty("file.encoding");
                String value = Charset.forName(encoding).decode(buffer).toString();
                this.buffer.clear();

                action.accept(value);
                return true;
            } catch(IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public long estimateSize() {
            return length;
        }

        @Override
        public int characteristics() {
            return DISTINCT | NONNULL;
            //return CONCURRENT | DISTINCT | IMMUTABLE |
            //        NONNULL | ORDERED | SIZED | SUBSIZED;
        }

        /*@Override public Spliterator<T> trySplit() {
            final HoldingConsumer<T> holder = new HoldingConsumer<>();
            if (!spliterator.tryAdvance(holder)) return null;
            final Object[] a = new Object[batchSize];
            int j = 0;
            do a[j] = holder.value; while (++j < batchSize && tryAdvance(holder));
            if (est != Long.MAX_VALUE) est -= j;
            return Spliterators.spliterator(a, 0, j, characteristics());
        }
        @Override public boolean tryAdvance(Consumer<? super T> action) {
            return spliterator.tryAdvance(action);
        }
        @Override public void forEachRemaining(Consumer<? super T> action) {
            spliterator.forEachRemaining(action);
        }
        @Override public Comparator<? super T> getComparator() {
            if (hasCharacteristics(SORTED)) return null;
            throw new IllegalStateException();
        }
        @Override public long estimateSize() { return est; }
        @Override public int characteristics() { return characteristics; }//*/
    }
}
