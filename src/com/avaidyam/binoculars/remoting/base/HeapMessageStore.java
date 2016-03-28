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

package com.avaidyam.binoculars.remoting.base;

import java.util.concurrent.ConcurrentHashMap;

/**
 * writes/reads on-heap. must be single threaded per queue
 *
 */
public class HeapMessageStore implements MessageStore {

	int maxStoreLength = 64;
	ConcurrentHashMap<CharSequence,StoreEntry> map = new ConcurrentHashMap<>();

	public HeapMessageStore(int maxStoreLength) {
		this.maxStoreLength = maxStoreLength;
	}

	@Override
	public Object getMessage(CharSequence queueId, long sequence) {
		StoreEntry byteSources = map.get(queueId);
		if ( byteSources != null ) {
			return byteSources.get(sequence);
		}
		return null;
	}

	@Override
	public void putMessage(CharSequence queueId, long sequence, Object message) {
		StoreEntry byteSources = map.get(queueId);
		if ( byteSources == null ) {
			byteSources = new StoreEntry(maxStoreLength);
			map.put(queueId,byteSources);
		}
		byteSources.add(message,sequence);
	}

	@Override
	public void confirmMessage(CharSequence queueId, long sequence) {
		StoreEntry byteSources = map.get(queueId);
		if ( byteSources != null ) {
			byteSources.confirm(sequence);
		}
	}

	@Override
	public void killQueue(CharSequence queueId) {
		map.remove(queueId);
	}

	static class StoreEntry {

		public StoreEntry(int len) {
			messages = new Object[len];
			sequences = new long[len];
		}

		Object[] messages;
		long sequences[];
		int writePos;
		int readPos;

		public void add(Object msg, long seq) {
			messages[writePos] = msg;
			sequences[writePos] = seq;
			writePos++;
			if ( writePos == messages.length ) {
				writePos = 0;
			}
			if ( writePos == readPos ) {
				readPos++;
				if ( readPos == messages.length ) {
					readPos = 0;
				}
			}
		}

		public void confirm(long seq) {
			int idx = readPos;
			for (int i = 0; i < messages.length; i++) {
				if ( seq <= sequences[idx] ) {
					messages[idx] = null;
					sequences[idx] = 0;
				} else {
					readPos = idx;
					if ( readPos < 0 )
						readPos = 0;
					return;
				}
				idx++;
				if ( idx == messages.length )
					idx = 0;
			}
			readPos = idx-1;
			if ( readPos < 0 )
				readPos = 0;
		}

		public Object get(long seq) {
			int idx = readPos;
			for (int i = 0; i < messages.length; i++) {
				if ( seq == sequences[idx] )
					return messages[idx];
				idx++;
				if ( idx == messages.length )
					idx = 0;
			}
			return null;
		}

	}
}

