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
/*
 * Changes for SnappyData distributed computational and data platform.
 *
 * Portions Copyright (c) 2017 SnappyData, Inc. All rights reserved.
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

package com.gemstone.gemfire.internal.shared.unsafe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import javax.annotation.Nonnull;

import com.gemstone.gemfire.internal.shared.ChannelBufferOutputStream;
import com.gemstone.gemfire.internal.shared.ClientSharedUtils;
import com.gemstone.gemfire.internal.shared.OutputStreamChannel;
import org.apache.spark.unsafe.Platform;

/**
 * A somewhat more efficient implementation of {@link ChannelBufferOutputStream}
 * using internal unsafe class (~30% in raw single byte write calls).
 * Use {@link UnsafeHolder#newChannelBufferOutputStream} method to create
 * either this or {@link ChannelBufferOutputStream} depending on availability.
 * <p>
 * NOTE: THIS CLASS IS NOT THREAD-SAFE BY DESIGN. IF IT IS USED CONCURRENTLY
 * BY MULTIPLE THREADS THEN BAD THINGS CAN HAPPEN DUE TO UNSAFE MEMORY WRITES.
 * <p>
 * Note that the close() method of this class does not close the underlying
 * channel.
 *
 * @author swale
 * @since gfxd 1.1
 */
public class ChannelBufferUnsafeOutputStream extends OutputStreamChannel {

  protected ByteBuffer buffer;
  protected final long baseAddress;
  /**
   * Actual buffer position (+baseAddress) accounting is done by this. Buffer
   * position is adjusted during refill and other places where required using
   * this.
   */
  protected long addrPosition;
  protected long addrLimit;

  /**
   * Some minimum buffer size, particularly for longs and encoding UTF strings
   * efficiently. If reducing this, then consider the logic in
   * {@link ChannelBufferUnsafeDataOutputStream#writeUTF(String)} carefully.
   */
  protected static final int MIN_BUFFER_SIZE = 10;

  public ChannelBufferUnsafeOutputStream(WritableByteChannel channel) {
    this(channel, ChannelBufferOutputStream.DEFAULT_BUFFER_SIZE, false);
  }

  public ChannelBufferUnsafeOutputStream(WritableByteChannel channel,
      int bufferSize, boolean useUnsafeAllocation) {
    super(channel);
    this.baseAddress = allocateBuffer(bufferSize, useUnsafeAllocation);
    resetBufferPositions();
  }

  public ChannelBufferUnsafeOutputStream(
      ChannelBufferUnsafeOutputStream other, WritableByteChannel channel,
      int bufferSize, boolean useUnsafeAllocation) throws IOException {
    super(channel);
    final ByteBuffer buffer = other.buffer;
    if (buffer != null) {
      other.flush();
      other.buffer = null;
      buffer.clear();
      this.buffer = buffer;
      this.baseAddress = other.baseAddress;
    } else {
      this.baseAddress = allocateBuffer(bufferSize, useUnsafeAllocation);
    }
    resetBufferPositions();
  }

  /**
   * Get handle to the underlying ByteBuffer. ONLY TO BE USED BY TESTS.
   */
  public ByteBuffer getInternalBuffer() {
    // set the current position
    this.buffer.position(position());
    return this.buffer;
  }

  protected final void resetBufferPositions() {
    this.addrPosition = this.baseAddress + this.buffer.position();
    this.addrLimit = this.baseAddress + this.buffer.limit();
  }

