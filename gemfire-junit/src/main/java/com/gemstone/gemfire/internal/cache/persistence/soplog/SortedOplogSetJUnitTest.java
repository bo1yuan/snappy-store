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
package com.gemstone.gemfire.internal.cache.persistence.soplog;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.gemstone.gemfire.internal.cache.persistence.soplog.SortedOplogSet.FlushHandler;
import com.gemstone.gemfire.internal.cache.persistence.soplog.SortedReader.SortedIterator;
import com.gemstone.gemfire.internal.cache.persistence.soplog.hfile.HFileSortedOplogFactory;
import com.gemstone.gemfire.internal.util.LogService;

public class SortedOplogSetJUnitTest extends SortedReaderTestCase {
  private SortedOplogSet set;
  
  public void testMergedIterator() throws IOException {
    FlushCounter handler = new FlushCounter();
    SortedOplogSet sos = createSoplogSet("merge");
    
    // #1
    sos.put(wrapInt(1), wrapInt(-1));
    sos.put(wrapInt(2), wrapInt(-1));
    sos.put(wrapInt(3), wrapInt(-1));
    sos.put(wrapInt(4), wrapInt(-1));
    sos.flush(null, handler);
    
    // #2
    sos.put(wrapInt(2), wrapInt(2));
    sos.put(wrapInt(4), wrapInt(-1));
    sos.put(wrapInt(6), wrapInt(6));
    sos.put(wrapInt(8), wrapInt(8));
    sos.flush(null, handler);
    
    // #3
    sos.put(wrapInt(1), wrapInt(-1));
    sos.put(wrapInt(3), wrapInt(3));
    sos.put(wrapInt(5), wrapInt(-1));
    sos.put(wrapInt(7), wrapInt(7));
    sos.put(wrapInt(9), wrapInt(9));
    sos.flush(null, handler);
    
    // #4
    sos.put(wrapInt(0), wrapInt(0));
    sos.put(wrapInt(1), wrapInt(1));
    sos.put(wrapInt(4), wrapInt(4));
    sos.put(wrapInt(5), wrapInt(5));

    while (!handler.flushes.compareAndSet(3, 0));
    
    // the iteration pattern for this test should be 0-9:
    // 0 1 4 5   sbuffer #4
    // 1 3 5 7 9 soplog #3
    // 2 4 6 8   soplog #2
    // 1 2 3 4   soplog #1
    List<Integer> result = new ArrayList<Integer>();
    SortedIterator<ByteBuffer> iter = sos.scan();
    try {
      while (iter.hasNext()) {
        ByteBuffer key = iter.next();
        ByteBuffer val = iter.value();
        assertEquals(key, val);
        
        result.add(key.getInt());
      }
    } finally {
      iter.close();
    }

    sos.close();
    
    assertEquals(10, result.size());
    for (int i = 0; i < 10; i++) {
      assertEquals(i, result.get(i).intValue());
    }
  }

  @Override
  protected SortedReader<ByteBuffer> createReader(NavigableMap<byte[], byte[]> data) 
      throws IOException {
    set = createSoplogSet("test");
    
    int i = 0;
    int flushes = 0;
    FlushCounter fc = new FlushCounter();
    
    for (Entry<byte[], byte[]> entry : data.entrySet()) {
      set.put(entry.getKey(), entry.getValue());
      if (i++ % 13 == 0) {
        flushes++;
        set.flush(null, fc);
      }
    }
    
    while (!fc.flushes.compareAndSet(flushes, 0));
    return set;
  }
  
  public void testClear() throws IOException {
    set.clear();
    validateEmpty(set);
  }
  
  public void testDestroy() throws IOException {
    set.destroy();
    assertTrue(((SortedOplogSetImpl) set).isClosed());
    try {
      set.scan();
      fail();
    } catch (IllegalStateException e) { }
  }
  
  public void testClearInterruptsFlush() throws Exception {
    FlushCounter handler = new FlushCounter();
    SortedOplogSetImpl sos = prepSoplogSet("clearDuringFlush");
    
    sos.testDelayDuringFlush = new CountDownLatch(1);
    sos.flush(null, handler);
    sos.clear();
    
    flushAndWait(handler, sos);
    validateEmpty(sos);
    assertEquals(2, handler.flushes.get());
  }
  
