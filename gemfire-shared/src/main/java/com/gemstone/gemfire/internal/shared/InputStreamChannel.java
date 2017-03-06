/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.gemstone.gemfire.internal.shared;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.locks.LockSupport;

/**
 * Intermediate class that extends both an InputStream and ReadableByteChannel.
 * 
 * @author swale
 * @since gfxd 1.1
 */
public abstract class InputStreamChannel extends InputStream implements
    ReadableByteChannel, Closeable {

  protected final ReadableByteChannel channel;
  protected volatile Thread parkedThread;
  protected volatile long bytesRead;

  /**
   * nanos to park reader thread to wait for reading data in non-blocking mode
   * (will be explicitly signalled by selector if data is received)
   */
  protected static final long PARK_NANOS = 200L;
  protected static final long PARK_NANOS_MAX = 15000000000L;

  protected InputStreamChannel(ReadableByteChannel channel) {
    this.channel = channel;
  }

  /**
   * Get the underlying {@link ReadableByteChannel}.
   */
  public final ReadableByteChannel getUnderlyingChannel() {
    return this.channel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract int read(ByteBuffer dst) throws IOException;

  /**
   * Reads four input bytes and returns an <code>int</code> value. Let
   * <code>a-d</code> be the first through fourth bytes read. The value returned
   * is:
   * <p>
   * 
   * <pre>
   * <code>
   * (((a &amp; 0xff) &lt;&lt; 24) | ((b &amp; 0xff) &lt;&lt; 16) |
   * &#32;((c &amp; 0xff) &lt;&lt; 8) | (d &amp; 0xff))
   * </code>
   * </pre>
   * 
   * This method is suitable for reading bytes written by the
   * <code>writeInt</code> method of interface <code>DataOutput</code>.
   * 
   * @return the <code>int</code> value read.
   * @exception EOFException
   *              if this stream reaches the end before reading all the bytes.
   * @exception IOException
   *              if an I/O error occurs.
   */
  public abstract int readInt() throws IOException;

  public int readFrame() throws IOException {
    throw new UnsupportedOperationException(getClass().getSimpleName()
        + ": readFrame not supported");
  }

  public int readFrameFragment(int fragmentSize) throws IOException {
    throw new UnsupportedOperationException(getClass().getSimpleName()
        + ": readFrameFragment not supported");
  }

  /**
   * Common base method to read into a given ByteBuffer destination via an
   * intermediate direct byte buffer owned by the implementation of this class.
   */
  protected final int readBuffered(final ByteBuffer dst,
      final ByteBuffer channelBuffer) throws IOException {
    int dstLen = dst.remaining();
    // first copy anything remaining from buffer
    final int remaining = channelBuffer.remaining();
    if (dstLen <= remaining) {
      if (dstLen > 0) {
        final int pos = channelBuffer.position();
        // reduce this buffer's limit temporarily to the required len
        channelBuffer.limit(pos + dstLen);
        try {
          dst.put(channelBuffer);
        } finally {
          // restore the limit
          channelBuffer.limit(pos + remaining);
        }
        return dstLen;
      } else {
        return 0;
      }
    }

    // refill buffer once and read whatever available into buf;
    // caller should invoke in a loop if buffer is still not full
    int readBytes = 0;
    if (remaining > 0) {
      dst.put(channelBuffer);
      dstLen -= remaining;
      readBytes += remaining;
    }
    // if dst is a reasonably large direct byte buffer, then refill from
    // channel directly else use channel direct buffer for best performance
    if (dstLen >= (channelBuffer.limit() >>> 1) && dst.isDirect()) {
      final int bufBytes = readIntoBufferNonBlocking(dst);
      if (bufBytes > 0) {
        return (readBytes + bufBytes);
      } else {
        return readBytes > 0 ? readBytes : bufBytes;
      }
    } else {
      final int bufBytes = refillBufferBase(channelBuffer, -1, null);
      if (bufBytes > 0) {
        if (dstLen >= bufBytes) {
          dst.put(channelBuffer);
          return (readBytes + bufBytes);
        } else {
          final int pos = channelBuffer.position();
          // reduce this buffer's limit temporarily to the required length
          channelBuffer.limit(pos + dstLen);
          try {
            dst.put(channelBuffer);
          } finally {
            // restore the limit
            channelBuffer.limit(pos + bufBytes);
          }
          return (readBytes + dstLen);
        }
      } else {
        return readBytes > 0 ? readBytes : bufBytes;
      }
    }
  }

  protected int refillBuffer(final ByteBuffer channelBuffer,
      final int tryReadBytes, final String eofMessage) throws IOException {
    return refillBufferBase(channelBuffer, tryReadBytes, eofMessage);
  }

  protected final int refillBufferBase(final ByteBuffer channelBuffer,
      final int tryReadBytes, final String eofMessage) throws IOException {
    resetAndCopyLeftOverBytes(channelBuffer);
    int initPosition = channelBuffer.position();
    int totalReadBytes = initPosition;
    final int channelBytes = readIntoBuffer(channelBuffer);
    if (channelBytes > 0) {
      totalReadBytes += channelBytes;
    }
    // eof on stream but we may still have remaining bytes in channelBuffer
    else if (totalReadBytes == 0) {
      totalReadBytes = channelBytes;
    }
    while (tryReadBytes > totalReadBytes && channelBuffer.hasRemaining()) {
      int readBytes = readIntoBuffer(channelBuffer);
      if (readBytes < 0) {
        if (eofMessage != null) {
          throw new EOFException(eofMessage);
        }
        else {
          if (totalReadBytes == 0) {
            totalReadBytes = readBytes;
          }
          break;
        }
      }
      if (totalReadBytes < 0) {
        totalReadBytes = 0;
      }
      totalReadBytes += readBytes;
    }
    channelBuffer.flip();
    assert (totalReadBytes == channelBuffer.limit()) || (totalReadBytes == -1
        && channelBuffer.limit() == 0): "readBytes=" + totalReadBytes
            + " != limit=" + channelBuffer.limit();
    if (totalReadBytes > initPosition) {
      this.bytesRead += (totalReadBytes - initPosition);
    }
    return totalReadBytes;
  }

  protected void resetAndCopyLeftOverBytes(final ByteBuffer channelBuffer) {
    if (channelBuffer.hasRemaining()) {
      channelBuffer.compact();
    }
    else {
      channelBuffer.clear();
    }
  }

  /**
   * Fill the given buffer reading data from channel at least one byte unless
   * end-of-stream has been reached.
   * 
   * @return number of bytes read or -1 on end-of-stream
   */
  protected int readIntoBuffer(ByteBuffer buffer) throws IOException {
    long parkNanos = 0;
    int readBytes;
    while ((readBytes = this.channel.read(buffer)) == 0) {
      if (!buffer.hasRemaining()) {
        break;
      }
      // at this point we are out of the selector thread and don't want to
      // create unlimited size buffers upfront in selector, so will use simple
      // signalling between selector and this thread to proceed
      this.parkedThread = Thread.currentThread();
      LockSupport.parkNanos(this, PARK_NANOS);
      this.parkedThread = null;
      if ((parkNanos += PARK_NANOS) > PARK_NANOS_MAX) {
        throw new SocketTimeoutException("Connection read timed out.");
      }
    }
    return readBytes;
  }

  /**
   * Fill the given buffer reading data from channel whatever is available in
   * one call (non-blocking if channel is so configured).
   * 
   * @return number of bytes read (can be zero) or -1 on end-of-stream
   */
  protected int readIntoBufferNonBlocking(ByteBuffer buffer)
      throws IOException {
    final int numBytes = this.channel.read(buffer);
    if (numBytes > 0) {
      this.bytesRead += numBytes;
    }
    return numBytes;
  }

  public final Thread getParkedThread() {
    return this.parkedThread;
  }

  public final long getBytesRead() {
    return this.bytesRead;
  }
}