  protected long allocateBuffer(int bufferSize,
      boolean useUnsafeAllocation) {
    // expect minimum bufferSize of 10 bytes
    if (bufferSize < MIN_BUFFER_SIZE) {
      throw new IllegalArgumentException(
          "ChannelBufferUnsafeDataOutputStream: buffersize=" + bufferSize
              + " too small (minimum " + MIN_BUFFER_SIZE + ')');
    }
    final ByteBuffer buffer = useUnsafeAllocation
        // use Platform.allocate which does not have the smallish limit used
        // by ByteBuffer.allocateDirect -- see sun.misc.VM.maxDirectMemory()
        ? UnsafeHolder.allocateDirectBuffer(bufferSize)
        : ByteBuffer.allocateDirect(bufferSize);
    // set the order to native explicitly to skip any byte order conversions
    buffer.order(ByteOrder.nativeOrder());
    this.buffer = buffer;

    try {
      return UnsafeHolder.getDirectBufferAddress(buffer);
    } catch (Exception e) {
      releaseBuffer();
      throw ClientSharedUtils.newRuntimeException(
          "failed in creating an 'unsafe' buffered channel stream", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void write(int b) throws IOException {
    putByte((byte)(b & 0xff));
  }

  protected final void write_(byte[] b, int off, int len) throws IOException {
    if (len == 1) {
      putByte(b[off]);
      return;
    }

    while (len > 0) {
      final long addrPos = this.addrPosition;
      final int remaining = (int)(this.addrLimit - addrPos);
      if (len <= remaining) {
        Platform.copyMemory(b, Platform.BYTE_ARRAY_OFFSET + off,
            null, addrPos, len);
        this.addrPosition += len;
        return;
      } else {
        // copy b to buffer and flush
        if (remaining > 0) {
          Platform.copyMemory(b, Platform.BYTE_ARRAY_OFFSET + off,
              null, addrPos, remaining);
          this.addrPosition += remaining;
          len -= remaining;
          off += remaining;
        }
        flushBufferBlocking(this.buffer);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void write(@Nonnull byte[] b) throws IOException {
    write_(b, 0, b.length);
  }

  protected final void putByte(byte b) throws IOException {
    if (this.addrPosition >= this.addrLimit) {
      flushBufferBlocking(this.buffer);
    }
    Platform.putByte(null, this.addrPosition++, b);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void write(@Nonnull byte[] b,
      int off, int len) throws IOException {
    UnsafeHolder.checkBounds(b.length, off, len);
    write_(b, off, len);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final int write(ByteBuffer src) throws IOException {
    // We will just use our ByteBuffer for the write. It might be possible
    // to get slight performance advantage in using unsafe instead, but
    // copying from source ByteBuffer will not be efficient without
    // reflection to get src's native address in case it is a direct
    // byte buffer. Avoiding the complication since the benefit will be
    // very small in any case (and reflection cost may well offset that).

    if (!isOpen()) {
      throw new ClosedChannelException();
    }

    // adjust this buffer position first
    this.buffer.position((int)(this.addrPosition - this.baseAddress));
    // now we are actually set to just call base class method
    try {
      return super.writeBuffered(src, this.buffer);
    } finally {
      // finally reset the raw positions from buffer
      resetBufferPositions();
    }
  }

  public final int position() {
    return (int)(this.addrPosition - this.baseAddress);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() throws IOException {
    final ByteBuffer buffer;
    if (this.addrPosition > this.baseAddress &&
        (buffer = this.buffer) != null) {
      flushBufferBlocking(buffer);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean isOpen() {
    return this.channel.isOpen();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    flush();
    this.addrPosition = this.addrLimit = 0;
    releaseBuffer();
  }

  public final boolean validBuffer() {
    return this.addrLimit != 0;
  }

  protected final void releaseBuffer() {
    final ByteBuffer buffer = this.buffer;
    if (buffer != null) {
      this.buffer = null;
      UnsafeHolder.releaseDirectBuffer(buffer);
    }
  }

  /**
   * Close the underlying channel in addition to flushing/clearing the buffer.
   */
  public void closeChannel() throws IOException {
    flush();
    this.addrPosition = this.addrLimit = 0;
    this.channel.close();
    releaseBuffer();
  }

  protected void flushBufferBlocking(final ByteBuffer buffer)
      throws IOException {
    buffer.position(position());
    buffer.flip();
    try {
      do {
        writeBuffer(buffer, this.channel);
      } while (buffer.hasRemaining());
    } finally {
      if (buffer.hasRemaining()) {
        buffer.compact();
      } else {
        buffer.clear();
      }
      resetBufferPositions();
    }
  }
}
