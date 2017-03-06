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
 * Changes for SnappyData data platform.
 *
 * Portions Copyright (c) 2016 SnappyData, Inc. All rights reserved.
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

package io.snappydata.thrift.internal;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Arrays;

import com.gemstone.gemfire.internal.shared.ClientSharedData;
import com.pivotal.gemfirexd.internal.shared.common.io.DynamicByteArrayOutputStream;
import com.pivotal.gemfirexd.internal.shared.common.reference.SQLState;
import io.snappydata.thrift.BlobChunk;
import io.snappydata.thrift.SnappyException;
import io.snappydata.thrift.common.BufferedBlob;
import io.snappydata.thrift.common.ThriftExceptionUtil;
import io.snappydata.thrift.snappydataConstants;

public final class ClientBlob extends ClientLobBase implements BufferedBlob {

  private BlobChunk currentChunk;
  private InputStream dataStream;
  private int baseChunkSize;
  private long initOffset;
  private final boolean freeForStream;

  ClientBlob(ClientService service) {
    super(service);
    this.dataStream = new MemInputStream(ClientSharedData.ZERO_ARRAY);
    this.length = 0;
    this.freeForStream = false;
  }

  ClientBlob(InputStream dataStream, long length,
      ClientService service) throws SQLException {
    super(service);
    checkLength(length > 0 ? length : 0);
    this.dataStream = dataStream;
    if (dataStream instanceof FileInputStream) {
      try {
        this.initOffset = ((FileInputStream)dataStream).getChannel().position();
      } catch (IOException ioe) {
        throw ThriftExceptionUtil.newSQLException(
            SQLState.LANG_STREAMING_COLUMN_I_O_EXCEPTION, ioe, "java.sql.Blob");
      }
    }
    this.length = (int)length;
    this.freeForStream = true;
  }

  ClientBlob(BlobChunk firstChunk, ClientService service,
      HostConnection source, boolean freeForStream) throws SQLException {
    super(service, firstChunk.last ? snappydataConstants.INVALID_ID
        : firstChunk.lobId, source);
    this.baseChunkSize = firstChunk.chunk.remaining();
    this.currentChunk = firstChunk;
    this.freeForStream = freeForStream;
    long length = -1;
    if (firstChunk.isSetTotalLength()) {
      length = firstChunk.getTotalLength();
    } else if (firstChunk.last) {
      length = this.baseChunkSize;
    }
    checkLength(length);
    this.length = (int)length;
  }

  public ClientBlob(ByteBuffer buffer) {
    super(null);
    this.currentChunk = new BlobChunk(buffer, true);
    this.streamedInput = false;
    this.length = buffer.remaining();
    this.freeForStream = false;
  }