  public void testClearRepeat() throws Exception {
    int i = 0;
    do {
      testClearInterruptsFlush();
      LogService.logger().fine("Test " + i + " is complete");
      tearDown();
      setUp();
    } while (i++ < 100);
 }
  
  public void testCloseInterruptsFlush() throws Exception {
    FlushCounter handler = new FlushCounter();
    SortedOplogSetImpl sos = prepSoplogSet("closeDuringFlush");
    
    sos.testDelayDuringFlush = new CountDownLatch(1);
    sos.flush(null, handler);
    sos.close();
    
    assertTrue(sos.isClosed());
    assertEquals(1, handler.flushes.get());
  }

  public void testDestroyInterruptsFlush() throws Exception {
    FlushCounter handler = new FlushCounter();
    SortedOplogSetImpl sos = prepSoplogSet("destroyDuringFlush");
    
    sos.testDelayDuringFlush = new CountDownLatch(1);
    sos.flush(null, handler);
    sos.destroy();
    
    assertTrue(sos.isClosed());
    assertEquals(1, handler.flushes.get());
  }

  public void testScanAfterClear() throws IOException {
    SortedIterator<ByteBuffer> iter = set.scan();
    set.clear();
    assertFalse(iter.hasNext());
  }

  public void testScanAfterClose() throws IOException {
    SortedIterator<ByteBuffer> iter = set.scan();
    set.close();
    assertFalse(iter.hasNext());
  }
  
  public void testEmptyFlush() throws Exception {
    FlushCounter handler = new FlushCounter();
    SortedOplogSet sos = prepSoplogSet("empty");
    
    flushAndWait(handler, sos);
    flushAndWait(handler, sos);
  }
  
  public void testErrorDuringFlush() throws Exception {
    FlushCounter handler = new FlushCounter();
    handler.error.set(true);
    
    SortedOplogSetImpl sos = prepSoplogSet("err");
    sos.testErrorDuringFlush = true;
    
    flushAndWait(handler, sos);
  }
  
  protected void validateEmpty(SortedOplogSet sos) throws IOException {
    assertEquals(0, sos.bufferSize());
    assertEquals(0, sos.unflushedSize());
    
    SortedIterator<ByteBuffer> iter = sos.scan();
    assertFalse(iter.hasNext());
    iter.close();
    sos.close();
  }
  
  protected SortedOplogSetImpl prepSoplogSet(String name) throws IOException {
    SortedOplogSetImpl sos = createSoplogSet(name);

    sos.put(wrapInt(1), wrapInt(1));
    sos.put(wrapInt(2), wrapInt(1));
    sos.put(wrapInt(3), wrapInt(1));
    sos.put(wrapInt(4), wrapInt(1));
    
    return sos;
  }
  
  protected SortedOplogSetImpl createSoplogSet(String name) throws IOException {
    SortedOplogFactory factory = new HFileSortedOplogFactory(name, null, new SortedOplogStatistics("stats", name), new HFileStoreStatistics("storeStats", name));
    Compactor compactor = new NonCompactor(name, new File("."));
    
    return new SortedOplogSetImpl(factory, Executors.newSingleThreadExecutor(), compactor);
  }

  protected void flushAndWait(FlushCounter handler, SortedOplogSet sos)
      throws InterruptedException, IOException {
    sos.flush(null, handler);
    while (sos.unflushedSize() > 0) {
      Thread.sleep(1000);
    }
  }

  protected static class FlushCounter implements FlushHandler {
    private final AtomicInteger flushes = new AtomicInteger(0);
    private final AtomicBoolean error = new AtomicBoolean(false);
    
    @Override 
    public void complete() {
      LogService.logger().fine("Flush complete! " + this);
      assertFalse(error.get());
      flushes.incrementAndGet();
    }
    
    @Override 
    public void error(Throwable t) {
      if (!error.get()) {
        t.printStackTrace();
        fail(t.getMessage());
      }
    }
  }
}