  @Override
  protected int streamLength(boolean forceMaterialize) throws SQLException {
    try {
      final InputStream dataStream = this.dataStream;
      if (dataStream instanceof MemInputStream) {
        return ((MemInputStream)dataStream).size();
      } else if (!forceMaterialize && dataStream instanceof FileInputStream) {
        // can determine the size of stream for FileInputStream
        long length = ((FileInputStream)dataStream).getChannel().size() -
            initOffset;
        checkLength(length);
        return (int)length;
      } else if (forceMaterialize) {
        // materialize the stream
        final int bufSize = 32768;
        byte[] buffer = new byte[bufSize];
        int readLen = readStream(dataStream, buffer, 0, bufSize);
        if (readLen < bufSize) {
          // fits into single buffer; trim if much smaller than bufSize
          if (readLen <= 0) {
            readLen = 0;
            buffer = ClientSharedData.ZERO_ARRAY;
          } else if (readLen < (bufSize >>> 1)) {
            buffer = Arrays.copyOf(buffer, readLen);
          }
          this.dataStream = new MemInputStream(buffer, readLen);
          return readLen;
        } else {
          DynamicByteArrayOutputStream out = new DynamicByteArrayOutputStream(
              bufSize + (bufSize >>> 1));
          out.write(buffer, 0, bufSize);
          while ((readLen = readStream(dataStream, buffer, 0, bufSize)) > 0) {
            out.write(buffer, 0, readLen);
          }
          int streamSize = out.getUsed();
          this.dataStream = new MemInputStream(out, streamSize);
          out.close(); // no-op to remove a warning
          return streamSize;
        }
      } else {
        return -1;
      }
    } catch (IOException ioe) {
      throw ThriftExceptionUtil.newSQLException(
          SQLState.LANG_STREAMING_COLUMN_I_O_EXCEPTION, ioe, "java.sql.Blob");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void clear() {
    this.currentChunk = null;
    // don't need to do anything to close MemInputStream yet
    this.dataStream = null;
  }

  final int readBytes(final long offset, byte[] b, int boffset, int length)
      throws SQLException {
    BlobChunk chunk;
    checkOffset(offset);
    if (this.streamedInput) {
      try {
        long skipBytes = offset - this.streamOffset;
        if (skipBytes != 0) {
          if (this.dataStream instanceof MemInputStream) {
            ((MemInputStream)this.dataStream).changePosition((int)offset);
          } else if (skipBytes > 0) {
            long skipped;
            while ((skipped = this.dataStream.skip(skipBytes)) > 0) {
              if ((skipBytes -= skipped) <= 0) {
                break;
              }
            }
            if (skipBytes > 0) {
              byte[] buffer = new byte[(int)Math.min(skipBytes, 8192)];
              int readBytes;
              while ((readBytes = this.dataStream.read(buffer)) > 0) {
                if ((skipBytes -= readBytes) <= 0) {
                  break;
                }
              }
            }
            if (skipBytes > 0) {
              // offset beyond the blob
              throw ThriftExceptionUtil.newSQLException(
                  SQLState.BLOB_POSITION_TOO_LARGE, null, offset + 1);
            }
          } else if (skipBytes < 0) {
            if (this.dataStream instanceof FileInputStream) {
              // need to seek into the stream but only for FileInputStream
              FileInputStream fis = (FileInputStream)this.dataStream;
              fis.getChannel().position(offset);
            } else {
              throw ThriftExceptionUtil.newSQLException(
                  SQLState.BLOB_BAD_POSITION, null, offset + 1);
            }
          }
        }
        // keep reading stream till end
        int readLen = readStream(this.dataStream, b, boffset, length);
        if (readLen >= 0) {
          checkOffset(offset + readLen);
          this.streamOffset = (int)(offset + readLen);
          return readLen;
        } else {
          return -1; // end of data
        }
      } catch (IOException ioe) {
        throw ThriftExceptionUtil.newSQLException(
            SQLState.LANG_STREAMING_COLUMN_I_O_EXCEPTION, ioe, "java.sql.Blob");
      }
    } else if ((chunk = this.currentChunk) != null) {
      ByteBuffer buffer = chunk.chunk;
      // check if it lies outside the current chunk
      if (chunk.lobId != snappydataConstants.INVALID_ID &&
          (offset < chunk.offset ||
              (offset + length) > (chunk.offset + buffer.remaining()))) {
        // fetch new chunk
        try {
          this.currentChunk = chunk = service.getBlobChunk(
              getLobSource(true, "Blob.readBytes"), chunk.lobId, offset,
              Math.max(baseChunkSize, length), false);
          buffer = chunk.chunk;
        } catch (SnappyException se) {
          throw ThriftExceptionUtil.newSQLException(se);
        }
      }
      int bpos = buffer.position();
      if (offset > chunk.offset) {
        buffer.position((int)(offset - chunk.offset));
      }
      length = Math.min(length, buffer.remaining());
      if (length > 0) {
        buffer.get(b, boffset, length);
        buffer.position(bpos);
        return length;
      } else {
        return -1; // end of data
      }
    } else {
      throw ThriftExceptionUtil.newSQLException(SQLState.LOB_OBJECT_INVALID);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteBuffer getAsBuffer() throws SQLException {
    BlobChunk chunk = this.currentChunk;
    if (chunk != null && chunk.last && chunk.offset == 0) {
      // the first and only chunk
      return chunk.chunk;
    } else {
      return ByteBuffer.wrap(getBytes(1, (int)length()));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(long pos, int length) throws SQLException {
    final long offset = pos - 1;
    length = checkOffset(offset, length, true);

    if (length > 0) {
      byte[] result = new byte[length];
      int nbytes = readBytes(offset, result, 0, length);
      if (nbytes == length) {
        return result;
      } else {
        return Arrays.copyOf(result, nbytes);
      }
    } else {
      return ClientSharedData.ZERO_ARRAY;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public InputStream getBinaryStream() throws SQLException {
    if (this.streamedInput) {
      return this.dataStream;
    } else if (this.currentChunk != null) {
      return new LobStream(0, this.length);
    } else {
      throw ThriftExceptionUtil.newSQLException(SQLState.LOB_OBJECT_INVALID);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public InputStream getBinaryStream(long pos, long len) throws SQLException {
    if (this.streamedInput || this.currentChunk != null) {
      final long offset = pos - 1;
      len = checkOffset(offset, len, true);
      return new LobStream(offset, len);
    } else {
      throw ThriftExceptionUtil.newSQLException(SQLState.LOB_OBJECT_INVALID);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int setBytes(long pos, byte[] bytes, int boffset, int len)
      throws SQLException {
    checkOffset(pos - 1, len, false);
    if (boffset < 0 || boffset > bytes.length) {
      throw ThriftExceptionUtil.newSQLException(SQLState.BLOB_INVALID_OFFSET,
          null, boffset);
    }

    if (len > 0) {
      MemInputStream ms;
      byte[] sbuffer;
      if (this.streamedInput) {
        if (dataStream instanceof MemInputStream) {
          ms = (MemInputStream)dataStream;
        } else if (streamLength(true) >= 0) {
          ms = (MemInputStream)dataStream;
        } else {
          throw ThriftExceptionUtil.newSQLException(SQLState.NOT_IMPLEMENTED,
              null, "setBytes after get* invocation on stream");
        }
        sbuffer = ms.getBuffer();
      } else {
        // materialize remote blob data
        sbuffer = getBytes(1, this.length);
        @SuppressWarnings("resource")
        MemInputStream mms = new MemInputStream(sbuffer);
        this.currentChunk = null;
        this.streamedInput = true;
        ms = mms;
      }
      final int offset = (int)(pos - 1);
      int newSize = offset + len;
      if (newSize <= sbuffer.length) {
        // just change the underlying buffer and update count if required
        System.arraycopy(bytes, boffset, sbuffer, offset, len);
        ms.changeSize(newSize);
      } else {
        // create new buffer and set into input stream
        sbuffer = Arrays.copyOf(sbuffer, newSize);
        System.arraycopy(bytes, boffset, sbuffer, offset, len);
        ms.changeBuffer(sbuffer);
        ms.changeSize(newSize);
      }
      this.length = newSize;
      this.dataStream = ms;
      return len;
    } else {
      return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int setBytes(long pos, byte[] bytes) throws SQLException {
    return setBytes(pos, bytes, 0, bytes.length);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OutputStream setBinaryStream(long pos) throws SQLException {
    if (pos == 1 && this.length == 0) {
      free();
      DynamicByteArrayOutputStream out = new DynamicByteArrayOutputStream();
      this.dataStream = new MemInputStream(out, 0);
      this.streamedInput = true;
      return out;
    } else {
      checkOffset(pos - 1);
      throw ThriftExceptionUtil.newSQLException(
          SQLState.NOT_IMPLEMENTED, null, "Blob.setBinaryStream(" + pos + ')');
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long position(byte[] pattern, long start) throws SQLException {
    // TODO: implement
    throw ThriftExceptionUtil.newSQLException(
        SQLState.NOT_IMPLEMENTED, null, "Blob.position");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long position(Blob pattern, long start) throws SQLException {
    // TODO: implement
    throw ThriftExceptionUtil.newSQLException(
        SQLState.NOT_IMPLEMENTED, null, "Blob.position");
  }

  final class LobStream extends InputStream {

    private long blobOffset;
    private final long length;
    private final byte[] singleByte;

    LobStream(long offset, long length) {
      this.blobOffset = offset;
      this.length = length;
      this.singleByte = new byte[1];
    }

    int read_(byte[] b, int offset, int len) throws IOException {
      if (this.length >= 0) {
        long remaining = this.length - this.blobOffset;
        if (remaining < Integer.MAX_VALUE) {
          len = Math.min((int)remaining, len);
        }
      }
      try {
        int nbytes;
        if (len > 0 && (nbytes = readBytes(this.blobOffset,
            b, offset, len)) >= 0) {
          this.blobOffset += nbytes;
          return nbytes;
        } else {
          return -1; // end of data
        }
      } catch (SQLException sqle) {
        if (sqle.getCause() instanceof IOException) {
          throw (IOException)sqle.getCause();
        } else {
          throw new IOException(sqle.getMessage(), sqle);
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
      if (read_(this.singleByte, 0, 1) == 1) {
        return this.singleByte[0];
      } else {
        return -1;
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(@SuppressWarnings("NullableProblems") byte[] b,
        int offset, int len) throws IOException {
      if (b == null) {
        throw new NullPointerException();
      } else if (offset < 0 || len < 0 || len > (b.length - offset)) {
        throw new IndexOutOfBoundsException();
      }

      if (len != 0) {
        return read_(b, offset, len);
      } else {
        return 0;
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long n) throws IOException {
      if (n > 0) {
        if (this.length >= 0) {
          n = Math.min(this.length - this.blobOffset, n);
          if (n <= 0) {
            return 0;
          }
        }
        if (streamedInput) {
          n = dataStream.skip(n);
          if (n > 0) {
            this.blobOffset += n;
          }
        } else if (currentChunk != null) {
          this.blobOffset += n;
        } else {
          return 0;
        }
        return n;
      }
      return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() throws IOException {
      BlobChunk chunk;
      if (streamedInput) {
        return dataStream.available();
      } else if ((chunk = currentChunk) != null) {
        long coffset = this.blobOffset - chunk.offset;
        if (coffset >= 0 && coffset < Integer.MAX_VALUE) {
          int remaining = chunk.chunk.remaining() - (int)coffset;
          if (remaining >= 0) {
            return remaining;
          }
        }
        return 0;
      } else {
        return 0;
      }
    }

    @Override
    public void close() throws IOException {
      if (freeForStream) {
        free();
      }
    }
  }
}
